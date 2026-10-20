package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.antctrlwin.anomalib4j.evaluation.GeneralityGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class GeneralityAuditTest {
    private static BufferedImage screw() {
        var image=new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,700,700);g.setColor(java.awt.Color.DARK_GRAY);
            g.fillRect(140,250,80,200);g.fillRect(220,320,340,60);g.fillPolygon(new int[]{560,610,560},new int[]{320,350,380},3);
        }finally{g.dispose();}return image;
    }
    @Test void headMassAndGridAlignedTranslationAndRotationRecoverPolarity() {
        var geometry=new GeneralityGeometry();var image=screw();
        var a=geometry.estimate(GeneralityImages.view(image),"screw").get(Strategy.AXIS_WITH_POLARITY);
        assertEquals(State.OK,a.state());assertTrue(a.anisotropy()>.5);assertTrue(Math.abs(a.polarity())>.1);
        assertTrue(a.transform().canonicalX(180,350)>349.5);
        assertEquals(349.5,a.transform().canonicalX(a.cx(),a.cy()),1e-10);
        var changes=List.of(new GeneralityImages.Perturbation(-16.0/700,0,0),new GeneralityImages.Perturbation(16.0/700,0,0),
                new GeneralityImages.Perturbation(0,-16.0/700,0),new GeneralityImages.Perturbation(0,16.0/700,0),
                new GeneralityImages.Perturbation(0,0,-10),new GeneralityImages.Perturbation(0,0,-5),
                new GeneralityImages.Perturbation(0,0,5),new GeneralityImages.Perturbation(0,0,10));
        for(var change:changes) {
            var b=geometry.estimate(GeneralityImages.view(GeneralityImages.perturb(image,change)),"screw").get(Strategy.AXIS_WITH_POLARITY);
            assertEquals(State.OK,b.state());double[] expected=GeneralityImages.expectedCenter(a,change);
            assertTrue(Math.hypot(b.cx()-expected[0],b.cy()-expected[1])<2,()->change+" original="+a+" changed="+b+" expected="+Arrays.toString(expected));
            assertTrue(GeneralityImages.angleError(b.angle()-a.angle()-change.angle(),360)<1);
        }
    }
    @Test void unalignedTranslationRetainsTheObservedBoxGridPhaseError() {
        var estimator=new GeneralityGeometry();var image=screw();var change=new GeneralityImages.Perturbation(-.02,0,0);
        var a=estimator.estimate(GeneralityImages.view(image),"screw").get(Strategy.AXIS_WITH_POLARITY);
        var b=estimator.estimate(GeneralityImages.view(GeneralityImages.perturb(image,change)),"screw").get(Strategy.AXIS_WITH_POLARITY);
        double[] expected=GeneralityImages.expectedCenter(a,change);double error=Math.hypot(b.cx()-expected[0],b.cy()-expected[1]);
        assertEquals(State.OK,b.state());assertTrue(error>2&&error<4);
        assertTrue(GeneralityImages.angleError(b.angle()-a.angle(),360)<1);
    }
    @Test void weakAxisIsExplicitButCenterRemainsUsable() {
        var image=new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{g.setColor(java.awt.Color.WHITE);g.fillOval(200,204,300,292);}finally{g.dispose();}
        var estimator=new GeneralityGeometry();var a=estimator.estimate(GeneralityImages.view(image),"hazelnut");
        assertEquals(State.OK,a.get(Strategy.CENTER_ONLY).state());assertEquals(State.WEAK_AXIS,a.get(Strategy.AXIS).state());
        assertThrows(IllegalStateException.class,()->a.get(Strategy.AXIS).transform());
        assertEquals(a,estimator.estimate(GeneralityImages.view(image),"hazelnut"));
    }
    @Test void segmentationTiesChooseFirstAndBorderRemainsFailure() {
        int[] rgb=new int[700*700];Arrays.fill(rgb,0xffffff);
        for(int y=100;y<180;y++)for(int x=100;x<180;x++){rgb[y*700+x]=0;rgb[y*700+x+400]=0;}
        var estimator=new GeneralityGeometry();var pose=estimator.estimate(rgb,"bottle").get(Strategy.CENTER_ONLY);
        assertEquals(State.OK,pose.state());assertTrue(pose.cx()<200);
        Arrays.fill(rgb,0);assertEquals(State.SEGMENTATION_FAILURE,estimator.estimate(rgb,"bottle").get(Strategy.CENTER_ONLY).state());
        assertTrue(GeneralityGeometry.strategies("grid").isEmpty());
    }
    @Test void metalNutControlUsesUnchangedProductionPose() {
        int[] rgb=new int[700*700];for(int y=150;y<550;y++)Arrays.fill(rgb,y*700+150,y*700+550,0xffffff);
        var reference=new MetalNutGeometryEstimator().estimate(rgb);var actual=new GeneralityGeometry().estimate(rgb,"metal_nut").get(Strategy.H4);
        assertEquals(CanonicalGeometry.Status.SUCCESS,reference.status());assertEquals(State.OK,actual.state());
        assertEquals(reference.cx(),actual.cx());assertEquals(reference.cy(),actual.cy());assertEquals(reference.angle(),actual.angle());
        assertEquals(reference.transform(),actual.transform());
    }
    private static double[] direction(int channel,double sign) {
        double[] row=new double[18816];for(int p=0;p<196;p++)row[p*96+channel]=sign;return row;
    }
    @Test void truePairMedianDiffersFromMedianPositionMean() {
        var result=GeneralityCoherence.compute(new double[][]{direction(0,1),direction(1,1),direction(0,1)});
        assertEquals(1.0/3,result.pooledPairwiseCosine().mean(),1e-12);
        assertEquals(0,result.pooledPairwiseCosine().median());assertEquals(1.0/3,result.positionCosineMeans().median(),1e-12);
        assertEquals(0,result.adjacentFeatureContrast());assertEquals(0,result.looBestCellDrift());
    }
    @Test void sampleStdAndDegenerateTemplatesAreFinite() {
        var result=GeneralityCoherence.compute(new double[][]{direction(0,1),direction(0,-1)});
        assertEquals(-1,result.pooledPairwiseCosine().mean());assertEquals(Math.sqrt(2)/96,result.sampleStd().mean(),1e-12);
        assertEquals(2.0/96,result.positionVariance().mean(),1e-12);assertEquals(196L*95,result.lowStdCounts().get("1.0E-6"));
        assertThrows(IllegalArgumentException.class,()->GeneralityCoherence.compute(new double[][]{new double[18816],new double[18816]}));
        assertEquals(1.5,GeneralityCoherence.distribution(new double[]{0,1,2,3}).median());
    }
    @Test void experimentalViewsAndLegacySeamPreserveTheirDefinedInputs() throws Exception {
        var image=screw();assertArrayEquals(image.getRGB(0,0,700,700,null,0,700),GeneralityImages.view(image));
        var identity=GeneralityImages.perturb(image,new GeneralityImages.Perturbation(0,0,0));
        assertEquals(GeneralityImages.pixelHash(GeneralityImages.view(image)),GeneralityImages.pixelHash(GeneralityImages.view(identity)));
        assertThrows(IllegalArgumentException.class,()->GeneralityImages.view(new BufferedImage(699,700,1)));
        assertEquals(700*700,GeneralityImages.view(new BufferedImage(900,900,1)).length);
        try(var encoder=new OnnxMobileNetV4Encoder()) {
            assertArrayEquals(encoder.extract(image),encoder.extractPrepared(GeneralityImages.legacyTensor(image)));
        }
    }
    @Test void angularSymmetryAndPolarityBranchesRemainDistinct() {
        assertEquals(0,GeneralityImages.angleError(90,90));assertEquals(0,GeneralityImages.angleError(180,180));
        assertEquals(180,GeneralityImages.angleError(180,360));assertEquals(2,GeneralityImages.angleError(358,360));
    }
    @Test void grayscaleStorageAndIdentityPerturbationMustPreserveTheLogicalRgbView() {
        var gray=new BufferedImage(900,900,BufferedImage.TYPE_BYTE_GRAY);
        for(int y=0;y<900;y++)for(int x=0;x<900;x++)gray.getRaster().setSample(x,y,0,(3*x+5*y)%256);
        int[] pixels=gray.getRGB(0,0,900,900,null,0,900);
        var logical=new BufferedImage(900,900,BufferedImage.TYPE_INT_RGB);logical.setRGB(0,0,900,900,pixels,0,900);
        assertArrayEquals(pixels,logical.getRGB(0,0,900,900,null,0,900));
        assertArrayEquals(GeneralityImages.view(logical),GeneralityImages.view(gray));
        assertArrayEquals(GeneralityImages.view(gray),GeneralityImages.view(GeneralityImages.perturb(gray,new GeneralityImages.Perturbation(0,0,0))));
    }
}
