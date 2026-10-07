package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class Spatial28EvaluationTest {
    @Test void descriptorUsesActualSpatial28EncoderAndFixedPolicies() throws Exception {
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_28)) {
            var descriptor = Spatial28Evaluation.descriptor(encoder);
            assertEquals(new GridShape(28, 28, 64), descriptor.grid());
            assertEquals("SPATIAL_28", descriptor.layerId());
            assertEquals("bottle-spatial28-normal-v1", descriptor.modelId());
            assertEquals(encoder.modelId(), descriptor.encoderId());
            assertEquals("imagenet-rgb-resize224-bicubic-v1", descriptor.preprocessingId());
            assertEquals(10_000, descriptor.vsaDimensions());
            assertEquals(42L, descriptor.projectionSeed());
            assertEquals(1e-6, descriptor.normalization().normEpsilon());
            var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            try { assertEquals(28 * 28 * 64, encoder.extract(image).length); }
            finally { image.flush(); }
        }
        try (var baseline = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            assertThrows(IllegalArgumentException.class, () -> Spatial28Evaluation.descriptor(baseline));
        }
    }

    @Test void rawSummaryUsesAll784CellsWithoutClampingAndPreservesFirstTie() {
        double[] raw = new double[784];
        Arrays.fill(raw, -20);
        raw[17 * 28 + 19] = 5;
        raw[783] = 5;
        double[] original = raw.clone();
        var summary = Spatial28Evaluation.summarize(raw);
        assertEquals(-20, summary.min());
        assertEquals(5, summary.max());
        assertEquals((-20.0 * 782 + 10) / 784, summary.mean(), 1e-12);
        assertEquals(17, summary.row());
        assertEquals(19, summary.column());
        assertArrayEquals(original, raw);
        raw[783] = 6;
        assertEquals(27, Spatial28Evaluation.summarize(raw).row());
        assertEquals(27, Spatial28Evaluation.summarize(raw).column());
        assertThrows(IllegalArgumentException.class, () -> Spatial28Evaluation.summarize(new double[196]));
        raw[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> Spatial28Evaluation.summarize(raw));
    }

    @Test void boundedEquivalenceSampleAndOverlaysCoverEachGroup() {
        assertEquals(4, Spatial28Evaluation.EXAMPLES.size());
        assertEquals(Set.of("good", "broken_large", "broken_small", "contamination"),
                Spatial28Evaluation.EXAMPLES.stream().map(BottleEvaluation::defect).collect(Collectors.toSet()));
        assertTrue(Spatial28Evaluation.EXAMPLES.stream().allMatch(filename -> filename.endsWith("_000.png")));
    }

    @Test void comparisonKeepsFrozenBaselineAndDistinguishesFeatureStages() {
        String report = Spatial28Evaluation.comparison(.9, .8, .7, 1e-12, 3136);
        assertTrue(report.contains("0.9857142857142858"));
        assertTrue(report.contains("0.9569495750935939"));
        assertTrue(report.contains("0.8653474657258569"));
        assertTrue(report.contains("0.90000000000000000"));
        assertTrue(report.contains("0.80000000000000000"));
        assertTrue(report.contains("3136"));
        assertTrue(report.contains("earlier 64-channel feature stage"));
        assertTrue(report.contains("cannot be attributed solely to spatial resolution"));
    }
}
