package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationLabelsTest {
    @Test void parsesBottleDefects() {
        for (String defect : new String[]{"good", "broken_large", "broken_small", "contamination"}) {
            assertEquals(defect, EvaluationLabels.defect("bottle_" + defect + "_012.png"));
        }
        assertEquals("bottle", EvaluationLabels.category("bottle_good_000.png"));
    }

    @Test void parsesMetalNutDefects() {
        for (String defect : new String[]{"good", "bent", "color", "flip", "scratch"}) {
            assertEquals(defect, EvaluationLabels.defect("metal_nut_" + defect + "_000.png"));
        }
        assertEquals("metal_nut", EvaluationLabels.category("metal_nut_scratch_001.png"));
        assertEquals("metal_nut", EvaluationLabels.category("metal_nut_good_000.png"));
    }

    @Test void prefersLongestMatchingMultiWordDefect() {
        assertEquals("broken_large", EvaluationLabels.defect("bottle_broken_large_000.png"));
        assertEquals("broken_small", EvaluationLabels.defect("bottle_broken_small_000.png"));
        assertEquals("good", EvaluationLabels.defect("bottle_good_000.png"));
    }

    @Test void rejectsUnknownCategoryDefectAndMalformedNames() {
        for (String filename : new String[]{
                "cable_good_000.png",
                "bottle_scratch_000.png",
                "bottle_good_.png",
                "bottle_good_000.jpg",
                "bottle_good_000",
                "metal_scratch_000.png",
                "bottle_broken_large.png",
        }) {
            assertThrows(IllegalArgumentException.class, () -> EvaluationLabels.defect(filename), filename);
        }
        assertThrows(IllegalArgumentException.class, () -> EvaluationLabels.defect(null));
        assertThrows(IllegalArgumentException.class, () -> EvaluationLabels.category("cable_good_000.png"));
    }

    @Test void bottleEvaluationDelegatesToGenericParser() {
        assertEquals("broken_large", BottleEvaluation.defect("bottle_broken_large_005.png"));
        assertEquals("good", BottleEvaluation.defect("bottle_good_000.png"));
        assertThrows(IllegalArgumentException.class, () -> BottleEvaluation.defect("cable_good_000.png"));
    }
}
