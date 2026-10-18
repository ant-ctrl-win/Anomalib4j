package io.github.antctrlwin.anomalib4j.onnx;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalRgbTest {
    @Test void preservesEveryByteAndProductionTensor() {
        // Given all byte values in each channel, with noncanonical source geometry.
        var source = new BufferedImage(256, 19, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 19; y++) for (int x = 0; x < 256; x++)
            source.setRGB(x, y, (x << 16) | ((255 - x) << 8) | x);
        // When recovering the canonical RGB intermediate.
        var canonical = CanonicalRgb.from(source);
        // Then the complete production input tensor is bit-identical.
        assertArrayEquals(ImageNetPreprocessor.preprocess(source), ImageNetPreprocessor.preprocess(canonical));
        for (int value = 0; value < 256; value++) for (int channel = 0; channel < 3; channel++)
            assertEquals(value, CanonicalRgb.byteValue(CanonicalRgb.normalized(value, channel), channel));
    }
}
