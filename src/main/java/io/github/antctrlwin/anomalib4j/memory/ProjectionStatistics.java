package io.github.antctrlwin.anomalib4j.memory;

import java.util.Objects;
import java.util.Arrays;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;

public final class ProjectionStatistics {
    private final double[] mean;
    private final double[] stdDev;
    private final ModelDescriptor descriptor;

    public ProjectionStatistics(double[] mean, double[] stdDev) {
        this(mean, stdDev, null);
    }

    private ProjectionStatistics(double[] mean, double[] stdDev, ModelDescriptor descriptor) {
        Objects.requireNonNull(mean, "mean");
        Objects.requireNonNull(stdDev, "stdDev");
        if (mean.length == 0 || mean.length != stdDev.length) {
            throw new IllegalArgumentException("Statistics dimensions must be equal and positive");
        }
        this.mean = mean.clone();
        this.stdDev = stdDev.clone();
        this.descriptor = descriptor;
        for (int d = 0; d < this.mean.length; d++) {
            if (!Double.isFinite(this.mean[d]) || !Double.isFinite(this.stdDev[d]) || this.stdDev[d] <= 0) {
                throw new IllegalArgumentException("Means must be finite and deviations finite and positive");
            }
        }
    }

    public ProjectionStatistics withDescriptor(ModelDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.vsaDimensions() != dimensions()) {
            throw new IllegalArgumentException("Statistics dimensions do not match model descriptor");
        }
        if (this.descriptor != null && !this.descriptor.equals(descriptor)) {
            throw new IllegalArgumentException("Statistics are already bound to another model descriptor");
        }
        return new ProjectionStatistics(mean, stdDev, descriptor);
    }

    public void requireCompatible(ModelDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (dimensions() != descriptor.vsaDimensions() || !descriptor.equals(this.descriptor)) {
            throw new IllegalArgumentException("Statistics are unbound or have an incompatible model descriptor");
        }
    }

    public boolean hasSameContractAndValues(ProjectionStatistics other) {
        Objects.requireNonNull(other, "other");
        return Objects.equals(descriptor, other.descriptor)
                && Arrays.equals(mean, other.mean) && Arrays.equals(stdDev, other.stdDev);
    }

    public int dimensions() {
        return mean.length;
    }

    public double mean(int dimension) {
        return mean[dimension];
    }

    public double stdDev(int dimension) {
        return stdDev[dimension];
    }

    public double[] mean() {
        return mean.clone();
    }

    public double[] stdDev() {
        return stdDev.clone();
    }
}
