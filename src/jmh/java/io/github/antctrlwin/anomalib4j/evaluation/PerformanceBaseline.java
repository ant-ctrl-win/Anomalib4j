package io.github.antctrlwin.anomalib4j.evaluation;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.BenchmarkPreprocessing;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
@Timeout(time = 2, timeUnit = TimeUnit.HOURS)
public class PerformanceBaseline {
    @State(Scope.Thread)
    public static class ImageState {
        @Param({"../Anomalib4j_md/mvtec-ad-DatasetNinja"})
        public String dataset;
        public BufferedImage image;

        @Setup(Level.Trial)
        public void loadImage() throws IOException {
            image = read(Path.of(dataset).resolve("test/img/bottle_good_000.png"));
            if (image.getWidth() != 900 || image.getHeight() != 900) {
                throw new IllegalStateException("Baseline requires the original 900x900 Bottle image");
            }
        }

        @TearDown(Level.Trial)
        public void releaseImage() { if (image != null) image.flush(); }
    }

    @State(Scope.Thread)
    public static class EncoderState extends ImageState {
        public OnnxMobileNetV4Encoder encoder;

        @Setup(Level.Trial)
        public void openEncoder() throws Exception {
            encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14);
        }

        @TearDown(Level.Trial)
        public void closeEncoder() throws Exception { if (encoder != null) encoder.close(); }
    }

    @State(Scope.Thread)
    public static class ModelState extends EncoderState {
        public AdjointFilterBank filters;
        public PositionalRawCalibration calibration;
        public float[] features;
        public double[] raw;
        public double[] z;

        @Setup(Level.Trial)
        public void buildModel() throws Exception {
            var split = HeldOutBottleCalibration.split(NormalImageTrainer.discoverBottleImages(Path.of(dataset)), 42L);
            var descriptor = new ModelDescriptor("bottle-spatial14-heldout-167-v1", encoder.modelId(),
                    encoder.variant().name(), encoder.grid(), 10_000, 42L,
                    new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(96, 10_000, 42L);
            filters = new NormalImageTrainer(encoder, descriptor, projection, 1e-8)
                    .train(split.archetypeTraining()).filters();
            var calibrationRaw = new ArrayList<double[]>();
            for (Path path : split.calibration()) {
                BufferedImage calibrationImage = read(path);
                try {
                    calibrationRaw.add(HeldOutBottleCalibration.rawScores(filters, encoder.extract(calibrationImage)));
                } finally { calibrationImage.flush(); }
            }
            calibration = PositionalRawCalibration.fit(descriptor, calibrationRaw, 1e-6);
            calibration.requireCompatible(filters.descriptor());
            features = encoder.extract(image);
            raw = HeldOutBottleCalibration.rawScores(filters, features);
            z = calibration.calibrate(raw);
        }
    }

    @Benchmark
    public void preprocessing(ImageState state, Blackhole bh) {
        bh.consume(BenchmarkPreprocessing.preprocess(state.image));
    }

    @Benchmark
    public void encoderExtract(EncoderState state, Blackhole bh) throws Exception {
        bh.consume(state.encoder.extract(state.image));
    }

    @Benchmark
    public void compiledAdjoint(ModelState state, Blackhole bh) {
        bh.consume(HeldOutBottleCalibration.rawScores(state.filters, state.features));
    }

    @Benchmark
    public void positionalZ(ModelState state, Blackhole bh) {
        bh.consume(state.calibration.calibrate(state.raw));
    }

    @Benchmark
    public void bilinearUpsampling(ModelState state, Blackhole bh) {
        bh.consume(LocalizationMaps.upsample(state.z, 14, 14, 900, 900));
    }

    @Benchmark
    public void detectionInference(ModelState state, Blackhole bh) throws Exception {
        var scores = HeldOutBottleCalibration.score(state.filters, state.encoder.extract(state.image), state.calibration);
        bh.consume(BottleEvaluation.summarize(scores.z()).max());
        bh.consume(scores);
    }

    @Benchmark
    public void localizationInference(ModelState state, Blackhole bh) throws Exception {
        var scores = HeldOutBottleCalibration.score(state.filters, state.encoder.extract(state.image), state.calibration);
        bh.consume(BottleEvaluation.summarize(scores.z()).max());
        bh.consume(LocalizationMaps.upsample(scores.z(), 14, 14, state.image.getWidth(), state.image.getHeight()));
        bh.consume(scores);
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Cannot decode " + path);
        return image;
    }
}
