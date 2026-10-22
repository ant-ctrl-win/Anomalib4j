package io.github.antctrlwin.anomalib4j.m2;

import io.github.antctrlwin.anomalib4j.onnx.EncoderSpec;
import java.util.Objects;

/** Immutable inference state for the frozen positional diagonal M2 detector. */
public final class M2Model {
    public static final int CELLS = 196;
    public static final int CHANNELS = 96;
    public static final int ELEMENTS = CELLS * CHANNELS;
    public static final double FLOOR = 1e-6;
    public static final String PREPROCESSING = "imagenet-rgb-resize224-bicubic-v1";
    private final String encoderSha256;
    private final EncoderSpec encoderSpec;
    private final double[] mean, std, alpha, beta;

    public record Local(double[] mean, double[] effectiveStd) {
        public Local {
            if (mean == null || (mean.length != ELEMENTS && mean.length != EncoderSpec.LARGE_SPATIAL_14.grid().elements()))
                throw new IllegalArgumentException("Expected Small or Large local state");
            requireFinite(mean, mean.length); requireScales(effectiveStd, mean.length);
            mean = mean.clone(); effectiveStd = effectiveStd.clone();
        }
        @Override public double[] mean() { return mean.clone(); }
        @Override public double[] effectiveStd() { return effectiveStd.clone(); }
    }
    public record Calibration(double[] alpha, double[] beta) {
        public Calibration {
            requireFinite(alpha, CELLS); requireScales(beta, CELLS);
            alpha = alpha.clone(); beta = beta.clone();
        }
        @Override public double[] alpha() { return alpha.clone(); }
        @Override public double[] beta() { return beta.clone(); }
    }
    public M2Model(String encoderSha256, Local local, Calibration calibration) {
        this(EncoderSpec.SMALL_SPATIAL_14, encoderSha256, local, calibration);
    }
    public M2Model(EncoderSpec spec, Local local, Calibration calibration) {
        this(spec, spec.sha256(), local, calibration);
    }
    M2Model(EncoderSpec spec, String encoderSha256, Local local, Calibration calibration) {
        encoderSpec = Objects.requireNonNull(spec);
        if (encoderSha256 == null || !encoderSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("SHA-256 required");
        this.encoderSha256 = encoderSha256;
        mean = local.mean(); std = local.effectiveStd();
        requireFinite(mean, spec.grid().elements()); requireScales(std, spec.grid().elements());
        alpha = calibration.alpha(); beta = calibration.beta();
    }
    public String encoderSha256() { return encoderSha256; }
    public EncoderSpec encoderSpec() { return encoderSpec; }
    public Local local() { return new Local(mean, std); }
    public Calibration calibration() { return new Calibration(alpha, beta); }

    public static double[] normalize(float[] features) {
        return normalize(features, EncoderSpec.SMALL_SPATIAL_14);
    }
    public static double[] normalize(float[] features, EncoderSpec spec) {
        int channels = spec.grid().channels(), elements = spec.grid().elements();
        if (features.length != elements) throw new IllegalArgumentException("Expected " + spec.grid() + " HWC");
        double[] unit = new double[elements];
        for (int p = 0; p < CELLS; p++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                double value = features[p * channels + c];
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite descriptor");
                sum += value * value;
            }
            double norm = Math.sqrt(sum);
            if (norm < FLOOR) throw new IllegalArgumentException("Descriptor norm below guard at position " + p);
            for (int c = 0; c < channels; c++) unit[p * channels + c] = features[p * channels + c] / norm;
        }
        return unit;
    }
    public static void requireUnit(double[] unit) {
        requireUnit(unit, EncoderSpec.SMALL_SPATIAL_14);
    }
    public static void requireUnit(double[] unit, EncoderSpec spec) {
        int channels = spec.grid().channels();
        requireFinite(unit, spec.grid().elements());
        for (int p = 0; p < CELLS; p++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) sum += unit[p * channels + c] * unit[p * channels + c];
            if (Math.abs(sum - 1) > 2e-12) throw new IllegalArgumentException("Expected unit descriptor at " + p);
        }
    }
    public double[] raw(double[] unit) {
        requireUnit(unit, encoderSpec);
        int channels = encoderSpec.grid().channels();
        double[] result = new double[CELLS];
        for (int p = 0; p < CELLS; p++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                int i = p * channels + c;
                double residual = (unit[i] - mean[i]) / std[i];
                sum += residual * residual;
            }
            result[p] = sum / channels;
        }
        requireFinite(result, CELLS);
        return result;
    }
    public double[] calibrate(double[] raw) {
        requireFinite(raw, CELLS);
        double[] z = new double[CELLS];
        for (int p = 0; p < CELLS; p++) z[p] = (raw[p] - alpha[p]) / beta[p];
        requireFinite(z, CELLS);
        return z;
    }
    public double[] score(float[] features) { return calibrate(raw(normalize(features, encoderSpec))); }
    public static double imageScore(double[] z) {
        requireFinite(z, CELLS);
        double max = Double.NEGATIVE_INFINITY;
        for (double value : z) max = Math.max(max, value);
        return max;
    }
    static void requireFinite(double[] values, int size) {
        if (values == null || values.length != size) throw new IllegalArgumentException("Invalid array shape");
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite value");
    }
    private static void requireScales(double[] values, int size) {
        requireFinite(values, size);
        for (double value : values) if (value < FLOOR) throw new IllegalArgumentException("Scale below floor");
    }
}
