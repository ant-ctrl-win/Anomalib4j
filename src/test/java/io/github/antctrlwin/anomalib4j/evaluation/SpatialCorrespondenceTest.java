package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialCorrespondenceTest {
    private M2Model model(int exactReference) {
        double[] mean = new double[18816], std = new double[18816], beta = new double[196];
        Arrays.fill(std, 1); Arrays.fill(beta, 1);
        if (exactReference >= 0) mean[exactReference * 96] = 1;
        return new M2Model("0".repeat(64), new M2Model.Local(mean, std),
                new M2Model.Calibration(new double[196], beta));
    }
    private double[] units(int channel) {
        double[] result = new double[18816];
        for (int p = 0; p < 196; p++) result[p * 96 + channel] = 1;
        return result;
    }
    @Test void positionalModeIsBitIdenticalToM2() {
        var base = model(15);
        var scorer = new SpatialCorrespondence(base, SpatialCorrespondence.Mode.P);
        assertArrayEquals(base.raw(units(0)), scorer.raw(units(0)).values());
        assertArrayEquals(base.calibrate(base.raw(units(0))), scorer.z(scorer.raw(units(0))));
    }
    @Test void neighborhoodsClipCornersEdgesAndInterior() {
        assertArrayEquals(new int[]{0, 1, 14, 15}, SpatialCorrespondence.references(0, SpatialCorrespondence.Mode.N1));
        assertArrayEquals(new int[]{0, 1, 2, 14, 15, 16}, SpatialCorrespondence.references(1, SpatialCorrespondence.Mode.N1));
        assertArrayEquals(new int[]{0, 1, 2, 14, 15, 16, 28, 29, 30}, SpatialCorrespondence.references(15, SpatialCorrespondence.Mode.N1));
        assertArrayEquals(new int[]{180, 181, 194, 195}, SpatialCorrespondence.references(195, SpatialCorrespondence.Mode.N1));
    }
    @Test void localArgminDoesNotMoveOutputFromQueryPosition() {
        var scorer = new SpatialCorrespondence(model(15), SpatialCorrespondence.Mode.N1);
        var raw = scorer.raw(units(0));
        assertEquals(15, raw.reference()[0]);
        assertEquals(0, raw.values()[0]);
        assertEquals(1.0 / 96, raw.values()[195]);
    }
    @Test void globalArgminCanMatchOppositeCorner() {
        var scorer = new SpatialCorrespondence(model(195), SpatialCorrespondence.Mode.G);
        var raw = scorer.raw(units(0));
        assertEquals(195, raw.reference()[0]);
        assertEquals(0, raw.values()[0]);
    }
    @Test void selectedReferenceSuppliesItsOwnAnisotropicScale() {
        double[] mean = new double[18816], std = new double[18816], beta = new double[196];
        Arrays.fill(std, 1); Arrays.fill(beta, 1);
        mean[96] = .9;
        std[15 * 96] = 100;
        var base = new M2Model("0".repeat(64), new M2Model.Local(mean, std),
                new M2Model.Calibration(new double[196], beta));
        var raw = new SpatialCorrespondence(base, SpatialCorrespondence.Mode.N1).raw(units(0));
        assertEquals(15, raw.reference()[0]);
        assertEquals(.0001 / 96, raw.values()[0], 1e-18);
    }
    @Test void calibrationRemainsAtQueryPositionAfterReferenceMatch() {
        var base = model(15);
        double[] alpha = new double[196], beta = new double[196];
        for (int p = 0; p < 196; p++) { alpha[p] = p * 10; beta[p] = 2; }
        var calibrated = new M2Model(base.encoderSha256(), base.local(), new M2Model.Calibration(alpha, beta));
        var scorer = new SpatialCorrespondence(calibrated, SpatialCorrespondence.Mode.N1);
        double[] z = scorer.z(scorer.raw(units(0)));
        assertEquals(0, z[0]);
        assertEquals(-5, z[1]);
    }
    @Test void tiesChooseLowestRowMajorReferenceDeterministically() {
        for (var mode : List.of(SpatialCorrespondence.Mode.N1, SpatialCorrespondence.Mode.G)) {
            var scorer = new SpatialCorrespondence(model(-1), mode);
            var raw = scorer.raw(units(0));
            assertEquals(0, raw.reference()[15]);
            assertArrayEquals(raw.reference(), scorer.raw(units(0)).reference());
        }
    }
    @Test void singletonReferenceMatchesPositionalScore() {
        var base = model(15);
        var scorer = new SpatialCorrespondence(base, SpatialCorrespondence.Mode.N1);
        assertEquals(base.raw(units(0))[15], scorer.minimum(units(0), 15, new int[]{15}).value());
    }
    @Test void modesCalibrateOnlyHeldOutRawScoresAndKeepLocalState() {
        var base = model(195);
        double[] originalMeans = base.local().mean(), originalScales = base.local().effectiveStd();
        var heldOut = List.of(units(0), units(1), units(2));
        var p = new SpatialCorrespondence(base, SpatialCorrespondence.Mode.P).calibrated(heldOut);
        var n = new SpatialCorrespondence(base, SpatialCorrespondence.Mode.N1).calibrated(heldOut);
        var g = new SpatialCorrespondence(base, SpatialCorrespondence.Mode.G).calibrated(heldOut);
        assertArrayEquals(base.calibration().alpha(), p.calibration().alpha());
        assertNotEquals(n.calibration().alpha()[0], g.calibration().alpha()[0]);
        assertEquals(1.0 / 96, n.calibration().alpha()[0], 1e-15);
        assertEquals(1.0 / 144, g.calibration().alpha()[0], 1e-15);
        assertEquals(1e-6, n.calibration().beta()[0]);
        double[] before = g.calibration().alpha();
        g.z(g.raw(units(95)));
        assertArrayEquals(before, g.calibration().alpha());
        assertArrayEquals(originalMeans, base.local().mean());
        assertArrayEquals(originalScales, base.local().effectiveStd());
    }
    @Test void rejectsNonfiniteQueriesAndProducesFiniteScores() {
        var scorer = new SpatialCorrespondence(model(15), SpatialCorrespondence.Mode.G).calibrated(List.of(units(0), units(1)));
        assertTrue(Arrays.stream(scorer.z(scorer.raw(units(2)))).allMatch(Double::isFinite));
        var invalid = units(0); invalid[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> scorer.raw(invalid));
    }
}
