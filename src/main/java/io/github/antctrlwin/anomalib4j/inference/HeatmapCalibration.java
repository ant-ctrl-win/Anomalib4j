package io.github.antctrlwin.anomalib4j.inference;

import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;

import java.util.Objects;

public record HeatmapCalibration(ModelDescriptor descriptor,
                                 double nominalThreshold, double anomalyThreshold) {
    public HeatmapCalibration {
        Objects.requireNonNull(descriptor, "descriptor");
        double span = anomalyThreshold - nominalThreshold;
        if (!Double.isFinite(nominalThreshold) || !Double.isFinite(anomalyThreshold)
                || !Double.isFinite(span) || span <= 0) {
            throw new IllegalArgumentException("Calibration requires finite thresholds with positive finite span");
        }
    }

    public float calibrate(double rawDiscrepancy) {
        if (!Double.isFinite(rawDiscrepancy)) {
            throw new IllegalArgumentException("Raw discrepancy must be finite");
        }
        if (rawDiscrepancy <= nominalThreshold) return 0;
        if (rawDiscrepancy >= anomalyThreshold) return 1;
        return (float) ((rawDiscrepancy - nominalThreshold) / (anomalyThreshold - nominalThreshold));
    }
}
