package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import java.awt.image.BufferedImage;

final class ScrewQualificationInput {
    static final String FRAMING_SHA="e4af7c946a0ac980a12936541e2825b262e8ff6082b52690613a14b214ee6928";
    static final ScrewFraming.Contract FRAMING=new ScrewFraming.Contract("SCREW_FROZEN",1,395.50859765961616,349.2832413193618,4);
    private final GeneralityGeometry estimator=new GeneralityGeometry();
    private final AffineTensorSampler sampler=new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);
    record Prepared(GeneralityGeometry.Pose pose,ScrewFraming.Measurement support,RigidTransform transform) {
        boolean usable(){return pose.state()==GeneralityGeometry.State.OK;}
        String reason(){return usable()?"OK":pose.border()?"SOURCE_BORDER":pose.state().name();}
        String framingState(){return !usable()?"NOT_AVAILABLE":support.supportOutside()>0?"CLIPPED":support.clearance()<4?"MARGIN_VIOLATION":"SAFE";}
    }
    Prepared prepare(BufferedImage image,String name,float[] tensor) throws Exception {
        if(image==null||image.getWidth()!=1024||image.getHeight()!=1024)throw new IllegalArgumentException("Screw qualification requires native1024x1024");
        if(tensor.length!=AffineTensorSampler.ELEMENTS)throw new IllegalArgumentException("Expected NCHW224 destination");
        int[] rgb=GeneralityImages.view(image);var pose=estimator.estimate(rgb,"screw").get(GeneralityGeometry.Strategy.AXIS_WITH_POLARITY);
        if(pose.state()!=GeneralityGeometry.State.OK)return new Prepared(pose,null,null);
        var field=GeneralityGeometry.class.getDeclaredField("mask");field.setAccessible(true);
        var shape=ScrewFraming.shape(name,pose,((boolean[])field.get(estimator)).clone());
        var measurement=ScrewFraming.measure(shape,FRAMING);var transform=FRAMING.rigid(pose);
        sampler.sample(rgb,transform,tensor);return new Prepared(pose,measurement,transform);
    }
    static double[] evaluatedMap(double[] z,RigidTransform transform,int size) {
        if(size<=0)throw new IllegalArgumentException("Positive evaluation size required");
        double[] a={transform.cos(),-transform.sin(),transform.tx(),transform.sin(),transform.cos(),transform.ty()};
        double[] view=CanonicalPilotBridge.backproject(LocalizationMaps.upsample(z,14,14,700,700),700,a);
        return size==700?view:LocalizationMaps.upsample(view,700,700,size,size);
    }
}
