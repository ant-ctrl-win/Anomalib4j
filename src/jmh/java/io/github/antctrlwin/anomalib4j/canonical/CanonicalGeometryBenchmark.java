package io.github.antctrlwin.anomalib4j.canonical;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(value = 2, jvmArgsAppend = "-Xmx2g")
public class CanonicalGeometryBenchmark {
    @State(Scope.Thread)
    public static class Data {
        @Param({"required"}) public String run;
        @Param({"required"}) public String phase2;
        @Param({"required"}) public String dataset;
        GeometryProfileFixtures fixtures;
        final int[] rgb = new int[700*700];
        final MetalNutGeometryEstimator production = new MetalNutGeometryEstimator();
        final GeometryProfileStages whole = new GeometryProfileStages();
        int cursor;
        @Setup(Level.Trial) public void setup() throws Exception {
            Path root = Path.of(run); GeometryProfileFixtures.verify(root);
            fixtures = new GeometryProfileFixtures(Path.of(phase2),Path.of(dataset));
            Path trials = root.resolve("trials"); Files.createDirectories(trials);
            Path evidence = Files.createTempDirectory(trials,"trial-"+ProcessHandle.current().pid()+"-");
            GeometryProfileFixtures.json(evidence.resolve("runtime.json"),Map.of(
                    "fixtures",fixtures.metadata,"java",System.getProperty("java.runtime.version"),
                    "heap_max_bytes",Runtime.getRuntime().maxMemory(),"source_snapshot_sha256",GeometryProfileFixtures.sha(root.resolve("source-hashes.json")),
                    "onnx_invoked",false,"hole_fill_reset","selected mask copied outside per-invocation measurement"));
        }
        int next() { int i=cursor; cursor=(cursor+1)%9; return i; }
        GeometryProfileStages nextStage() { return fixtures.stages[next()]; }
        @TearDown(Level.Trial) public void close() { fixtures.close(); }
    }
    @State(Scope.Thread)
    public static class HoleInput {
        GeometryProfileStages current;
        @Setup(Level.Invocation) public void reset(Data data) {
            int i = data.next(); current = data.fixtures.stages[i];
            System.arraycopy(data.fixtures.selected[i],0,current.mask,0,GeometryProfileStages.COUNT);
        }
    }
    @Benchmark public void wholeGeometry(Data s, Blackhole bh) {
        s.fixtures.images[s.next()].getRGB(0,0,700,700,s.rgb,0,700); bh.consume(s.production.estimate(s.rgb));
    }
    @Benchmark public void estimatorPacked(Data s, Blackhole bh) { bh.consume(s.production.estimate(s.fixtures.packed[s.next()])); }
    @Benchmark public void stagedWholeControl(Data s, Blackhole bh) {
        s.fixtures.images[s.next()].getRGB(0,0,700,700,s.rgb,0,700); bh.consume(s.whole.estimate(s.rgb));
    }
    @Benchmark public void sourceGetRGB(Data s, Blackhole bh) {
        s.fixtures.images[s.next()].getRGB(0,0,700,700,s.rgb,0,700); bh.consume(s.rgb);
    }
    @Benchmark public void reduction(Data s, Blackhole bh) {
        int i=s.next(); var stage=s.fixtures.stages[i]; stage.reduce(s.fixtures.packed[i]); bh.consume(stage.gray);
    }
    @Benchmark public void histogram(Data s, Blackhole bh) { var stage=s.nextStage(); stage.histogram(); bh.consume(stage.histogram); }
    @Benchmark @OperationsPerInvocation(64) public void otsuBatch64(Data s, Blackhole bh) {
        for (int n=0; n<64; n++) bh.consume(s.nextStage().otsu());
    }
    @Benchmark public void thresholdAndComponents(Data s, Blackhole bh) { var stage=s.nextStage(); stage.components(); bh.consume(stage.selectedLabel); }
    @Benchmark public void selectedMask(Data s, Blackhole bh) { var stage=s.nextStage(); stage.selectedMask(); bh.consume(stage.mask); }
    @Benchmark public void holeFill(HoleInput s, Blackhole bh) { s.current.holes(); bh.consume(s.current.mask); }
    @Benchmark public void centroidAreaBorder(Data s, Blackhole bh) {
        var stage=s.nextStage(); stage.centroid(); bh.consume(stage.area); bh.consume(stage.cx); bh.consume(stage.cy); bh.consume(stage.border);
    }
    @Benchmark public void harmonic4(Data s, Blackhole bh) { var stage=s.nextStage(); stage.harmonic(); bh.consume(stage.re); bh.consume(stage.im); }
    @Benchmark @OperationsPerInvocation(64) public void orientationTransformBatch64(Data s, Blackhole bh) {
        for (int n=0; n<64; n++) { var pose=s.nextStage().finish(); bh.consume(pose); bh.consume(pose.transform()); }
    }
}
