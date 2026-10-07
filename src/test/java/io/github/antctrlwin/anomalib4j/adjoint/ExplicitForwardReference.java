package io.github.antctrlwin.anomalib4j.adjoint;

final class ExplicitForwardReference {
    private ExplicitForwardReference() {
    }

    static double score(Fixtures fixture, float[] features, int patch) {
        int channels = fixture.grid.channels();
        double[] normalized = new double[channels];
        double squaredNorm = 0;
        for (int c = 0; c < channels; c++) {
            double value = features[patch * channels + c];
            squaredNorm += value * value;
        }
        double norm = Math.max(Math.sqrt(squaredNorm), fixture.policy.normEpsilon());
        for (int c = 0; c < channels; c++) {
            normalized[c] = features[patch * channels + c] / norm;
        }

        double[] projected = new double[fixture.projection.length];
        for (int d = 0; d < projected.length; d++) {
            for (int c = 0; c < channels; c++) {
                projected[d] += fixture.projection[d][c] * normalized[c];
            }
            projected[d] /= Math.sqrt(projected.length);
        }
        double score = 0;
        for (int d = 0; d < projected.length; d++) {
            double standardized = (projected[d] - fixture.mean[d]) / fixture.sigma[d];
            score += standardized * fixture.archetypes[patch][d];
        }
        return score;
    }
}
