package io.github.antctrlwin.anomalib4j.model;

/** Epsilon clamps the L2 norm, not its square. */
public record NormalizationPolicy(double normEpsilon) {
    public NormalizationPolicy {
        if (!Double.isFinite(normEpsilon) || normEpsilon <= 0.0) {
            throw new IllegalArgumentException("Norm epsilon must be finite and positive");
        }
    }
}
