package io.github.antctrlwin.anomalib4j.onnx;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class EncoderMicroprofileTest {
    @Test void isolatedOnnxOutputMatchesProductionExtractAndCanBeCopiedRepeatedly() throws Exception {
        var state = new EncoderMicroprofile.OnnxState();
        state.image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            state.image.setRGB(x, y, ((x * 7) << 16) | ((y * 7) << 8) | (x + y));
        }
        state.prepareOnnx();
        assertArrayEquals(ImageNetPreprocessor.preprocess(state.image), state.nchw);
        try {
            try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
                float[] actual = EncoderMicroprofile.copyOutput(state.result);
                assertEquals(14 * 14 * 96, actual.length);
                assertArrayEquals(encoder.extract(state.image), actual, 1e-6f);
                float[] secondCopy = EncoderMicroprofile.copyOutput(state.result);
                assertNotSame(actual, secondCopy);
                assertArrayEquals(actual, secondCopy);
                actual[0] = Float.NaN;
                assertArrayEquals(secondCopy, EncoderMicroprofile.copyOutput(state.result));
                try (var nextResult = state.session.run(state.inputs)) {
                    assertArrayEquals(secondCopy, EncoderMicroprofile.copyOutput(nextResult), 1e-6f);
                }
            }
        } finally {
            state.closeOnnx();
            state.releaseImage();
        }
    }
}
