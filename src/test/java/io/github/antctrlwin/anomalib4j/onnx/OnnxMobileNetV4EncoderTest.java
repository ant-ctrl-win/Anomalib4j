package io.github.antctrlwin.anomalib4j.onnx;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.GridShape;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class OnnxMobileNetV4EncoderTest {
    private BufferedImage image() {
        var image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, ((x % 256) << 16) | ((y % 256) << 8) | ((x + y) % 256));
            }
        }
        return image;
    }

    @Test
    void loadsClasspathModelAndExecutesDefault14By14() throws Exception {
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            assertEquals(new GridShape(14, 14, 96), encoder.grid());
            assertEquals(OnnxMobileNetV4Encoder.Variant.SPATIAL_14, encoder.variant());
            assertEquals("mobilenetv4_conv_small.e2400_r224_in1k", encoder.modelId());
            float[] output = encoder.extract(image());
            assertEquals(14 * 14 * 96, output.length);
            assertFiniteAndNonTrivial(output);
        }
    }

    @Test
    void executes28By28WithoutSeparateImplementation() throws Exception {
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_28)) {
            assertEquals(new GridShape(28, 28, 64), encoder.grid());
            float[] output = encoder.extract(image());
            assertEquals(28 * 28 * 64, output.length);
            assertFiniteAndNonTrivial(output);
        }
    }

    @Test
    void repeatedCallsReuseEncoderAndReturnIndependentBuffers() throws Exception {
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            BufferedImage image = image();
            float[] first = encoder.extract(image);
            float[] expected = first.clone();
            float[] second = encoder.extract(image);
            assertNotSame(first, second);
            assertArrayEquals(expected, second, 1e-6f);
            first[0] = Float.NaN;
            assertArrayEquals(expected, encoder.extract(image), 1e-6f);
            assertThrows(NullPointerException.class, () -> encoder.extract(null));
        }
    }

    @Test
    void closeIsIdempotentAndExtractionAfterCloseIsRejected() throws Exception {
        var encoder = new OnnxMobileNetV4Encoder();
        encoder.close();
        encoder.close();
        assertThrows(IllegalStateException.class, () -> encoder.extract(image()));
        try (var other = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_28)) {
            assertFiniteAndNonTrivial(other.extract(image()));
        }
    }

    private static void assertFiniteAndNonTrivial(float[] values) {
        boolean nonZero = false;
        for (float value : values) {
            assertTrue(Float.isFinite(value));
            nonZero |= value != 0;
        }
        assertTrue(nonZero);
    }
}
