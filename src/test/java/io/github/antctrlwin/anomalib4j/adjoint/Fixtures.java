package io.github.antctrlwin.anomalib4j.adjoint;

import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;

final class Fixtures {
    final GridShape grid = new GridShape(2, 2, 3);
    final NormalizationPolicy policy = new NormalizationPolicy(1e-6);
    final ModelDescriptor descriptor = new ModelDescriptor("synthetic-v1", "fixture-cnn", "spatial",
            grid, 5, 42L, policy, 1);
    final double[][] projection = {
            {1, -1, 1}, {-1, 1, 1}, {1, 1, -1}, {-1, -1, 1}, {1, -1, -1}
    };
    final double[] mean = {0.2, -0.4, 0.7, 0.1, -0.3};
    final double[] sigma = {0.5, 1.3, 0.8, 2.0, 0.3};
    final double[][] archetypes = {
            {0.2, -0.5, 0.4, 0.1, -0.7},
            {-0.3, 0.1, 0.8, -0.2, 0.4},
            {0.6, 0.2, -0.3, 0.5, -0.1},
            {-0.1, 0.7, 0.2, -0.4, 0.3}
    };
    final float[] features = {1.2f, -0.4f, 2.1f, -3f, 0.2f, 0.8f,
            0.4f, 1.7f, -0.6f, -0.2f, -0.7f, 1.3f};

    Fixtures() {
        for (double[] archetype : archetypes) {
            double squaredNorm = 0;
            for (double value : archetype) {
                squaredNorm += value * value;
            }
            double norm = Math.sqrt(squaredNorm);
            for (int d = 0; d < archetype.length; d++) {
                archetype[d] /= norm;
            }
        }
    }

    AdjointFilterBank compileAdjoint() {
        double[] weights = new double[grid.elements()];
        double[] biases = new double[grid.cells()];
        double scale = 1.0 / Math.sqrt(projection.length);
        for (int p = 0; p < grid.cells(); p++) {
            for (int d = 0; d < projection.length; d++) {
                double q = archetypes[p][d] / sigma[d];
                biases[p] -= mean[d] * q;
                for (int c = 0; c < grid.channels(); c++) {
                    weights[p * grid.channels() + c] += scale * projection[d][c] * q;
                }
            }
        }
        return new AdjointFilterBank(descriptor, grid, policy, weights, biases);
    }
}
