package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
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
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BottlePositionalCalibrationIT {
    private static final double RAW_SIGMA_FLOOR = 1e-6;
    private static final Set<String> EXAMPLES = Set.of("bottle_good_000.png", "bottle_broken_large_000.png",
            "bottle_broken_small_000.png", "bottle_contamination_000.png");

    @Test void evaluatesInSamplePositionalCalibrationAfterFrozenSpatial14Training() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        Path output = Files.createDirectories(Path.of("target/bottle-spatial14-positional-calibration"));
        var testPaths = new ArrayList<Path>();
        try (var paths = Files.newDirectoryStream(dataset.resolve("test/img"), "bottle_*.png")) {
            for (Path path : paths) if (Files.isRegularFile(path)) testPaths.add(path);
        }
        testPaths.sort(Comparator.comparing((Path path) -> !BottleEvaluation.defect(path.getFileName().toString()).equals("good"))
                .thenComparing(path -> path.getFileName().toString()));
        assertEquals(83, testPaths.size());
        var calibratedMaps = new LinkedHashMap<String, double[]>();
        var shapes = new LinkedHashMap<String, Dimension>();
        var goodRaw = new ArrayList<Double>();
        var anomalyRaw = new ArrayList<Double>();
        var goodZ = new ArrayList<Double>();
        var anomalyZ = new ArrayList<Double>();
        var scoresCsv = new StringBuilder("filename,row,column,raw,z\n");
        var imagesCsv = new StringBuilder("filename,ground_truth,defect,image_raw,image_z,z_max_row,z_max_column,z_min,z_mean,z_max\n");
        int totalPixels = 0;
        double displayMin = Double.POSITIVE_INFINITY;
        double displayMax = Double.NEGATIVE_INFINITY;
        PositionalRawCalibration calibration;
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            var descriptor = new ModelDescriptor("bottle-normal-v1", encoder.modelId(), encoder.variant().name(),
                    encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(descriptor.grid().channels(), descriptor.vsaDimensions(), descriptor.projectionSeed());
            var training = NormalImageTrainer.discoverBottleImages(dataset);
            assertEquals(209, training.size());
            var model = new NormalImageTrainer(encoder, descriptor, projection, 1e-8).train(training, completed -> {
                if (completed % 20 == 0 || completed == 418) System.out.println("SPATIAL_14 training passes " + completed + "/418");
            });
            assertEquals(40_964, model.statisticObservations());
            var trainingRaw = new ArrayList<double[]>();
            for (Path path : training) {
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, path.toString());
                try { trainingRaw.add(raw(model.filters(), encoder.extract(image))); }
                finally { image.flush(); }
            }
            calibration = PositionalRawCalibration.fit(descriptor, trainingRaw, RAW_SIGMA_FLOOR);
            calibration.requireCompatible(model.filters().descriptor());
            assertEquals(209, calibration.count());
            Files.writeString(output.resolve("parameters.csv"), calibration.parametersCsv());
            Files.writeString(output.resolve("parameter-summary.csv"), calibration.summaryCsv());
            for (Path path : testPaths) {
                String filename = path.getFileName().toString();
                String defect = BottleEvaluation.defect(filename);
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, filename);
                double[] raw;
                try {
                    shapes.put(filename, new Dimension(image.getWidth(), image.getHeight()));
                    totalPixels = Math.addExact(totalPixels, Math.multiplyExact(image.getWidth(), image.getHeight()));
                    raw = raw(model.filters(), encoder.extract(image));
                } finally { image.flush(); }
                double[] z = calibration.calibrate(raw);
                calibratedMaps.put(filename, z);
                var summary = BottleEvaluation.summarize(z);
                double rawMaximum = BottleEvaluation.summarize(raw).max();
                boolean good = defect.equals("good");
                (good ? goodRaw : anomalyRaw).add(rawMaximum);
                (good ? goodZ : anomalyZ).add(summary.max());
                displayMin = Math.min(displayMin, summary.min());
                displayMax = Math.max(displayMax, summary.max());
                imagesCsv.append(String.format(Locale.ROOT, "%s,%s,%s,%.17g,%.17g,%d,%d,%.17g,%.17g,%.17g%n",
                        filename, good ? "good" : "anomaly", defect, rawMaximum, summary.max(), summary.row(), summary.column(),
                        summary.min(), summary.mean(), summary.max()));
                for (int p = 0; p < raw.length; p++) scoresCsv.append(String.format(Locale.ROOT,
                        "%s,%d,%d,%.17g,%.17g%n", filename, p / 14, p % 14, raw[p], z[p]));
                System.out.println("Positional calibration scored " + calibratedMaps.size() + "/83");
            }
        }
        assertEquals(20, goodZ.size());
        assertEquals(63, anomalyZ.size());
        double rawImageAuRoc = BottleEvaluation.auroc(goodRaw.stream().mapToDouble(Double::doubleValue).toArray(),
                anomalyRaw.stream().mapToDouble(Double::doubleValue).toArray());
        assertEquals(.9857142857142858, rawImageAuRoc, 1e-12, "Frozen raw baseline regression");
        double imageAuRoc = BottleEvaluation.auroc(goodZ.stream().mapToDouble(Double::doubleValue).toArray(),
                anomalyZ.stream().mapToDouble(Double::doubleValue).toArray());
        Files.writeString(output.resolve("image-scores.csv"), imagesCsv);
        Files.writeString(output.resolve("test-scores.csv"), scoresCsv);
        var metrics = new LocalizationMetrics(totalPixels);
        for (var entry : calibratedMaps.entrySet()) {
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
            if (EXAMPLES.contains(filename)) {
                var image = ImageIO.read(dataset.resolve("test/img").resolve(filename).toFile());
                assertNotNull(image);
                try { LocalizationMaps.overlay(output.resolve(filename), image, mask, prediction, displayMin, displayMax,
                        "Positional z (bilinear, blue to red)"); }
                finally { image.flush(); }
            }
        }
        for (String example : EXAMPLES) assertTrue(calibratedMaps.containsKey(example));
        var result = metrics.calculate();
        assertTrue(Double.isFinite(result.pixelAuRoc()) && result.pixelAuRoc() >= 0 && result.pixelAuRoc() <= 1 + 1e-10);
        assertTrue(Double.isFinite(result.auPro()) && result.auPro() >= 0 && result.auPro() <= 1 + 1e-10);
        String report = String.format(Locale.ROOT, """
                # SPATIAL_14: in-sample positional calibration

                The same 209 training-good images build the VSA archetypes and estimate positional
                raw-score means and sample deviations. No held-out calibration, cross-validation or
                leave-one-out is used. Test images do not enter calibration estimation.
                There are 196 positions, each with %d observations. Sigma floor: %.17g raw-score units.
                Floored positions: %d. See parameters.csv and parameter-summary.csv for all mu/sigma
                values and their min, quartiles, mean, max and sample standard deviation across positions.

                z[p] = (raw[p] - mu[p]) / max(sampleSigma[p], floor); raw[p] = 1 - compiledScore[p].
                No clamp, sigmoid, smoothing, threshold or per-image normalization. Image score: max(z).
                Calibration is applied on the 14x14 map BEFORE bilinear upsampling to original size.
                Pixel AUROC and AUPRO follow docs/benchmark/LOCALIZATION_CONVENTIONS.md unchanged.

                | Metric | Frozen raw baseline | Positional z (measured) | Delta |
                | --- | --- | --- | --- |
                | Image AUROC | %.17g | %.17g | %+.17g |
                | Pixel AUROC | %.17g | %.17g | %+.17g |
                | AUPRO@0.30 | %.17g | %.17g | %+.17g |

                Test: 83 images (20 good / 63 anomaly). Raw image AUROC recomputed in this run: %.17g.
                Raw pixel metrics in the baseline column are frozen Sprint 8 results, not recomputed.
                Shared overlay color scale: %.17g to %.17g z units, used only for visualization.
                """, calibration.count(), calibration.floor(), calibration.flooredPositions(),
                .9857142857142858, imageAuRoc, imageAuRoc - .9857142857142858,
                .9569495750935939, result.pixelAuRoc(), result.pixelAuRoc() - .9569495750935939,
                .8653474657258569, result.auPro(), result.auPro() - .8653474657258569,
                rawImageAuRoc, displayMin, displayMax);
        Files.writeString(output.resolve("comparison.md"), report);
        Files.writeString(output.resolve("metrics.csv"), String.format(Locale.ROOT,
                "image_auroc,pixel_auroc,aupro030,raw_image_auroc,calibration_images,positions,sigma_floor,floored_positions,regions,foreground_pixels,background_pixels%n"
                        + "%.17g,%.17g,%.17g,%.17g,%d,196,%.17g,%d,%d,%d,%d%n",
                imageAuRoc, result.pixelAuRoc(), result.auPro(), rawImageAuRoc, calibration.count(), calibration.floor(), calibration.flooredPositions(),
                result.regionCount(), result.positivePixels(), result.negativePixels()));
        var curve = new StringBuilder("fpr,pro\n");
        for (int i = 0; i < result.proAtFpr().length; i++) curve.append(String.format(Locale.ROOT, "%.3f,%.17g%n", i * .001, result.proAtFpr()[i]));
        Files.writeString(output.resolve("pro-curve.csv"), curve);
        System.out.println(report);
        System.out.println(calibration.summaryCsv());
    }

    private static double[] raw(AdjointFilterBank filters, float[] features) {
        double[] raw = new double[filters.grid().cells()];
        for (int p = 0; p < raw.length; p++) {
            raw[p] = 1 - filters.scoreCell(features, p);
            assertTrue(Double.isFinite(raw[p]));
        }
        return raw;
    }
}
