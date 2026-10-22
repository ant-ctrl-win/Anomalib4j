package io.github.antctrlwin.anomalib4j.evaluation;

import ai.onnxruntime.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
public class M2Benchmark {
    @State(Scope.Thread)
    public static class StateData {
        @Param({"required"}) public String run;
        @Param({"required"}) public String baseline;
        @Param({"required"}) public String dataset;
        @Param({"bottle"}) public String category;
        @Param({"cpu-verified"}) public String benchmarkName;
        @Param({"SMALL_SPATIAL_14"}) public String encoderSpec;
        EncoderSpec spec;
        M2Model m2;
        ComparisonModel.Loaded m0;
        M2Detector detector;
        OnnxMobileNetV4Encoder encoder;
        BufferedImage image;
        double[] units, raw, z;
        float[] nchw;
        OrtSession session;
        OrtEnvironment environment;

        @Setup(Level.Trial) public void setup() throws Exception {
            var providers = OrtEnvironment.getAvailableProviders();
            if (!providers.contains(OrtProvider.CPU) || !EnumSet.of(OrtProvider.CPU, OrtProvider.AZURE).containsAll(providers))
                throw new IllegalStateException("Unexpected hardware runtime providers: " + providers);
            Path directory = Path.of(run);
            spec = EncoderSpec.valueOf(encoderSpec);
            m2 = M2ModelStore.read(directory.resolve("M2/" + category + "/model.bin"), spec);
            if (spec == EncoderSpec.SMALL_SPATIAL_14 && !baseline.equals("required"))
                m0 = ComparisonModel.load(Path.of(baseline).resolve("anomalib4j/" + category));
            Path fixture = DatasetManifests.fixture(Path.of(dataset), directory.resolve("data/" + category), false);
            image = ComparisonRunner.read(fixture);
            encoder = new OnnxMobileNetV4Encoder(spec);
            detector = new M2Detector(m2);
            units = M2Model.normalize(encoder.extract(image), spec); raw = m2.raw(units); z = m2.calibrate(raw);
            nchw = BenchmarkPreprocessing.preprocess(image);
            environment = OrtEnvironment.getEnvironment();
            Path evidence = directory.resolve("benchmark/" + benchmarkName); Files.createDirectories(evidence);
            try (var stream = OnnxMobileNetV4Encoder.class.getResourceAsStream(spec.resourcePath());
                 var options = new OrtSession.SessionOptions()) {
                if (stream == null) throw new IllegalStateException("Missing encoder");
                byte[] model = stream.readAllBytes();
                M2CpuEvidence.verify(model, nchw, evidence, spec.inputName());
                session = environment.createSession(model, options);
            }
            ComparisonArtifacts.json(evidence.resolve("runtime-" + ProcessHandle.current().pid() + ".json"), Map.of(
                "available_providers", providers.toString(), "active_provider", "CPUExecutionProvider",
                "session_options", "default; no additional EP; all profiled nodes CPU; Azure available but inactive", "ort", environment.getVersion(),
                "java", System.getProperty("java.version"), "affinity", ComparisonArtifacts.affinity(),
                "heap_max_bytes", Runtime.getRuntime().maxMemory(), "fixture", fixture.toString(),
                "fixture_sha256", NpyMap.sha256(fixture), "encoder_sha256", spec.sha256()));
            System.out.println("CPU-only ONNX Runtime " + environment.getVersion() + "; providers=" + providers);
        }
        @TearDown(Level.Trial) public void close() throws Exception {
            session.close(); detector.close(); encoder.close(); image.flush();
        }
    }
    @Benchmark public void preprocessing(StateData s, Blackhole bh) { bh.consume(BenchmarkPreprocessing.preprocess(s.image)); }
    @Benchmark public void cnnExtraction(StateData s, Blackhole bh) throws Exception {
        try (var input = OnnxTensor.createTensor(s.environment, FloatBuffer.wrap(s.nchw), new long[]{1, 3, 224, 224});
             var result = s.session.run(Map.of(s.spec.inputName(), input))) {
            var output = (OnnxTensor) result.get(s.spec.outputName()).orElseThrow();
            float[] features = new float[s.spec.grid().elements()]; output.getFloatBuffer().get(features);
            for (float value : features) if (!Float.isFinite(value)) throw new IllegalStateException("Nonfinite features");
            bh.consume(features);
        }
    }
    @Benchmark public void m2RawScorer(StateData s, Blackhole bh) { bh.consume(s.m2.raw(s.units)); }
    @Benchmark public void positionalCalibration(StateData s, Blackhole bh) { bh.consume(s.m2.calibrate(s.raw)); }
    @Benchmark public void interpolation(StateData s, Blackhole bh) {
        bh.consume(LocalizationMaps.upsample(s.z, 14, 14, s.image.getWidth(), s.image.getHeight()));
    }
    @Benchmark public void m2Detection(StateData s, Blackhole bh) throws Exception { bh.consume(s.detector.detect(s.image)); }
    @Benchmark public void m2Localization(StateData s, Blackhole bh) throws Exception {
        var z = s.detector.nativeMap(s.image);
        bh.consume(M2Model.imageScore(z));
        bh.consume(LocalizationMaps.upsample(z, 14, 14, s.image.getWidth(), s.image.getHeight()));
    }
    @Benchmark public void m0Detection(StateData s, Blackhole bh) throws Exception {
        if (s.m0 == null) throw new IllegalStateException("M0 benchmark requires Small and a retained M0 baseline");
        var scores = HeldOutBottleCalibration.score(s.m0.filters(), s.encoder.extract(s.image), s.m0.calibration());
        bh.consume(BottleEvaluation.summarize(scores.z()).max()); bh.consume(scores);
    }
    @Benchmark public void m0Localization(StateData s, Blackhole bh) throws Exception {
        if (s.m0 == null) throw new IllegalStateException("M0 benchmark requires Small and a retained M0 baseline");
        var scores = HeldOutBottleCalibration.score(s.m0.filters(), s.encoder.extract(s.image), s.m0.calibration());
        bh.consume(BottleEvaluation.summarize(scores.z()).max());
        bh.consume(LocalizationMaps.upsample(scores.z(), 14, 14, s.image.getWidth(), s.image.getHeight())); bh.consume(scores);
    }
}
