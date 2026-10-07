package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ComparisonRunner {
    private ComparisonRunner() { }

    public static void main(String[] args) throws Exception {
        String action = args[0];
        Path dataset = Path.of(args[1]);
        Path directory = Path.of(args[2]);
        Path manifests = Path.of(args[3]);
        String category = args[4];
        boolean smoke = Boolean.parseBoolean(args[5]);
        int size = switch (category) { case "bottle" -> 900; case "metal_nut" -> 700;
            default -> throw new IllegalArgumentException("Unsupported category"); };
        if (args.length > 6) {
            Path gate = Path.of(args[6]);
            while (!Files.exists(gate)) Thread.sleep(20);
        }
        Files.createDirectories(directory);
        switch (action) {
            case "fit" -> fit(dataset, directory, manifests, category, smoke);
            case "infer" -> infer(dataset, directory, manifests, category, size, smoke);
            case "memory" -> {
                fit(dataset, directory, manifests, category, smoke);
                ComparisonModel.Loaded model = ComparisonModel.load(directory);
                try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
                    BufferedImage image = read(dataset.resolve((smoke ? "train" : "test") + "/img/" + category + "_good_000.png"));
                    ComparisonArtifacts.phase(directory, "warmup");
                    repeat(model, encoder, image, smoke ? .1 : 10);
                    ComparisonArtifacts.phase(directory, "steady");
                    repeat(model, encoder, image, smoke ? .2 : 10);
                    ComparisonArtifacts.phase(directory, "complete");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }
    }

    static void fit(Path dataset, Path directory, Path manifests, String category, boolean smoke) throws Exception {
        ComparisonArtifacts.phase(directory, "initialization");
        long start = System.nanoTime();
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            var descriptor = new ModelDescriptor(category + "-spatial14-heldout-v1", encoder.modelId(),
                    encoder.variant().name(), encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(96, 10_000, 42L);
            long initialized = System.nanoTime();
            ComparisonArtifacts.phase(directory, "fitting");
            List<Path> fit = paths(dataset, manifests.resolve("anomalib4j-fit.txt"), "train", smoke);
            List<Path> calibration = paths(dataset, manifests.resolve("anomalib4j-calibration.txt"), "train", smoke);
            var model = new NormalImageTrainer(encoder, descriptor, projection, 1e-8).train(fit);
            ComparisonArtifacts.phase(directory, "calibration");
            var calibrationRaw = new ArrayList<double[]>();
            for (Path path : calibration) {
                BufferedImage image = read(path);
                try { calibrationRaw.add(HeldOutBottleCalibration.rawScores(model.filters(), encoder.extract(image))); }
                finally { image.flush(); }
            }
            var z = PositionalRawCalibration.fit(descriptor, calibrationRaw, 1e-6);
            long ready = System.nanoTime();
            ComparisonArtifacts.phase(directory, "ready");
            ComparisonArtifacts.json(directory.resolve("fitting.json"), Map.of(
                    "initialization_seconds", (initialized - start) / 1e9, "fitting_and_calibration_seconds", (ready - initialized) / 1e9,
                    "ready_to_infer_seconds", (ready - start) / 1e9, "training_decode_io", "included, two fit passes plus calibration pass",
                    "fit_count", fit.size(), "calibration_count", calibration.size(), "fit_batch", 1,
                    "process_startup_imports", "excluded", "smoke", smoke));
            ComparisonModel.write(directory, model, calibrationRaw);
            Files.writeString(directory.resolve("parameters.csv"), z.parametersCsv(), StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("parameter-summary.csv"), z.summaryCsv(), StandardOpenOption.CREATE_NEW);
            ComparisonArtifacts.json(directory.resolve("payload.json"), Map.of(
                    "backbone_file_bytes", Files.size(Path.of("src/main/resources/models/mobilenetv4_spatial_14x14.onnx")),
                    "compiled_filter_logical_bytes", (long) (descriptor.grid().elements() + descriptor.grid().cells()) * 8,
                    "calibration_logical_bytes", (long) descriptor.grid().cells() * 3 * 8,
                    "serialized_learning_archive_bytes", Files.size(directory.resolve("learning-state.bin")),
                    "note", "Archive includes training archetypes/statistics and calibration observations; those are not required resident inference state"));
            ComparisonArtifacts.json(directory.resolve("runtime.json"), Map.of(
                    "pid", ProcessHandle.current().pid(), "process_affinity", ComparisonArtifacts.affinity(), "java", System.getProperty("java.version"),
                    "heap_max_bytes", Runtime.getRuntime().maxMemory(), "ort_workers", "unknown: default SessionOptions",
                    "jvm_args", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        }
    }

    static void infer(Path dataset, Path directory, Path manifests, String category, int size, boolean smoke) throws Exception {
        var model = ComparisonModel.load(directory);
        List<Path> images = paths(dataset, manifests.resolve(smoke ? "competitor-fit.txt" : "test.txt"), smoke ? "train" : "test", smoke);
        String runId = directory.getParent().getParent().getFileName().toString();
        String geometry = category + "-" + size + "x" + size + "-14x14";
        var csv = new StringBuilder("run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n");
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            for (Path path : images) {
                BufferedImage image = read(path);
                if (image.getWidth() != size || image.getHeight() != size) throw new IllegalArgumentException("Unexpected image geometry");
                var scores = HeldOutBottleCalibration.score(model.filters(), encoder.extract(image), model.calibration());
                String filename = path.getFileName().toString();
                String stem = filename.replace(".png", "");
                Path nativePath = Path.of("maps", stem + ".native.npy");
                Path evaluatedPath = Path.of("maps", stem + ".evaluated.npy");
                ComparisonArtifacts.writeMap(directory.resolve(nativePath), scores.z(), 14, 14, filename, geometry, "native");
                ComparisonArtifacts.writeMap(directory.resolve("maps/" + stem + ".raw.npy"), scores.raw(), 14, 14, filename, geometry, "native");
                ComparisonArtifacts.writeMap(directory.resolve(evaluatedPath), LocalizationMaps.upsample(scores.z(), 14, 14, size, size), size, size, filename, geometry, "evaluated");
                String defect = EvaluationLabels.defect(filename);
                csv.append(String.join(",", runId, "anomalib4j", category, filename, defect.equals("good") ? "good" : "anomaly", defect,
                        Double.toString(BottleEvaluation.summarize(scores.z()).max()), "positional_z", nativePath.toString().replace('\\', '/'),
                        evaluatedPath.toString().replace('\\', '/'), "float64", "14x14", geometry, NpyMap.sha256(path))).append('\n');
                image.flush();
            }
        }
        Files.writeString(directory.resolve("predictions.csv"), csv.toString(), StandardOpenOption.CREATE_NEW);
    }

    static List<Path> paths(Path dataset, Path manifest, String role, boolean smoke) throws Exception {
        List<String> names = Files.readAllLines(manifest).stream().filter(s -> !s.isBlank()).toList();
        if (smoke) names = names.subList(0, 2);
        return names.stream().map(name -> dataset.resolve(role + "/img/" + name)).toList();
    }

    static BufferedImage read(Path path) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Cannot decode " + path);
        return image;
    }

    private static volatile double consumed;
    private static void repeat(ComparisonModel.Loaded model, OnnxMobileNetV4Encoder encoder, BufferedImage image, double seconds) throws Exception {
        long until = System.nanoTime() + (long) (seconds * 1e9);
        do {
            var scores = HeldOutBottleCalibration.score(model.filters(), encoder.extract(image), model.calibration());
            double[] full = LocalizationMaps.upsample(scores.z(), 14, 14, image.getWidth(), image.getHeight());
            consumed = BottleEvaluation.summarize(scores.z()).max() + full[full.length / 2];
        } while (System.nanoTime() < until);
    }
}
