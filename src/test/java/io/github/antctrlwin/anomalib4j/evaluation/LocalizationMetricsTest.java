package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationMetricsTest {
    @Test void perfectReversedAndTiedScores() {
        boolean[] mask = {true, true, false, false};
        var perfect = result(new double[]{3, 2, 1, 0}, mask, 4);
        assertEquals(1, perfect.pixelAuRoc(), 1e-12);
        assertEquals(1, perfect.auPro(), 1e-12);
        var reverse = result(new double[]{0, 1, 2, 3}, mask, 4);
        assertEquals(0, reverse.pixelAuRoc(), 1e-12);
        assertEquals(0, reverse.auPro(), 1e-12);
        var tied = result(new double[]{7, 7, 7, 7}, mask, 4);
        assertEquals(.5, tied.pixelAuRoc(), 1e-12);
        assertEquals(.15, tied.auPro(), 1e-12);
        assertEquals(.3, tied.proAtFpr()[300], 1e-12);
    }

    @Test void averagesRegionsEquallyAndInterpolatesAtPointThree() {
        var metrics = result(new double[]{9, 8, 0, 0, 7, 6, 5},
                new boolean[]{true, false, false, false, true, true, true}, 7);
        assertEquals(2, metrics.regionCount());
        assertEquals(.75, metrics.pixelAuRoc(), 1e-12);
        assertEquals(.5, metrics.auPro(), 1e-12);
        assertEquals(.5, metrics.proAtFpr()[0], 1e-12);
        assertEquals(.5, metrics.proAtFpr()[300], 1e-12);
    }

    @Test void usesEightConnectivityAndKeepsImagesSeparateIncludingGoodBackground() {
        var metrics = new LocalizationMetrics(12);
        metrics.add(new double[]{2, 0, 0, 2}, new boolean[]{true, false, false, true}, 2, 2);
        metrics.add(new double[]{2, 0, 0, 2}, new boolean[]{true, false, false, true}, 2, 2);
        metrics.add(new double[]{0, 0, 0, 0}, new boolean[4], 2, 2);
        var result = metrics.calculate();
        assertEquals(2, result.regionCount());
        assertEquals(4, result.positivePixels());
        assertEquals(8, result.negativePixels());
        assertEquals(1, result.auPro(), 1e-12);
        assertThrows(IllegalStateException.class, metrics::calculate);
        assertEquals(2, result(new double[]{0, 0, 1, 1, 0, 0},
                new boolean[]{false, false, true, true, false, false}, 3).regionCount());
    }

    @Test void interpolatesMixedPositiveNegativeTieWithUnequalRegionWeights() {
        var metrics = result(new double[]{9, 8, 0, 0, 8, 6, 5},
                new boolean[]{true, false, false, false, true, true, true}, 7);
        assertEquals(9.5 / 12, metrics.pixelAuRoc(), 1e-12);
        assertEquals(.575, metrics.auPro(), 1e-12);
        assertEquals(.65, metrics.proAtFpr()[300], 1e-12);
    }

    @Test void pixelAuRocMatchesIndependentPairwiseOracleWithRandomTies() {
        var random = new Random(42);
        for (int repetition = 0; repetition < 100; repetition++) {
            double[] scores = new double[40];
            boolean[] mask = new boolean[40];
            for (int i = 0; i < scores.length; i++) { scores[i] = random.nextInt(8) - 4; mask[i] = i % 3 == 0; }
            double wins = 0;
            int pairs = 0;
            for (int p = 0; p < scores.length; p++) if (mask[p]) {
                for (int n = 0; n < scores.length; n++) if (!mask[n]) {
                    wins += scores[p] > scores[n] ? 1 : scores[p] == scores[n] ? .5 : 0;
                    pairs++;
                }
            }
            assertEquals(wins / pairs, result(scores, mask, 8).pixelAuRoc(), 1e-12);
        }
    }

    @Test void rejectsDegenerateAndIncompleteInputs() {
        var metrics = new LocalizationMetrics(2);
        assertThrows(IllegalArgumentException.class, () -> metrics.add(new double[]{Double.NaN, 0}, new boolean[2], 2, 1));
        assertThrows(IllegalStateException.class, metrics::calculate);
        metrics.add(new double[]{0, 0}, new boolean[2], 2, 1);
        assertThrows(IllegalStateException.class, metrics::calculate);
        assertThrows(IllegalStateException.class, () -> result(new double[]{1, 2}, new boolean[]{true, true}, 2));
    }

    private static LocalizationMetrics.Result result(double[] scores, boolean[] mask, int width) {
        var metrics = new LocalizationMetrics(scores.length);
        metrics.add(scores, mask, width, scores.length / width);
        return metrics.calculate();
    }
}
