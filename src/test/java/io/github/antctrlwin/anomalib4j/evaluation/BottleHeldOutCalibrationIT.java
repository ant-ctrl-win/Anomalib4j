package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;
import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BottleHeldOutCalibrationIT {
    private static final Set<String> EXAMPLES = Set.of("bottle_good_000.png", "bottle_broken_large_000.png",
            "bottle_broken_small_000.png", "bottle_contamination_000.png");

    @Test void comparesRawAndCalibratedScoresFromOneModelTrainedOnlyOn167Images() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        run(dataset, Path.of("target/bottle-spatial14-heldout-calibration"), 42L);
    }

    static HeldOutMultiSeedSummary.Run run(Path dataset, Path output, long splitSeed) throws Exception {
        Files.createDirectories(output);
        var split = HeldOutBottleCalibration.split(NormalImageTrainer.discoverBottleImages(dataset), splitSeed);
        Files.write(output.resolve("archetype-training.txt"), split.archetypeTraining().stream().map(path -> path.getFileName().toString()).toList());
        Files.write(output.resolve("calibration.txt"), split.calibration().stream().map(path -> path.getFileName().toString()).toList());
        var testPaths = new ArrayList<Path>();
        try (var paths = Files.newDirectoryStream(dataset.resolve("test/img"), "bottle_*.png")) {
            for (Path path : paths) if (Files.isRegularFile(path)) testPaths.add(path);
        }
        testPaths.sort(Comparator.comparing((Path path) -> !BottleEvaluation.defect(path.getFileName().toString()).equals("good"))
                .thenComparing(path -> path.getFileName().toString()));
        assertEquals(83, testPaths.size());
        var rawMaps = new LinkedHashMap<String, double[]>();
        var zMaps = new LinkedHashMap<String, double[]>();
        var shapes = new LinkedHashMap<String, Dimension>();
        var goodRaw = new ArrayList<Double>();
        var anomalyRaw = new ArrayList<Double>();
        var goodZ = new ArrayList<Double>();
        var anomalyZ = new ArrayList<Double>();
        var scoresCsv = new StringBuilder("filename,row,column,raw_167,z_167_42\n");
        var imagesCsv = new StringBuilder("filename,ground_truth,defect,image_raw_167,image_z_167_42\n");
        int totalPixels = 0;
        PositionalRawCalibration calibration;
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            var descriptor = new ModelDescriptor("bottle-spatial14-heldout-167-v1", encoder.modelId(), encoder.variant().name(),
                    encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(descriptor.grid().channels(), descriptor.vsaDimensions(), descriptor.projectionSeed());
            var model = new NormalImageTrainer(encoder, descriptor, projection, 1e-8).train(split.archetypeTraining(), completed -> {
                if (completed % 20 == 0 || completed == 334) System.out.println("Held-out model training passes " + completed + "/334");
            });
            assertEquals(167, model.imageCount());
            assertEquals(32_732, model.statisticObservations());
            assertEquals(descriptor, model.filters().descriptor());
            for (int p = 0; p < descriptor.grid().cells(); p++) assertEquals(167, model.memory().sampleCount(p));
            var heldOutRaw = new ArrayList<double[]>();
            for (Path path : split.calibration()) {
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, path.toString());
                try { heldOutRaw.add(HeldOutBottleCalibration.rawScores(model.filters(), encoder.extract(image))); }
                finally { image.flush(); }
            }
            calibration = PositionalRawCalibration.fit(descriptor, heldOutRaw, 1e-6);
            calibration.requireCompatible(model.filters().descriptor());
            assertEquals(42, calibration.count());
            Files.writeString(output.resolve("parameters.csv"), calibration.parametersCsv());
            Files.writeString(output.resolve("parameter-summary.csv"), calibration.summaryCsv());
            for (Path path : testPaths) {
                String filename = path.getFileName().toString();
                String defect = BottleEvaluation.defect(filename);
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, filename);
                HeldOutBottleCalibration.Scores scores;
                try {
                    shapes.put(filename, new Dimension(image.getWidth(), image.getHeight()));
                    totalPixels = Math.addExact(totalPixels, Math.multiplyExact(image.getWidth(), image.getHeight()));
                    scores = HeldOutBottleCalibration.score(model.filters(), encoder.extract(image), calibration);
                } finally { image.flush(); }
                rawMaps.put(filename, scores.raw());
                zMaps.put(filename, scores.z());
                double rawMaximum = BottleEvaluation.summarize(scores.raw()).max();
                double zMaximum = BottleEvaluation.summarize(scores.z()).max();
                boolean good = defect.equals("good");
                (good ? goodRaw : anomalyRaw).add(rawMaximum);
                (good ? goodZ : anomalyZ).add(zMaximum);
                imagesCsv.append(String.format(Locale.ROOT, "%s,%s,%s,%.17g,%.17g%n", filename,
                        good ? "good" : "anomaly", defect, rawMaximum, zMaximum));
                for (int p = 0; p < scores.raw().length; p++) scoresCsv.append(String.format(Locale.ROOT,
                        "%s,%d,%d,%.17g,%.17g%n", filename, p / 14, p % 14, scores.raw()[p], scores.z()[p]));
                System.out.println("Held-out paired test scoring " + rawMaps.size() + "/83");
            }
        }
        assertEquals(20, goodRaw.size());
        assertEquals(63, anomalyRaw.size());
        assertEquals(rawMaps.keySet(), zMaps.keySet());
        for (String example : EXAMPLES) assertTrue(zMaps.containsKey(example));
        Files.writeString(output.resolve("test-scores.csv"), scoresCsv);
        Files.writeString(output.resolve("image-scores.csv"), imagesCsv);
        double rawImageAuRoc = BottleEvaluation.auroc(goodRaw.stream().mapToDouble(Double::doubleValue).toArray(),
                anomalyRaw.stream().mapToDouble(Double::doubleValue).toArray());
        double zImageAuRoc = BottleEvaluation.auroc(goodZ.stream().mapToDouble(Double::doubleValue).toArray(),
                anomalyZ.stream().mapToDouble(Double::doubleValue).toArray());
        var rawLocalization = localize(dataset, output, rawMaps, shapes, totalPixels, false);
        var zLocalization = localize(dataset, output, zMaps, shapes, totalPixels, true);
        assertEquals(rawLocalization.regionCount(), zLocalization.regionCount());
        assertEquals(rawLocalization.positivePixels(), zLocalization.positivePixels());
        assertEquals(rawLocalization.negativePixels(), zLocalization.negativePixels());
        var raw = new HeldOutBottleCalibration.Metrics(rawImageAuRoc, rawLocalization.pixelAuRoc(), rawLocalization.auPro());
        var z = new HeldOutBottleCalibration.Metrics(zImageAuRoc, zLocalization.pixelAuRoc(), zLocalization.auPro());
        var delta = z.minus(raw);
        String diagnostics = String.format(Locale.ROOT,
                "%nCalibration observations per position: %d. Sigma floor: %.17g. Floored positions: %d.%n"
                        + "Training VSA observations: 32732 = 167 x 196. Archetype samples per cell: 167.%n"
                        + "\n## Positional parameter summaries\n\n```csv\n%s```\n",
                calibration.count(), calibration.floor(), calibration.flooredPositions(), calibration.summaryCsv());
        String report = HeldOutBottleCalibration.comparison(raw, z, splitSeed) + diagnostics;
        Files.writeString(output.resolve("comparison.md"), report);
        Files.writeString(output.resolve("metrics.csv"), "evaluation,image_auroc,pixel_auroc,aupro030\n"
                + row("RAW_167", raw) + row("CALIBRATED_167+42", z) + row("CALIBRATED_167+42_MINUS_RAW_167", delta));
        System.out.println(report);
        return new HeldOutMultiSeedSummary.Run(splitSeed, raw, z);
    }

    private static LocalizationMetrics.Result localize(Path dataset, Path output, Map<String, double[]> maps,
                                                       Map<String, Dimension> shapes, int totalPixels, boolean calibrated) throws Exception {
        var metrics = new LocalizationMetrics(totalPixels);
        double low = maps.values().stream().mapToDouble(map -> BottleEvaluation.summarize(map).min()).min().orElseThrow();
        double high = maps.values().stream().mapToDouble(map -> BottleEvaluation.summarize(map).max()).max().orElseThrow();
        int processed = 0;
        for (var entry : maps.entrySet()) {
            String filename = entry.getKey();
            Dimension shape = shapes.get(filename);
            boolean good = BottleEvaluation.defect(filename).equals("good");
            boolean[] mask = DatasetNinjaMasks.read(dataset.resolve("test/ann").resolve(filename + ".json"), shape.width, shape.height, good);
            int foreground = 0;
            for (boolean pixel : mask) if (pixel) foreground++;
            if (good) assertEquals(0, foreground);
            else assertTrue(foreground > 0, filename);
            double[] prediction = LocalizationMaps.upsample(entry.getValue(), 14, 14, shape.width, shape.height);
            metrics.add(prediction, mask, shape.width, shape.height);
            if (calibrated && EXAMPLES.contains(filename)) {
                var image = ImageIO.read(dataset.resolve("test/img").resolve(filename).toFile());
                assertNotNull(image);
                try { LocalizationMaps.overlay(output.resolve(filename), image, mask, prediction, low, high,
                        "Held-out positional z (bilinear, blue to red)"); }
                finally { image.flush(); }
            }
            System.out.println((calibrated ? "Z" : "RAW") + " localization maps " + ++processed + "/83");
        }
        var result = metrics.calculate();
        assertTrue(Double.isFinite(result.pixelAuRoc()) && result.pixelAuRoc() >= 0 && result.pixelAuRoc() <= 1 + 1e-10);
        assertTrue(Double.isFinite(result.auPro()) && result.auPro() >= 0 && result.auPro() <= 1 + 1e-10);
        var curve = new StringBuilder("fpr,pro\n");
        for (int i = 0; i < result.proAtFpr().length; i++) curve.append(String.format(Locale.ROOT, "%.3f,%.17g%n", i * .001, result.proAtFpr()[i]));
        Files.writeString(output.resolve(calibrated ? "z-pro-curve.csv" : "raw-pro-curve.csv"), curve);
        return result;
    }

    private static String row(String name, HeldOutBottleCalibration.Metrics metrics) {
        return String.format(Locale.ROOT, "%s,%.17g,%.17g,%.17g%n", name, metrics.imageAuRoc(), metrics.pixelAuRoc(), metrics.auPro());
    }
}
