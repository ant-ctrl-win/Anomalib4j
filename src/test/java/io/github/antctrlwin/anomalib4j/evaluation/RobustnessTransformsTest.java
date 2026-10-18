package io.github.antctrlwin.anomalib4j.evaluation;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RobustnessTransformsTest {
    private BufferedImage ramp() {
        var image = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 224; x++) image.setRGB(x, y, (x << 16) | (y << 8) | 200);
        return image;
    }
    @Test void allFamilyIdentitiesPreservePixels() {
        // Given a spatially varying RGB image.
        var input = ramp();
        for (var family : RobustnessTransforms.Family.values()) {
            // When applying the identity level.
            var output = RobustnessTransforms.apply(input, family, family.identity());
            // Then no pixel changes.
            assertArrayEquals(input.getRGB(0, 0, 224, 224, null, 0, 224), output.getRGB(0, 0, 224, 224, null, 0, 224));
        }
    }
    @Test void positiveTranslationMovesRightAndReplicatesEdges() {
        var input = ramp(); // Given.
        var output = RobustnessTransforms.apply(input, RobustnessTransforms.Family.TRANSLATION_X, 8); // When.
        assertEquals(input.getRGB(20, 30), output.getRGB(28, 30)); // Then.
        assertEquals(input.getRGB(0, 30), output.getRGB(0, 30));
        assertEquals(input.getRGB(0, 30), output.getRGB(7, 30));
    }
    @Test void negativeTranslationMovesUp() {
        var input = ramp(); // Given.
        var output = RobustnessTransforms.apply(input, RobustnessTransforms.Family.TRANSLATION_Y, -4); // When.
        assertEquals(input.getRGB(20, 30), output.getRGB(20, 26)); // Then.
        assertEquals(input.getRGB(20, 223), output.getRGB(20, 223));
    }
    @Test void rotationAndScaleAreDeterministic() {
        var input = ramp(); // Given.
        for (var family : new RobustnessTransforms.Family[]{RobustnessTransforms.Family.ROTATION, RobustnessTransforms.Family.SCALE}) {
            double level = family.levels()[0];
            var first = RobustnessTransforms.apply(input, family, level); // When.
            var second = RobustnessTransforms.apply(input, family, level);
            assertArrayEquals(first.getRGB(0, 0, 224, 224, null, 0, 224), second.getRGB(0, 0, 224, 224, null, 0, 224)); // Then.
        }
    }
    @Test void brightnessAndContrastClipWithoutWrapping() {
        var input = ramp(); // Given.
        var bright = RobustnessTransforms.apply(input, RobustnessTransforms.Family.BRIGHTNESS, 2); // When.
        var contrast = RobustnessTransforms.apply(input, RobustnessTransforms.Family.CONTRAST, 2);
        assertEquals(255, bright.getRGB(223, 0) >>> 16 & 255); // Then.
        assertEquals(0, contrast.getRGB(0, 0) >>> 16 & 255);
    }
    @Test void gammaUsesOutputEqualsInputPowerGamma() {
        var input = ramp(); // Given.
        var output = RobustnessTransforms.apply(input, RobustnessTransforms.Family.GAMMA, 2); // When.
        assertEquals(Math.round(255 * Math.pow(128 / 255.0, 2)), output.getRGB(128, 0) >>> 16 & 255); // Then.
    }
    @Test void quarterTurnUsesImageCenterAndClockwiseDirection() {
        var input = ramp();
        var output = RobustnessTransforms.apply(input, RobustnessTransforms.Family.ROTATION, 90);
        assertEquals(input.getRGB(160, 112), output.getRGB(111, 160));
    }
    @Test void enlargementUsesCenteredBilinearSampling() {
        var input = ramp();
        var output = RobustnessTransforms.apply(input, RobustnessTransforms.Family.SCALE, 2);
        assertEquals((120 << 16) | (112 << 8) | 200, output.getRGB(128, 112) & 0xffffff);
    }
    @Test void geometricBoundariesPreserveUniformColor() {
        var input = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 224; x++) input.setRGB(x, y, 0x3f7fbf);
        for (var family : new RobustnessTransforms.Family[]{RobustnessTransforms.Family.ROTATION, RobustnessTransforms.Family.SCALE}) {
            var output = RobustnessTransforms.apply(input, family, family.levels()[0]);
            assertEquals(0x3f7fbf, output.getRGB(0, 0) & 0xffffff);
            assertEquals(0x3f7fbf, output.getRGB(223, 223) & 0xffffff);
        }
    }
    @Test void interiorExcludesExactlyOneRing() {
        double[] map = new double[196]; // Given.
        Arrays.fill(map, -2);
        map[0] = 100; map[15] = 3; map[12 * 14 + 12] = 4;
        double interior = RobustnessTransforms.interior(map); // When.
        assertEquals(4, interior); // Then.
    }
}
