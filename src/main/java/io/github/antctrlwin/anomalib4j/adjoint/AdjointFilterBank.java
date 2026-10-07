package io.github.antctrlwin.anomalib4j.adjoint;

import io.github.antctrlwin.anomalib4j.inference.PatchScoreKernel;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;

import java.util.Objects;

/** Immutable positional filters: weights in HWC order and one bias per cell. */
public final class AdjointFilterBank {
    private final ModelDescriptor descriptor;
    private final GridShape grid;
    private final NormalizationPolicy normalization;
    private final double[] weights;
    private final double[] biases;

    public AdjointFilterBank(ModelDescriptor descriptor, GridShape grid,
                             NormalizationPolicy normalization,
                             double[] weights, double[] biases) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.grid = Objects.requireNonNull(grid, "grid");
        this.normalization = Objects.requireNonNull(normalization, "normalization");
        if (!descriptor.grid().equals(grid) || !descriptor.normalization().equals(normalization)) {
            throw new IllegalArgumentException("Filter geometry or normalization does not match descriptor");
        }
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(biases, "biases");
        if (weights.length != grid.elements() || biases.length != grid.cells()) {
            throw new IllegalArgumentException("Filter buffers do not match grid dimensions");
        }
        this.weights = weights.clone();
        this.biases = biases.clone();
        requireFinite(this.weights);
        requireFinite(this.biases);
    }

    public ModelDescriptor descriptor() {
        return descriptor;
    }

    public GridShape grid() {
        return grid;
    }

    public NormalizationPolicy normalization() {
        return normalization;
    }

    /** Scores one cell of a complete HWC map, which must remain stable during the call. */
    public double scoreCell(float[] features, int patchIndex) {
        Objects.requireNonNull(features, "features");
        if (features.length != grid.elements()) {
            throw new IllegalArgumentException("Feature map does not match grid dimensions");
        }
        Objects.checkIndex(patchIndex, grid.cells());
        int offset = patchIndex * grid.channels();
        return PatchScoreKernel.score(features, offset, weights, offset,
                grid.channels(), biases[patchIndex], normalization.normEpsilon());
    }

    private static void requireFinite(double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Filter coefficients must be finite");
            }
        }
    }
}
