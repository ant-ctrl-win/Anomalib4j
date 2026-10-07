package io.github.antctrlwin.anomalib4j.onnx;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import io.github.antctrlwin.anomalib4j.evaluation.PerformanceBaseline;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
public class EncoderMicroprofile {
    private static final long[] INPUT_SHAPE = {1, 3, 224, 224};
    private static final long[] OUTPUT_SHAPE = {1, 14, 14, 96};
    private static final int ELEMENTS = 14 * 14 * 96;

    @State(Scope.Thread)
    public static class OnnxState extends PerformanceBaseline.ImageState {
        public OrtEnvironment environment;
        public float[] nchw;
        public OrtSession session;
        public OnnxTensor input;
        public Map<String, OnnxTensor> inputs;
        public OrtSession.Result result;

        @Setup(Level.Trial)
        public void prepareOnnx() throws Exception {
            environment = OrtEnvironment.getEnvironment();
            nchw = ImageNetPreprocessor.preprocess(image);
            byte[] model;
            try (var stream = OnnxMobileNetV4Encoder.class.getResourceAsStream("/models/mobilenetv4_spatial_14x14.onnx")) {
                if (stream == null) throw new IOException("Missing SPATIAL_14 ONNX resource");
                model = stream.readAllBytes();
            }
            try (var options = new OrtSession.SessionOptions()) {
                session = environment.createSession(model, options);
            }
            try {
                input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(nchw), INPUT_SHAPE);
                inputs = Map.of("input_image", input);
                result = session.run(inputs);
                copyOutput(result);
            } catch (Exception failure) {
                try { closeOnnx(); } catch (Exception closeFailure) { failure.addSuppressed(closeFailure); }
                result = null;
                input = null;
                session = null;
                throw failure;
            }
        }

        @TearDown(Level.Trial)
        public void closeOnnx() throws Exception {
            try { if (result != null) result.close(); }
            finally {
                try { if (input != null) input.close(); }
                finally { if (session != null) session.close(); }
            }
        }
    }

    @Benchmark
    public void preprocessing(PerformanceBaseline.ImageState state, Blackhole bh) {
        bh.consume(ImageNetPreprocessor.preprocess(state.image));
    }

    @Benchmark
    public void tensorCreateAndClose(OnnxState state, Blackhole bh) throws Exception {
        try (var tensor = OnnxTensor.createTensor(state.environment, FloatBuffer.wrap(state.nchw), INPUT_SHAPE)) {
            bh.consume(tensor);
        }
    }

    @Benchmark
    public void sessionRunAndClose(OnnxState state, Blackhole bh) throws Exception {
        try (var result = state.session.run(state.inputs)) {
            bh.consume(result);
        }
    }

    @Benchmark
    public void outputExtractionCopy(OnnxState state, Blackhole bh) {
        bh.consume(copyOutput(state.result));
    }

    @Benchmark
    public void fullExtract(PerformanceBaseline.EncoderState state, Blackhole bh) throws Exception {
        bh.consume(state.encoder.extract(state.image));
    }

    static float[] copyOutput(OrtSession.Result result) {
        var value = result.get("spatial_features")
                .orElseThrow(() -> new IllegalStateException("Missing spatial_features output"));
        if (!(value instanceof OnnxTensor output)) throw new IllegalStateException("Spatial features must be a tensor");
        var info = output.getInfo();
        if (info.type != OnnxJavaType.FLOAT || !Arrays.equals(info.getShape(), OUTPUT_SHAPE)) {
            throw new IllegalStateException("Unexpected SPATIAL_14 output contract");
        }
        FloatBuffer data = output.getFloatBuffer();
        if (data == null || data.remaining() != ELEMENTS) throw new IllegalStateException("Unexpected output buffer size");
        float[] features = new float[ELEMENTS];
        data.get(features);
        for (float feature : features) {
            if (!Float.isFinite(feature)) throw new IllegalStateException("Nonfinite feature");
        }
        return features;
    }
}
