package io.github.antctrlwin.anomalib4j.evaluation;

import ai.onnxruntime.*;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations=5,time=2)
@Measurement(iterations=10,time=2)
@Fork(value=2,jvmArgsAppend="-Xmx2g")
public class BgrFastPathBenchmark {
    @State(Scope.Thread)
    public static class Data {
        @Param({"required"}) public String run;
        @Param({"required"}) public String phase2;
        @Param({"required"}) public String dataset;
        final BufferedImage[] images=new BufferedImage[9];
        final int[] rgb=new int[700*700];
        final float[] tensor=new float[AffineTensorSampler.ELEMENTS];
        final MetalNutGeometryEstimator geometry=new MetalNutGeometryEstimator();
        final BgrGenericReference.Input reference=new BgrGenericReference.Input();
        final CanonicalPreprocessor preprocessor=new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        BgrGenericReference oldDetector;
        CanonicalM2Detector detector;
        int cursor;
        @Setup(Level.Trial) public void setup() throws Exception {
            Path root=Path.of(run); var complete=ComparisonArtifacts.JSON.readTree(root.resolve("parity-complete.json").toFile());
            var hashes=ComparisonArtifacts.JSON.readTree(root.resolve("source-hashes.json").toFile());
            for(var entry:hashes.properties())if(!NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue().asText()))
                throw new IllegalStateException("Measured source changed: "+entry.getKey());
            var bundle=CanonicalM2BundleStore.read(Path.of(complete.path("archive").asText()),M2ModelStore.encoderHash());
            if(!bundle.identity().equals(complete.path("model_identity").asText()))throw new IllegalStateException("Qualified bundle changed");
            var rows=ComparisonArtifacts.JSON.readTree(Path.of(phase2).resolve("source-inputs.json").toFile());
            var fixtures=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for(var row:rows)if(row.path("role").asText().equals("fit") && fixtures.size()<8)fixtures.add(row);
            for(var row:rows)if(row.path("role").asText().equals("test") && row.path("image").asText().equals("metal_nut_bent_011.png"))fixtures.add(row);
            if(fixtures.size()!=9)throw new IllegalStateException("Nine matched fixtures required");
            var identities=new ArrayList<Map<String,Object>>();
            for(int i=0;i<9;i++) {
                var row=fixtures.get(i); Path path=Path.of(dataset).resolve(i<8?"train/img":"test/img").resolve(row.path("image").asText());
                if(!NpyMap.sha256(path).equals(row.path("sha256").asText()))throw new IllegalStateException("Fixture changed");
                images[i]=ImageIO.read(path.toFile());
                if(images[i].getType()!=BufferedImage.TYPE_3BYTE_BGR)throw new IllegalStateException("Matched BGR fixture required");
                var pose=preprocessor.prepare(images[i],tensor); CanonicalPhase2Bridge.requireGeometry(pose,row.path("expected"));
                identities.add(Map.of("source",path.toString(),"sha256",NpyMap.sha256(path),"type",images[i].getType(),"angle",pose.angle()));
            }
            var providers=OrtEnvironment.getAvailableProviders();
            if(!providers.contains(OrtProvider.CPU) || !EnumSet.of(OrtProvider.CPU,OrtProvider.AZURE).containsAll(providers))
                throw new IllegalStateException("Unexpected providers");
            Path trials=root.resolve("trials");Files.createDirectories(trials); Path evidence=Files.createTempDirectory(trials,"trial-"+ProcessHandle.current().pid()+"-");
            try(var stream=OnnxMobileNetV4Encoder.class.getResourceAsStream("/models/mobilenetv4_spatial_14x14.onnx")) {
                if(stream==null)throw new IllegalStateException("Missing encoder"); M2CpuEvidence.verify(stream.readAllBytes(),tensor,evidence);
            }
            oldDetector=new BgrGenericReference(bundle.model());detector=new CanonicalM2Detector(bundle);
            ComparisonArtifacts.json(evidence.resolve("runtime.json"),Map.of("fixtures",identities,"available_providers",providers.toString(),
                    "active_provider","CPUExecutionProvider","ort",OrtEnvironment.getEnvironment().getVersion(),
                    "java",System.getProperty("java.runtime.version"),"heap_max_bytes",Runtime.getRuntime().maxMemory(),
                    "source_snapshot_sha256",NpyMap.sha256(root.resolve("source-hashes.json")),"model_identity",bundle.identity(),
                    "affinity",ComparisonArtifacts.affinity(),"parity_sha256",NpyMap.sha256(root.resolve("parity-complete.json"))));
        }
        int next(){int i=cursor;cursor=(cursor+1)%9;return i;}
        @TearDown(Level.Trial) public void close() throws Exception {oldDetector.close();detector.close();for(var image:images)image.flush();}
    }
    @Benchmark public void oldAcquisition(Data s,Blackhole bh){s.images[s.next()].getRGB(0,0,700,700,s.rgb,0,700);bh.consume(s.rgb);}
    @Benchmark public void newAcquisition(Data s,Blackhole bh){CanonicalRgbAcquisition.read(s.images[s.next()],s.rgb);bh.consume(s.rgb);}
    @Benchmark public void oldGeometry(Data s,Blackhole bh){s.images[s.next()].getRGB(0,0,700,700,s.rgb,0,700);bh.consume(s.geometry.estimate(s.rgb));}
    @Benchmark public void newGeometry(Data s,Blackhole bh){CanonicalRgbAcquisition.read(s.images[s.next()],s.rgb);bh.consume(s.geometry.estimate(s.rgb));}
    @Benchmark public void oldPreprocessing(Data s,Blackhole bh){bh.consume(s.reference.prepare(s.images[s.next()],s.tensor));bh.consume(s.tensor);}
    @Benchmark public void newPreprocessing(Data s,Blackhole bh){bh.consume(s.preprocessor.prepare(s.images[s.next()],s.tensor));bh.consume(s.tensor);}
    @Benchmark public void oldDecision(Data s,Blackhole bh) throws Exception {bh.consume(s.oldDetector.detectScore(s.images[s.next()]));}
    @Benchmark public void newDecision(Data s,Blackhole bh) throws Exception {bh.consume(s.detector.detectScore(s.images[s.next()]));}
}
