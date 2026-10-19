package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class CanonicalPhase2Bridge {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CanonicalPhase2Bridge() { }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "extract" -> extract(args[1], Path.of(args[2]), Path.of(args[3]));
            case "defects" -> defects(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            default -> throw new IllegalArgumentException("Expected extract or defects");
        }
    }

    static AffineTensorSampler.Kernel kernel(String id) {
        return switch (id) {
            case "B" -> AffineTensorSampler.Kernel.BILINEAR;
            case "C" -> AffineTensorSampler.Kernel.BICUBIC;
            case "D" -> AffineTensorSampler.Kernel.WIDENED_TENT;
            default -> throw new IllegalArgumentException("Frozen B/C/D candidate required");
        };
    }

    static void requireGeometry(CanonicalGeometry pose, JsonNode expected) {
        if (pose.status() != CanonicalGeometry.Status.SUCCESS || !expected.path("status").asText().equals("success"))
            throw new IllegalStateException("Source geometry failure; no fallback");
        if (!pose.reason().name().equalsIgnoreCase(expected.path("reason").asText())
                || Math.abs(pose.cx()-expected.path("cx").asDouble()) > 1e-6
                || Math.abs(pose.cy()-expected.path("cy").asDouble()) > 1e-6
                || Math.abs(pose.angle()-expected.path("angle").asDouble()) > 1e-6)
            throw new IllegalStateException("Frozen source pose/branch mismatch");
        var a = pose.transform();
        double[] coefficients = {a.cos(), -a.sin(), a.tx(), a.sin(), a.cos(), a.ty()};
        var forward = expected.path("forward");
        if (forward.size() != 6) throw new IllegalArgumentException("Expected six affine coefficients");
        for (int i = 0; i < 6; i++)
            if (!forward.get(i).isNumber() || !Double.isFinite(forward.get(i).asDouble())
                    || Math.abs(coefficients[i]-forward.get(i).asDouble()) > 1e-6)
                throw new IllegalStateException("Frozen source affine mismatch");
    }

    private static void extract(String candidate, Path manifest, Path output) throws Exception {
        var preprocessor = new CanonicalPreprocessor(kernel(candidate));
        var rows = JSON.readTree(manifest.toFile());
        var byRole = new LinkedHashMap<String, List<JsonNode>>();
        for (String role : List.of("fit", "calibration", "test", "rotation")) byRole.put(role, new ArrayList<>());
        for (JsonNode row : rows) {
            var group = byRole.get(row.path("role").asText());
            if (group == null || row.path("image").asText().isBlank()) throw new IllegalArgumentException("Invalid source manifest");
            group.add(row);
        }
        Files.createDirectory(output);
        float[] tensor = new float[AffineTensorSampler.ELEMENTS];
        var poses = new ArrayList<Map<String, Object>>();
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            for (var entry : byRole.entrySet()) {
                String role = entry.getKey(); var inputs = entry.getValue();
                if (inputs.isEmpty()) continue;
                double[] units = new double[inputs.size()*M2Model.ELEMENTS], matrices = new double[inputs.size()*6];
                var names = new ArrayList<String>();
                for (int n = 0; n < inputs.size(); n++) {
                    JsonNode row = inputs.get(n); Path source = Path.of(row.path("source").asText());
                    if (!NpyMap.sha256(source).equals(row.path("sha256").asText())) throw new IllegalStateException("Source bytes changed");
                    var image = ImageIO.read(source.toFile());
                    var pose = preprocessor.prepare(image, tensor);
                    poses.add(Map.of("role", role, "image", row.path("image").asText(), "geometry", pose));
                    JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("geometry.json").toFile(), poses);
                    requireGeometry(pose, row.path("expected"));
                    var a = pose.transform();
                    double[] forward = {a.cos(), -a.sin(), a.tx(), a.sin(), a.cos(), a.ty()};
                    System.arraycopy(forward, 0, matrices, 6*n, 6);
                    double[] unit = M2Model.normalize(encoder.extractPrepared(tensor));
                    System.arraycopy(unit, 0, units, M2Model.ELEMENTS*n, M2Model.ELEMENTS);
                    names.add(row.path("image").asText());
                }
                Files.write(output.resolve(role+".txt"), names, StandardOpenOption.CREATE_NEW);
                ComparisonArtifacts.writeMap(output.resolve(role+".npy"), units, inputs.size(), M2Model.ELEMENTS, candidate, preprocessor.preprocessingId(), "unit-features");
                ComparisonArtifacts.writeMap(output.resolve(role+"-transforms.npy"), matrices, inputs.size(), 6, candidate, "source-to-canonical", "affine");
                System.out.println("PHASE2 - "+candidate+" - EXTRACT "+role+" - complete - "+inputs.size());
            }
        }
        ComparisonArtifacts.json(output.resolve("preprocessing.json"), Map.of("candidate", candidate,
                "id", preprocessor.preprocessingId(), "records", poses.size(), "source_pose_parity", true));
    }

    private static void defects(Path directory, Path dataset, Path output) throws Exception {
        var rows = Files.readAllLines(directory.resolve("predictions.csv")).stream().skip(1).map(s -> s.split(",", -1)).toList();
        var types = new TreeSet<String>();
        for (String[] row : rows) if (!row[5].equals("good")) types.add(row[5]);
        var metadata = new DatasetMetadata(dataset);
        var results = new ArrayList<Map<String, Object>>();
        for (String defect : types) {
            int pixels = 0;
            for (String[] row : rows) if (row[5].equals("good") || row[5].equals(defect)) pixels += metadata.inspect("test", row[3]).pixels();
            var metrics = new LocalizationMetrics(pixels);
            var good = new ArrayList<Double>(); var anomaly = new ArrayList<Double>();
            for (String[] row : rows) if (row[5].equals("good") || row[5].equals(defect)) {
                var info = metadata.inspect("test", row[3]);
                boolean normal = row[5].equals("good");
                (normal ? good : anomaly).add(Double.parseDouble(row[6]));
                Path path = directory.resolve(row[9]).normalize();
                if (!path.startsWith(directory.normalize())) throw new IllegalArgumentException("Map path escaped source case");
                var map = NpyMap.read(path);
                ComparisonEvaluator.requireMap(path, map, NpyMap.readSidecar(path), row[3], row[12], info.width(), info.height());
                boolean[] mask = DatasetNinjaMasks.read(dataset.resolve("test/ann/"+row[3]+".json"), info.width(), info.height(), normal);
                metrics.add(map.values(), mask, info.width(), info.height());
            }
            var calculated = metrics.calculate();
            var result = new LinkedHashMap<String, Object>();
            result.putAll(Map.of("defect", defect, "n_good", good.size(), "n_anomaly", anomaly.size(),
                    "image_auroc", BottleEvaluation.auroc(good.stream().mapToDouble(Double::doubleValue).toArray(), anomaly.stream().mapToDouble(Double::doubleValue).toArray()),
                    "pixel_auroc", calculated.pixelAuRoc(), "aupro030", calculated.auPro(), "regions", calculated.regionCount()));
            result.put("foreground_pixels", calculated.positivePixels()); result.put("background_pixels", calculated.negativePixels());
            results.add(result);
        }
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        ComparisonArtifacts.json(output, results);
        System.out.println("PHASE2 - PER DEFECT - complete - "+types.size());
    }
}
