package io.github.antctrlwin.anomalib4j.evaluation;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.antctrlwin.anomalib4j.evaluation.RobustnessTransforms.Family.*;

class OracleGeometryTest {
    @Test void inverseParametersAreMathematicalInverses() {
        assertEquals(-20, OracleGeometry.inverse(ROTATION, 20));
        assertEquals(1 / .9, OracleGeometry.inverse(SCALE, .9));
        assertEquals(.9, OracleGeometry.inverse(SCALE, OracleGeometry.inverse(SCALE, .9)), 1e-15);
        assertThrows(IllegalArgumentException.class, () -> OracleGeometry.inverse(SCALE, 0));
        assertThrows(IllegalArgumentException.class, () -> OracleGeometry.inverse(TRANSLATION_X, 2));
    }

    @Test void identitiesPreservePixelsAndRoundTripIsDeterministic() {
        var image = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 224; x++)
            image.setRGB(x, y, ((x * 13 + y) % 256) * 0x010101);
        for (var family : new RobustnessTransforms.Family[]{ROTATION, SCALE}) {
            var identity = RobustnessTransforms.apply(image, family, family.identity());
            assertEquals(0, OracleGeometry.rgbDifference(image, identity).max());
            double level = family == ROTATION ? 10 : .95;
            var perturbed = RobustnessTransforms.apply(image, family, level);
            var a = RobustnessTransforms.apply(perturbed, family, OracleGeometry.inverse(family, level));
            var b = RobustnessTransforms.apply(perturbed, family, OracleGeometry.inverse(family, level));
            assertEquals(0, OracleGeometry.rgbDifference(a, b).max());
            assertTrue(OracleGeometry.rgbDifference(image, a).mae() > 0);
        }
    }

    @Test void mapDifferenceSeparatesOuterRingAndInterior() {
        double[] a = new double[196], b = new double[196];
        b[0] = 14; b[15] = 7;
        var full = OracleGeometry.difference(a, b, false);
        var inner = OracleGeometry.difference(a, b, true);
        assertEquals(21.0 / 196, full.mae());
        assertEquals(Math.sqrt(245.0 / 196), full.rmse());
        assertEquals(14, full.max());
        assertEquals(7.0 / 144, inner.mae());
        assertEquals(7.0 / 12, inner.rmse());
        assertEquals(7, inner.max());
        assertArrayEquals(new double[196], a);
    }
}
