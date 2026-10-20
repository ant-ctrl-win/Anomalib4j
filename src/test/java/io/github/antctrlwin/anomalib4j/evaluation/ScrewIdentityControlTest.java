package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScrewIdentityControlTest {
    @TempDir Path temporary;
    private static M2Model model() {
        double[] mean=new double[18816],std=new double[18816],alpha=new double[196],beta=new double[196];Arrays.fill(std,1);Arrays.fill(beta,2);
        mean[37]=-.125;alpha[17]=.75;return new M2Model("a".repeat(64),new M2Model.Local(mean,std),new M2Model.Calibration(alpha,beta));
    }
    @Test void identityTensorMatchesAuditedDirectBAndHasNoCentroidPlacement() {
        assertEquals(new RigidTransform(1,0,0,0),ScrewIdentityControl.IDENTITY);
        assertEquals(0,ScrewIdentityControl.IDENTITY.canonicalX(0,0));assertEquals(699,ScrewIdentityControl.IDENTITY.canonicalY(699,699));
        var image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);var random=new Random(42);
        int[] rgb=new int[1024*1024];for(int i=0;i<rgb.length;i++)rgb[i]=random.nextInt();image.setRGB(0,0,1024,1024,rgb,0,1024);
        float[] actual=new float[AffineTensorSampler.ELEMENTS],expected=actual.clone();ScrewIdentityControl.tensor(image,actual);
        new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR).sample(GeneralityImages.view(image),new RigidTransform(1,0,0,0),expected);assertArrayEquals(expected,actual);
    }
    @Test void logicalGrayAndEquivalentRgbMatchAndUniformInputNeedsNoPose() {
        var gray=new BufferedImage(1024,1024,BufferedImage.TYPE_BYTE_GRAY);
        for(int y=0;y<1024;y++)for(int x=0;x<1024;x++)gray.getRaster().setSample(x,y,0,(x+y)%256);
        var rgb=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);rgb.setRGB(0,0,1024,1024,gray.getRGB(0,0,1024,1024,null,0,1024),0,1024);
        float[] a=new float[AffineTensorSampler.ELEMENTS],b=a.clone();ScrewIdentityControl.tensor(gray,a);ScrewIdentityControl.tensor(rgb,b);assertArrayEquals(a,b);
        var uniform=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);Arrays.fill(a,Float.NaN);ScrewIdentityControl.tensor(uniform,a);
        for(float v:a)assertTrue(Float.isFinite(v));assertThrows(IllegalArgumentException.class,()->ScrewIdentityControl.tensor(new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB),a));
    }
    @Test void frozenExclusionsCannotBeRestoredOrOrderChanged() {
        var calNumbers=new HashSet<Integer>();calNumbers.add(179);for(int i=256;i<319;i++)calNumbers.add(i);
        var fit=IntStream.range(0,320).filter(i->!calNumbers.contains(i)).mapToObj(i->String.format(Locale.ROOT,"screw_good_%03d.png",i)).toList();
        var usable=fit.stream().filter(n->!ScrewIdentityControl.exclusions("fit").contains(n)).toList();
        ScrewIdentityControl.requirePopulation("fit",fit,usable);assertEquals(253,usable.size());
        assertThrows(IllegalStateException.class,()->ScrewIdentityControl.requirePopulation("fit",fit,fit));
        var reversed=new ArrayList<>(usable);Collections.reverse(reversed);assertThrows(IllegalStateException.class,()->ScrewIdentityControl.requirePopulation("fit",fit,reversed));
        var cal=calNumbers.stream().sorted().map(i->String.format(Locale.ROOT,"screw_good_%03d.png",i)).toList();
        ScrewIdentityControl.requirePopulation("calibration",cal,cal.stream().filter(n->!ScrewIdentityControl.exclusions("calibration").contains(n)).toList());
        assertEquals(Set.of("screw_good_179.png"),ScrewIdentityControl.exclusions("calibration"));assertTrue(ScrewIdentityControl.exclusions("test").isEmpty());
    }
    @Test void identityBundleIsLosslessAndForeignLoadersRejectIt() throws Exception {
        Path identity=temporary.resolve("identity");ScrewIdentityBundle.write(identity,model());var reloaded=ScrewIdentityBundle.read(identity,"a".repeat(64));
        assertArrayEquals(ScrewQualificationBundle.payload(model()),ScrewQualificationBundle.payload(reloaded));
        assertThrows(FileAlreadyExistsException.class,()->ScrewIdentityBundle.write(identity,model()));
        assertThrows(IOException.class,()->ScrewQualificationBundle.read(identity,"a".repeat(64)));assertThrows(IOException.class,()->CanonicalM2BundleStore.read(identity,"a".repeat(64)));
        Path canonical=temporary.resolve("canonical");ScrewQualificationBundle.write(canonical,model());assertThrows(IOException.class,()->ScrewIdentityBundle.read(canonical,"a".repeat(64)));
        assertNotEquals(ScrewIdentityBundle.PREPROCESSING,ScrewQualificationBundle.PREPROCESSING);
    }
    @Test void everyIdentityFieldMismatchWrongEncoderAndCorruptionAreRejected() throws Exception {
        int i=0;for(String key:ScrewIdentityBundle.fields().keySet()) {
            var fields=new TreeMap<>(ScrewIdentityBundle.fields());fields.put(key,fields.get(key)+"-wrong");Path p=temporary.resolve("field-"+i++);Files.write(p,ScrewIdentityBundle.encode(model(),fields));
            assertThrows(IOException.class,()->ScrewIdentityBundle.read(p,"a".repeat(64)),key);
        }
        byte[] good=ScrewIdentityBundle.encode(model(),ScrewIdentityBundle.fields());Path original=temporary.resolve("original");Files.write(original,good);
        assertThrows(IOException.class,()->ScrewIdentityBundle.read(original,"b".repeat(64)));
        byte[][] invalid={good.clone(),Arrays.copyOf(good,good.length-1),Arrays.copyOf(good,good.length+1),good.clone(),new byte[327681]};invalid[0][good.length-1]^=1;invalid[3][2]^=1;
        for(i=0;i<invalid.length;i++){Path p=temporary.resolve("corrupt-"+i);Files.write(p,invalid[i]);assertThrows(IOException.class,()->ScrewIdentityBundle.read(p,"a".repeat(64)));}
    }
    @Test void validHashDoesNotPermitInvalidStateOrDuplicateFields() throws Exception {
        for(boolean duplicate:new boolean[]{false,true}) {
            byte[] payload=ScrewQualificationBundle.payload(model());ByteBuffer.wrap(payload).putDouble(18816*8,0);
            var bytes=new ByteArrayOutputStream();var fields=ScrewIdentityBundle.fields();
            try(var out=new DataOutputStream(bytes)) {
                out.writeUTF(ScrewIdentityBundle.FORMAT);out.writeInt(fields.size());String first=fields.keySet().iterator().next();
                for(var entry:fields.entrySet()){out.writeUTF(duplicate?first:entry.getKey());out.writeUTF(entry.getValue());}
                out.writeUTF("a".repeat(64));out.writeUTF(ScrewQualificationBundle.hash(payload));out.writeInt(payload.length);out.write(payload);
            }
            Path p=temporary.resolve("invalid-state-"+duplicate);Files.write(p,bytes.toByteArray());assertThrows(IOException.class,()->ScrewIdentityBundle.read(p,"a".repeat(64)));
        }
    }
    @Test void mapCompositionIncludesTheExistingIdentityMappingExactly() {
        double[] z=new double[196];for(int i=0;i<196;i++)z[i]=Math.sin(i);
        double[] view=CanonicalPilotBridge.backproject(LocalizationMaps.upsample(z,14,14,700,700),700,new double[]{1,0,0,0,1,0});
        assertArrayEquals(LocalizationMaps.upsample(view,700,700,1024,1024),ScrewQualificationInput.evaluatedMap(z,ScrewIdentityControl.IDENTITY,1024));
    }
    @Test void deltaDirectionsAndDescriptiveMapStatisticsAreExplicit() throws Exception {
        var h=ComparisonArtifacts.JSON.readTree("{\"image_auroc\":0.2,\"pixel_auroc\":0.3,\"aupro030\":0.4}");
        var b=ComparisonArtifacts.JSON.readTree("{\"image_auroc\":0.3,\"pixel_auroc\":0.2,\"aupro030\":0.5}");
        var c=ComparisonArtifacts.JSON.readTree("{\"image_auroc\":0.6,\"pixel_auroc\":0.4,\"aupro030\":0.3}");
        var rows=ScrewIdentityControl.comparison(h,b,c);assertEquals(.1,(Double)rows.get(0).get("historical_to_identity"),1e-15);assertEquals(.3,(Double)rows.get(0).get("identity_to_canonical"),1e-15);
        assertThrows(IllegalArgumentException.class,()->ScrewIdentityControl.comparison(h,ComparisonArtifacts.JSON.createObjectNode(),c));
        assertEquals(1,ScrewIdentityControl.correlation(new double[]{1,2,3},new double[]{3,5,7}),1e-12);
        assertEquals(-1,ScrewIdentityControl.correlation(new double[]{1,2,3},new double[]{3,2,1}),1e-12);assertNull(ScrewIdentityControl.correlation(new double[]{1,1},new double[]{2,3}));
        assertEquals(1,ScrewIdentityControl.firstArgmax(new double[]{0,3,3}));assertThrows(IllegalArgumentException.class,()->ScrewIdentityControl.firstArgmax(new double[0]));
    }
    @Test void freezeOrderingAndIntegrityAreRequired() throws Exception {
        for(String stage:List.of("train","test","evaluate","diagnose"))assertThrows(IllegalStateException.class,()->ScrewIdentityControl.main(new String[]{stage,temporary.toString()}));
        Path input=temporary.resolve("frozen");Files.writeString(input,"unchanged");ComparisonArtifacts.json(temporary.resolve("input-hashes.json"),Map.of(input.toString(),NpyMap.sha256(input)));
        ScrewIdentityControl.verify(temporary);Files.writeString(input,"modified");assertThrows(IllegalStateException.class,()->ScrewIdentityControl.verify(temporary));
    }
    @Test void retainedCoherenceMatchesExactlyAcrossJsonIntegerNodeRepresentations() throws Exception {
        double[][] units=new double[3][18816];for(int i=0;i<3;i++)for(int p=0;p<196;p++)units[i][p*96+i]=1;
        var summary=GeneralityCoherence.compute(units);var live=ComparisonArtifacts.JSON.valueToTree(summary);
        var retained=ComparisonArtifacts.JSON.readTree(ComparisonArtifacts.JSON.writeValueAsBytes(summary));
        assertTrue(ScrewIdentityControl.sameCoherence(summary,retained),()->"live count type="+live.path("lowStdCounts").path("1.0E-6").getClass().getSimpleName()
                +" retained count type="+retained.path("lowStdCounts").path("1.0E-6").getClass().getSimpleName());
        ((com.fasterxml.jackson.databind.node.ObjectNode)retained.path("sampleStd")).put("median",1e-12);
        assertFalse(ScrewIdentityControl.sameCoherence(summary,retained));
    }
}
