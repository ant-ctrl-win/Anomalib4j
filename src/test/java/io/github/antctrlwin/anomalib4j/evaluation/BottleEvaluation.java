package io.github.antctrlwin.anomalib4j.evaluation;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

final class BottleEvaluation {
    private BottleEvaluation() { }

    static String defect(String filename) {
        return EvaluationLabels.defect(filename);
    }

    static double auroc(double[] good, double[] anomaly) {
        if (good.length == 0 || anomaly.length == 0) throw new IllegalArgumentException("Both classes required");
        double wins = 0;
        for (double normal : good) {
            for (double abnormal : anomaly) {
                if (!Double.isFinite(normal) || !Double.isFinite(abnormal)) {
                    throw new IllegalArgumentException("Scores must be finite");
                }
                wins += abnormal > normal ? 1 : abnormal == normal ? 0.5 : 0;
            }
        }
        return wins / ((double) good.length * anomaly.length);
    }

    static double percentile(double[] values, double fraction) {
        if (values.length == 0 || !Double.isFinite(fraction) || fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("Nonempty distribution and fraction in [0,1] required");
        }
        double[] sorted = values.clone();
        for (double value : sorted) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite value");
        Arrays.sort(sorted);
        double index = fraction * (sorted.length - 1);
        int lower = (int) index;
        int upper = Math.min(lower + 1, sorted.length - 1);
        return sorted[lower] + (index - lower) * (sorted[upper] - sorted[lower]);
    }

    static Summary summarize(double[] raw) {
        if (raw.length != 196) throw new IllegalArgumentException("Expected 14x14 raw map");
        double min = Double.POSITIVE_INFINITY;
        double sum = 0;
        int maximum = 0;
        for (int p = 0; p < raw.length; p++) {
            if (!Double.isFinite(raw[p])) throw new IllegalArgumentException("Nonfinite discrepancy");
            min = Math.min(min, raw[p]);
            sum += raw[p];
            if (raw[p] > raw[maximum]) maximum = p;
        }
        return new Summary(min, sum / raw.length, raw[maximum], maximum / 14, maximum % 14);
    }

    static String distribution(String label, double[] scores) {
        return String.format(Locale.ROOT, "%s,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                label, scores.length, percentile(scores, 0), percentile(scores, .25),
                percentile(scores, .5), Arrays.stream(scores).average().orElseThrow(),
                percentile(scores, .75), percentile(scores, 1));
    }

    static void heatmap(Path output, String filename, double[] raw, double low, double high) throws IOException {
        int cell = 64;
        int left = 62;
        int top = 115;
        var image = new BufferedImage(1000, 1060, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
            g.drawString(filename + " | raw discrepancy 1 - score", 25, 32);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.drawString(String.format(Locale.ROOT, "Shared display scale: %.2f (blue) to %.2f (red). No score calibration.", low, high), 25, 60);
            g.drawString("Rows / columns: zero-based. Cell values rounded for display only.", 25, 84);
            for (int i = 0; i < 14; i++) {
                g.setColor(Color.BLACK);
                g.drawString(Integer.toString(i), left + i * cell + 25, top - 9);
                g.drawString(Integer.toString(i), 25, top + i * cell + 36);
            }
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            for (int p = 0; p < 196; p++) {
                float fraction = high == low ? .5f : (float) ((raw[p] - low) / (high - low));
                Color color = Color.getHSBColor((1 - fraction) * .66f, .55f, 1);
                int x = left + p % 14 * cell;
                int y = top + p / 14 * cell;
                g.setColor(color);
                g.fillRect(x, y, cell, cell);
                g.setColor(Color.WHITE);
                g.drawRect(x, y, cell, cell);
                g.setColor(Color.BLACK);
                String value = String.format(Locale.ROOT, "%.1f", raw[p]);
                g.drawString(value, x + (cell - g.getFontMetrics().stringWidth(value)) / 2, y + 37);
            }
            Summary summary = summarize(raw);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.drawString(String.format(Locale.ROOT, "Image max = %.6f at (%d, %d); mean = %.6f; min = %.6f",
                    summary.max(), summary.row(), summary.column(), summary.mean(), summary.min()), 25, 1040);
        } finally {
            g.dispose();
        }
        if (!ImageIO.write(image, "png", output.toFile())) throw new IOException("PNG writer unavailable");
    }

    record Summary(double min, double mean, double max, int row, int column) { }
    record Sample(String filename, String defect, float[] features, double[] raw) { }
}
