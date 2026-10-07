package io.github.antctrlwin.anomalib4j.evaluation;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
public class ComparisonBenchmark {
    @State(Scope.Thread)
    public static class ModelState {
        @Param({"required"}) public String caseDirectory;
        @Param({"required"}) public String dataset;
        @Param({"bottle"}) public String category;
        @Param({"false"}) public boolean smoke;
        ComparisonModel.Loaded model;
        OnnxMobileNetV4Encoder encoder;
        BufferedImage image;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            model = ComparisonModel.load(Path.of(caseDirectory));
            encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14);
            image = ComparisonRunner.read(Path.of(dataset).resolve((smoke ? "train" : "test") + "/img/" + category + "_good_000.png"));
            ComparisonArtifacts.json(Path.of(caseDirectory).resolve("runtime-jmh-" + ProcessHandle.current().pid() + ".json"),
                    java.util.Map.of("pid", ProcessHandle.current().pid(), "process_affinity", ComparisonArtifacts.affinity(), "java", System.getProperty("java.version"),
                            "heap_max_bytes", Runtime.getRuntime().maxMemory(), "ort_workers", "unknown: default SessionOptions",
                            "jvm_args", java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        }

        @TearDown(Level.Trial)
        public void close() throws Exception { encoder.close(); image.flush(); }
    }

    @Benchmark public void detectionInference(ModelState state, Blackhole bh) throws Exception {
        var scores = HeldOutBottleCalibration.score(state.model.filters(), state.encoder.extract(state.image), state.model.calibration());
        bh.consume(BottleEvaluation.summarize(scores.z()).max());
        bh.consume(scores);
    }

    @Benchmark public void localizationInference(ModelState state, Blackhole bh) throws Exception {
        var scores = HeldOutBottleCalibration.score(state.model.filters(), state.encoder.extract(state.image), state.model.calibration());
        bh.consume(BottleEvaluation.summarize(scores.z()).max());
        bh.consume(LocalizationMaps.upsample(scores.z(), 14, 14, state.image.getWidth(), state.image.getHeight()));
        bh.consume(scores);
    }
}
