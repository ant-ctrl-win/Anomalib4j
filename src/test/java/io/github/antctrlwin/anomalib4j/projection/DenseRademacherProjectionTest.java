package io.github.antctrlwin.anomalib4j.projection;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DenseRademacherProjectionTest {
    @Test
    void reproducesLegacyLcgInChannelFirstOrder() {
        int channels = 67;
        int dimensions = 71;
        var projection = new DenseRademacherProjection(channels, dimensions);
        long state = (42L ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        for (int c = 0; c < channels; c++) {
            float[] basis = new float[channels];
            basis[c] = 1;
            double[] actual = new double[dimensions];
            projection.project(basis, actual);
            for (int d = 0; d < dimensions; d++) {
                state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                assertEquals((state >>> 47) != 0 ? 1.0 : -1.0, actual[d]);
            }
        }
    }

    @Test
    void sameSeedProducesSameMatrixAndDifferentSeedsDiffer() {
        var first = new DenseRademacherProjection(9, 37, 42L);
        var second = new DenseRademacherProjection(9, 37, 42L);
        var other = new DenseRademacherProjection(9, 37, 43L);
        boolean differs = false;
        for (int c = 0; c < 9; c++) {
            float[] basis = new float[9];
            basis[c] = 1;
            double[] a = new double[37];
            double[] b = new double[37];
            double[] z = new double[37];
            first.project(basis, a);
            second.project(basis, b);
            other.project(basis, z);
            assertArrayEquals(a, b);
            differs |= !Arrays.equals(a, z);
        }
        assertTrue(differs);
    }

    @Test
    void transposeSatisfiesInnerProductIdentityAndOverwritesOutput() {
        var projection = new DenseRademacherProjection(67, 131, -17L);
        var random = new Random(123L);
        for (int trial = 0; trial < 12; trial++) {
            float[] x = new float[67];
            double[] q = new double[131];
            for (int c = 0; c < x.length; c++) x[c] = random.nextFloat() * 2 - 1;
            for (int d = 0; d < q.length; d++) q[d] = random.nextDouble() * 2 - 1;
            float[] originalX = x.clone();
            double[] originalQ = q.clone();
            double[] y = new double[131];
            double[] transposed = new double[67];
            Arrays.fill(y, 99);
            Arrays.fill(transposed, 99);
            projection.project(x, y);
            projection.adjoint(q, transposed);
            double left = 0;
            double right = 0;
            for (int d = 0; d < q.length; d++) left += y[d] * q[d];
            for (int c = 0; c < x.length; c++) right += x[c] * transposed[c];
            assertEquals(left, right, 1e-10);
            assertArrayEquals(originalX, x);
            assertArrayEquals(originalQ, q);
            double[] again = new double[67];
            projection.adjoint(q, again);
            assertArrayEquals(again, transposed);
        }
    }

    @Test
    void validatesDimensionsValuesAndAliasing() {
        assertThrows(IllegalArgumentException.class, () -> new DenseRademacherProjection(0, 3));
        var projection = new DenseRademacherProjection(2, 2);
        assertThrows(IllegalArgumentException.class, () -> projection.project(new float[1], new double[2]));
        assertThrows(IllegalArgumentException.class, () -> projection.project(new float[]{Float.NaN, 0}, new double[2]));
        assertThrows(IllegalArgumentException.class, () -> projection.adjoint(new double[]{0, Double.POSITIVE_INFINITY}, new double[2]));
        double[] shared = {1, 2};
        assertThrows(IllegalArgumentException.class, () -> projection.adjoint(shared, shared));
    }
}
