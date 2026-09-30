package org.vaimee.vsa.anomalib.validation;

/** Errors are abs(rho - 1); metrics are NaN when no patches are evaluated. */
public record RhoReport(long evaluatedPatches,
                        long excludedNearZeroPatches,
                        double meanRatio,
                        double p99Error,
                        double maxError) {
}
