package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BottleLocalizationIT {
    @Test void evaluatesSavedSprint7MapsAgainstOriginalResolutionMasks() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        Path rawFile = Path.of(System.getProperty("anomalib.rawMaps", "target/bottle-raw-heatmaps.csv"));
        var rawMaps = LocalizationArtifacts.readRaw(rawFile);
        var names = new HashSet<String>();
        try (var paths = Files.newDirectoryStream(dataset.resolve("test/img"), "bottle_*.png")) {
            for (Path path : paths) if (Files.isRegularFile(path)) names.add(path.getFileName().toString());
        }
        assertEquals(83, names.size());
        assertEquals(names, rawMaps.keySet(), "Saved maps must match the entire bottle test set");
        var dimensions = new HashMap<String, Dimension>();
        int totalPixels = 0;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        var goodScores = new ArrayList<Double>();
        var anomalyScores = new ArrayList<Double>();
        for (var entry : rawMaps.entrySet()) {
            Dimension shape = imageSize(dataset.resolve("test/img").resolve(entry.getKey()));
            dimensions.put(entry.getKey(), shape);
            totalPixels = Math.addExact(totalPixels, Math.multiplyExact(shape.width, shape.height));
            var summary = BottleEvaluation.summarize(entry.getValue());
            low = Math.min(low, summary.min());
            high = Math.max(high, summary.max());
            (BottleEvaluation.defect(entry.getKey()).equals("good") ? goodScores : anomalyScores).add(summary.max());
        }
        assertEquals(20, goodScores.size());
        assertEquals(63, anomalyScores.size());
        double imageAuRoc = BottleEvaluation.auroc(goodScores.stream().mapToDouble(Double::doubleValue).toArray(),
                anomalyScores.stream().mapToDouble(Double::doubleValue).toArray());
        assertEquals(.9857142857142858, imageAuRoc, 1e-12, "Sprint 7 image-score regression");
        Path output = Files.createDirectories(Path.of("target/bottle-localization"));
        var metrics = new LocalizationMetrics(totalPixels);
        Set<String> examples = Set.of("bottle_good_000.png", "bottle_broken_large_000.png",
                "bottle_broken_small_000.png", "bottle_contamination_000.png");
        int processed = 0;
        for (var entry : rawMaps.entrySet()) {
            String filename = entry.getKey();
            Dimension shape = dimensions.get(filename);
            boolean good = BottleEvaluation.defect(filename).equals("good");
            boolean[] mask = DatasetNinjaMasks.read(dataset.resolve("test/ann").resolve(filename + ".json"),
                    shape.width, shape.height, good);
            int foreground = 0;
            for (boolean value : mask) if (value) foreground++;
            if (good) assertEquals(0, foreground);
            else assertTrue(foreground > 0, "Missing foreground for " + filename);
            double[] prediction = LocalizationMaps.upsample(entry.getValue(), 14, 14, shape.width, shape.height);
            metrics.add(prediction, mask, shape.width, shape.height);
            if (examples.contains(filename)) {
                var original = ImageIO.read(dataset.resolve("test/img").resolve(filename).toFile());
                assertNotNull(original);
                try { LocalizationMaps.overlay(output.resolve(filename), original, mask, prediction, low, high); }
                finally { original.flush(); }
            }
            System.out.println("Localization maps reconstructed: " + ++processed + "/83");
        }
        var result = metrics.calculate();
        assertTrue(Double.isFinite(result.pixelAuRoc()) && result.pixelAuRoc() >= 0 && result.pixelAuRoc() <= 1 + 1e-10);
        assertTrue(Double.isFinite(result.auPro()) && result.auPro() >= 0 && result.auPro() <= 1 + 1e-10);
        String summary = String.format(Locale.ROOT,
                "images=%d%ngood=20%nanomaly=63%nimageAuRoc=%.17g%npixelAuRoc=%.17g%nauPro030=%.17g%n"
                        + "regions=%d%npositivePixels=%d%nnegativePixels=%d%nconnectivity=8%n"
                        + "upsampling=bilinear-half-pixel-border-replicate%nthresholds=all-distinct-scores-ties-grouped%n"
                        + "fprLimit=0.30%nnormalization=area/0.30%nrawMaps=%s%n",
                processed, imageAuRoc, result.pixelAuRoc(), result.auPro(), result.regionCount(),
                result.positivePixels(), result.negativePixels(), rawFile.toAbsolutePath());
        Files.writeString(output.resolve("metrics.txt"), summary);
        var curve = new StringBuilder("fpr,pro\n");
        for (int i = 0; i < result.proAtFpr().length; i++) {
            curve.append(String.format(Locale.ROOT, "%.3f,%.17g%n", i * .001, result.proAtFpr()[i]));
        }
        Files.writeString(output.resolve("pro-curve.csv"), curve);
        System.out.println(summary);
    }

    private static Dimension imageSize(Path path) throws IOException {
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Cannot open image: " + path);
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image: " + path);
            var reader = readers.next();
            try {
                reader.setInput(input);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally { reader.dispose(); }
        }
    }
}
