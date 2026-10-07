package io.github.antctrlwin.anomalib4j.calibration;

import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.inference.HeatmapCalibration;
import io.github.antctrlwin.anomalib4j.inference.HeatmapEngine;

import java.util.Arrays;
import java.util.Objects;

public final class HeatmapCalibrator {
    private final double nominalPercentile;
    private final double anomalyPercentile;
    private final double minimumSpan;

    public HeatmapCalibrator() {
        this(0.5, 0.99, 1e-6);
    }

    /** Percentiles are in (0, 1]; minimumSpan is in raw discrepancy units. */
    public HeatmapCalibrator(double nominalPercentile, double anomalyPercentile, double minimumSpan) {
        if (!Double.isFinite(nominalPercentile) || !Double.isFinite(anomalyPercentile)
                || nominalPercentile <= 0 || nominalPercentile >= anomalyPercentile || anomalyPercentile > 1) {
            throw new IllegalArgumentException("Percentiles must satisfy 0 < nominal < anomaly <= 1");
        }
        if (!Double.isFinite(minimumSpan) || minimumSpan <= 0) {
            throw new IllegalArgumentException("Minimum span must be finite and positive");
        }
        this.nominalPercentile = nominalPercentile;
        this.anomalyPercentile = anomalyPercentile;
        this.minimumSpan = minimumSpan;
    }

    /** Pools cells from held-out normal maps using nearest-rank empirical percentiles. */
    public HeatmapCalibration calibrate(Iterable<float[]> normalFeatureMaps, AdjointFilterBank filters) {
        Objects.requireNonNull(normalFeatureMaps, "normalFeatureMaps");
        Objects.requireNonNull(filters, "filters");
        var engine = new HeatmapEngine(filters);
        int cells = filters.grid().cells();
        double[] raw = new double[cells];
        float[] visual = new float[cells];
        double[] samples = new double[cells];
        int count = 0;
        for (float[] features : normalFeatureMaps) {
            engine.evaluateInto(features, raw, visual);
            int required = Math.addExact(count, cells);
            if (required > samples.length) {
                int capacity = (int) Math.min(Integer.MAX_VALUE,
                        Math.max((long) required, samples.length * 2L));
                samples = Arrays.copyOf(samples, capacity);
            }
            System.arraycopy(raw, 0, samples, count, cells);
            count = required;
        }
        if (count == 0) {
            throw new IllegalArgumentException("Calibration requires at least one normal feature map");
        }
        Arrays.sort(samples, 0, count);
        double lower = samples[(int) Math.ceil(nominalPercentile * count) - 1];
        double upper = samples[(int) Math.ceil(anomalyPercentile * count) - 1];
        if (upper - lower < minimumSpan) {
            upper = Math.max(lower + minimumSpan, Math.nextUp(lower));
        }
        return new HeatmapCalibration(filters.descriptor(), lower, upper);
    }
}
