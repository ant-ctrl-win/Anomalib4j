package io.github.antctrlwin.anomalib4j.model;

/** Feature map dimensions in contiguous HWC order. */
public record GridShape(int height, int width, int channels) {
    public GridShape {
        if (height <= 0 || width <= 0 || channels <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        Math.multiplyExact(Math.multiplyExact(height, width), channels);
    }

    public int cells() {
        return Math.multiplyExact(height, width);
    }

    public int elements() {
        return Math.multiplyExact(cells(), channels);
    }
}
