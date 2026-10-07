package io.github.antctrlwin.anomalib4j.model;

import java.util.Objects;

public record ModelDescriptor(String modelId, String encoderId, String layerId,
                              GridShape grid, int vsaDimensions, long projectionSeed,
                              NormalizationPolicy normalization, int version, String preprocessingId) {
    public ModelDescriptor(String modelId, String encoderId, String layerId,
                           GridShape grid, int vsaDimensions, long projectionSeed,
                           NormalizationPolicy normalization, int version) {
        this(modelId, encoderId, layerId, grid, vsaDimensions, projectionSeed,
                normalization, version, "unspecified");
    }

    public ModelDescriptor {
        requireIdentifier(modelId, "modelId");
        requireIdentifier(encoderId, "encoderId");
        requireIdentifier(layerId, "layerId");
        requireIdentifier(preprocessingId, "preprocessingId");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(normalization, "normalization");
        if (vsaDimensions <= 0 || version <= 0) {
            throw new IllegalArgumentException("VSA dimensions and version must be positive");
        }
        Math.multiplyExact(grid.cells(), vsaDimensions);
    }

    public void requireCompatible(GridShape actualGrid, int channels, int dimensions,
                                  long seed, NormalizationPolicy policy) {
        if (!grid.equals(actualGrid)) {
            throw new IllegalArgumentException("Grid does not match model descriptor");
        }
        if (channels != grid.channels() || dimensions != vsaDimensions) {
            throw new IllegalArgumentException("Projection dimensions do not match model descriptor");
        }
        if (seed != projectionSeed) {
            throw new IllegalArgumentException("Projection seed does not match model descriptor");
        }
        if (!normalization.equals(policy)) {
            throw new IllegalArgumentException("Normalization does not match model descriptor");
        }
    }

    private static void requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
