package io.github.antctrlwin.anomalib4j.inference;

import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;

import java.util.Objects;

public final class HeatmapEngine {
    private final AdjointFilterBank filters;
    private final HeatmapCalibration calibration;

    /** Without calibration, visual values are simply clamp(1 - score, 0, 1). */
    public HeatmapEngine(AdjointFilterBank filters) {
        this(filters, null);
    }

    public HeatmapEngine(AdjointFilterBank filters, HeatmapCalibration calibration) {
        this.filters = Objects.requireNonNull(filters, "filters");
        if (calibration != null && !filters.descriptor().equals(calibration.descriptor())) {
            throw new IllegalArgumentException("Calibration descriptor does not match compiled filters");
        }
        this.calibration = calibration;
    }

    public HeatmapResult evaluate(float[] featureMap) {
        validateFeatures(featureMap);
        double[] raw = new double[filters.grid().cells()];
        float[] calibrated = new float[raw.length];
        evaluateInto(featureMap, raw, calibrated);
        return new HeatmapResult(filters.grid().height(), filters.grid().width(), raw, calibrated);
    }

    /**
     * Overwrites caller-owned outputs without allocation on valid inputs.
     * Buffers must remain exclusive to this call; discard outputs if evaluation fails.
     */
    public void evaluateInto(float[] featureMap, double[] outRaw, float[] outCalibrated) {
        validateFeatures(featureMap);
        Objects.requireNonNull(outRaw, "outRaw");
        Objects.requireNonNull(outCalibrated, "outCalibrated");
        int cells = filters.grid().cells();
        if (outRaw.length != cells || outCalibrated.length != cells) {
            throw new IllegalArgumentException("Output buffers do not match heatmap dimensions");
        }
        if (featureMap == outCalibrated) {
            throw new IllegalArgumentException("Feature and output buffers must not alias");
        }
        for (int p = 0; p < cells; p++) {
            double raw = 1.0 - filters.scoreCell(featureMap, p);
            outRaw[p] = raw;
            outCalibrated[p] = calibration == null
                    ? (float) Math.clamp(raw, 0.0, 1.0)
                    : calibration.calibrate(raw);
        }
    }

    private void validateFeatures(float[] featureMap) {
        Objects.requireNonNull(featureMap, "featureMap");
        if (featureMap.length != filters.grid().elements()) {
            throw new IllegalArgumentException("Feature map does not match model grid");
        }
    }
}
