package io.github.antctrlwin.anomalib4j.projection;

/** Raw linear operators; outputs are overwritten and input buffers remain unchanged. */
public sealed interface ProjectionStrategy permits DenseRademacherProjection {
    int inputDimensions();

    int outputDimensions();

    long seed();

    void project(float[] input, double[] output);

    /** Input and output must be distinct arrays, including for square matrices. */
    void adjoint(double[] input, double[] output);
}
