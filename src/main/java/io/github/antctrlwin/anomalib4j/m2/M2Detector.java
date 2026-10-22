package io.github.antctrlwin.anomalib4j.m2;

import ai.onnxruntime.OrtException;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Owns one reusable encoder session; model contains only inference state. */
public final class M2Detector implements AutoCloseable {
    private final M2Model model;
    private final OnnxMobileNetV4Encoder encoder;
    public M2Detector(M2Model model) throws IOException, OrtException {
        if (!model.encoderSha256().equals(M2ModelStore.encoderHash(model.encoderSpec()))) throw new IOException("Encoder identity mismatch");
        this.model = model;
        encoder = new OnnxMobileNetV4Encoder(model.encoderSpec());
    }
    public double[] nativeMap(BufferedImage image) throws OrtException { return model.score(encoder.extract(image)); }
    public double detect(BufferedImage image) throws OrtException { return M2Model.imageScore(nativeMap(image)); }
    @Override public void close() throws OrtException { encoder.close(); }
}
