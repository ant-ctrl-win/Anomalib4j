package io.github.antctrlwin.anomalib4j.memory;

import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;

import java.util.Objects;

public final class PositionalMemoryBank {
    private final ModelDescriptor descriptor;
    private final GridShape grid;
    private final int vsaDimensions;
    private final double[] archetypes;
    private final long[] sampleCounts;
    private final ProjectionStatistics statistics;

    public PositionalMemoryBank(ModelDescriptor descriptor, GridShape grid, int vsaDimensions,
                                double[] archetypes, long[] sampleCounts,
                                ProjectionStatistics statistics) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.grid = Objects.requireNonNull(grid, "grid");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        if (!descriptor.grid().equals(grid) || descriptor.vsaDimensions() != vsaDimensions) {
            throw new IllegalArgumentException("Memory dimensions do not match model descriptor");
        }
        statistics.requireCompatible(descriptor);
        this.vsaDimensions = vsaDimensions;
        Objects.requireNonNull(archetypes, "archetypes");
        Objects.requireNonNull(sampleCounts, "sampleCounts");
        if (archetypes.length != Math.multiplyExact(grid.cells(), vsaDimensions)
                || sampleCounts.length != grid.cells()) {
            throw new IllegalArgumentException("Memory buffers do not match model dimensions");
        }
        this.archetypes = archetypes.clone();
        this.sampleCounts = sampleCounts.clone();
        for (int p = 0; p < grid.cells(); p++) {
            if (this.sampleCounts[p] <= 0) {
                throw new IllegalArgumentException("Every memory cell requires observations");
            }
            double norm = 0;
            for (int d = 0; d < vsaDimensions; d++) {
                double value = this.archetypes[p * vsaDimensions + d];
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Archetypes must be finite");
                }
                norm = Math.hypot(norm, value);
            }
            if (Math.abs(norm - 1.0) > 1e-10) {
                throw new IllegalArgumentException("Archetypes must have unit L2 norm");
            }
        }
    }

    public ModelDescriptor descriptor() {
        return descriptor;
    }

    public GridShape grid() {
        return grid;
    }

    public int vsaDimensions() {
        return vsaDimensions;
    }

    public long sampleCount(int cellIndex) {
        Objects.checkIndex(cellIndex, grid.cells());
        return sampleCounts[cellIndex];
    }

    public void getArchetype(int cellIndex, double[] destination) {
        Objects.checkIndex(cellIndex, grid.cells());
        Objects.requireNonNull(destination, "destination");
        if (destination.length != vsaDimensions) {
            throw new IllegalArgumentException("Destination must have D elements");
        }
        System.arraycopy(archetypes, cellIndex * vsaDimensions, destination, 0, vsaDimensions);
    }

    public void requireStatistics(ProjectionStatistics candidate) {
        Objects.requireNonNull(candidate, "statistics");
        candidate.requireCompatible(descriptor);
        if (!statistics.hasSameContractAndValues(candidate)) {
            throw new IllegalArgumentException("Statistics differ from those used to build the memory");
        }
    }
}
