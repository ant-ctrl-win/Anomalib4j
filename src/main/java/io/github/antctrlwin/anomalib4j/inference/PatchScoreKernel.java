package io.github.antctrlwin.anomalib4j.inference;

import java.util.Objects;

public final class PatchScoreKernel {
    private PatchScoreKernel() {
    }

    /**
     * Computes dot(W, x) / max(norm(x), epsilon) + bias without allocations.
     * Weights must be finite and stable for the duration of the call; the filter
     * bank validates and owns them. Feature values are checked in the same pass.
     */
    public static double score(
            float[] features, int featureOffset,
            double[] weights, int weightOffset,
            int channels, double bias, double normEpsilon) {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(weights, "weights");
        if (channels <= 0) {
            throw new IllegalArgumentException("Channels must be positive");
        }
        Objects.checkFromIndexSize(featureOffset, channels, features.length);
        Objects.checkFromIndexSize(weightOffset, channels, weights.length);
        if (!Double.isFinite(normEpsilon) || normEpsilon <= 0.0 || !Double.isFinite(bias)) {
            throw new IllegalArgumentException("Invalid epsilon or bias");
        }

        double squaredNorm = 0.0;
        double dot = 0.0;
        for (int c = 0; c < channels; c++) {
            double value = features[featureOffset + c];
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Features must be finite");
            }
            squaredNorm += value * value;
            dot += weights[weightOffset + c] * value;
        }
        double denominator = Math.max(Math.sqrt(squaredNorm), normEpsilon);
        double result = dot / denominator + bias;
        if (!Double.isFinite(result)) {
            throw new ArithmeticException("Non-finite patch score");
        }
        return result;
    }
}
