package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PositionalRawCalibrationTest {
    private final ModelDescriptor descriptor = new ModelDescriptor("raw-test", "cnn", "layer",
            new GridShape(1, 2, 1), 10000, 42, new NormalizationPolicy(1e-6), 1);

    @Test void estimatesSeparatePositionMeansAndSampleDeviations() {
        var calibration = PositionalRawCalibration.fit(descriptor,
                List.of(new double[]{1, 10}, new double[]{2, 14}, new double[]{3, 18}), 1e-6);
        assertEquals(3, calibration.count());
        assertEquals(2, calibration.mean(0));
        assertEquals(14, calibration.mean(1));
        assertEquals(1, calibration.sampleSigma(0));
        assertEquals(4, calibration.sampleSigma(1));
        assertArrayEquals(new double[]{-1, 1}, calibration.calibrate(new double[]{1, 18}), 1e-12);
        assertEquals(0, calibration.flooredPositions());
    }

    @Test void centeredTrainingZScoresHaveUnitSampleVarianceAtEachPosition() {
        var maps = List.of(new double[]{-9, 10}, new double[]{-2, 15}, new double[]{4, 14}, new double[]{11, 21});
        var calibration = PositionalRawCalibration.fit(descriptor, maps, 1e-6);
        for (int p = 0; p < 2; p++) {
            double sum = 0;
            double squares = 0;
            for (double[] raw : maps) {
                double z = calibration.calibrate(raw)[p];
                sum += z;
                squares += z * z;
            }
            assertEquals(0, sum / maps.size(), 1e-12);
            assertEquals(1, squares / (maps.size() - 1), 1e-12);
        }
    }

    @Test void floorsConstantAndTinyDeviationsWithoutClampingScores() {
        var calibration = PositionalRawCalibration.fit(descriptor,
                List.of(new double[]{5, 1}, new double[]{5, 1 + 1e-8}), 1e-6);
        assertEquals(0, calibration.sampleSigma(0));
        assertTrue(calibration.sampleSigma(1) > 0 && calibration.sampleSigma(1) < 1e-6);
        assertEquals(1e-6, calibration.sigma(0));
        assertEquals(1e-6, calibration.sigma(1));
        assertEquals(2, calibration.flooredPositions());
        double[] z = calibration.calibrate(new double[]{4, 2});
        assertEquals(-1_000_000, z[0]);
        assertTrue(z[1] > 100_000);
        assertTrue(calibration.parametersCsv().contains("true"));
    }

    @Test void doesNotRetainInputsOrNormalizeEachInferenceImage() {
        double[] first = {1, 10};
        double[] second = {3, 14};
        var calibration = PositionalRawCalibration.fit(descriptor, List.of(first, second), 1e-6);
        first[0] = 1000;
        second[1] = 1000;
        double[] input = {4, 20};
        double[] z = calibration.calibrate(input);
        assertArrayEquals(new double[]{4, 20}, input);
        assertEquals(Math.sqrt(2), z[0], 1e-12);
        assertEquals(2 * Math.sqrt(2), z[1], 1e-12);
        input[1] = -1000;
        assertEquals(z[0], calibration.calibrate(input)[0]);
        z[0] = 0;
        assertEquals(2, calibration.mean(0));
        assertTrue(calibration.summaryCsv().contains("sample_std_across_positions"));
        assertEquals(4, calibration.summaryCsv().lines().count());
    }

    @Test void rejectsInvalidSamplesFloorsAndDifferentModelIdentity() {
        assertThrows(IllegalArgumentException.class, () -> PositionalRawCalibration.fit(descriptor, List.of(new double[2]), 1e-6));
        var valid = List.of(new double[]{1, 2}, new double[]{3, 4});
        for (double floor : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> PositionalRawCalibration.fit(descriptor, valid, floor));
        }
        assertThrows(IllegalArgumentException.class, () -> PositionalRawCalibration.fit(descriptor, List.of(new double[2], new double[3]), 1e-6));
        assertThrows(IllegalArgumentException.class, () -> PositionalRawCalibration.fit(descriptor,
                List.of(new double[2], new double[]{Double.NaN, 0}), 1e-6));
        var calibration = PositionalRawCalibration.fit(descriptor, valid, 1e-6);
        calibration.requireCompatible(descriptor);
        var different = new ModelDescriptor("other", "cnn", "layer", descriptor.grid(), 10000, 42, descriptor.normalization(), 1);
        assertThrows(IllegalArgumentException.class, () -> calibration.requireCompatible(different));
        assertThrows(IllegalArgumentException.class, () -> calibration.calibrate(new double[1]));
        assertThrows(IllegalArgumentException.class, () -> calibration.calibrate(new double[]{0, Double.POSITIVE_INFINITY}));
    }
}
