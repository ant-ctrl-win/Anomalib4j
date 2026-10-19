package io.github.antctrlwin.anomalib4j.evaluation;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;
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
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
public class CanonicalM2Benchmark {
    static Path trialEvidence(Path root) throws java.io.IOException {
        Files.createDirectories(root);
        return Files.createTempDirectory(root,"trial-"+ProcessHandle.current().pid()+"-");
    }
    @State(Scope.Thread)
    public static class Data {
        @Param({"required"}) public String run;
        @Param({"required"}) public String phase2;
        @Param({"required"}) public String dataset;
        final BufferedImage[] images = new BufferedImage[9];
        final int[][] packed = new int[9][];
        final RigidTransform[] poses = new RigidTransform[9];
        final float[][] tensors = new float[9][], features = new float[9][];
        final double[][] units = new double[9][];
        final int[] rgb = new int[700*700];
        final float[] destination = new float[AffineTensorSampler.ELEMENTS];
        final MetalNutGeometryEstimator geometry = new MetalNutGeometryEstimator();
        final AffineTensorSampler sampler = new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);
        final CanonicalPreprocessor preprocessor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        CanonicalM2Detector detector;
        OnnxMobileNetV4Encoder encoder;
        M2Model model;
        int cursor;

        @Setup(Level.Trial) public void setup() throws Exception {
            Path root = Path.of(run);
            if (!Files.exists(root.resolve("quality-complete.json"))) throw new IllegalStateException("Quality parity must precede timing");
            var hashes = ComparisonArtifacts.JSON.readTree(root.resolve("source-hashes.json").toFile());
            for (var fields = hashes.fields(); fields.hasNext();) {
                var entry = fields.next();
                if (!NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue().asText())) throw new IllegalStateException("Measured source changed");
            }
            var providers = OrtEnvironment.getAvailableProviders();
            if (!providers.contains(OrtProvider.CPU) || !EnumSet.of(OrtProvider.CPU,OrtProvider.AZURE).containsAll(providers))
                throw new IllegalStateException("Unexpected runtime providers: "+providers);
            var bundle = CanonicalM2BundleStore.read(root.resolve("metal-nut.canonical-m2"),M2ModelStore.encoderHash());
            if (!bundle.identity().equals(ComparisonArtifacts.JSON.readTree(root.resolve("quality-complete.json").toFile()).path("model_identity").asText()))
                throw new IllegalStateException("Qualified bundle changed");
            model = bundle.model(); detector = new CanonicalM2Detector(bundle); encoder = new OnnxMobileNetV4Encoder();
            var rows = ComparisonArtifacts.JSON.readTree(Path.of(phase2).resolve("source-inputs.json").toFile());
            var fixtures = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for (var row : rows) if (row.path("role").asText().equals("fit") && fixtures.size() < 8) fixtures.add(row);
            for (var row : rows) if (row.path("role").asText().equals("test") && row.path("image").asText().equals("metal_nut_bent_011.png")) fixtures.add(row);
            if (fixtures.size() != 9) throw new IllegalStateException("Eight frozen FIT plus disclosed bent_011 stress fixture required");
            var identities = new ArrayList<Map<String,Object>>();
            for (int i = 0; i < fixtures.size(); i++) {
                var row = fixtures.get(i);
                Path path = Path.of(dataset).resolve(i < 8 ? "train/img" : "test/img").resolve(row.path("image").asText());
                if (!NpyMap.sha256(path).equals(row.path("sha256").asText())) throw new IllegalStateException("Timing input changed");
                images[i] = ImageIO.read(path.toFile()); packed[i] = images[i].getRGB(0,0,700,700,null,0,700);
                tensors[i] = new float[AffineTensorSampler.ELEMENTS];
                var pose = preprocessor.prepare(images[i],tensors[i]); CanonicalPhase2Bridge.requireGeometry(pose,row.path("expected"));
                poses[i] = pose.transform(); features[i] = encoder.extractPrepared(tensors[i]); units[i] = M2Model.normalize(features[i]);
                identities.add(Map.of("image",path.toString(),"sha256",row.path("sha256").asText(),"angle",pose.angle(),
                        "cx",pose.cx(),"cy",pose.cy(),"source_type",images[i].getType(),"stress",i == 8));
            }
            Path evidence = trialEvidence(root.resolve("benchmark"));
            try (var stream = OnnxMobileNetV4Encoder.class.getResourceAsStream("/models/mobilenetv4_spatial_14x14.onnx")) {
                if (stream == null) throw new IllegalStateException("Missing encoder");
                M2CpuEvidence.verify(stream.readAllBytes(),tensors[0],evidence);
            }
            ComparisonArtifacts.json(evidence.resolve("runtime.json"),Map.of(
                    "fixtures",identities,"model_identity",bundle.identity(),"source_snapshot_sha256",NpyMap.sha256(root.resolve("source-hashes.json")),
                    "available_providers",providers.toString(),"active_provider","CPUExecutionProvider", "ort",OrtEnvironment.getEnvironment().getVersion(),
                    "java",System.getProperty("java.runtime.version"),"heap_max_bytes",Runtime.getRuntime().maxMemory(),
                    "session_options","default; separate untimed profile; no additional provider","affinity",ComparisonArtifacts.affinity()));
        }
        int next() { int value = cursor; cursor = (cursor+1)%images.length; return value; }
        @TearDown(Level.Trial) public void close() throws Exception {
            detector.close(); encoder.close(); for (var image : images) image.flush();
        }
    }
    @Benchmark public void geometry(Data s, Blackhole bh) {
        s.images[s.next()].getRGB(0,0,700,700,s.rgb,0,700); bh.consume(s.geometry.estimate(s.rgb));
    }
    @Benchmark public void tensorB(Data s, Blackhole bh) {
        int i = s.next(); s.sampler.sample(s.packed[i],s.poses[i],s.destination); bh.consume(s.destination);
    }
    @Benchmark public void preprocessing(Data s, Blackhole bh) {
        bh.consume(s.preprocessor.prepare(s.images[s.next()],s.destination)); bh.consume(s.destination);
    }
    @Benchmark public void encoderPrepared(Data s, Blackhole bh) throws Exception {
        bh.consume(s.encoder.extractPrepared(s.tensors[s.next()]));
    }
    @Benchmark public void descriptorNormalization(Data s, Blackhole bh) { bh.consume(M2Model.normalize(s.features[s.next()])); }
    @Benchmark public void rawZMax(Data s, Blackhole bh) {
        bh.consume(M2Model.imageScore(s.model.calibrate(s.model.raw(s.units[s.next()]))));
    }
    @Benchmark public void fastDecision(Data s, Blackhole bh) throws Exception { bh.consume(s.detector.detectScore(s.images[s.next()])); }
}
