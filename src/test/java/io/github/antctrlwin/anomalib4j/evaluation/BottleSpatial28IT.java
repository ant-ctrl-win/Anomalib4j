package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;
import io.github.antctrlwin.anomalib4j.validation.ExplicitVsaScorer;
import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class BottleSpatial28IT {
    @Test void trainsNewSpatial28ModelAndEvaluatesBottle() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        Path output = Files.createDirectories(Path.of("target/bottle-spatial28"));
        var testPaths = new ArrayList<Path>();
        try (var paths = Files.newDirectoryStream(dataset.resolve("test/img"), "bottle_*.png")) {
            for (Path path : paths) if (Files.isRegularFile(path)) testPaths.add(path);
        }
        testPaths.sort(Comparator.comparing((Path path) -> !BottleEvaluation.defect(path.getFileName().toString()).equals("good"))
                .thenComparing(path -> path.getFileName().toString()));
        assertEquals(83, testPaths.size());
        var rawMaps = new LinkedHashMap<String, double[]>();
        var shapes = new LinkedHashMap<String, Dimension>();
        int totalPixels = 0;
        int comparisons = 0;
        double maxError = 0;
        double displayMin = Double.POSITIVE_INFINITY;
        double displayMax = Double.NEGATIVE_INFINITY;
        var goodScores = new ArrayList<Double>();
        var anomalyScores = new ArrayList<Double>();
        var rawCsv = new StringBuilder("filename,row,column,raw_discrepancy\n");
        var imageCsv = new StringBuilder("filename,ground_truth,defect,image_score_raw,max_row,max_column,raw_min,raw_mean,raw_max\n");
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_28)) {
            var descriptor = Spatial28Evaluation.descriptor(encoder);
            var projection = new DenseRademacherProjection(descriptor.grid().channels(), descriptor.vsaDimensions(), descriptor.projectionSeed());
            var training = NormalImageTrainer.discoverBottleImages(dataset);
            assertEquals(209, training.size());
            var model = new NormalImageTrainer(encoder, descriptor, projection, 1e-8).train(training, completed -> {
                if (completed % 20 == 0 || completed == 418) System.out.println("SPATIAL_28 training passes " + completed + "/418");
            });
            assertEquals(163_856, model.statisticObservations());
            assertEquals(descriptor, model.memory().descriptor());
            assertEquals(descriptor, model.filters().descriptor());
            for (int p = 0; p < descriptor.grid().cells(); p++) assertEquals(209, model.memory().sampleCount(p));
            var explicit = new ExplicitVsaScorer(model.memory(), model.statistics(), projection, descriptor.normalization());
            for (Path path : testPaths) {
                String filename = path.getFileName().toString();
                String defect = BottleEvaluation.defect(filename);
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, filename);
                float[] features;
                try {
                    shapes.put(filename, new Dimension(image.getWidth(), image.getHeight()));
                    totalPixels = Math.addExact(totalPixels, Math.multiplyExact(image.getWidth(), image.getHeight()));
                    features = encoder.extract(image);
                } finally { image.flush(); }
                double[] raw = new double[descriptor.grid().cells()];
                for (int p = 0; p < raw.length; p++) {
                    double compiled = model.filters().scoreCell(features, p);
                    assertTrue(Double.isFinite(compiled));
                    if (Spatial28Evaluation.EXAMPLES.contains(filename)) {
                        double reference = explicit.scoreCell(features, p);
                        assertTrue(Double.isFinite(reference));
                        maxError = Math.max(maxError, Math.abs(reference - compiled));
                        assertEquals(reference, compiled, 1e-9, filename + ": cell " + p);
                        comparisons++;
                    }
                    raw[p] = 1 - compiled;
                    rawCsv.append(String.format(Locale.ROOT, "%s,%d,%d,%.17g%n", filename, p / 28, p % 28, raw[p]));
                }
                rawMaps.put(filename, raw);
                var summary = Spatial28Evaluation.summarize(raw);
                displayMin = Math.min(displayMin, summary.min());
                displayMax = Math.max(displayMax, summary.max());
                (defect.equals("good") ? goodScores : anomalyScores).add(summary.max());
                imageCsv.append(String.format(Locale.ROOT, "%s,%s,%s,%.17g,%d,%d,%.17g,%.17g,%.17g%n",
                        filename, defect.equals("good") ? "good" : "anomaly", defect, summary.max(), summary.row(), summary.column(),
                        summary.min(), summary.mean(), summary.max()));
                System.out.println("SPATIAL_28 scored " + rawMaps.size() + "/83");
            }
        }
        assertEquals(4 * 784, comparisons);
        assertEquals(20, goodScores.size());
        assertEquals(63, anomalyScores.size());
        for (String example : Spatial28Evaluation.EXAMPLES) assertTrue(rawMaps.containsKey(example));
        Files.writeString(output.resolve("raw-heatmaps.csv"), rawCsv);
        Files.writeString(output.resolve("image-scores.csv"), imageCsv);
        double imageAuRoc = BottleEvaluation.auroc(goodScores.stream().mapToDouble(Double::doubleValue).toArray(),
                anomalyScores.stream().mapToDouble(Double::doubleValue).toArray());
        var metrics = new LocalizationMetrics(totalPixels);
        for (var entry : rawMaps.entrySet()) {
            String filename = entry.getKey();
            Dimension shape = shapes.get(filename);
            boolean good = BottleEvaluation.defect(filename).equals("good");
            boolean[] mask = DatasetNinjaMasks.read(dataset.resolve("test/ann").resolve(filename + ".json"), shape.width, shape.height, good);
            int foreground = 0;
            for (boolean pixel : mask) if (pixel) foreground++;
            if (good) assertEquals(0, foreground);
            else assertTrue(foreground > 0, filename);
            double[] prediction = LocalizationMaps.upsample(entry.getValue(), 28, 28, shape.width, shape.height);
            metrics.add(prediction, mask, shape.width, shape.height);
            if (Spatial28Evaluation.EXAMPLES.contains(filename)) {
                var image = ImageIO.read(dataset.resolve("test/img").resolve(filename).toFile());
                assertNotNull(image);
                try { LocalizationMaps.overlay(output.resolve(filename), image, mask, prediction, displayMin, displayMax); }
                finally { image.flush(); }
            }
        }
        var result = metrics.calculate();
        assertTrue(Double.isFinite(result.pixelAuRoc()) && result.pixelAuRoc() >= 0 && result.pixelAuRoc() <= 1 + 1e-10);
        assertTrue(Double.isFinite(result.auPro()) && result.auPro() >= 0 && result.auPro() <= 1 + 1e-10);
        String report = Spatial28Evaluation.comparison(imageAuRoc, result.pixelAuRoc(), result.auPro(), maxError, comparisons);
        Files.writeString(output.resolve("comparison.md"), report);
        Files.writeString(output.resolve("metrics.csv"), String.format(Locale.ROOT,
                "variant,image_auroc,pixel_auroc,aupro030,max_error,comparisons,regions,foreground_pixels,background_pixels%n"
                        + "SPATIAL_28,%.17g,%.17g,%.17g,%.17g,%d,%d,%d,%d%n",
                imageAuRoc, result.pixelAuRoc(), result.auPro(), maxError, comparisons, result.regionCount(), result.positivePixels(), result.negativePixels()));
        var curve = new StringBuilder("fpr,pro\n");
        for (int i = 0; i < result.proAtFpr().length; i++) curve.append(String.format(Locale.ROOT, "%.3f,%.17g%n", i * .001, result.proAtFpr()[i]));
        Files.writeString(output.resolve("pro-curve.csv"), curve);
        System.out.println(report);
    }
}
