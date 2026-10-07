package io.github.antctrlwin.anomalib4j.inference;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointCompiler;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.calibration.HeatmapCalibrator;
import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBuilder;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HeatmapEngineTest {
    private final GridShape grid = new GridShape(3, 3, 2);
    private final NormalizationPolicy policy = new NormalizationPolicy(1e-6);
    private final ModelDescriptor descriptor = new ModelDescriptor("normal", "cnn", "layer", grid, 64, 42, policy, 1);

    private float[] normalMap(float amplitude) {
        float[] map = new float[grid.elements()];
        for (int p = 0; p < grid.cells(); p++) map[p * 2] = amplitude;
        return map;
    }

    private AdjointFilterBank trainedModel() {
        double[] sigma = new double[64];
        Arrays.fill(sigma, 1);
        var stats = new ProjectionStatistics(new double[64], sigma).withDescriptor(descriptor);
        var projection = new DenseRademacherProjection(2, 64);
        var builder = new PositionalMemoryBuilder(descriptor, grid, projection, stats, policy);
        for (int i = 1; i <= 5; i++) builder.observe(normalMap(i));
        return new AdjointCompiler().compile(builder.build(), stats, projection, policy);
    }

    private HeatmapEngine calibratedEngine() {
        var bank = trainedModel();
        List<float[]> heldOut = new ArrayList<>();
        for (int i = 1; i <= 10; i++) heldOut.add(normalMap(0.25f * i));
        return new HeatmapEngine(bank, new HeatmapCalibrator().calibrate(heldOut, bank));
    }

    @Test
    void anomalyIsLocalizedOnlyAtRowTwoColumnTwo() {
        var engine = calibratedEngine();
        float[] normal = normalMap(1);
        var nominal = engine.evaluate(normal);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                assertEquals(0, nominal.rawDiscrepancy(r, c), 1e-12);
                assertEquals(0, nominal.calibrated(r, c), 1e-6);
            }
        }
        float[] anomalous = normal.clone();
        anomalous[(2 * 3 + 2) * 2] = -1;
        var result = engine.evaluate(anomalous);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                boolean injected = r == 2 && c == 2;
                assertEquals(injected ? 2 : 0, result.rawDiscrepancy(r, c), 1e-12);
                assertEquals(injected ? 1 : 0, result.calibrated(r, c), 1e-6);
            }
        }
        assertEquals(1, result.anomalyScore());
        assertEquals(0, nominal.anomalyScore(), 1e-6);
    }

    @Test
    void evaluateIntoMatchesAllocatingApiAndOverwritesBuffers() {
        var engine = calibratedEngine();
        float[] map = normalMap(1);
        map[16] = -1;
        float[] original = map.clone();
        var expected = engine.evaluate(map);
        double[] raw = new double[9];
        float[] calibrated = new float[9];
        Arrays.fill(raw, Double.NaN);
        Arrays.fill(calibrated, Float.NaN);
        engine.evaluateInto(map, raw, calibrated);
        for (int p = 0; p < 9; p++) {
            assertEquals(expected.rawDiscrepancy(p / 3, p % 3), raw[p]);
            assertEquals(expected.calibrated(p / 3, p % 3), calibrated[p]);
        }
        assertArrayEquals(original, map);
        engine.evaluateInto(normalMap(1), raw, calibrated);
        assertEquals(0, raw[8], 1e-12);
        assertEquals(0, calibrated[8], 1e-6);
        assertEquals(1, expected.calibrated(2, 2));
    }

    @Test
    void warmedEvaluateIntoAllocatesZeroBytesOnSupportedJvm() {
        var base = ManagementFactory.getThreadMXBean();
        assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) base;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        var engine = calibratedEngine();
        float[] map = normalMap(1);
        double[] raw = new double[9];
        float[] calibrated = new float[9];
        for (int i = 0; i < 100_000; i++) engine.evaluateInto(map, raw, calibrated);
        long thread = Thread.currentThread().threadId();
        bean.getThreadAllocatedBytes(thread);
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20_000; i++) engine.evaluateInto(map, raw, calibrated);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertEquals(0, allocated, "Heap bytes allocated by warmed evaluateInto calls");
        assertEquals(0, raw[0], 1e-12);
    }

    @Test
    void uncalibratedModeClampsRawDiscrepancy() {
        var engine = new HeatmapEngine(trainedModel());
        float[] map = normalMap(1);
        map[16] = -1;
        var result = engine.evaluate(map);
        assertEquals(0, result.calibrated(0, 0), 1e-6);
        assertEquals(1, result.calibrated(2, 2));
    }

    @Test
    void validatesDescriptorsShapesFiniteInputAndAliasing() {
        var bank = trainedModel();
        var other = new ModelDescriptor("normal", "cnn", "layer", grid, 64, 42, policy, 2);
        assertThrows(IllegalArgumentException.class, () -> new HeatmapEngine(bank, new HeatmapCalibration(other, 0, 1)));
        var engine = new HeatmapEngine(bank);
        assertThrows(IllegalArgumentException.class, () -> engine.evaluateInto(new float[1], new double[9], new float[9]));
        assertThrows(IllegalArgumentException.class, () -> engine.evaluateInto(normalMap(1), new double[8], new float[9]));
        assertThrows(IllegalArgumentException.class, () -> engine.evaluateInto(normalMap(1), new double[9], new float[8]));
        float[] invalid = normalMap(1);
        invalid[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> engine.evaluate(invalid));
        var oneChannel = new GridShape(1, 1, 1);
        var contract = new ModelDescriptor("one", "cnn", "layer", oneChannel, 1, 42, policy, 1);
        var single = new HeatmapEngine(new AdjointFilterBank(contract, oneChannel, policy, new double[]{1}, new double[]{0}));
        float[] shared = {1};
        assertThrows(IllegalArgumentException.class, () -> single.evaluateInto(shared, new double[1], shared));
    }
}
