package io.github.antctrlwin.anomalib4j.canonical;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MetalNutGeometryEstimatorTest {
    private static final int N = 175;

    private static byte[] cross() {
        byte[] image = new byte[N*N];
        for (int y = 25; y <= 149; y++) for (int x = 25; x <= 149; x++)
            if (Math.abs(x-87) <= 23 || Math.abs(y-87) <= 23) image[y*N+x] = (byte) 255;
        return image;
    }

    @Test void symmetricExactCentroidIncludesOriginAndMatchesIndependentTrigonometricMoment() {
        var estimator = new MetalNutGeometryEstimator();
        var actual = estimator.estimateReduced(cross());
        assertEquals(CanonicalGeometry.Status.SUCCESS, actual.status());
        assertEquals(349.5, actual.cx()); assertEquals(349.5, actual.cy());
        double re = 0, im = 0; int count = 0;
        boolean[] mask = estimator.filledMask();
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (mask[y*N+x]) {
            double angle = Math.atan2(y-87.0, x-87.0);
            re += Math.cos(4*angle); im += Math.sin(4*angle); count++;
        }
        assertEquals(Math.hypot(re, im)/count, actual.harmonic4(), 1e-14);
        assertEquals(0, modulo(actual.angle()), 1e-12);
        assertEquals(actual, estimator.estimateReduced(cross()));
    }

    @Test void backgroundFillsDiagonalGapButForegroundJoinsDiagonally() {
        byte[] image = new byte[N*N];
        for (int y = 30; y <= 144; y++) for (int x = 30; x <= 144; x++) image[y*N+x] = (byte) 255;
        image[30*N+30] = 0; image[31*N+31] = 0;
        image[29*N+145] = (byte) 255;
        var estimator = new MetalNutGeometryEstimator();
        estimator.estimateReduced(image);
        boolean[] selected = estimator.selectedComponent(), filled = estimator.filledMask();
        assertTrue(selected[29*N+145]);
        assertFalse(selected[31*N+31]); assertTrue(filled[31*N+31]);
        assertFalse(filled[30*N+30]);
    }

    @Test void tiedComponentsKeepFirstRowMajorComponent() {
        byte[] image = new byte[N*N];
        for (int y = 20; y < 30; y++) for (int x = 20; x < 30; x++) {
            image[y*N+x] = (byte) 255; image[(y+80)*N+x+80] = (byte) 255;
        }
        var estimator = new MetalNutGeometryEstimator(); estimator.estimateReduced(image);
        assertTrue(estimator.selectedComponent()[20*N+20]);
        assertFalse(estimator.selectedComponent()[100*N+100]);
    }

    @Test void emptyUniformBorderAndWeakHarmonicFailExplicitly() {
        var estimator = new MetalNutGeometryEstimator();
        assertEquals(CanonicalGeometry.Reason.AREA_OR_BORDER, estimator.estimate(new int[700*700]).reason());
        byte[] full = new byte[N*N]; Arrays.fill(full, (byte)255);
        assertThrows(IllegalStateException.class, () -> estimator.estimateReduced(full).transform());
        byte[] disk = new byte[N*N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++)
            if ((x-87)*(x-87)+(y-87)*(y-87) <= 60*60) disk[y*N+x] = (byte)255;
        assertEquals(CanonicalGeometry.Reason.WEAK_FOURFOLD_HARMONIC, estimator.estimateReduced(disk).reason());
        byte[] border = cross();
        for (int y = 0; y < 25; y++) border[y*N+87] = (byte)255;
        assertEquals(CanonicalGeometry.Reason.AREA_OR_BORDER, estimator.estimateReduced(border).reason());
    }

    @Test void branchBoundaryIsModulo90AndQuarterTurnCoefficientsAreExact() {
        var estimator = new MetalNutGeometryEstimator();
        byte[] square = new byte[N*N];
        for (int y = 35; y <= 139; y++) for (int x = 35; x <= 139; x++) square[y*N+x] = (byte)255;
        var pose = estimator.estimateReduced(square);
        assertEquals(CanonicalGeometry.Status.SUCCESS, pose.status());
        assertEquals(45, Math.abs(pose.angle()), 1e-10);
        assertEquals(0, modulo(pose.angle()-estimator.estimateReduced(quarterTurn(square)).angle()), 1e-10);
        var removePositive = RigidTransform.center(349.5,349.5,90);
        assertEquals(0, removePositive.cos()); assertEquals(-1, removePositive.sin());
        assertEquals(0, removePositive.canonicalX(699,0));
        assertEquals(0, removePositive.canonicalY(699,0));
        var removeNegative = RigidTransform.center(349.5,349.5,-90);
        assertEquals(1, removeNegative.sin());
        assertEquals(699, removeNegative.canonicalX(0,0));
    }

    @Test void reducedBuffersAreSnapshotsAndWorkspaceCanBeReusedAfterFailure() {
        var estimator = new MetalNutGeometryEstimator(); var expected = estimator.estimateReduced(cross());
        Arrays.fill(estimator.reducedImage(), (byte)0); Arrays.fill(estimator.filledMask(), false);
        estimator.estimate(new int[700*700]);
        assertEquals(expected, estimator.estimateReduced(cross()));
        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(new int[3]));
    }

    private static byte[] quarterTurn(byte[] source) {
        byte[] rotated = new byte[source.length];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) rotated[x*N+N-1-y] = source[y*N+x];
        return rotated;
    }
    private static double modulo(double angle) { return ((angle+45)%90+90)%90-45; }
}
