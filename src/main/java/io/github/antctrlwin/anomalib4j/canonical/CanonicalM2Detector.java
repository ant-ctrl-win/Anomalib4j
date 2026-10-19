package io.github.antctrlwin.anomalib4j.canonical;

import ai.onnxruntime.OrtException;
import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.m2.M2ModelStore;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/** Sequential owner: use separate instances for concurrent callers, or externally serialize calls/close. */
public final class CanonicalM2Detector implements AutoCloseable {
    private final CanonicalM2Bundle bundle;
    private final CanonicalM2Input input;

    public CanonicalM2Detector(CanonicalM2Bundle bundle) throws IOException, OrtException {
        this.bundle = Objects.requireNonNull(bundle);
        if (!bundle.model().encoderSha256().equals(M2ModelStore.encoderHash())) throw new IOException("Canonical encoder mismatch");
        input = new CanonicalM2Input();
    }
    public double detectScore(BufferedImage image) throws OrtException {
        return M2Model.imageScore(bundle.model().score(input.extract(image).features()));
    }
    public CanonicalInference inferWithMap(BufferedImage image) throws OrtException {
        var encoded = input.extract(image);
        double[] map = bundle.model().score(encoded.features());
        return new CanonicalInference(M2Model.imageScore(map), map, encoded.geometry().transform(), bundle.identity());
    }
    @Override public void close() throws OrtException { input.close(); }
}
