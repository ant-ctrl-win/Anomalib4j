package io.github.antctrlwin.anomalib4j.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

public final class ComparisonEvaluator {
    private ComparisonEvaluator() { }

    public static void main(String[] args) throws Exception {
        evaluate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), args[3], args[4]);
    }

    static void evaluate(Path directory, Path dataset, Path manifest, String method, String category) throws Exception {
        int size = category.equals("bottle") ? 900 : 700;
        var expected = Files.readAllLines(manifest).stream().filter(s -> !s.isBlank()).toList();
        var lines = Files.readAllLines(directory.resolve("predictions.csv"));
        if (lines.size() != expected.size() + 1) throw new IllegalArgumentException("Predictions/test manifest cardinality mismatch");
        var metrics = new LocalizationMetrics(Math.multiplyExact(expected.size(), size * size));
        var good = new ArrayList<Double>();
        var anomaly = new ArrayList<Double>();
        var seen = new HashSet<String>();
        for (int i = 1; i < lines.size(); i++) {
            String[] row = lines.get(i).split(",", -1);
            String filename = row[3];
            if (!expected.contains(filename) || !seen.add(filename) || !row[1].equals(method)
                    || !row[2].equals(category) || !EvaluationLabels.category(filename).equals(category)) throw new IllegalArgumentException("Prediction identity mismatch");
            String defect = EvaluationLabels.defect(filename);
            boolean normal = defect.equals("good");
            if (!row[5].equals(defect) || !row[4].equals(normal ? "good" : "anomaly")) throw new IllegalArgumentException("Prediction label mismatch");
            double score = Double.parseDouble(row[6]);
            if (!Double.isFinite(score)) throw new IllegalArgumentException("Nonfinite native image score");
            (normal ? good : anomaly).add(score);
            Path mapPath = directory.resolve(row[9]).normalize();
            if (!mapPath.startsWith(directory.normalize())) throw new IllegalArgumentException("Map path escapes case directory");
            NpyMap.Map map = NpyMap.read(mapPath);
            NpyMap.Sidecar metadata = NpyMap.readSidecar(mapPath);
            requireMap(mapPath, map, metadata, filename, row[12], size);
            boolean[] mask = DatasetNinjaMasks.read(dataset.resolve("test/ann/" + filename + ".json"), size, size, normal);
            metrics.add(map.values(), mask, size, size);
        }
        LocalizationMetrics.Result result = metrics.calculate();
        var output = new java.util.LinkedHashMap<String, Object>();
        output.putAll(Map.of("status", "verified", "method", method, "category", category,
                "n_good", good.size(), "n_anomaly", anomaly.size(), "image_auroc", BottleEvaluation.auroc(
                        good.stream().mapToDouble(Double::doubleValue).toArray(), anomaly.stream().mapToDouble(Double::doubleValue).toArray()),
                "pixel_auroc", result.pixelAuRoc(), "aupro030", result.auPro(), "regions", result.regionCount()));
        output.put("foreground_pixels", result.positivePixels());
        output.put("background_pixels", result.negativePixels());
        output.put("evaluator", "EvaluationLabels+DatasetNinjaMasks+LocalizationMetrics/LOCALIZATION_CONVENTIONS.md");
        output.put("output_kind", "evaluated npy+json/v1, image_score from predictions.csv");
        output.put("dataset_manifest_sha256", NpyMap.sha256(manifest));
        output.put("config_sha256", NpyMap.sha256(directory.resolve("config.json")));
        ComparisonArtifacts.json(directory.resolve("metrics-unified.json"), output);
        ComparisonArtifacts.json(directory.resolve("metrics-native.json"), Map.of("status", "open", "reason", "Native framework evaluator intentionally not invoked; primary metrics are unified"));
    }

    static void requireMap(Path path, NpyMap.Map map, NpyMap.Sidecar metadata, String filename, String geometry, int size) throws Exception {
        if (map.rows() != size || map.columns() != size || !metadata.format().equals("npy+json/v1")
                || !metadata.filename().equals(filename) || !metadata.geometryId().equals(geometry)
                || !metadata.scoreKind().equals("evaluated") || !metadata.array().equals(path.getFileName().toString())
                || metadata.shape().length != 2 || metadata.shape()[0] != size || metadata.shape()[1] != size
                || !metadata.dtype().equals(map.descr().equals("<f8") ? "float64" : "float32")
                || !metadata.sha256().equals(NpyMap.sha256(path))) throw new IllegalArgumentException("Evaluation map/metadata mismatch: " + path);
    }
}
