package io.github.antctrlwin.anomalib4j.memory;

import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.ProjectionStrategy;
import io.github.antctrlwin.anomalib4j.projection.NormalizedPatchProjection;

import java.util.Objects;

public final class PositionalMemoryBuilder {
    private final ModelDescriptor descriptor;
    private final GridShape grid;
    private final NormalizedPatchProjection transform;
    private final ProjectionStatistics statistics;
    private double[] accumulators;
    private double[] pending;
    private final long[] sampleCounts;
    private final double[] projected;

    public PositionalMemoryBuilder(ModelDescriptor descriptor, GridShape grid,
                                   ProjectionStrategy projection, ProjectionStatistics statistics,
                                   NormalizationPolicy normalization) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.grid = Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(projection, "projection");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        Objects.requireNonNull(normalization, "normalization");
        descriptor.requireCompatible(grid, projection.inputDimensions(), projection.outputDimensions(),
                projection.seed(), normalization);
        statistics.requireCompatible(descriptor);
        int size = Math.multiplyExact(grid.cells(), descriptor.vsaDimensions());
        accumulators = new double[size];
        pending = new double[size];
        sampleCounts = new long[grid.cells()];
        transform = new NormalizedPatchProjection(projection, normalization);
        projected = new double[descriptor.vsaDimensions()];
    }

    public void observe(float[] featureMap) {
        Objects.requireNonNull(featureMap, "featureMap");
        if (featureMap.length != grid.elements()) {
            throw new IllegalArgumentException("Feature map does not match memory grid");
        }
        for (float value : featureMap) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Features must be finite");
            }
        }
        for (long count : sampleCounts) Math.incrementExact(count);
        for (int p = 0; p < grid.cells(); p++) {
            transform.project(featureMap, p * grid.channels(), projected);
            int offset = p * projected.length;
            for (int d = 0; d < projected.length; d++) {
                double z = (projected[d] - statistics.mean(d)) / statistics.stdDev(d);
                double updated = accumulators[offset + d] + z;
                if (!Double.isFinite(updated)) {
                    throw new ArithmeticException("Non-finite standardized accumulation in cell " + p);
                }
                pending[offset + d] = updated;
            }
        }
        // Commit the complete frame only after all cells succeed; reuse both buffers.
        double[] previous = accumulators;
        accumulators = pending;
        pending = previous;
        for (int p = 0; p < sampleCounts.length; p++) sampleCounts[p]++;
    }

    public PositionalMemoryBank build() {
        int dimensions = descriptor.vsaDimensions();
        double[] archetypes = new double[accumulators.length];
        for (int p = 0; p < grid.cells(); p++) {
            if (sampleCounts[p] == 0) {
                throw new IllegalStateException("No observations for cell " + p);
            }
            int offset = p * dimensions;
            double norm = 0;
            for (int d = 0; d < dimensions; d++) norm = Math.hypot(norm, accumulators[offset + d]);
            if (norm == 0 || !Double.isFinite(norm)) {
                throw new IllegalStateException("Cannot normalize accumulated archetype for cell " + p);
            }
            for (int d = 0; d < dimensions; d++) archetypes[offset + d] = accumulators[offset + d] / norm;
        }
        return new PositionalMemoryBank(descriptor, grid, dimensions, archetypes, sampleCounts, statistics);
    }
}
