package io.github.antctrlwin.anomalib4j.training;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.validation.ExplicitVsaScorer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NormalImageTrainerTest {
    @TempDir Path temporary;

    @Test
    void discoversOnlySortedBottleTrainingFiles() throws Exception {
        Path images = Files.createDirectories(temporary.resolve("train/img"));
        Path second = Files.createFile(images.resolve("bottle_good_002.png"));
        Path first = Files.createFile(images.resolve("bottle_good_001.png"));
        Files.createFile(images.resolve("cable_good_001.png"));
        Files.createDirectory(images.resolve("bottle_good_directory.png"));
        List<Path> found = NormalImageTrainer.discoverBottleImages(temporary);
        assertEquals(List.of(first, second), found);
        assertThrows(UnsupportedOperationException.class, () -> found.add(first));
        assertThrows(IOException.class, () -> NormalImageTrainer.discoverBottleImages(temporary.resolve("missing")));
        Path empty = temporary.resolve("empty");
        Files.createDirectories(empty.resolve("train/img"));
        assertThrows(IOException.class, () -> NormalImageTrainer.discoverBottleImages(empty));
    }

    @Test
    void trainsTwoPassesAndMatchesIndependentStatisticsAndForward() throws Exception {
        List<Path> paths = List.of(writeImage("first.png", 11), writeImage("second.png", 37));
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            ModelDescriptor descriptor = descriptor(encoder, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(descriptor.grid().channels(),
                    descriptor.vsaDimensions(), descriptor.projectionSeed());
            var progress = new ArrayList<Integer>();
            var result = new NormalImageTrainer(encoder, descriptor, projection, 1e-8).train(paths, progress::add);
            assertEquals(List.of(1, 2, 3, 4), progress);
            assertEquals(2, result.imageCount());
            assertEquals(392, result.statisticObservations());
            result.statistics().requireCompatible(descriptor);
            assertEquals(descriptor, result.filters().descriptor());
            var samples = new ArrayList<double[]>();
            var maps = new ArrayList<float[]>();
            for (Path path : paths) {
                BufferedImage image = ImageIO.read(path.toFile());
                float[] features;
                try { features = encoder.extract(image); } finally { image.flush(); }
                maps.add(features);
                for (int p = 0; p < descriptor.grid().cells(); p++) {
                    int offset = p * descriptor.grid().channels();
                    float[] patch = Arrays.copyOfRange(features, offset, offset + descriptor.grid().channels());
                    double[] y = new double[descriptor.vsaDimensions()];
                    projection.project(patch, y);
                    double squared = 0;
                    for (float value : patch) squared += (double) value * value;
                    double norm = Math.max(Math.sqrt(squared), descriptor.normalization().normEpsilon());
                    for (int d = 0; d < y.length; d++) y[d] = y[d] / norm / Math.sqrt(y.length);
                    samples.add(y);
                }
            }
            for (int d = 0; d < descriptor.vsaDimensions(); d++) {
                double mean = 0;
                for (double[] sample : samples) mean += sample[d];
                mean /= samples.size();
                double varianceSum = 0;
                for (double[] sample : samples) varianceSum += Math.pow(sample[d] - mean, 2);
                assertEquals(mean, result.statistics().mean(d), 1e-12);
                assertEquals(Math.max(1e-8, Math.sqrt(varianceSum / (samples.size() - 1))),
                        result.statistics().stdDev(d), 1e-12);
            }
            var scorer = new ExplicitVsaScorer(result.memory(), result.statistics(), projection, descriptor.normalization());
            double[] archetype = new double[descriptor.vsaDimensions()];
            for (int p = 0; p < descriptor.grid().cells(); p++) {
                assertEquals(2, result.memory().sampleCount(p));
                result.memory().getArchetype(p, archetype);
                double reference = 0;
                for (int d = 0; d < archetype.length; d++) {
                    reference += (samples.get(p)[d] - result.statistics().mean(d))
                            / result.statistics().stdDev(d) * archetype[d];
                }
                assertEquals(reference, scorer.scoreCell(maps.getFirst(), p), 1e-12);
                assertEquals(reference, result.filters().scoreCell(maps.getFirst(), p), 1e-9);
            }
            assertThrows(IllegalArgumentException.class, () -> scorer.scoreCell(new float[1], 0));
            assertThrows(IndexOutOfBoundsException.class, () -> scorer.scoreCell(maps.getFirst(), 196));
        }
    }

    @Test
    void rejectsMismatchedContractsAndInvalidImages() throws Exception {
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            ModelDescriptor descriptor = descriptor(encoder, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(96, 11, 42L);
            assertThrows(IllegalArgumentException.class, () -> new NormalImageTrainer(encoder,
                    descriptor(encoder, "other-preprocessing"), projection, 1e-8));
            assertThrows(IllegalArgumentException.class, () -> new NormalImageTrainer(encoder,
                    descriptor, new DenseRademacherProjection(96, 11, 43L), 1e-8));
            assertThrows(IllegalArgumentException.class, () -> new NormalImageTrainer(encoder,
                    descriptor, projection, Double.NaN));
            var trainer = new NormalImageTrainer(encoder, descriptor, projection, 1e-8);
            assertThrows(IllegalArgumentException.class, () -> trainer.train(List.of()));
            Path invalid = Files.writeString(temporary.resolve("invalid.png"), "not an image");
            assertThrows(IOException.class, () -> trainer.train(List.of(invalid)));
        }
    }

    private static ModelDescriptor descriptor(OnnxMobileNetV4Encoder encoder, String preprocessing) {
        return new ModelDescriptor("synthetic-training", encoder.modelId(), encoder.variant().name(),
                encoder.grid(), 11, 42L, new NormalizationPolicy(1e-6), 1, preprocessing);
    }

    private Path writeImage(String filename, int phase) throws IOException {
        var image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, ((x * phase & 255) << 16) | ((y * 9 & 255) << 8) | ((x + y) * 3 & 255));
            }
        }
        Path path = temporary.resolve(filename);
        try { assertTrue(ImageIO.write(image, "png", path.toFile())); } finally { image.flush(); }
        return path;
    }
}
