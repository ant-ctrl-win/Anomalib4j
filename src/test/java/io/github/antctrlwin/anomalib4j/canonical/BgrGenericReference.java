package io.github.antctrlwin.anomalib4j.canonical;

import ai.onnxruntime.OrtException;
import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/** Retains the pre-optimization generic acquisition and detector path only for tests/timing. */
public final class BgrGenericReference implements AutoCloseable {
    public static final class Input {
        public final int[] rgb=new int[700*700];
        public final MetalNutGeometryEstimator geometry=new MetalNutGeometryEstimator();
        private final AffineTensorSampler sampler=new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);
        public CanonicalGeometry prepare(BufferedImage image,float[] tensor) {
            Objects.requireNonNull(image);
            if(image.getWidth()!=700 || image.getHeight()!=700)throw new IllegalArgumentException("Expected source700");
            if(tensor.length!=AffineTensorSampler.ELEMENTS)throw new IllegalArgumentException("Expected NCHW224 destination");
            image.getRGB(0,0,700,700,rgb,0,700); var pose=geometry.estimate(rgb);
            if(pose.status()==CanonicalGeometry.Status.SUCCESS)sampler.sample(rgb,pose.transform(),tensor);
            return pose;
        }
    }
    private final Input input=new Input();
    private final float[] tensor=new float[AffineTensorSampler.ELEMENTS];
    private final OnnxMobileNetV4Encoder encoder=new OnnxMobileNetV4Encoder();
    private final M2Model model;
    private boolean closed;
    public BgrGenericReference(M2Model model) throws IOException,OrtException {this.model=Objects.requireNonNull(model);}
    private record Encoded(float[] features,CanonicalGeometry geometry) { }
    private Encoded extract(BufferedImage image) throws OrtException {
        if(closed)throw new IllegalStateException("Reference is closed");
        var pose=input.prepare(image,tensor);
        if(pose.status()!=CanonicalGeometry.Status.SUCCESS)throw new CanonicalGeometryException(pose);
        return new Encoded(encoder.extractPrepared(tensor),pose);
    }
    public double detectScore(BufferedImage image) throws OrtException {return M2Model.imageScore(model.score(extract(image).features()));}
    @Override public void close() throws OrtException {if(!closed){closed=true;encoder.close();}}
}
