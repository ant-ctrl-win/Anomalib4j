package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Experimental adapters; scoring and production M0 remain outside this bridge. */
public final class LocalAblationBridge {
    private LocalAblationBridge() { }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "projection" -> projection(Path.of(args[1]));
            case "evaluate" -> evaluate(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), args[4], args[5]);
            default -> throw new IllegalArgumentException("Unknown ablation bridge action");
        }
    }

    static void projection(Path output) throws Exception {
        var projection = new DenseRademacherProjection(96, 10_000, 42);
        double[] matrix = new double[96 * 10_000];
        double[] column = new double[10_000];
        for (int c = 0; c < 96; c++) {
            float[] basis = new float[96];
            basis[c] = 1;
            projection.project(basis, column);
            for (int d = 0; d < 10_000; d++) matrix[c * 10_000 + d] = column[d] / 100;
        }
        ComparisonArtifacts.writeMap(output, matrix, 96, 10_000, "projection", "96x10000-seed42", "projection");
    }

    static void evaluate(Path dataset, Path directory, Path manifest, String method, String category) throws Exception {
        if (!java.util.List.of("C1", "V1", "M2", "M2-P", "M2-N1", "M2-G").contains(method)) throw new IllegalArgumentException("Unknown local model");
        var names = Files.readAllLines(manifest);
        var maps = NpyMap.read(directory.resolve("native-z.npy"));
        if (maps.rows() != names.size() || maps.columns() != 196) throw new IllegalArgumentException("Native Z shape mismatch");
        var metadata = new DatasetMetadata(dataset);
        var csv = new StringBuilder("run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n");
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            var info = metadata.inspect("test", name);
            if (!info.category().equals(category)) throw new IllegalArgumentException("Wrong category");
            double[] nativeZ = java.util.Arrays.copyOfRange(maps.values(), i * 196, (i + 1) * 196);
            String stem = name.substring(0, name.length() - 4);
            String geometry = category + "-" + info.width() + "x" + info.height() + "-14x14";
            String nativePath = "maps/" + stem + ".native.npy";
            String fullPath = "maps/" + stem + ".evaluated.npy";
            ComparisonArtifacts.writeMap(directory.resolve(nativePath), nativeZ, 14, 14, name, geometry, "native");
            ComparisonArtifacts.writeMap(directory.resolve(fullPath), LocalizationMaps.upsample(nativeZ, 14, 14, info.width(), info.height()),
                    info.height(), info.width(), name, geometry, "evaluated");
            csv.append(String.join(",", directory.getParent().getParent().getFileName().toString(), method, category, name,
                    info.good() ? "good" : "anomaly", info.defect(), Double.toString(BottleEvaluation.summarize(nativeZ).max()),
                    "positional_z", nativePath, fullPath, "float64", "14x14", geometry,
                    NpyMap.sha256(dataset.resolve("test/img/" + name)))).append('\n');
        }
        Files.writeString(directory.resolve("predictions.csv"), csv.toString(), StandardOpenOption.CREATE_NEW);
        ComparisonEvaluator.evaluate(directory, dataset, manifest, method, category);
    }
}
