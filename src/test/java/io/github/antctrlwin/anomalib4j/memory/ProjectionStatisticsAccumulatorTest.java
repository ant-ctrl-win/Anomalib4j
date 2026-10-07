package io.github.antctrlwin.anomalib4j.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionStatisticsAccumulatorTest {
    @Test
    void welfordMatchesStaticSampleStatistics() {
        double[][] samples = {{1, 10, -2}, {2, 14, -4}, {4, 18, 0}, {5, 22, 2}};
        var accumulator = new ProjectionStatisticsAccumulator(3);
        for (double[] sample : samples) accumulator.observe(sample);
        var result = accumulator.finalizeStatistics(1e-8);
        assertEquals(4, accumulator.count());
        for (int d = 0; d < 3; d++) {
            double mean = 0;
            for (double[] sample : samples) mean += sample[d];
            mean /= samples.length;
            double variance = 0;
            for (double[] sample : samples) variance += Math.pow(sample[d] - mean, 2);
            variance /= samples.length - 1;
            assertEquals(mean, result.mean(d), 1e-12);
            assertEquals(Math.sqrt(variance), result.stdDev(d), 1e-12);
        }
    }

    @Test
    void floorAppliesToStandardDeviationAndSnapshotsAreIndependent() {
        var accumulator = new ProjectionStatisticsAccumulator(2);
        double[] sample = {3, 0};
        accumulator.observe(sample);
        sample[0] = 100;
        accumulator.observe(new double[]{3, 0.01});
        var snapshot = accumulator.finalizeStatistics(0.1);
        assertArrayEquals(new double[]{3, 0.005}, snapshot.mean(), 1e-12);
        assertArrayEquals(new double[]{0.1, 0.1}, snapshot.stdDev());
        accumulator.observe(new double[]{9, 1});
        assertEquals(3, snapshot.mean(0));
        assertEquals(5, accumulator.finalizeStatistics(0.1).mean(0));
    }

    @Test
    void containerOwnsArraysAndReturnsCopies() {
        double[] means = {1, 2};
        double[] deviations = {0.5, 0.7};
        var statistics = new ProjectionStatistics(means, deviations);
        means[0] = 9;
        deviations[0] = 9;
        statistics.mean()[0] = 99;
        statistics.stdDev()[0] = 99;
        assertEquals(1, statistics.mean(0));
        assertEquals(0.5, statistics.stdDev(0));
        assertEquals(2, statistics.dimensions());
    }

    @Test
    void rejectsInvalidInputsWithoutChangingAccumulatedState() {
        var accumulator = new ProjectionStatisticsAccumulator(2);
        assertThrows(IllegalStateException.class, () -> accumulator.finalizeStatistics(1e-8));
        accumulator.observe(new double[]{1, 2});
        assertThrows(IllegalStateException.class, () -> accumulator.finalizeStatistics(1e-8));
        assertThrows(IllegalArgumentException.class, () -> accumulator.observe(new double[]{3, Double.NaN}));
        assertThrows(IllegalArgumentException.class, () -> accumulator.observe(new double[]{3}));
        assertEquals(1, accumulator.count());
        accumulator.observe(new double[]{3, 4});
        assertArrayEquals(new double[]{2, 3}, accumulator.finalizeStatistics(1e-8).mean());
        for (double floor : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> accumulator.finalizeStatistics(floor));
        }
        assertThrows(IllegalArgumentException.class, () -> new ProjectionStatistics(new double[]{0}, new double[]{0}));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionStatistics(new double[]{Double.NaN}, new double[]{1}));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionStatistics(new double[]{1}, new double[]{1, 2}));
    }
}
