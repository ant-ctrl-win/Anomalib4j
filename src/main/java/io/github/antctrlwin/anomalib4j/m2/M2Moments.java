package io.github.antctrlwin.anomalib4j.m2;

/** Streaming sample statistics; each observation belongs to exactly one split. */
public final class M2Moments {
    private final double[] mean;
    private final double[] squared;
    private long count;

    public M2Moments(int size) {
        if (size <= 0) throw new IllegalArgumentException("Positive size required");
        mean = new double[size];
        squared = new double[size];
    }

    public void add(double[] values) {
        M2Model.requireFinite(values, mean.length);
        count++;
        for (int i = 0; i < mean.length; i++) {
            double delta = values[i] - mean[i];
            mean[i] += delta / count;
            squared[i] += delta * (values[i] - mean[i]);
            if (!Double.isFinite(squared[i]) || !Double.isFinite(mean[i]))
                throw new IllegalArgumentException("Statistics overflow");
        }
    }

    public double[] mean() { requireSamples(); return mean.clone(); }
    public double[] sampleStd() {
        requireSamples();
        double[] result = new double[mean.length];
        for (int i = 0; i < result.length; i++) result[i] = Math.sqrt(Math.max(0, squared[i] / (count - 1)));
        return result;
    }
    public double[] effectiveStd() {
        double[] result = sampleStd();
        for (int i = 0; i < result.length; i++) result[i] = Math.max(result[i], M2Model.FLOOR);
        return result;
    }
    private void requireSamples() {
        if (count < 2) throw new IllegalStateException("At least two observations required");
    }
}
