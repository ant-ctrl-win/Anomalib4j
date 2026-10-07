package io.github.antctrlwin.anomalib4j.evaluation;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

final class LocalizationMaps {
    private LocalizationMaps() { }

    static double[] upsample(double[] source, int sourceWidth, int sourceHeight, int width, int height) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0
                || source.length != Math.multiplyExact(sourceWidth, sourceHeight)) throw new IllegalArgumentException("Invalid map shape");
        for (double value : source) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite raw score");
        double[] output = new double[Math.multiplyExact(width, height)];
        for (int row = 0; row < height; row++) {
            double y = Math.clamp((row + .5) * sourceHeight / height - .5, 0, sourceHeight - 1);
            int y0 = (int) y;
            int y1 = Math.min(y0 + 1, sourceHeight - 1);
            for (int col = 0; col < width; col++) {
                double x = Math.clamp((col + .5) * sourceWidth / width - .5, 0, sourceWidth - 1);
                int x0 = (int) x;
                int x1 = Math.min(x0 + 1, sourceWidth - 1);
                double top = source[y0 * sourceWidth + x0] * (1 - (x - x0)) + source[y0 * sourceWidth + x1] * (x - x0);
                double bottom = source[y1 * sourceWidth + x0] * (1 - (x - x0)) + source[y1 * sourceWidth + x1] * (x - x0);
                output[row * width + col] = top * (1 - (y - y0)) + bottom * (y - y0);
            }
        }
        return output;
    }

    static void overlay(Path path, BufferedImage original, boolean[] mask, double[] scores,
                        double low, double high) throws IOException {
        overlay(path, original, mask, scores, low, high, "Raw bilinear map (blue to red)");
    }

    static void overlay(Path path, BufferedImage original, boolean[] mask, double[] scores,
                        double low, double high, String scoreLabel) throws IOException {
        int width = original.getWidth();
        int height = original.getHeight();
        if (mask.length != width * height || scores.length != mask.length || !Double.isFinite(low)
                || !Double.isFinite(high) || low > high) throw new IllegalArgumentException("Invalid overlay shape/scale");
        var result = new BufferedImage(width * 3, height + 60, BufferedImage.TYPE_INT_RGB);
        var g = result.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, result.getWidth(), result.getHeight());
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            String[] titles = {"Original", "Ground truth (red)", scoreLabel};
            for (int panel = 0; panel < 3; panel++) {
                g.drawString(titles[panel], panel * width + 5, 20);
                g.drawImage(original, panel * width, 60, null);
            }
            g.drawString(String.format(java.util.Locale.ROOT, "Shared display scale: %.6f to %.6f", low, high), 5, 45);
            for (int p = 0; p < scores.length; p++) {
                int x = p % width;
                int y = p / width;
                int rgb = original.getRGB(x, y);
                if (mask[p]) result.setRGB(width + x, 60 + y, blend(rgb, Color.RED.getRGB()));
                float fraction = high == low ? .5f : (float) Math.clamp((scores[p] - low) / (high - low), 0, 1);
                result.setRGB(2 * width + x, 60 + y, blend(rgb, Color.getHSBColor((1 - fraction) * .66f, 1, 1).getRGB()));
            }
        } finally { g.dispose(); }
        if (!ImageIO.write(result, "png", path.toFile())) throw new IOException("PNG writer unavailable");
    }

    private static int blend(int background, int foreground) {
        int result = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) {
            int value = (int) Math.round(.55 * ((foreground >>> shift) & 255) + .45 * ((background >>> shift) & 255));
            result |= value << shift;
        }
        return result;
    }
}
