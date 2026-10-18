package io.github.antctrlwin.anomalib4j.evaluation;

import java.awt.image.BufferedImage;

/** Frozen diagnostic transformations, never used by the production detector. */
public final class RobustnessTransforms {
    public enum Family {
        TRANSLATION_X, TRANSLATION_Y, ROTATION, SCALE, BRIGHTNESS, CONTRAST, GAMMA;
        public double identity() {
            return switch (this) { case TRANSLATION_X, TRANSLATION_Y, ROTATION -> 0; default -> 1; };
        }
        public double[] levels() {
            return switch (this) {
                case TRANSLATION_X, TRANSLATION_Y -> new double[]{-8, -4, -2, 0, 2, 4, 8};
                case ROTATION -> new double[]{-20, -10, -5, 0, 5, 10, 20};
                case SCALE -> new double[]{.90, .95, 1, 1.05, 1.10};
                case BRIGHTNESS, CONTRAST, GAMMA -> new double[]{.80, .90, 1, 1.10, 1.20};
            };
        }
    }
    private RobustnessTransforms() { }

    public static BufferedImage apply(BufferedImage source, Family family, double level) {
        if (source.getWidth() != 224 || source.getHeight() != 224 || !Double.isFinite(level))
            throw new IllegalArgumentException("Expected canonical 224 RGB and finite level");
        int[] input = source.getRGB(0, 0, 224, 224, null, 0, 224);
        int[] result = new int[input.length];
        double angle = Math.toRadians(level), cos = Math.cos(angle), sin = Math.sin(angle);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 224; x++) {
            double sx = x, sy = y;
            switch (family) {
                case TRANSLATION_X -> sx -= level;
                case TRANSLATION_Y -> sy -= level;
                // Inverse mapping; positive angle moves content clockwise in image coordinates.
                case ROTATION -> { sx = cos * (x - 111.5) + sin * (y - 111.5) + 111.5;
                    sy = -sin * (x - 111.5) + cos * (y - 111.5) + 111.5; }
                case SCALE -> { sx = (x - 111.5) / level + 111.5; sy = (y - 111.5) / level + 111.5; }
                case BRIGHTNESS, CONTRAST, GAMMA -> { }
            }
            sx = Math.max(0, Math.min(223, sx)); sy = Math.max(0, Math.min(223, sy));
            int x0 = (int) Math.floor(sx), y0 = (int) Math.floor(sy);
            int x1 = Math.min(223, x0 + 1), y1 = Math.min(223, y0 + 1);
            double dx = sx - x0, dy = sy - y0;
            int pixel = 0;
            for (int shift : new int[]{16, 8, 0}) {
                double a = (input[y0 * 224 + x0] >>> shift) & 255;
                double b = (input[y0 * 224 + x1] >>> shift) & 255;
                double c = (input[y1 * 224 + x0] >>> shift) & 255;
                double d = (input[y1 * 224 + x1] >>> shift) & 255;
                double value = (a * (1 - dx) + b * dx) * (1 - dy) + (c * (1 - dx) + d * dx) * dy;
                value = switch (family) {
                    case BRIGHTNESS -> value * level;
                    case CONTRAST -> 127.5 + (value - 127.5) * level;
                    case GAMMA -> 255 * Math.pow(value / 255, level);
                    default -> value;
                };
                pixel |= (int) Math.round(Math.max(0, Math.min(255, value))) << shift;
            }
            result[y * 224 + x] = pixel;
        }
        var output = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        output.setRGB(0, 0, 224, 224, result, 0, 224);
        return output;
    }

    public static double interior(double[] z) {
        if (z.length != 196) throw new IllegalArgumentException("Expected 14x14 map");
        double max = Double.NEGATIVE_INFINITY;
        for (int y = 1; y < 13; y++) for (int x = 1; x < 13; x++) max = Math.max(max, z[y * 14 + x]);
        return max;
    }
}
