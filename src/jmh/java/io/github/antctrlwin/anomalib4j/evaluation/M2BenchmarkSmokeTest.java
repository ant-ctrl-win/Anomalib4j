package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.EncoderSpec;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openjdk.jmh.infra.Blackhole;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class M2BenchmarkSmokeTest {
    @TempDir Path temp;
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void genericScopesExecuteWithSelectedEncoder(EncoderSpec spec) throws Exception {
        Path run = temp.resolve("run"), dataset = temp.resolve("dataset");
        Files.createDirectories(run.resolve("M2/bottle")); Files.createDirectories(run.resolve("data/bottle"));
        Files.createDirectories(dataset.resolve("test/img"));
        String filename = "bottle_good_007.png";
        Files.writeString(run.resolve("data/bottle/test.txt"),filename+"\n");
        var image = new BufferedImage(900,900,BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<900; y++) for (int x=0; x<900; x++) image.setRGB(x,y,(x*257+y*65539)&0xffffff);
        ImageIO.write(image,"png",dataset.resolve("test/img/"+filename).toFile());
        double[] std = new double[spec.grid().elements()], beta = new double[196];
        Arrays.fill(std,1); Arrays.fill(beta,1);
        var model = new M2Model(spec,new M2Model.Local(new double[std.length],std),
                new M2Model.Calibration(new double[196],beta));
        M2ModelStore.write(run.resolve("M2/bottle/model.bin"),model);
        var state = new M2Benchmark.StateData();
        state.run=run.toString(); state.dataset=dataset.toString(); state.baseline="required";
        state.category="bottle"; state.benchmarkName="smoke"; state.encoderSpec=spec.name();
        state.setup();
        try {
            var benchmark = new M2Benchmark();
            var blackhole = new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
            benchmark.cnnExtraction(state,blackhole);
            benchmark.m2Detection(state,blackhole);
            benchmark.m2Localization(state,blackhole);
            assertEquals(spec,state.m2.encoderSpec());
            assertEquals(spec.grid().elements(),state.units.length);
            assertEquals(900,state.image.getWidth()); assertEquals(900,state.image.getHeight());
            assertThrows(IllegalStateException.class, () -> benchmark.m0Detection(state,blackhole));
        } finally { state.close(); }
    }
}
