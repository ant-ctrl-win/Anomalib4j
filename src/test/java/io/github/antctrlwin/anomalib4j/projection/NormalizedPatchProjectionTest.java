package io.github.antctrlwin.anomalib4j.projection;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NormalizedPatchProjectionTest {
    @Test
    void preservesOriginalPreZScoreArithmeticIncludingClampAndOffsets() {
        var projection = new DenseRademacherProjection(3, 17);
        var policy = new NormalizationPolicy(1e-6);
        var transform = new NormalizedPatchProjection(projection, policy);
        for (float[] patch : new float[][]{{2, -3, 0.7f}, {0, 0, 0}, {1e-8f, -2e-8f, 0}}) {
            double normSquared = 0;
            for (float value : patch) normSquared += (double) value * value;
            double norm = Math.max(Math.sqrt(normSquared), policy.normEpsilon());
            double[] expected = new double[17];
            projection.project(patch, expected);
            for (int d = 0; d < expected.length; d++) expected[d] = expected[d] / norm / Math.sqrt(17);
            float[] map = {99, 88, patch[0], patch[1], patch[2], 77};
            float[] original = map.clone();
            double[] actual = new double[17];
            Arrays.fill(actual, Double.NaN);
            transform.project(map, 2, actual);
            assertArrayEquals(expected, actual);
            assertArrayEquals(original, map);
        }
    }

    @Test
    void rejectsBadOffsetsLengthsAndNonFiniteFeatures() {
        var transform = new NormalizedPatchProjection(new DenseRademacherProjection(3, 5), new NormalizationPolicy(1e-6));
        assertThrows(IndexOutOfBoundsException.class, () -> transform.project(new float[3], 1, new double[5]));
        assertThrows(IllegalArgumentException.class, () -> transform.project(new float[3], 0, new double[4]));
        assertThrows(IllegalArgumentException.class, () -> transform.project(new float[]{1, 2, Float.NaN}, 0, new double[5]));
    }
}
