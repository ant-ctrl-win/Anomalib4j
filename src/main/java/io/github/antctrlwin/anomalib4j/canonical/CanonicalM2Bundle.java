package io.github.antctrlwin.anomalib4j.canonical;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import java.util.Objects;

public final class CanonicalM2Bundle {
    private final M2Model model;
    private final String identity;

    public CanonicalM2Bundle(M2Model model) {
        this.model = Objects.requireNonNull(model);
        if (model.encoderSpec() != io.github.antctrlwin.anomalib4j.onnx.EncoderSpec.SMALL_SPATIAL_14)
            throw new IllegalArgumentException("Canonical Metal Nut is qualified only with Small");
        identity = CanonicalM2BundleStore.identity(model);
    }
    public M2Model model() { return model; }
    public String identity() { return identity; }
    public String preprocessingId() { return CanonicalM2Contract.PREPROCESSING; }
}
