package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.m2.M2Moments;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class SpatialCorrespondence {
    public enum Mode { P, N1, G;
        public String id() { return "M2-" + name(); }
    }
    public record Raw(double[] values, int[] reference) { }
    record Minimum(double value, int reference) { }
    private final M2Model model;
    private final Mode mode;
    private final double[] mean, std;
    private final int[][] candidates;

    public SpatialCorrespondence(M2Model model, Mode mode) {
        this.model = model; this.mode = mode;
        mean = model.local().mean(); std = model.local().effectiveStd();
        candidates = IntStream.range(0, 196).mapToObj(p -> references(p, mode)).toArray(int[][]::new);
    }
    public Mode mode() { return mode; }
    public M2Model.Calibration calibration() { return model.calibration(); }
    public SpatialCorrespondence calibrated(List<double[]> heldOut) {
        if (heldOut.size() < 2) throw new IllegalArgumentException("At least two held-out observations");
        if (mode == Mode.P) return this;
        var moments = new M2Moments(196);
        for (double[] unit : heldOut) moments.add(raw(unit).values());
        var calibratedModel = new M2Model(model.encoderSha256(), model.local(),
                new M2Model.Calibration(moments.mean(), moments.effectiveStd()));
        return new SpatialCorrespondence(calibratedModel, mode);
    }
    public Raw raw(double[] unit) {
        if (mode == Mode.P) return new Raw(model.raw(unit), IntStream.range(0, 196).toArray());
        M2Model.requireUnit(unit);
        double[] values = new double[196]; int[] reference = new int[196];
        for (int p = 0; p < 196; p++) {
            var best = minimum(unit, p, candidates[p]);
            values[p] = best.value(); reference[p] = best.reference();
        }
        if (!Arrays.stream(values).allMatch(Double::isFinite)) throw new IllegalArgumentException("Nonfinite distance");
        return new Raw(values, reference);
    }
    Minimum minimum(double[] unit, int p, int[] references) {
        double minimum = Double.POSITIVE_INFINITY; int argmin = -1;
        for (int q : references) {
            double sum = 0;
            for (int c = 0; c < 96; c++) {
                double residual = (unit[p * 96 + c] - mean[q * 96 + c]) / std[q * 96 + c];
                sum += residual * residual;
            }
            double distance = sum / 96;
            if (distance < minimum) { minimum = distance; argmin = q; }
        }
        return new Minimum(minimum, argmin);
    }
    public double[] z(Raw raw) { return model.calibrate(raw.values()); }
    static int[] references(int p, Mode mode) {
        if (p < 0 || p >= 196) throw new IllegalArgumentException("Invalid query position");
        return switch (mode) {
            case P -> new int[]{p};
            case G -> IntStream.range(0, 196).toArray();
            case N1 -> IntStream.range(0, 196).filter(q -> Math.abs(p / 14 - q / 14) <= 1
                    && Math.abs(p % 14 - q % 14) <= 1).toArray();
        };
    }
    public static double samePosition(Raw raw) {
        return IntStream.range(0, 196).filter(p -> raw.reference()[p] == p).count() / 196.0;
    }
    public static double displacement(Raw raw) {
        double sum = 0;
        for (int p = 0; p < 196; p++) {
            int q = raw.reference()[p];
            sum += Math.hypot(p / 14 - q / 14, p % 14 - q % 14);
        }
        return sum / 196;
    }
}
