package io.github.antctrlwin.anomalib4j.projection;

import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;

import java.util.Objects;

/** Reusable, non-thread-safe workspace for the common pre-Z-score transformation. */
public final class NormalizedPatchProjection {
    private final ProjectionStrategy projection;
    private final NormalizationPolicy normalization;
    private final float[] patch;
    private final double rootD;

    public NormalizedPatchProjection(ProjectionStrategy projection, NormalizationPolicy normalization) {
        this.projection = Objects.requireNonNull(projection, "projection");
        this.normalization = Objects.requireNonNull(normalization, "normalization");
        patch = new float[projection.inputDimensions()];
        rootD = Math.sqrt(projection.outputDimensions());
    }

    public void project(float[] features, int offset, double[] destination) {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(offset, patch.length, features.length);
        if (destination.length != projection.outputDimensions()) {
            throw new IllegalArgumentException("Destination must match VSA dimensions");
        }
        System.arraycopy(features, offset, patch, 0, patch.length);
        double squaredNorm = 0;
        for (float value : patch) squaredNorm += (double) value * value;
        double norm = Math.max(Math.sqrt(squaredNorm), normalization.normEpsilon());
        projection.project(patch, destination);
        // Keep the original double-precision operation order; do not normalize into floats.
        for (int d = 0; d < destination.length; d++) destination[d] = destination[d] / norm / rootD;
    }
}
