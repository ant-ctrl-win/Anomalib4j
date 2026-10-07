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
        SPATIAL_14("/models/mobilenetv4_spatial_14x14.onnx", new GridShape(14, 14, 96)),
        SPATIAL_28("/models/mobilenetv4_spatial_28x28.onnx", new GridShape(28, 28, 64));

        private final String resourcePath;
        private final GridShape grid;

        Variant(String resourcePath, GridShape grid) {
            this.resourcePath = resourcePath;
            this.grid = grid;
        }
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
        return "mobilenetv4_conv_small.e2400_r224_in1k";
    }

    public String preprocessingId() {
        return "imagenet-rgb-resize224-bicubic-v1";
    }

    /** Returns independent contiguous HWC features; calls and close are serialized per encoder. */
    public synchronized float[] extract(BufferedImage image) throws OrtException {
        if (closed) {
            throw new IllegalStateException("Encoder is closed");
        }
        float[] nchw = ImageNetPreprocessor.preprocess(image);
        try (OnnxTensor input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(nchw), INPUT_SHAPE);
             OrtSession.Result result = session.run(Map.of(INPUT_NAME, input))) {
            var value = result.get(OUTPUT_NAME)
                    .orElseThrow(() -> new IllegalStateException("Missing spatial_features output"));
            if (!(value instanceof OnnxTensor output)) {
                throw new IllegalStateException("Spatial features must be a tensor");
            }
            requireTensor(output.getInfo(), outputShape(variant), OUTPUT_NAME);
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
            model = stream.readAllBytes();
        }
        OrtSession created = null;
        try (var options = new OrtSession.SessionOptions()) {
            created = environment.createSession(model, options);
            Map<String, NodeInfo> inputs = created.getInputInfo();
            Map<String, NodeInfo> outputs = created.getOutputInfo();
            if (inputs.size() != 1 || outputs.size() != 1
                    || !inputs.containsKey(INPUT_NAME) || !outputs.containsKey(OUTPUT_NAME)) {
                throw new IllegalArgumentException("Unexpected MobileNetV4 input/output names");
            }
            requireTensor(inputs.get(INPUT_NAME).getInfo(), INPUT_SHAPE, INPUT_NAME);
            requireTensor(outputs.get(OUTPUT_NAME).getInfo(), outputShape(variant), OUTPUT_NAME);
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
