package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalPilotBridgeTest {
    @Test void identityBackprojectionPreservesEveryValue() {
        double[] map = new double[25];
        for (int p = 0; p < 25; p++) map[p] = Math.sin(p);
        assertArrayEquals(map, CanonicalPilotBridge.backproject(map, 5, new double[]{1,0,0,0,1,0}));
    }
    @Test void backprojectionSamplesForwardCoordinatesAndReplicatesBoundary() {
        double[] map = new double[25];
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) map[y*5+x] = x+10*y;
        double[] rotated = CanonicalPilotBridge.backproject(map, 5, new double[]{0,-1,4,1,0,0});
        assertEquals(12, rotated[2*5+1]);
        double[] shifted = CanonicalPilotBridge.backproject(map, 5, new double[]{1,0,.5,0,1,0});
        assertEquals(21.5, shifted[2*5+1]);
        assertEquals(24, shifted[2*5+4]);
    }
}
