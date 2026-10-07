package io.github.antctrlwin.anomalib4j.inference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeatmapResultTest {
    @Test
    void resultOwnsBuffersAndExposesSafeCopiesAndGlobalMaximum() {
        double[] raw = {-2, 1, 3, 4};
        float[] calibrated = {0, 0.1f, 0.7f, 1};
        var result = new HeatmapResult(2, 2, raw, calibrated);
        raw[0] = 9;
        calibrated[0] = 1;
        assertEquals(-2, result.rawDiscrepancy(0, 0));
        assertEquals(0, result.calibrated(0, 0));
        assertEquals(1, result.anomalyScore());
        assertEquals(2, result.height());
        assertEquals(2, result.width());
        double[] rawCopy = new double[4];
        float[] visualCopy = new float[4];
        result.copyRawDiscrepancyInto(rawCopy);
        result.copyCalibratedInto(visualCopy);
        assertArrayEquals(new double[]{-2, 1, 3, 4}, rawCopy);
        assertArrayEquals(new float[]{0, 0.1f, 0.7f, 1}, visualCopy);
        rawCopy[3] = -10;
        visualCopy[3] = 0;
        assertEquals(4, result.rawDiscrepancy(1, 1));
        assertEquals(1, result.calibrated(1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> result.calibrated(2, 0));
        assertThrows(IllegalArgumentException.class, () -> result.copyCalibratedInto(new float[3]));
    }

    @Test
    void rejectsInvalidGeometryAndValues() {
        assertThrows(IllegalArgumentException.class, () -> new HeatmapResult(0, 1, new double[0], new float[0]));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapResult(1, 2, new double[1], new float[2]));
        assertThrows(IllegalArgumentException.class, () -> new HeatmapResult(1, 1, new double[]{Double.NaN}, new float[]{0}));
        for (float value : new float[]{-0.1f, 1.1f, Float.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> new HeatmapResult(1, 1, new double[]{0}, new float[]{value}));
        }
    }
}
