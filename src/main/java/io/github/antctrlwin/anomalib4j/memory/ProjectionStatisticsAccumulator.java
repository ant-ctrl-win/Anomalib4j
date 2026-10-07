package io.github.antctrlwin.anomalib4j.memory;

import java.util.Objects;

public final class ProjectionStatisticsAccumulator {
    private final double[] mean;
    private final double[] m2;
    private long count;

    public ProjectionStatisticsAccumulator(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Statistics dimensions must be positive");
        }
        mean = new double[dimensions];
        m2 = new double[dimensions];
    }

    public int dimensions() {
        return mean.length;
    }

    public long count() {
        return count;
    }

    public void observe(double[] sample) {
        Objects.requireNonNull(sample, "sample");
        if (sample.length != mean.length) {
            throw new IllegalArgumentException("Sample does not match statistics dimensions");
        }
        long nextCount = Math.incrementExact(count);
        // Validate the complete update before mutating any accumulated dimension.
        for (int d = 0; d < mean.length; d++) {
            if (!Double.isFinite(sample[d])) {
                throw new IllegalArgumentException("Samples must be finite");
            }
            double delta = sample[d] - mean[d];
            double nextMean = mean[d] + delta / nextCount;
            double nextM2 = m2[d] + delta * (sample[d] - nextMean);
            if (!Double.isFinite(nextMean) || !Double.isFinite(nextM2)) {
                throw new ArithmeticException("Statistics update overflow");
            }
        }
        for (int d = 0; d < mean.length; d++) {
            double delta = sample[d] - mean[d];
            mean[d] += delta / nextCount;
            m2[d] += delta * (sample[d] - mean[d]);
        }
        count = nextCount;
    }

    /** Returns a snapshot; floorEpsilon clamps standard deviation, not variance. */
    public ProjectionStatistics finalizeStatistics(double floorEpsilon) {
        if (!Double.isFinite(floorEpsilon) || floorEpsilon <= 0) {
            throw new IllegalArgumentException("Standard deviation floor must be finite and positive");
        }
        if (count < 2) {
            throw new IllegalStateException("Sample variance requires at least two observations");
        }
        double[] stdDev = new double[mean.length];
        for (int d = 0; d < mean.length; d++) {
            double variance = Math.max(0.0, m2[d] / (count - 1));
            stdDev[d] = Math.max(Math.sqrt(variance), floorEpsilon);
        }
        return new ProjectionStatistics(mean, stdDev);
    }
}
