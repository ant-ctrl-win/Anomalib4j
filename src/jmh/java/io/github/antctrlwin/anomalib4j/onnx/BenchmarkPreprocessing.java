package io.github.antctrlwin.anomalib4j.onnx;

import java.awt.image.BufferedImage;

/** Benchmark-only access to the existing package-private implementation. */
public final class BenchmarkPreprocessing {
    private BenchmarkPreprocessing() { }

    public static float[] preprocess(BufferedImage image) {
        return ImageNetPreprocessor.preprocess(image);
    }
}
