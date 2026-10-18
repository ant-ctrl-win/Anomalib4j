package io.github.antctrlwin.anomalib4j.onnx;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Test-only access to the production resize result without changing its API. */
public final class CanonicalRgb {
    private static final double[] MEAN = {0.485, 0.456, 0.406};
    private static final double[] STD = {0.229, 0.224, 0.225};
    private CanonicalRgb() { }

    public static BufferedImage from(BufferedImage source) {
        float[] tensor = ImageNetPreprocessor.preprocess(source);
        int plane = 224 * 224;
        var rgb = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[plane];
        // Float32 normalization retains ample precision to recover every uint8 value.
        for (int p = 0; p < plane; p++) for (int c = 0; c < 3; c++)
            pixels[p] |= byteValue(tensor[c * plane + p], c) << (16 - 8 * c);
        rgb.setRGB(0, 0, 224, 224, pixels, 0, 224);
        if (!Arrays.equals(tensor, ImageNetPreprocessor.preprocess(rgb)))
            throw new IllegalStateException("Canonical RGB changed the production tensor");
        return rgb;
    }

    static int byteValue(float value, int channel) {
        return (int) Math.round((value * STD[channel] + MEAN[channel]) * 255);
    }
    static float normalized(int value, int channel) {
        return (float) ((value / 255.0 - MEAN[channel]) / STD[channel]);
    }
}
