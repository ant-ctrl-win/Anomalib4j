package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class PositionalRawCalibration {
    private final ModelDescriptor descriptor;
    private final double[] mean;
    private final double[] sampleSigma;
    private final double[] sigma;
    private final int count;
    private final double floor;

    private PositionalRawCalibration(ModelDescriptor descriptor, double[] mean, double[] sampleSigma,
                                     double[] sigma, int count, double floor) {
        this.descriptor = descriptor;
        this.mean = mean.clone();
        this.sampleSigma = sampleSigma.clone();
        this.sigma = sigma.clone();
        this.count = count;
        this.floor = floor;
    }

    static PositionalRawCalibration fit(ModelDescriptor descriptor, List<double[]> rawMaps, double floor) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(rawMaps, "rawMaps");
        if (!Double.isFinite(floor) || floor <= 0) throw new IllegalArgumentException("Positive finite sigma floor required");
        if (rawMaps.size() < 2) throw new IllegalArgumentException("Sample deviation requires at least two maps");
        int cells = descriptor.grid().cells();
        double[] mean = new double[cells];
        double[] m2 = new double[cells];
        int count = 0;
        for (double[] raw : rawMaps) {
            validate(raw, cells);
            count++;
            for (int p = 0; p < cells; p++) {
                double delta = raw[p] - mean[p];
                mean[p] += delta / count;
                m2[p] += delta * (raw[p] - mean[p]);
                if (!Double.isFinite(mean[p]) || !Double.isFinite(m2[p])) throw new ArithmeticException("Raw statistics overflow");
            }
        }
        double[] sampleSigma = new double[cells];
        double[] sigma = new double[cells];
        for (int p = 0; p < cells; p++) {
            sampleSigma[p] = Math.sqrt(Math.max(0, m2[p] / (count - 1)));
            sigma[p] = Math.max(sampleSigma[p], floor);
        }
        return new PositionalRawCalibration(descriptor, mean, sampleSigma, sigma, count, floor);
    }

    void requireCompatible(ModelDescriptor candidate) {
        if (!descriptor.equals(candidate)) throw new IllegalArgumentException("Calibration/model descriptor mismatch");
    }

    double[] calibrate(double[] raw) {
        validate(raw, mean.length);
        double[] z = new double[raw.length];
        for (int p = 0; p < raw.length; p++) {
            z[p] = (raw[p] - mean[p]) / sigma[p];
            if (!Double.isFinite(z[p])) throw new ArithmeticException("Calibrated score overflow");
        }
        return z;
    }

    double mean(int p) { return mean[p]; }
    double sampleSigma(int p) { return sampleSigma[p]; }
    double sigma(int p) { return sigma[p]; }
    int count() { return count; }
    double floor() { return floor; }

    String parametersCsv() {
        var csv = new StringBuilder("row,column,count,mu,sigma_sample,sigma_effective,floored\n");
        for (int p = 0; p < mean.length; p++) {
            csv.append(String.format(Locale.ROOT, "%d,%d,%d,%.17g,%.17g,%.17g,%s%n",
                    p / descriptor.grid().width(), p % descriptor.grid().width(), count,
                    mean[p], sampleSigma[p], sigma[p], sampleSigma[p] < floor));
        }
        return csv.toString();
    }

    String summaryCsv() {
        return "parameter,positions,min,p25,median,mean,p75,max,sample_std_across_positions\n"
                + summarize("mu", mean) + summarize("sigma_sample", sampleSigma) + summarize("sigma_effective", sigma);
    }

    int flooredPositions() {
        int floored = 0;
        for (double value : sampleSigma) if (value < floor) floored++;
        return floored;
    }

    private static String summarize(String name, double[] values) {
        double average = 0;
        double m2 = 0;
        int n = 0;
        for (double value : values) {
            double delta = value - average;
            average += delta / ++n;
            m2 += delta * (value - average);
        }
        double deviation = n > 1 ? Math.sqrt(Math.max(0, m2 / (n - 1))) : 0;
        return String.format(Locale.ROOT, "%s,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                name, n, BottleEvaluation.percentile(values, 0), BottleEvaluation.percentile(values, .25),
                BottleEvaluation.percentile(values, .5), average, BottleEvaluation.percentile(values, .75),
                BottleEvaluation.percentile(values, 1), deviation);
    }

    private static void validate(double[] raw, int cells) {
        Objects.requireNonNull(raw, "raw");
        if (raw.length != cells) throw new IllegalArgumentException("Raw map does not match calibration grid");
        for (double value : raw) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite raw score");
    }
}
