package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class BottleEvaluationTest {
    @Test void aurocHandlesRankingReversalAndTies() {
        assertEquals(1, BottleEvaluation.auroc(new double[]{-5, -4}, new double[]{-3, -2}));
        assertEquals(0, BottleEvaluation.auroc(new double[]{3, 4}, new double[]{1, 2}));
        assertEquals(.5, BottleEvaluation.auroc(new double[]{1, 1}, new double[]{1, 1}));
        assertEquals(.875, BottleEvaluation.auroc(new double[]{0, 1}, new double[]{1, 2}));
        assertThrows(IllegalArgumentException.class, () -> BottleEvaluation.auroc(new double[0], new double[]{1}));
        assertThrows(IllegalArgumentException.class, () -> BottleEvaluation.auroc(new double[]{0}, new double[]{Double.NaN}));
    }

    @Test void summarizesUnclampedRawValuesAndFirstMaximum() {
        double[] raw = new double[196];
        Arrays.fill(raw, -3);
        raw[30] = 5;
        raw[50] = 5;
        var summary = BottleEvaluation.summarize(raw);
        assertEquals(-3, summary.min());
        assertEquals(5, summary.max());
        assertEquals((-3.0 * 194 + 10) / 196, summary.mean());
        assertEquals(2, summary.row());
        assertEquals(2, summary.column());
    }

    @Test void parsesAllDefectNamesWithoutAnnotations() {
        for (String label : new String[]{"good", "broken_large", "broken_small", "contamination"}) {
            assertEquals(label, BottleEvaluation.defect("bottle_" + label + "_012.png"));
        }
        assertThrows(IllegalArgumentException.class, () -> BottleEvaluation.defect("cable_good_000.png"));
    }

    @Test void interpolatesPercentilesWithoutChangingInput() {
        double[] data = {4, 1, 3, 2};
        assertEquals(2.5, BottleEvaluation.percentile(data, .5));
        assertEquals(1.75, BottleEvaluation.percentile(data, .25));
        assertArrayEquals(new double[]{4, 1, 3, 2}, data);
    }
}
