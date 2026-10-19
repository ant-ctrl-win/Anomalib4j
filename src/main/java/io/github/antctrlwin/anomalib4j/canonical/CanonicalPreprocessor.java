package io.github.antctrlwin.anomalib4j.canonical;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** One sequential workspace. Destination tensors are supplied and owned by the caller. */
public final class CanonicalPreprocessor {
    private final int[] source = new int[700*700];
    private final MetalNutGeometryEstimator estimator = new MetalNutGeometryEstimator();
    private final AffineTensorSampler sampler;

    public CanonicalPreprocessor(AffineTensorSampler.Kernel kernel) { sampler = new AffineTensorSampler(kernel); }
    public String preprocessingId() { return "metal-nut-otsu175-h4-scale1-v1/"+sampler.preprocessingId(); }

    public CanonicalGeometry prepare(BufferedImage image, float[] destination) {
        Objects.requireNonNull(image);
        if (image.getWidth() != 700 || image.getHeight() != 700) throw new IllegalArgumentException("Expected source700");
        if (destination.length != AffineTensorSampler.ELEMENTS) throw new IllegalArgumentException("Expected NCHW224 destination");
        image.getRGB(0, 0, 700, 700, source, 0, 700);
        CanonicalGeometry pose = estimator.estimate(source);
        if (pose.status() == CanonicalGeometry.Status.SUCCESS) sampler.sample(source, pose.transform(), destination);
        return pose;
    }
}
