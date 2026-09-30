package org.vaimee.vsa.anomalib.onnx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

final class ImageNetPreprocessor {
    static final int INPUT_SIZE = 224;

    private ImageNetPreprocessor() {
    }

    static float[] preprocess(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        int width = image.getWidth();
        int height = image.getHeight();
        int[] sourcePixels = image.getRGB(0, 0, width, height, null, 0, width);
        // getRGB converts to sRGB; TYPE_INT_RGB discards alpha without compositing.
        var rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, width, height, sourcePixels, 0, width);
        var resized = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(rgb, 0, 0, INPUT_SIZE, INPUT_SIZE, null);
        } finally {
            graphics.dispose();
        }
        int[] pixels = resized.getRGB(0, 0, INPUT_SIZE, INPUT_SIZE, null, 0, INPUT_SIZE);
        int plane = INPUT_SIZE * INPUT_SIZE;
        float[] nchw = new float[3 * plane];
        for (int p = 0; p < plane; p++) {
            int pixel = pixels[p];
            nchw[p] = (float) ((((pixel >>> 16) & 255) / 255.0 - 0.485) / 0.229);
            nchw[plane + p] = (float) ((((pixel >>> 8) & 255) / 255.0 - 0.456) / 0.224);
            nchw[2 * plane + p] = (float) (((pixel & 255) / 255.0 - 0.406) / 0.225);
        }
        return nchw;
    }
}
