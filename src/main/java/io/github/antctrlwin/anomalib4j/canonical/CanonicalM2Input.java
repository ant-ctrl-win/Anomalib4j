package io.github.antctrlwin.anomalib4j.canonical;

import ai.onnxruntime.OrtException;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Shared sequential FIT/CAL/inference boundary; owns RGB, geometry and tensor workspaces. */
final class CanonicalM2Input implements AutoCloseable {
    private final CanonicalPreprocessor preprocessor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
    private final float[] tensor = new float[AffineTensorSampler.ELEMENTS];
    private final OnnxMobileNetV4Encoder encoder = new OnnxMobileNetV4Encoder();
    private boolean closed;

    CanonicalM2Input() throws IOException, OrtException {
        if (!preprocessor.preprocessingId().equals(CanonicalM2Contract.PREPROCESSING)) {
            encoder.close();
            throw new IllegalStateException("Canonical B implementation/contract mismatch");
        }
    }
    record Encoded(float[] features, CanonicalGeometry geometry) { }
    Encoded extract(BufferedImage image) throws OrtException {
        if (closed) throw new IllegalStateException("Canonical input is closed");
        var geometry = preprocessor.prepare(image, tensor);
        if (geometry.status() != CanonicalGeometry.Status.SUCCESS) throw new CanonicalGeometryException(geometry);
        return new Encoded(encoder.extractPrepared(tensor), geometry);
    }
    @Override public void close() throws OrtException {
        if (!closed) { closed = true; encoder.close(); }
    }
}
