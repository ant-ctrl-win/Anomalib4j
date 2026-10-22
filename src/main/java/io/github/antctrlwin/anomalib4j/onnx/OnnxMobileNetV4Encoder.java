package io.github.antctrlwin.anomalib4j.onnx;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import io.github.antctrlwin.anomalib4j.model.GridShape;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public final class OnnxMobileNetV4Encoder implements AutoCloseable {
    public enum Variant {
        SPATIAL_14(EncoderSpec.SMALL_SPATIAL_14),
        SPATIAL_28("/models/mobilenetv4_spatial_28x28.onnx", new GridShape(28, 28, 64)),
        LARGE_SPATIAL_14(EncoderSpec.LARGE_SPATIAL_14);

        private final String resourcePath;
        private final GridShape grid;
        private final EncoderSpec spec;

        Variant(EncoderSpec spec) {
            this.resourcePath = spec.resourcePath(); this.grid = spec.grid(); this.spec = spec;
        }

        Variant(String resourcePath, GridShape grid) {
            this.resourcePath = resourcePath;
            this.grid = grid;
            this.spec = null;
        }
        private String inputName() { return spec == null ? INPUT_NAME : spec.inputName(); }
        private String outputName() { return spec == null ? OUTPUT_NAME : spec.outputName(); }
    }

    private static final String INPUT_NAME = "input_image";
    private static final String OUTPUT_NAME = "spatial_features";
    private static final long[] INPUT_SHAPE = {1, 3, 224, 224};

    private final Variant variant;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private boolean closed;

    public OnnxMobileNetV4Encoder() throws IOException, OrtException {
        this(Variant.SPATIAL_14);
    }

    public OnnxMobileNetV4Encoder(EncoderSpec spec) throws IOException, OrtException {
        this(switch (Objects.requireNonNull(spec)) {
            case SMALL_SPATIAL_14 -> Variant.SPATIAL_14;
            case LARGE_SPATIAL_14 -> Variant.LARGE_SPATIAL_14;
        });
    }

    public OnnxMobileNetV4Encoder(Variant variant) throws IOException, OrtException {
        this.variant = Objects.requireNonNull(variant, "variant");
        // ORT owns this JVM-wide environment; each encoder owns only its session.
        environment = OrtEnvironment.getEnvironment();
        session = openSession(environment, variant);
    }

    public GridShape grid() {
        return variant.grid;
    }

    public Variant variant() {
        return variant;
    }

    public String modelId() {
        return variant.spec == null ? EncoderSpec.SMALL_SPATIAL_14.modelId() : variant.spec.modelId();
    }

    public String preprocessingId() {
        return "imagenet-rgb-resize224-bicubic-v1";
    }

    /** Returns independent contiguous HWC features; calls and close are serialized per encoder. */
    public synchronized float[] extract(BufferedImage image) throws OrtException {
        requireOpen();
        return extractInput(ImageNetPreprocessor.preprocess(image));
    }

    /** Prepared NCHW float32 input is borrowed only until this synchronized call returns. */
    public synchronized float[] extractPrepared(float[] nchw) throws OrtException {
        requireOpen();
        return extractInput(nchw);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Encoder is closed");
        }
    }

    private float[] extractInput(float[] nchw) throws OrtException {
        Objects.requireNonNull(nchw, "nchw");
        if (nchw.length != 3*224*224) throw new IllegalArgumentException("Expected prepared NCHW [1,3,224,224]");
        for (float value : nchw) if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite prepared input");
        try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(nchw), INPUT_SHAPE);
             OrtSession.Result result = session.run(Map.of(variant.inputName(), input))) {
            var value = result.get(variant.outputName())
                    .orElseThrow(() -> new IllegalStateException("Missing spatial_features output"));
            if (!(value instanceof OnnxTensor output)) {
                throw new IllegalStateException("Spatial features must be a tensor");
            }
            requireTensor(output.getInfo(), outputShape(variant), variant.outputName());
            FloatBuffer data = output.getFloatBuffer();
            if (data == null || data.remaining() != grid().elements()) {
                throw new IllegalStateException("Unexpected spatial feature buffer size");
            }
            float[] features = new float[grid().elements()];
            data.get(features);
            for (float feature : features) {
                if (!Float.isFinite(feature)) {
                    throw new IllegalStateException("Encoder produced non-finite features");
                }
            }
            return features;
        }
    }

    @Override
    public synchronized void close() throws OrtException {
        if (!closed) {
            closed = true;
            session.close();
        }
    }

    private static OrtSession openSession(OrtEnvironment environment, Variant variant) throws IOException, OrtException {
        byte[] model;
        try (InputStream stream = OnnxMobileNetV4Encoder.class.getResourceAsStream(variant.resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing classpath model: " + variant.resourcePath);
            }
            model = variant.spec == null ? stream.readAllBytes() : variant.spec.verifiedBytes();
        }
        OrtSession created = null;
        try (var options = new OrtSession.SessionOptions()) {
            created = environment.createSession(model, options);
            Map<String, NodeInfo> inputs = created.getInputInfo();
            Map<String, NodeInfo> outputs = created.getOutputInfo();
            if (inputs.size() != 1 || outputs.size() != 1
                    || !inputs.containsKey(variant.inputName()) || !outputs.containsKey(variant.outputName())) {
                throw new IllegalArgumentException("Unexpected MobileNetV4 input/output names");
            }
            requireTensor(inputs.get(variant.inputName()).getInfo(), INPUT_SHAPE, variant.inputName());
            requireTensor(outputs.get(variant.outputName()).getInfo(), outputShape(variant), variant.outputName());
        } catch (OrtException | RuntimeException | Error failure) {
            if (created != null) {
                try {
                    created.close();
                } catch (OrtException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
        return created;
    }

    private static long[] outputShape(Variant variant) {
        return new long[]{1, variant.grid.height(), variant.grid.width(), variant.grid.channels()};
    }

    private static void requireTensor(Object info, long[] shape, String name) {
        if (!(info instanceof TensorInfo tensor) || tensor.type != OnnxJavaType.FLOAT
                || !Arrays.equals(tensor.getShape(), shape)) {
            throw new IllegalArgumentException("Unexpected FLOAT tensor contract for " + name);
        }
    }
}
