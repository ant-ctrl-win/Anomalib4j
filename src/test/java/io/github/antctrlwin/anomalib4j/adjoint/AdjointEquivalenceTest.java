package io.github.antctrlwin.anomalib4j.adjoint;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AdjointEquivalenceTest {
    private static final double TOLERANCE = 1e-10;

    @Test
    void compiledFilterMatchesExplicitForward() {
        Fixtures fixture = new Fixtures();
        assertEquivalent(fixture, fixture.features);
    }

    @Test
    void zeroPatchReturnsUnscaledBias() {
        Fixtures fixture = new Fixtures();
        AdjointFilterBank bank = fixture.compileAdjoint();
        float[] zeros = new float[fixture.grid.elements()];
        for (int p = 0; p < fixture.grid.cells(); p++) {
            double expectedBias = 0;
            for (int d = 0; d < fixture.mean.length; d++) {
                expectedBias -= fixture.mean[d] / fixture.sigma[d] * fixture.archetypes[p][d];
            }
            assertEquals(expectedBias, bank.scoreCell(zeros, p), TOLERANCE);
        }
        assertEquivalent(fixture, zeros);
    }

    @Test
    void nearZeroPatchUsesSameClampInBothPaths() {
        Fixtures fixture = new Fixtures();
        float[] tiny = fixture.features.clone();
        for (int i = 0; i < tiny.length; i++) {
            tiny[i] *= 1e-8f;
        }
        assertEquivalent(fixture, tiny);
    }

    @Test
    void filterBankOwnsItsBuffers() {
        Fixtures fixture = new Fixtures();
        double[] weights = new double[fixture.grid.elements()];
        double[] biases = {1, 2, 3, 4};
        AdjointFilterBank bank = new AdjointFilterBank(
                fixture.descriptor, fixture.grid, fixture.policy, weights, biases);
        Arrays.fill(weights, 100);
        Arrays.fill(biases, -100);
        for (int p = 0; p < fixture.grid.cells(); p++) {
            assertEquals(p + 1.0, bank.scoreCell(fixture.features, p), TOLERANCE);
        }
    }

    @Test
    void rejectsInvalidGeometryPolicyAndBuffers() {
        Fixtures fixture = new Fixtures();
        assertThrows(IllegalArgumentException.class, () -> new GridShape(0, 2, 3));
        assertThrows(ArithmeticException.class, () -> new GridShape(Integer.MAX_VALUE, 2, 3));
        for (double epsilon : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new NormalizationPolicy(epsilon));
        }
        assertThrows(IllegalArgumentException.class, () -> new AdjointFilterBank(
                fixture.descriptor, fixture.grid, fixture.policy, new double[1], new double[4]));
        double[] invalid = new double[fixture.grid.elements()];
        invalid[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> new AdjointFilterBank(
                fixture.descriptor, fixture.grid, fixture.policy, invalid, new double[4]));
        AdjointFilterBank bank = fixture.compileAdjoint();
        assertThrows(IllegalArgumentException.class, () -> bank.scoreCell(new float[1], 0));
        assertThrows(IndexOutOfBoundsException.class, () -> bank.scoreCell(fixture.features, 4));
        float[] nonFinite = fixture.features.clone();
        nonFinite[0] = Float.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, () -> bank.scoreCell(nonFinite, 0));
    }

    private static void assertEquivalent(Fixtures fixture, float[] features) {
        AdjointFilterBank bank = fixture.compileAdjoint();
        for (int p = 0; p < fixture.grid.cells(); p++) {
            assertEquals(ExplicitForwardReference.score(fixture, features, p),
                    bank.scoreCell(features, p), TOLERANCE, "Patch " + p);
        }
    }
}
