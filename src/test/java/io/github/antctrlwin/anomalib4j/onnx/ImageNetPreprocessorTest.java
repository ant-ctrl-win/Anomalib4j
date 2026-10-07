package io.github.antctrlwin.anomalib4j.onnx;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ImageNetPreprocessorTest {
    @Test
    void packsRgbChannelsIntoNormalizedNchwPlanesDeterministically() {
        var image = new BufferedImage(224, 224, BufferedImage.TYPE_3BYTE_BGR);
        image.setRGB(0, 0, 0xFF204080);
        image.setRGB(223, 223, 0xFFFF0000);
        float[] tensor = ImageNetPreprocessor.preprocess(image);
        int plane = 224 * 224;
        assertEquals(3 * plane, tensor.length);
        assertEquals((32 / 255.0 - 0.485) / 0.229, tensor[0], 1e-6);
        assertEquals((64 / 255.0 - 0.456) / 0.224, tensor[plane], 1e-6);
        assertEquals((128 / 255.0 - 0.406) / 0.225, tensor[2 * plane], 1e-6);
        assertEquals((1 - 0.485) / 0.229, tensor[plane - 1], 1e-6);
        assertEquals(-0.456 / 0.224, tensor[2 * plane - 1], 1e-6);
        assertEquals(-0.406 / 0.225, tensor[3 * plane - 1], 1e-6);
        assertArrayEquals(tensor, ImageNetPreprocessor.preprocess(image));
        assertEquals(0xFF204080, image.getRGB(0, 0));
    }

    @Test
    void directResizeRetainsBothEdgesOfWideImageWithoutCenterCrop() {
        var image = new BufferedImage(448, 224, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, x < 56 ? 0xFF0000 : x >= 392 ? 0x0000FF : 0x00FF00);
            }
        }
        float[] tensor = ImageNetPreprocessor.preprocess(image);
        int plane = 224 * 224;
        int left = 112 * 224 + 5;
        int center = 112 * 224 + 112;
        int right = 112 * 224 + 218;
        assertEquals((1 - 0.485) / 0.229, tensor[left], 1e-6);
        assertEquals((1 - 0.456) / 0.224, tensor[plane + center], 1e-6);
        assertEquals((1 - 0.406) / 0.225, tensor[2 * plane + right], 1e-6);
    }

    @Test
    void rgbConversionDiscardsAlphaRatherThanCompositing() {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00FF0000);
        float[] tensor = ImageNetPreprocessor.preprocess(image);
        assertEquals((1 - 0.485) / 0.229, tensor[0], 1e-6);
        assertEquals(-0.456 / 0.224, tensor[224 * 224], 1e-6);
        assertThrows(NullPointerException.class, () -> ImageNetPreprocessor.preprocess(null));
    }
}
