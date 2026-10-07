package io.github.antctrlwin.anomalib4j.evaluation;

import java.util.ArrayList;

final class LocalizationMetrics {
    static final double FPR_LIMIT = .30;
    private final double[] scores;
    private final int[] regions;
    private final ArrayList<Integer> areas = new ArrayList<>();
    private int used;
    private boolean finished;

    LocalizationMetrics(int pixelCount) {
        if (pixelCount <= 0) throw new IllegalArgumentException("Positive pixel count required");
        scores = new double[pixelCount];
        regions = new int[pixelCount];
        areas.add(0);
    }

    void add(double[] prediction, boolean[] mask, int width, int height) {
        if (finished) throw new IllegalStateException("Metrics already calculated");
        if (width <= 0 || height <= 0 || prediction.length != Math.multiplyExact(width, height)
                || mask.length != prediction.length || prediction.length > scores.length - used) {
            throw new IllegalArgumentException("Map dimensions/capacity mismatch");
        }
        for (double value : prediction) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite score");
        System.arraycopy(prediction, 0, scores, used, prediction.length);
        int[] queue = new int[mask.length];
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || regions[used + start] != 0) continue;
            int id = areas.size();
            int head = 0;
            int tail = 1;
            queue[0] = start;
            regions[used + start] = id;
            while (head < tail) {
                int pixel = queue[head++];
                int row = pixel / width;
                int column = pixel % width;
                for (int y = Math.max(0, row - 1); y <= Math.min(height - 1, row + 1); y++) {
                    for (int x = Math.max(0, column - 1); x <= Math.min(width - 1, column + 1); x++) {
                        int next = y * width + x;
                        if (mask[next] && regions[used + next] == 0) {
                            regions[used + next] = id;
                            queue[tail++] = next;
                        }
                    }
                }
            }
            areas.add(tail);
        }
        used += prediction.length;
    }

    Result calculate() {
        if (finished || used != scores.length) throw new IllegalStateException("Metrics need a complete, unconsumed batch");
        long positives = 0;
        for (int area : areas) positives += area;
        long negatives = used - positives;
        if (positives == 0 || negatives == 0) throw new IllegalStateException("Pixel metrics require both foreground and background");
        finished = true;
        double[] regionWeights = new double[areas.size()];
        for (int id = 1; id < areas.size(); id++) regionWeights[id] = 1.0 / areas.get(id) / (areas.size() - 1);
        sort(0, scores.length - 1);
        long tp = 0;
        long fp = 0;
        double pro = 0;
        double auc = 0;
        double proArea = 0;
        double[] sampledPro = new double[301];
        int sample = 0;
        int end = scores.length - 1;
        while (end >= 0) {
            double oldFpr = (double) fp / negatives;
            double oldTpr = (double) tp / positives;
            double oldPro = pro;
            double threshold = scores[end];
            do {
                int region = regions[end--];
                if (region == 0) fp++;
                else {
                    tp++;
                    pro += regionWeights[region];
                }
            } while (end >= 0 && scores[end] == threshold);
            double fpr = (double) fp / negatives;
            double tpr = (double) tp / positives;
            auc += (fpr - oldFpr) * (oldTpr + tpr) / 2;
            if (fpr > oldFpr && oldFpr < FPR_LIMIT) {
                double stop = Math.min(fpr, FPR_LIMIT);
                double stopPro = interpolate(oldFpr, oldPro, fpr, pro, stop);
                proArea += (stop - oldFpr) * (oldPro + stopPro) / 2;
            }
            while (sample < sampledPro.length && sample * .001 < fpr) {
                sampledPro[sample] = interpolate(oldFpr, oldPro, fpr, pro, sample * .001);
                sample++;
            }
        }
        while (sample < sampledPro.length) sampledPro[sample++] = pro;
        return new Result(auc, proArea / FPR_LIMIT, areas.size() - 1, positives, negatives, sampledPro);
    }

    private static double interpolate(double x0, double y0, double x1, double y1, double x) {
        return y0 + (y1 - y0) * ((x - x0) / (x1 - x0));
    }

    private void sort(int low, int high) {
        while (low < high) {
            double pivot = scores[low + (high - low) / 2];
            int lower = low;
            int cursor = low;
            int upper = high;
            while (cursor <= upper) {
                if (scores[cursor] < pivot) swap(lower++, cursor++);
                else if (scores[cursor] > pivot) swap(cursor, upper--);
                else cursor++;
            }
            if (lower - low < high - upper) {
                sort(low, lower - 1);
                low = upper + 1;
            } else {
                sort(upper + 1, high);
                high = lower - 1;
            }
        }
    }

    private void swap(int a, int b) {
        double value = scores[a];
        scores[a] = scores[b];
        scores[b] = value;
        int region = regions[a];
        regions[a] = regions[b];
        regions[b] = region;
    }

    record Result(double pixelAuRoc, double auPro, int regionCount, long positivePixels,
                  long negativePixels, double[] proAtFpr) { }
}
