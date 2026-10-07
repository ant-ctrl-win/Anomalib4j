package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationMapsTest {
    @TempDir Path temporary;

    @Test void bilinearHalfPixelCoordinatesReplicateBordersAndPreserveRawRange() {
        double[] source = {-10, 10, 30, 50};
        double[] actual = LocalizationMaps.upsample(source, 2, 2, 4, 4);
        assertArrayEquals(new double[]{-10, -5, 5, 10, 0, 5, 15, 20,
                20, 25, 35, 40, 30, 35, 45, 50}, actual, 1e-12);
        assertArrayEquals(source, LocalizationMaps.upsample(source, 2, 2, 2, 2));
        assertArrayEquals(new double[]{20}, LocalizationMaps.upsample(source, 2, 2, 1, 1));
        assertArrayEquals(new double[]{-10, 10, 30, 50}, source);
    }

    @Test void handlesSinglePixelAxisAndRejectsInvalidShapes() {
        assertArrayEquals(new double[]{-8, -8, -8, -8, -8, -8}, LocalizationMaps.upsample(new double[]{-8}, 1, 1, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> LocalizationMaps.upsample(new double[]{1}, 2, 2, 4, 4));
        assertThrows(IllegalArgumentException.class, () -> LocalizationMaps.upsample(new double[]{Double.NaN}, 1, 1, 4, 4));
    }

    @Test void overlayWritesThreePanelsWithoutMutatingSourceOrScores() throws Exception {
        var original = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        double[] raw = new double[48];
        boolean[] mask = new boolean[48];
        mask[0] = true;
        Path file = temporary.resolve("overlay.png");
        LocalizationMaps.overlay(file, original, mask, raw, -1, 1);
        var overlay = ImageIO.read(file.toFile());
        assertEquals(24, overlay.getWidth());
        assertEquals(66, overlay.getHeight());
        assertEquals(original.getRGB(0, 0), overlay.getRGB(0, 60));
        assertNotEquals(original.getRGB(0, 0), overlay.getRGB(8, 60));
        assertArrayEquals(new double[48], raw);
        assertEquals(0xff000000, original.getRGB(0, 0));
    }
}
