package io.github.antctrlwin.anomalib4j.validation;

import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.ProjectionStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ProjectionDiagnostics {
    private ProjectionDiagnostics() {
    }

    public static double rho(ProjectionStrategy projection, float[] features) {
        Objects.requireNonNull(projection, "projection");
        double norm = featureNorm(projection, features);
        if (norm == 0) {
            throw new IllegalArgumentException("Rho is undefined for a zero vector");
        }
        return ratio(projection, features, norm, new double[projection.outputDimensions()]);
    }

    /** Excludes norms <= epsilon and uses nearest-rank p99 of absolute errors. */
    public static RhoReport evaluate(ProjectionStrategy projection, Iterable<float[]> batch,
                                     NormalizationPolicy policy) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(policy, "policy");
        List<Double> errors = new ArrayList<>();
        double[] projected = new double[projection.outputDimensions()];
        long excluded = 0;
        double meanRatio = 0;
        for (float[] features : batch) {
            double norm = featureNorm(projection, features);
            if (norm <= policy.normEpsilon()) {
                excluded++;
                continue;
            }
            double rho = ratio(projection, features, norm, projected);
            errors.add(Math.abs(rho - 1));
            meanRatio += (rho - meanRatio) / errors.size();
        }
        if (errors.isEmpty()) {
            return new RhoReport(0, excluded, Double.NaN, Double.NaN, Double.NaN);
        }
        errors.sort(Comparator.naturalOrder());
        int percentileIndex = (int) Math.ceil(0.99 * errors.size()) - 1;
        return new RhoReport(errors.size(), excluded, meanRatio,
                errors.get(percentileIndex), errors.getLast());
    }

    private static double featureNorm(ProjectionStrategy projection, float[] features) {
        Objects.requireNonNull(features, "features");
        if (features.length != projection.inputDimensions()) {
            throw new IllegalArgumentException("Feature dimensions do not match projection");
        }
        double squaredNorm = 0;
        for (float value : features) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Features must be finite");
            }
            squaredNorm += (double) value * value;
        }
        return Math.sqrt(squaredNorm);
    }

    private static double ratio(ProjectionStrategy projection, float[] features,
                                double norm, double[] projected) {
        projection.project(features, projected);
        double squaredNorm = 0;
        for (double value : projected) {
            squaredNorm += value * value;
        }
        return Math.sqrt(squaredNorm) / (Math.sqrt(projection.outputDimensions()) * norm);
    }
}
