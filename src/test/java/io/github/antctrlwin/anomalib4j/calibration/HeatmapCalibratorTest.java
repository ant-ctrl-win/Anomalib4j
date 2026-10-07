package io.github.antctrlwin.anomalib4j.calibration;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.inference.HeatmapCalibration;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeatmapCalibratorTest {
    private final GridShape grid = new GridShape(2, 2, 1);
    private final NormalizationPolicy policy = new NormalizationPolicy(1e-6);
    private final ModelDescriptor descriptor = new ModelDescriptor("known", "cnn", "layer", grid, 5, 42, policy, 1);

    @Test
    void estimatesKnownPooledNearestRankPercentiles() {
        var bank = new AdjointFilterBank(descriptor, grid, policy, new double[4], new double[]{3, 2, 1, 0});
        var calibration = new HeatmapCalibrator(0.5, 0.75, 1e-6)
                .calibrate(List.of(new float[4], new float[4], new float[4]), bank);
        assertEquals(descriptor, calibration.descriptor());
        assertEquals(-1, calibration.nominalThreshold());
        assertEquals(0, calibration.anomalyThreshold());
        assertEquals(0, calibration.calibrate(-2));
        assertEquals(0, calibration.calibrate(-1));
        assertEquals(0.5f, calibration.calibrate(-0.5));
        assertEquals(1, calibration.calibrate(0));
        assertEquals(1, calibration.calibrate(2));
    }

    @Test
    void constantNominalDataUsesConfiguredMinimumSpan() {
        var bank = new AdjointFilterBank(descriptor, grid, policy, new double[4], new double[4]);
        var calibration = new HeatmapCalibrator(0.5, 0.99, 0.25).calibrate(List.of(new float[4]), bank);
        assertEquals(1, calibration.nominalThreshold());
        assertEquals(1.25, calibration.anomalyThreshold());
        assertEquals(0, calibration.calibrate(1));
        assertEquals(1, calibration.calibrate(1.25));
    }

    @Test
    void rejectsEmptyDataInvalidPercentilesAndNonFiniteThresholds() {
        var bank = new AdjointFilterBank(descriptor, grid, policy, new double[4], new double[4]);
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibrator().calibrate(List.of(), bank));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibrator(0.99, 0.5, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibrator(0, 0.99, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibrator(0.5, 1.1, 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibrator(0.5, 0.99, 0));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibration(descriptor, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibration(descriptor, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibration(descriptor, 0, 1).calibrate(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapCalibrator().calibrate(List.of(new float[3]), bank));
    }
}
