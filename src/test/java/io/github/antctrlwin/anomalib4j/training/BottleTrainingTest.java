package io.github.antctrlwin.anomalib4j.training;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.inference.HeatmapEngine;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.validation.ExplicitVsaScorer;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class BottleTrainingTest {
    @Test
    void trainsAll209BottleImagesAndMatchesExplicitVsa() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset",
                "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        var paths = NormalImageTrainer.discoverBottleImages(dataset);
        assertEquals(209, paths.size());
        assertEquals("bottle_good_000.png", paths.getFirst().getFileName().toString());
        assertEquals("bottle_good_208.png", paths.getLast().getFileName().toString());
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            var descriptor = new ModelDescriptor("bottle-normal-v1", encoder.modelId(), encoder.variant().name(),
                    encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
            assertEquals(new GridShape(14, 14, 96), descriptor.grid());
            var projection = new DenseRademacherProjection(descriptor.grid().channels(),
                    descriptor.vsaDimensions(), descriptor.projectionSeed());
            var trainer = new NormalImageTrainer(encoder, descriptor, projection, 1e-8);
            System.out.println("Sprint 6: starting 209 statistics images + 209 bundling images");
            var result = trainer.train(paths, completed -> {
                if (completed % 10 == 0 || completed == paths.size() || completed == 2 * paths.size()) {
                    System.out.printf("Sprint 6: completed image passes %d/%d%n", completed, 2 * paths.size());
                }
            });
            assertEquals(209, result.imageCount());
            assertEquals(40_964, result.statisticObservations());
            assertEquals(descriptor, result.memory().descriptor());
            assertEquals(descriptor, result.filters().descriptor());
            result.statistics().requireCompatible(descriptor);
            for (int d = 0; d < descriptor.vsaDimensions(); d++) {
                assertTrue(Double.isFinite(result.statistics().mean(d)));
                assertTrue(Double.isFinite(result.statistics().stdDev(d)));
                assertTrue(result.statistics().stdDev(d) >= 1e-8);
            }
            double[] archetype = new double[descriptor.vsaDimensions()];
            for (int p = 0; p < descriptor.grid().cells(); p++) {
                assertEquals(209, result.memory().sampleCount(p));
                result.memory().getArchetype(p, archetype);
                double squaredNorm = 0;
                for (double value : archetype) squaredNorm += value * value;
                assertEquals(1.0, Math.sqrt(squaredNorm), 1e-10);
            }
            assertNotNull(new HeatmapEngine(result.filters()));
            var explicit = new ExplicitVsaScorer(result.memory(), result.statistics(), projection, descriptor.normalization());
            double maximumError = 0;
            int comparisons = 0;
            for (int index : new int[]{0, 104, 208}) {
                var image = ImageIO.read(paths.get(index).toFile());
                float[] features;
                try { features = encoder.extract(image); } finally { image.flush(); }
                for (int p = 0; p < descriptor.grid().cells(); p++) {
                    double reference = explicit.scoreCell(features, p);
                    double compiled = result.filters().scoreCell(features, p);
                    assertTrue(Double.isFinite(reference));
                    assertTrue(Double.isFinite(compiled));
                    maximumError = Math.max(maximumError, Math.abs(reference - compiled));
                    assertEquals(reference, compiled, 1e-9, "image=" + index + ", cell=" + p);
                    comparisons++;
                }
            }
            assertEquals(588, comparisons);
            System.out.printf(Locale.ROOT,
                    "SPRINT6_RESULT images=%d observations=%d cells=%d comparisons=%d maxError=%.17g%n",
                    result.imageCount(), result.statisticObservations(), descriptor.grid().cells(), comparisons, maximumError);
        }
    }
}
