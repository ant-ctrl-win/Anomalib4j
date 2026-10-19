package io.github.antctrlwin.anomalib4j.evaluation;

import java.awt.image.BufferedImage;

final class OracleGeometry {
    record Error(double mae, double rmse, double max) { }
    private OracleGeometry() { }

    static double inverse(RobustnessTransforms.Family family, double level) {
        if (!Double.isFinite(level)) throw new IllegalArgumentException("Finite level required");
        return switch (family) {
            case ROTATION -> -level;
            case SCALE -> {
                if (level <= 0) throw new IllegalArgumentException("Positive scale required");
                yield 1 / level;
            }
            default -> throw new IllegalArgumentException("Oracle scope is rotation/scale only");
        };
    }

    static Error difference(double[] a, double[] b, boolean interior) {
        if (a.length != 196 || b.length != 196) throw new IllegalArgumentException("Expected native maps");
        double sum = 0, squared = 0, max = 0;
        int n = 0;
        for (int p = 0; p < 196; p++) {
            if (interior && (p / 14 == 0 || p / 14 == 13 || p % 14 == 0 || p % 14 == 13)) continue;
            double d = Math.abs(a[p] - b[p]);
            if (!Double.isFinite(d)) throw new IllegalArgumentException("Nonfinite map");
            sum += d; squared += d*d; max = Math.max(max, d); n++;
        }
        return new Error(sum/n, Math.sqrt(squared/n), max);
    }

    static Error rgbDifference(BufferedImage a, BufferedImage b) {
        int[] left = a.getRGB(0, 0, 224, 224, null, 0, 224);
        int[] right = b.getRGB(0, 0, 224, 224, null, 0, 224);
        double sum = 0, squared = 0, max = 0;
        for (int p = 0; p < left.length; p++) for (int shift : new int[]{16, 8, 0}) {
            double d = Math.abs(((left[p] >>> shift) & 255) - ((right[p] >>> shift) & 255));
            sum += d; squared += d*d; max = Math.max(max, d);
        }
        return new Error(sum/(left.length*3), Math.sqrt(squared/(left.length*3)), max);
    }
}
