package io.github.antctrlwin.anomalib4j.onnx;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import io.github.antctrlwin.anomalib4j.evaluation.PerformanceBaseline;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
public class PreprocessingMicroprofile {
    private static final int SIZE = ImageNetPreprocessor.INPUT_SIZE;

    @State(Scope.Thread)
    public static class Stages extends PerformanceBaseline.ImageState {
        public int[] sourcePixels;
        public BufferedImage rgb;
        public BufferedImage resized;
        public int[] pixels;

        @Setup(Level.Trial)
        public void prepareStages() {
            sourcePixels = readRgb(image);
            rgb = materializeRgb(sourcePixels, image.getWidth(), image.getHeight());
            resized = resizeRgb(rgb);
            pixels = readRgb(resized);
            if (!Arrays.equals(ImageNetPreprocessor.preprocess(image), normalizedNchw(pixels))) {
                throw new IllegalStateException("Benchmark stages differ from production preprocessing");
            }
        }

        @TearDown(Level.Trial)
        public void releaseStages() {
            if (resized != null) resized.flush();
            if (rgb != null) rgb.flush();
        }
    }

    @Benchmark
    public void sourceRgbExtraction(PerformanceBaseline.ImageState state, Blackhole bh) {
        bh.consume(readRgb(state.image));
    }

    @Benchmark
    public void rgbImageMaterialization(Stages state, Blackhole bh) {
        bh.consume(materializeRgb(state.sourcePixels, state.image.getWidth(), state.image.getHeight()));
    }

    @Benchmark
    public void bicubicResize(Stages state, Blackhole bh) {
        bh.consume(resizeRgb(state.rgb));
    }

    @Benchmark
    public void resizedRgbExtraction(Stages state, Blackhole bh) {
        bh.consume(readRgb(state.resized));
    }

    @Benchmark
    public void scalingNormalizationNchw(Stages state, Blackhole bh) {
        bh.consume(normalizedNchw(state.pixels));
    }

    @Benchmark
    public void fullPreprocessing(PerformanceBaseline.ImageState state, Blackhole bh) {
        bh.consume(ImageNetPreprocessor.preprocess(state.image));
    }

    static int[] readRgb(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        return image.getRGB(0, 0, width, height, null, 0, width);
    }

    static BufferedImage materializeRgb(int[] sourcePixels, int width, int height) {
        var rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, width, height, sourcePixels, 0, width);
        return rgb;
    }

    static BufferedImage resizeRgb(BufferedImage rgb) {
        var resized = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(rgb, 0, 0, SIZE, SIZE, null);
        } finally { graphics.dispose(); }
        return resized;
    }

    static float[] normalizedNchw(int[] pixels) {
        int plane = SIZE * SIZE;
        float[] nchw = new float[3 * plane];
        for (int p = 0; p < plane; p++) {
            int pixel = pixels[p];
            nchw[p] = (float) ((((pixel >>> 16) & 255) / 255.0 - 0.485) / 0.229);
            nchw[plane + p] = (float) ((((pixel >>> 8) & 255) / 255.0 - 0.456) / 0.224);
            nchw[2 * plane + p] = (float) (((pixel & 255) / 255.0 - 0.406) / 0.225);
        }
        return nchw;
    }
}
