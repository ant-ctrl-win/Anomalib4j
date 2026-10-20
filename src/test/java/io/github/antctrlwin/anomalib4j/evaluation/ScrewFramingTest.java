package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.antctrlwin.anomalib4j.evaluation.ScrewFraming.*;

class ScrewFramingTest {
    @TempDir Path temporary;
    private static GeneralityGeometry.Pose pose(double angle) {
        return new GeneralityGeometry.Pose(GeneralityGeometry.State.OK,349.5,349.5,angle,angle,.1,.9,.4,0,false,0);
    }
    @Test void fullCellSupportIncludesRotatedCorners() {
        boolean[] mask=new boolean[175*175];mask[87*175+87]=true;
        var shape=shape("one",pose(45),mask);double r=2*Math.sqrt(2);
        assertEquals(-r,shape.support().minX(),1e-12);assertEquals(r,shape.support().maxX(),1e-12);
        assertEquals(-r,shape.support().minY(),1e-12);assertEquals(r,shape.support().maxY(),1e-12);
        var m=measure(shape,new Contract("A",1,349.5,349.5,0));
        assertEquals(16.0/(700*700),m.occupancy());assertEquals(0,m.outside());assertEquals(0,m.supportOutside());
    }
    @Test void sideCountsPartitionOutsideCentersWithoutClippingExtents() {
        var points=List.of(new Point(-400,0),new Point(370,0),new Point(0,0));
        var shape=new Shape("ends",pose(0),points,2,new Bounds(-402,372,-2,2));
        var m=measure(shape,new Contract("A",1,349.5,349.5,0));
        assertEquals(-50.5,m.centers().minX());assertEquals(719.5,m.centers().maxX());
        assertEquals(2,m.outside());assertEquals(1,m.tipOutside());assertEquals(1,m.headOutside());assertEquals(2.0/3,m.outsideFraction());
        assertEquals(-52.5,m.clearance());
    }
    @Test void globalIntersectionCanFixAsymmetricExtentWithoutScaling() {
        var e=new Envelope(-390,270,-70,80,"tip","head","top","bottom");
        var p=placement(e,1,4);assertTrue(p.feasible());assertEquals(394,p.minTargetX());assertEquals(425,p.maxTargetX());
        var c=movable("C",e,1,4);assertEquals(409.5,c.targetX());assertEquals(344.5,c.targetY());
        assertEquals(1,movableScale(e,4));assertEquals(345.5/390,centeredScale(e,4));
    }
    @Test void globalEnvelopeUsesAllImagesAndDetectsInfeasibility() {
        var a=new Shape("left",pose(0),List.of(new Point(-410,0)),0,new Bounds(-410,100,-1,1));
        var b=new Shape("right",pose(0),List.of(new Point(310,0)),0,new Bounds(-100,310,-2,2));
        var e=envelope(List.of(a,b));assertEquals("left",e.minXImage());assertEquals("right",e.maxXImage());
        assertFalse(placement(e,1,4).feasible());assertThrows(IllegalArgumentException.class,()->movable("bad",e,1,4));
        double scale=movableScale(e,4);assertEquals(691.0/720,scale);
        var c=movable("D",e,scale,4);assertEquals(4,scale*e.minX()+c.targetX(),1e-12);
        assertEquals(695,scale*e.maxX()+c.targetX(),1e-12);
    }
    @Test void placementUsesProductionBAndIdentityPlacementIsBitExact() {
        int[] rgb=new int[700*700];var random=new Random(42);for(int i=0;i<rgb.length;i++)rgb[i]=random.nextInt();
        var sampler=new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);float[] a=new float[AffineTensorSampler.ELEMENTS],b=a.clone();
        var p=pose(32.5);sampler.sample(rgb,p.transform(),a);sampler.sample(rgb,new Contract("A",1,349.5,349.5,0).rigid(p),b);
        assertArrayEquals(a,b);var moved=new Contract("C",1,402,347,4).rigid(p);
        assertEquals(402,moved.canonicalX(p.cx(),p.cy()),1e-12);assertEquals(347,moved.canonicalY(p.cx(),p.cy()),1e-12);
        assertThrows(IllegalStateException.class,()->new Contract("B",.9,349.5,349.5,4).rigid(p));
    }
    @Test void failedPoseCannotAcquireAFramingTransform() {
        var p=new GeneralityGeometry.Pose(GeneralityGeometry.State.SEGMENTATION_FAILURE,1,1,0,0,.1,.9,.4,0,true,0);
        assertThrows(IllegalStateException.class,()->new Contract("C",1,400,350,4).rigid(p));
        assertThrows(IllegalArgumentException.class,()->new Contract("bad",0,350,350,4));
        assertThrows(IllegalArgumentException.class,()->new Contract("bad",1,Double.NaN,350,4));
    }
    @Test void quantilesAndSupportClippingAreNotCenterClipping() {
        var shape=new Shape("edge",pose(0),List.of(new Point(-349,0)),2,new Bounds(-351,-347,-2,2));
        var m=measure(shape,new Contract("A",1,349.5,349.5,0));assertEquals(0,m.outside());assertEquals(1,m.supportOutside());
        var d=distribution(new double[]{0,10,20});assertEquals(1,d.p05());assertEquals(10,d.p50());assertEquals(19,d.p95());
        var sum=summary(List.of(m));assertEquals(0L,sum.get("center_clipping_cases"));assertEquals(1L,sum.get("support_clipping_cases"));
    }
    @Test void laterStagesRequireThePriorEvidenceBeforeReadingAnyImages() {
        for(String stage:List.of("features","freeze","cal"))assertThrows(IllegalStateException.class,
                ()->ScrewFramingCheckpoint.main(new String[]{stage,temporary.toString()}));
    }
    @Test void immutableEvidenceGuardRejectsTampering() throws Exception {
        Path input=temporary.resolve("input.txt");Files.writeString(input,"frozen");
        ComparisonArtifacts.json(temporary.resolve("input-hashes.json"),Map.of(input.toString(),NpyMap.sha256(input)));
        ScrewFramingCheckpoint.verify(temporary);Files.writeString(input,"changed");
        assertThrows(IllegalStateException.class,()->ScrewFramingCheckpoint.verify(temporary));
    }
}
