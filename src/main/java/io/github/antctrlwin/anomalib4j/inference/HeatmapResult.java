package io.github.antctrlwin.anomalib4j.inference;

import java.util.Objects;

public final class HeatmapResult {
    private final int height;
    private final int width;
    private final double[] rawDiscrepancy;
    private final float[] calibrated;
    private final float anomalyScore;

    public HeatmapResult(int height, int width, double[] rawDiscrepancy, float[] calibrated) {
        if (height <= 0 || width <= 0) {
            throw new IllegalArgumentException("Heatmap dimensions must be positive");
        }
        int cells = Math.multiplyExact(height, width);
        Objects.requireNonNull(rawDiscrepancy, "rawDiscrepancy");
        Objects.requireNonNull(calibrated, "calibrated");
        if (rawDiscrepancy.length != cells || calibrated.length != cells) {
            throw new IllegalArgumentException("Heatmap buffers do not match dimensions");
        }
        this.height = height;
        this.width = width;
        this.rawDiscrepancy = rawDiscrepancy.clone();
        this.calibrated = calibrated.clone();
        float maximum = 0;
        for (int p = 0; p < cells; p++) {
            if (!Double.isFinite(this.rawDiscrepancy[p])) {
                throw new IllegalArgumentException("Raw discrepancy must be finite");
            }
            float value = this.calibrated[p];
            if (!Float.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Calibrated values must lie in [0, 1]");
            }
            maximum = Math.max(maximum, value);
        }
        anomalyScore = maximum;
    }

    public int height() {
        return height;
    }

    public int width() {
        return width;
    }

    public double rawDiscrepancy(int row, int column) {
        return rawDiscrepancy[index(row, column)];
    }

    public float calibrated(int row, int column) {
        return calibrated[index(row, column)];
    }

    public float anomalyScore() {
        return anomalyScore;
    }

    public void copyRawDiscrepancyInto(double[] destination) {
        Objects.requireNonNull(destination, "destination");
        if (destination.length != rawDiscrepancy.length) {
            throw new IllegalArgumentException("Destination must match heatmap size");
        }
        System.arraycopy(rawDiscrepancy, 0, destination, 0, destination.length);
    }

    public void copyCalibratedInto(float[] destination) {
        Objects.requireNonNull(destination, "destination");
        if (destination.length != calibrated.length) {
            throw new IllegalArgumentException("Destination must match heatmap size");
        }
        System.arraycopy(calibrated, 0, destination, 0, destination.length);
    }

    private int index(int row, int column) {
        Objects.checkIndex(row, height);
        Objects.checkIndex(column, width);
        return row * width + column;
    }
}
