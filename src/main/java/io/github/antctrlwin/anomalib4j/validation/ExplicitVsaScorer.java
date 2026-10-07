package io.github.antctrlwin.anomalib4j.validation;

import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBank;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.NormalizedPatchProjection;
import io.github.antctrlwin.anomalib4j.projection.ProjectionStrategy;

import java.util.Objects;

/** Explicit reference path with reusable buffers; instances are not thread-safe. */
public final class ExplicitVsaScorer {
    private final PositionalMemoryBank memory;
    private final ProjectionStatistics statistics;
    private final NormalizedPatchProjection transform;
    private final double[] projected;
    private final double[] archetype;

    public ExplicitVsaScorer(PositionalMemoryBank memory, ProjectionStatistics statistics,
                             ProjectionStrategy projection, NormalizationPolicy normalization) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        Objects.requireNonNull(projection, "projection");
        memory.descriptor().requireCompatible(memory.grid(), projection.inputDimensions(),
                projection.outputDimensions(), projection.seed(), normalization);
        memory.requireStatistics(statistics);
        transform = new NormalizedPatchProjection(projection, normalization);
        projected = new double[memory.vsaDimensions()];
        archetype = new double[memory.vsaDimensions()];
    }

    public double scoreCell(float[] features, int cell) {
        Objects.requireNonNull(features, "features");
        if (features.length != memory.grid().elements()) {
            throw new IllegalArgumentException("Feature map does not match model grid");
        }
        Objects.checkIndex(cell, memory.grid().cells());
        transform.project(features, cell * memory.grid().channels(), projected);
        memory.getArchetype(cell, archetype);
        double score = 0;
        for (int d = 0; d < projected.length; d++) {
            score += ((projected[d] - statistics.mean(d)) / statistics.stdDev(d)) * archetype[d];
        }
        if (!Double.isFinite(score)) throw new ArithmeticException("Explicit score overflow");
        return score;
    }
}
