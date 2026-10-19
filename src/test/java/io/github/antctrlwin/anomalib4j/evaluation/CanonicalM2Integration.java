package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class CanonicalM2Integration {
    private CanonicalM2Integration() { }
    public static void main(String[] args) throws Exception {
        Path phase2 = Path.of(args[0]), dataset = Path.of(args[1]), output = Path.of(args[2]);
        if (!ComparisonArtifacts.JSON.readTree(phase2.resolve("selection.json").toFile()).path("selected_sampler").asText().equals("B")
                || !Files.exists(phase2.resolve("complete.json"))) throw new IllegalStateException("Completed B evidence required");
        var oldSources = ComparisonArtifacts.JSON.readTree(phase2.resolve("source-hashes.json").toFile());
        for (var fields = oldSources.fields(); fields.hasNext();) {
            var entry = fields.next();
            if (entry.getKey().startsWith("src/")) requireHash(Path.of(entry.getKey()), entry.getValue().asText());
        }
        Files.createDirectory(output);
        var sources = snapshot(Path.of("src"));
        ComparisonArtifacts.json(output.resolve("source-hashes.json"), sources);
        var inputs = new TreeMap<String, String>();
        var oldInputs = ComparisonArtifacts.JSON.readTree(phase2.resolve("input-hashes.json").toFile());
        for (var fields = oldInputs.fields(); fields.hasNext();) {
            var entry = fields.next(); Path path = Path.of(entry.getKey());
            requireHash(path, entry.getValue().asText()); inputs.put(path.toString(), entry.getValue().asText());
        }
        inputs.putAll(snapshot(phase2.resolve("B")));
        for (String file : List.of("selection.json", "complete.json", "source-inputs.json", "source-hashes.json", "input-hashes.json")) {
            Path path = phase2.resolve(file); inputs.put(path.toString(), NpyMap.sha256(path));
        }
        ComparisonArtifacts.json(output.resolve("input-hashes.json"), inputs);
        var manifest = ComparisonArtifacts.JSON.readTree(phase2.resolve("source-inputs.json").toFile());
        var roles = new LinkedHashMap<String, List<JsonNode>>();
        for (String role : List.of("fit", "calibration", "test")) roles.put(role, new ArrayList<>());
        for (var row : manifest) if (roles.containsKey(row.path("role").asText())) roles.get(row.path("role").asText()).add(row);
        int[] counts = {176,44,115}; int roleIndex = 0;
        var seen = new HashSet<String>();
        for (var entry : roles.entrySet()) {
            if (entry.getValue().size() != counts[roleIndex++]) throw new IllegalArgumentException("Frozen role count mismatch");
            List<String> names = entry.getValue().stream().map(r -> r.path("image").asText()).toList();
            if (!names.equals(Files.readAllLines(phase2.resolve("B/inputs/"+entry.getKey()+".txt"))))
                throw new IllegalStateException("Frozen role ordering mismatch");
            for (var row : entry.getValue()) {
                String name = row.path("image").asText();
                String splitIdentity = (entry.getKey().equals("test") ? "test/" : "train/")+name;
                if (!seen.add(splitIdentity) || !name.equals(Path.of(name).getFileName().toString())) throw new IllegalArgumentException("Duplicate/unsafe identity");
                Path source = source(dataset, entry.getKey(), row);
                requireHash(source,row.path("sha256").asText());
                inputs.put(source.toString(),row.path("sha256").asText());
            }
        }
        ComparisonArtifacts.json(output.resolve("verified-input-hashes.json"), inputs);
        var fitPaths = roles.get("fit").stream().map(r -> source(dataset,"fit",r)).toList();
        var calPaths = roles.get("calibration").stream().map(r -> source(dataset,"calibration",r)).toList();
        System.out.println("CANONICAL M2 - FIT/CAL - started - 176/44 source images");
        var bundle = CanonicalM2Trainer.fit(fitPaths,calPaths);
        Path archive = output.resolve("metal-nut.canonical-m2");
        CanonicalM2BundleStore.write(archive,bundle);
        bundle = CanonicalM2BundleStore.read(archive,M2ModelStore.encoderHash());
        var reference = M2ModelStore.read(phase2.resolve("B/model/model.bin"),M2ModelStore.encoderHash());
        requireExact(bundle.model().local().mean(),reference.local().mean(),"FIT mean");
        requireExact(bundle.model().local().effectiveStd(),reference.local().effectiveStd(),"FIT std");
        requireExact(bundle.model().calibration().alpha(),reference.calibration().alpha(),"CAL alpha");
        requireExact(bundle.model().calibration().beta(),reference.calibration().beta(),"CAL beta");
        System.out.println("CANONICAL M2 - FIT/CAL/PERSIST - exact state parity");
        Path evaluation = output.resolve("evaluation"), evaluationInputs = output.resolve("inputs");
        Files.createDirectory(evaluation); Files.createDirectory(evaluationInputs);
        var tests = roles.get("test");
        var retained = NpyMap.read(phase2.resolve("B/model/native-z.npy"));
        var retainedScores = NpyMap.read(phase2.resolve("B/model/image-scores.npy"));
        double[] maps = new double[tests.size()*196], matrices = new double[tests.size()*6];
        var inferenceRows = new ArrayList<Map<String,Object>>();
        try (var detector = new CanonicalM2Detector(bundle)) {
            for (int i = 0; i < tests.size(); i++) {
                var row = tests.get(i); var image = ImageIO.read(source(dataset,"test",row).toFile());
                var result = detector.inferWithMap(image);
                double fast = detector.detectScore(image);
                double[] z = result.nativeMap();
                requireExact(z,Arrays.copyOfRange(retained.values(),i*196,(i+1)*196),"TEST map "+i);
                if (Double.compare(fast,result.score()) != 0 || Double.compare(fast,retainedScores.values()[i]) != 0)
                    throw new IllegalStateException("Native score parity failed");
                var a = result.sourceToCanonical();
                double[] matrix = {a.cos(),-a.sin(),a.tx(),a.sin(),a.cos(),a.ty()};
                double[] expected = new double[6];
                for (int j = 0; j < 6; j++) expected[j] = row.path("expected").path("forward").get(j).asDouble();
                for (int j = 0; j < 6; j++) if (Math.abs(matrix[j]-expected[j]) > 1e-6) throw new IllegalStateException("Transform mismatch");
                System.arraycopy(z,0,maps,i*196,196); System.arraycopy(matrix,0,matrices,i*6,6);
                inferenceRows.add(Map.of("image",row.path("image").asText(),"score",fast,"transform",a,"model_identity",result.modelIdentity()));
                image.flush();
            }
        }
        Files.write(evaluationInputs.resolve("test.txt"), tests.stream().map(r -> r.path("image").asText()).toList(),StandardOpenOption.CREATE_NEW);
        ComparisonArtifacts.writeMap(evaluationInputs.resolve("test-transforms.npy"),matrices,tests.size(),6,"TEST","source-to-canonical","affine");
        ComparisonArtifacts.writeMap(evaluation.resolve("native-z.npy"),maps,tests.size(),196,"TEST","canonical-B","native");
        ComparisonArtifacts.json(output.resolve("inferences.json"),inferenceRows);
        ComparisonArtifacts.json(output.resolve("contract.json"),CanonicalM2Contract.fields());
        ComparisonArtifacts.json(evaluation.resolve("config.json"),Map.of("format",CanonicalM2BundleStore.FORMAT,"model_identity",bundle.identity(),
                "archive_sha256",NpyMap.sha256(archive),"preprocessing",bundle.preprocessingId(),"test_map_parity","bit-exact"));
        System.out.println("CANONICAL M2 - TEST - 115/115 exact fast scores/native maps; evaluating");
        CanonicalPilotBridge.main(new String[]{"evaluate",dataset.toString(),evaluation.toString(),evaluationInputs.toString()});
        var actual = ComparisonArtifacts.JSON.readTree(evaluation.resolve("metrics-unified.json").toFile());
        var expected = ComparisonArtifacts.JSON.readTree(phase2.resolve("B/model/metrics-unified.json").toFile());
        for (String key : List.of("image_auroc","pixel_auroc","aupro030","n_good","n_anomaly","regions","foreground_pixels","background_pixels"))
            if (Double.compare(actual.path(key).asDouble(),expected.path(key).asDouble()) != 0) throw new IllegalStateException("Metric/count mismatch: "+key);
        for (var entry : sources.entrySet()) requireHash(Path.of(entry.getKey()),entry.getValue());
        for (var entry : inputs.entrySet()) requireHash(Path.of(entry.getKey()),entry.getValue());
        var completion = new LinkedHashMap<String,Object>();
        completion.putAll(Map.of("fit",176,"calibration",44,"test",115,"state_max_error",0,"native_map_max_error",0,
                "score_max_error",0,"metric_max_error",0,"model_identity",bundle.identity(),"bundle_bytes",Files.size(archive),"source_entries",sources.size()));
        completion.put("verified_inputs",inputs.size());
        ComparisonArtifacts.json(output.resolve("quality-complete.json"),completion);
        System.out.println("CANONICAL M2 - QUALITY - PASS; exact state/maps/scores/metrics");
    }
    private static Path source(Path dataset, String role, JsonNode row) {
        return dataset.resolve(role.equals("test") ? "test/img" : "train/img").resolve(row.path("image").asText());
    }
    static Map<String,String> snapshot(Path root) throws Exception {
        var hashes = new TreeMap<String,String>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) hashes.put(path.toString(),NpyMap.sha256(path));
        }
        return hashes;
    }
    private static void requireHash(Path path, String hash) throws Exception {
        if (!NpyMap.sha256(path).equals(hash)) throw new IllegalStateException("Source/evidence changed: "+path);
    }
    private static void requireExact(double[] actual, double[] expected, String label) {
        if (!Arrays.equals(actual,expected)) throw new IllegalStateException("Unexpected Phase 2 B difference: "+label);
    }
}
