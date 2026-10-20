package io.github.antctrlwin.anomalib4j.canonical;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GeometryProfileStagesTest {
    @Test void stagedMirrorPreservesProductionOutputsMasksAndFailuresAcrossWorkspaceReuse() {
        var original = new MetalNutGeometryEstimator(); var mirror = new GeometryProfileStages();
        var normal = CanonicalPreprocessorTest.cross();
        var circle = new BufferedImage(700,700,1);
        var graphics = circle.createGraphics(); graphics.setColor(java.awt.Color.WHITE); graphics.fillOval(90,90,520,520); graphics.dispose();
        var border = new BufferedImage(700,700,1);
        graphics = border.createGraphics(); graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,500,500); graphics.dispose();
        for (var image : new BufferedImage[]{normal,new BufferedImage(700,700,1),circle,border,normal}) {
            int[] rgb = image.getRGB(0,0,700,700,null,0,700), before=rgb.clone();
            assertEquals(original.estimate(rgb),mirror.estimate(rgb));
            assertArrayEquals(original.reducedImage(),mirror.reducedImage());
            assertArrayEquals(original.selectedComponent(),mirror.selectedComponent());
            assertArrayEquals(original.filledMask(),mirror.filledMask()); assertArrayEquals(before,rgb);
        }
        assertThrows(IllegalArgumentException.class, () -> mirror.estimate(new int[10]));
    }
    @Test void reductionAndHistogramPreserveRoundedProductionBytesOnRandomPixels() {
        int[] rgb = new int[700*700]; var random = new Random(42);
        for (int i=0; i<rgb.length; i++) rgb[i]=random.nextInt(1<<24);
        var original=new MetalNutGeometryEstimator(); original.estimate(rgb);
        var mirror=new GeometryProfileStages(); mirror.reduce(rgb); mirror.histogram();
        assertArrayEquals(original.reducedImage(),mirror.reducedImage());
        int[] expected=new int[256]; for (byte value:original.reducedImage()) expected[value&255]++;
        assertArrayEquals(expected,mirror.histogram);
    }
    @Test void holeStageRestoresRealSelectedMaskBeforeEveryRepeatedInvocation() {
        var normal=CanonicalPreprocessorTest.cross(); int[] rgb=normal.getRGB(0,0,700,700,null,0,700);
        var mirror=new GeometryProfileStages(); var expected=mirror.estimate(rgb);
        boolean[] selected=mirror.selectedComponent(), filled=mirror.filledMask();
        for (int i=0;i<3;i++) {
            System.arraycopy(selected,0,mirror.mask,0,selected.length); mirror.holes();
            assertArrayEquals(filled,mirror.filledMask());
            mirror.centroid(); mirror.harmonic(); assertEquals(expected,mirror.finish());
        }
        assertEquals(expected.transform(),mirror.finish().transform());
    }
}
