package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScrewM2QualificationTest {
    @TempDir Path temporary;
    private static M2Model model() {
        double[] mean=new double[18816],std=new double[18816],alpha=new double[196],beta=new double[196];
        Arrays.fill(std,1);Arrays.fill(beta,2);mean[31]=.125;alpha[42]=-.75;
        return new M2Model("a".repeat(64),new M2Model.Local(mean,std),new M2Model.Calibration(alpha,beta));
    }
    private static BufferedImage image() {
        var image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{g.setColor(Color.WHITE);g.fillRect(0,0,1024,1024);g.setColor(Color.DARK_GRAY);
            g.fillRect(180,400,150,224);g.fillRect(300,480,560,64);
        } finally {g.dispose();}return image;
    }
    @Test void exactScrewContractIsDistinctAndBundleReloadIsLossless() throws Exception {
        assertNotEquals(CanonicalM2Contract.PREPROCESSING,ScrewQualificationBundle.PREPROCESSING);
        assertEquals(395.50859765961616,ScrewQualificationInput.FRAMING.targetX());
        assertEquals(349.2832413193618,ScrewQualificationInput.FRAMING.targetY());
        Path path=temporary.resolve("screw.bundle");ScrewQualificationBundle.write(path,model());
        var loaded=ScrewQualificationBundle.read(path,"a".repeat(64));assertArrayEquals(ScrewQualificationBundle.payload(model()),ScrewQualificationBundle.payload(loaded));
        assertEquals(NpyMap.sha256(path),ScrewQualificationBundle.hash(ScrewQualificationBundle.encode(model(),ScrewQualificationBundle.fields())));
        assertThrows(FileAlreadyExistsException.class,()->ScrewQualificationBundle.write(path,model()));
        assertThrows(IOException.class,()->CanonicalM2BundleStore.read(path,"a".repeat(64)));
        Path metal=temporary.resolve("metal.bundle");CanonicalM2BundleStore.write(metal,new CanonicalM2Bundle(model()));
        assertThrows(IOException.class,()->ScrewQualificationBundle.read(metal,"a".repeat(64)));
    }
    @Test void everyContractMismatchAndWrongEncoderAreRejected() throws Exception {
        int at=0;
        for(String key:ScrewQualificationBundle.fields().keySet()) {
            var bad=new TreeMap<>(ScrewQualificationBundle.fields());bad.put(key,bad.get(key)+"-mismatch");
            Path path=temporary.resolve("mismatch-"+at++);Files.write(path,ScrewQualificationBundle.encode(model(),bad));
            assertThrows(IOException.class,()->ScrewQualificationBundle.read(path,"a".repeat(64)),key);
        }
        Path path=temporary.resolve("encoder");ScrewQualificationBundle.write(path,model());
        assertThrows(IOException.class,()->ScrewQualificationBundle.read(path,"b".repeat(64)));
    }
    @Test void corruptionTruncationTrailingAndUnknownFormatAreRejected() throws Exception {
        byte[] original=ScrewQualificationBundle.encode(model(),ScrewQualificationBundle.fields());
        byte[][] invalid={original.clone(),Arrays.copyOf(original,original.length-1),Arrays.copyOf(original,original.length+1),original.clone(),new byte[327681]};
        invalid[0][original.length-1]^=1;invalid[3][2]^=1;
        for(int i=0;i<invalid.length;i++) {Path p=temporary.resolve("invalid-"+i);Files.write(p,invalid[i]);assertThrows(IOException.class,()->ScrewQualificationBundle.read(p,"a".repeat(64)));}
    }
    private static byte[] adversarial(byte[] payload,boolean duplicate) throws Exception {
        var bytes=new ByteArrayOutputStream();var contract=ScrewQualificationBundle.fields();
        try(var out=new DataOutputStream(bytes)) {
            out.writeUTF(ScrewQualificationBundle.FORMAT);out.writeInt(contract.size());String first=contract.keySet().iterator().next();
            for(var e:contract.entrySet()){out.writeUTF(duplicate?first:e.getKey());out.writeUTF(e.getValue());}
            out.writeUTF("a".repeat(64));out.writeUTF(ScrewQualificationBundle.hash(payload));out.writeInt(payload.length);out.write(payload);
        }
        return bytes.toByteArray();
    }
    @Test void correctlyHashedButInvalidNumericalStateAndDuplicateFieldsAreRejected() throws Exception {
        byte[] payload=ScrewQualificationBundle.payload(model());ByteBuffer.wrap(payload).putDouble(18816*8,0);
        Path badScale=temporary.resolve("bad-scale");Files.write(badScale,adversarial(payload,false));
        assertThrows(IOException.class,()->ScrewQualificationBundle.read(badScale,"a".repeat(64)));
        payload=ScrewQualificationBundle.payload(model());ByteBuffer.wrap(payload).putDouble(0,Double.NaN);
        Path badNumber=temporary.resolve("nan");Files.write(badNumber,adversarial(payload,false));
        assertThrows(IOException.class,()->ScrewQualificationBundle.read(badNumber,"a".repeat(64)));
        Path duplicate=temporary.resolve("duplicate");Files.write(duplicate,adversarial(ScrewQualificationBundle.payload(model()),true));
        assertThrows(IOException.class,()->ScrewQualificationBundle.read(duplicate,"a".repeat(64)));
    }
    @Test void nativeInputUsesTheUnchangedAuditedPoseAndBExactly() throws Exception {
        var image=image();float[] tensor=new float[AffineTensorSampler.ELEMENTS],expected=tensor.clone();
        var actual=new ScrewQualificationInput().prepare(image,"fixture",tensor);
        int[] rgb=GeneralityImages.view(image);var pose=new GeneralityGeometry().estimate(rgb,"screw").get(GeneralityGeometry.Strategy.AXIS_WITH_POLARITY);
        assertEquals(GeneralityGeometry.State.OK,pose.state());assertTrue(actual.usable());assertEquals(pose,actual.pose());
        var transform=ScrewQualificationInput.FRAMING.rigid(pose);new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR).sample(rgb,transform,expected);
        assertArrayEquals(expected,tensor);assertEquals(transform,actual.transform());
        assertThrows(IllegalArgumentException.class,()->new ScrewQualificationInput().prepare(new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB),"bad",tensor));
    }
    @Test void failedInputCannotProduceAReplacementTensorOrFallbackTransform() throws Exception {
        var input=new ScrewQualificationInput();float[] tensor=new float[AffineTensorSampler.ELEMENTS];input.prepare(image(),"valid",tensor);float[] old=tensor.clone();
        var empty=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);var g=empty.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1024,1024);g.dispose();
        var failed=input.prepare(empty,"failed",tensor);assertFalse(failed.usable());assertNull(failed.transform());assertNull(failed.support());assertEquals("NOT_AVAILABLE",failed.framingState());assertArrayEquals(old,tensor);
        assertTrue(input.prepare(image(),"valid-again",tensor).usable());
    }
    @Test void evaluationCompositionRetainsExisting700BehaviorAndOriginalCoordinateRamp() {
        double[] z=new double[196];for(int y=0;y<14;y++)for(int x=0;x<14;x++)z[y*14+x]=x+10*y;
        var transform=new RigidTransform(1,0,20,-10);double[] a={1,0,20,0,1,-10};
        var view=CanonicalPilotBridge.backproject(LocalizationMaps.upsample(z,14,14,700,700),700,a);
        assertArrayEquals(view,ScrewQualificationInput.evaluatedMap(z,transform,700));
        var original=ScrewQualificationInput.evaluatedMap(z,transform,1024);assertEquals(1024*1024,original.length);
        double x=(512.5)*700/1024-.5+20,y=(512.5)*700/1024-.5-10;
        double expected=((x+.5)*14/700-.5)+10*((y+.5)*14/700-.5);assertEquals(expected,original[512*1024+512],1e-12);
    }
    @Test void coverageRetainsUnscoredFailuresAndTheirDefectGroups() {
        var pose=new GeneralityGeometry.Pose(GeneralityGeometry.State.SEGMENTATION_FAILURE,0,0,0,0,0,0,0,0,true,0);
        var failed=new ScrewQualificationInput.Prepared(pose,null,null);
        var row=new ScrewM2Qualification.Observation("screw_thread_top_000.png","test","anomaly","thread_top",failed,null);
        var coverage=ScrewM2Qualification.coverage(List.of(row));assertEquals(1,coverage.get("attempted"));assertEquals(0,coverage.get("usable"));assertEquals(1,coverage.get("failed"));
        assertEquals("SURVIVOR_ONLY",coverage.get("metric_scope"));assertEquals("SOURCE_BORDER",row.preprocessing().reason());assertNull(row.score());
        assertEquals(1,((java.util.Map<?,?>)((java.util.Map<?,?>)coverage.get("by_defect")).get("thread_top")).get("failed"));
    }
    @Test void scoringAndMetricsStagesCannotPrecedeTrainingFreeze() {
        for(String stage:List.of("train","test","evaluate","diagnose"))assertThrows(IllegalStateException.class,()->ScrewM2Qualification.main(new String[]{stage,temporary.toString()}));
    }
}
