package io.github.antctrlwin.anomalib4j.canonical;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalPreprocessorTest {
    static BufferedImage cross() {
        var image = new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[700*700];
        for (int y = 100; y < 600; y++) for (int x = 100; x < 600; x++)
            if (Math.abs(x-350) < 94 || Math.abs(y-350) < 94) rgb[y*700+x] = 0xe0d0b0;
        image.setRGB(0,0,700,700,rgb,0,700);
        return image;
    }

    @Test void destinationIsCallerOwnedAndFailureLeavesItUntouched() {
        var preprocessor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.WIDENED_TENT);
        float[] output = new float[AffineTensorSampler.ELEMENTS]; Arrays.fill(output, 7);
        var failed = preprocessor.prepare(new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB),output);
        assertEquals(CanonicalGeometry.Status.FAILURE, failed.status());
        assertThrows(IllegalStateException.class, failed::transform);
        for (float value : output) assertEquals(7,value);
        var successful = preprocessor.prepare(cross(),output);
        assertEquals(CanonicalGeometry.Status.SUCCESS, successful.status());
        float[] expected = output.clone();
        assertEquals(successful,preprocessor.prepare(cross(),output)); assertArrayEquals(expected,output);
        for (float value : output) assertTrue(Float.isFinite(value));
        assertTrue(preprocessor.preprocessingId().contains("rotated-scaled-tent"));
    }

    @Test void bulkRgbConversionHandlesSubimagesAndBgrWithoutMutatingSource() {
        var source = cross();
        var parent = new BufferedImage(750,740,BufferedImage.TYPE_3BYTE_BGR);
        var sub = parent.getSubimage(13,19,700,700);
        int[] rgb = source.getRGB(0,0,700,700,null,0,700);
        sub.setRGB(0,0,700,700,rgb,0,700);
        var preprocessor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.BICUBIC);
        float[] first = new float[AffineTensorSampler.ELEMENTS], second = new float[first.length];
        var a = preprocessor.prepare(source,first); var b = preprocessor.prepare(sub,second);
        assertEquals(a,b); assertArrayEquals(first,second);
        assertArrayEquals(rgb,sub.getRGB(0,0,700,700,null,0,700));
    }

    @Test void dimensionsAndDestinationMustMatchBeforeWorkspaceMutation() {
        var preprocessor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        assertThrows(IllegalArgumentException.class, () -> preprocessor.prepare(new BufferedImage(224,224,1),new float[AffineTensorSampler.ELEMENTS]));
        assertThrows(IllegalArgumentException.class, () -> preprocessor.prepare(cross(),new float[3]));
        assertThrows(NullPointerException.class, () -> preprocessor.prepare(null,new float[AffineTensorSampler.ELEMENTS]));
    }
}
