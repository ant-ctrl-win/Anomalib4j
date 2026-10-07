package io.github.antctrlwin.anomalib4j.projection;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public final class DenseRademacherProjection implements ProjectionStrategy {
    private final int inputDimensions;
    private final int outputDimensions;
    private final int wordsPerRow;
    private final long[] positiveSigns;
    private final long seed;

    public DenseRademacherProjection(int inputDimensions, int outputDimensions) {
        this(inputDimensions, outputDimensions, 42L);
    }

    public DenseRademacherProjection(int inputDimensions, int outputDimensions, long seed) {
        if (inputDimensions <= 0 || outputDimensions <= 0) {
            throw new IllegalArgumentException("Projection dimensions must be positive");
        }
        this.inputDimensions = inputDimensions;
        this.seed = seed;
        this.outputDimensions = outputDimensions;
        this.wordsPerRow = (int) ((inputDimensions + 63L) / Long.SIZE);
        this.positiveSigns = new long[Math.multiplyExact(outputDimensions, wordsPerRow)];
        Random random = new Random(seed);
        // Legacy PRNG order is channel-first, independently of the row-packed layout.
        for (int c = 0; c < inputDimensions; c++) {
            for (int d = 0; d < outputDimensions; d++) {
                if (random.nextBoolean()) {
                    positiveSigns[d * wordsPerRow + c / Long.SIZE] |= 1L << (c % Long.SIZE);
                }
            }
        }
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public int inputDimensions() {
        return inputDimensions;
    }

    @Override
    public int outputDimensions() {
        return outputDimensions;
    }

    @Override
    public void project(float[] input, double[] output) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        if (input.length != inputDimensions || output.length != outputDimensions) {
            throw new IllegalArgumentException("Forward buffers do not match projection dimensions");
        }
        for (float value : input) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Projection input must be finite");
            }
        }
        for (int d = 0; d < outputDimensions; d++) {
            double sum = 0;
            int rowOffset = d * wordsPerRow;
            for (int c = 0; c < inputDimensions; c++) {
                boolean positive = (positiveSigns[rowOffset + c / Long.SIZE] & (1L << (c % Long.SIZE))) != 0;
                if (positive) sum += input[c];
                else sum -= input[c];
            }
            output[d] = sum;
        }
    }

    @Override
    public void adjoint(double[] input, double[] output) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        if (input == output) {
            throw new IllegalArgumentException("Adjoint input and output must not alias");
        }
        if (input.length != outputDimensions || output.length != inputDimensions) {
            throw new IllegalArgumentException("Adjoint buffers do not match projection dimensions");
        }
        for (double value : input) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Projection input must be finite");
            }
        }
        Arrays.fill(output, 0.0);
        for (int d = 0; d < outputDimensions; d++) {
            double value = input[d];
            int rowOffset = d * wordsPerRow;
            for (int c = 0; c < inputDimensions; c++) {
                boolean positive = (positiveSigns[rowOffset + c / Long.SIZE] & (1L << (c % Long.SIZE))) != 0;
                if (positive) output[c] += value;
                else output[c] -= value;
            }
        }
    }
}
