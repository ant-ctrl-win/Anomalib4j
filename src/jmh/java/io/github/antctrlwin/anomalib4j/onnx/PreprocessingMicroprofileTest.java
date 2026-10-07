package io.github.antctrlwin.anomalib4j.onnx;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class PreprocessingMicroprofileTest {
    @Test void composedStagesMatchProductionExactlyAcrossColorModelsAndShapes() {
        int[] types = {BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_3BYTE_BGR,
                BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_BYTE_INDEXED};
        for (int type : types) {
            for (int[] shape : new int[][]{{1, 1}, {37, 19}, {224, 224}, {301, 239}}) {
                var state = new PreprocessingMicroprofile.Stages();
                state.image = new BufferedImage(shape[0], shape[1], type);
                for (int y = 0; y < shape[1]; y++) for (int x = 0; x < shape[0]; x++) {
                    state.image.setRGB(x, y, (((x + y) * 29 & 255) << 24)
                            | ((x * 13 & 255) << 16) | ((y * 17 & 255) << 8) | ((x + y) * 7 & 255));
                }
                int[] before = PreprocessingMicroprofile.readRgb(state.image);
                try {
                    state.prepareStages();
                    float[] expected = ImageNetPreprocessor.preprocess(state.image);
                    float[] actual = PreprocessingMicroprofile.normalizedNchw(state.pixels);
                    assertArrayEquals(expected, actual, "type=" + type);
                    assertEquals(3 * 224 * 224, actual.length);
                    assertEquals(BufferedImage.TYPE_INT_RGB, state.rgb.getType());
                    assertEquals(224, state.resized.getWidth());
                    assertEquals(224, state.resized.getHeight());
                    actual[0] = Float.NaN;
                    assertArrayEquals(expected, PreprocessingMicroprofile.normalizedNchw(state.pixels));
                    assertArrayEquals(before, PreprocessingMicroprofile.readRgb(state.image));
                } finally {
                    state.releaseStages();
                    state.releaseImage();
                }
            }
        }
    }
}
