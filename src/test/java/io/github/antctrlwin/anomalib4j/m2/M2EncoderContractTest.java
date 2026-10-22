package io.github.antctrlwin.anomalib4j.m2;

import io.github.antctrlwin.anomalib4j.onnx.EncoderSpec;
import io.github.antctrlwin.anomalib4j.canonical.CanonicalM2Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class M2EncoderContractTest {
    @TempDir Path temp;
    static double[] unit(EncoderSpec spec, int channel) {
        double[] values = new double[spec.grid().elements()];
        for (int p = 0; p < 196; p++) values[p * spec.grid().channels() + channel] = 1;
        return values;
    }
    static M2Model fitted(EncoderSpec spec) {
        return M2Trainer.uncalibrated(spec, M2Trainer.fitStatistics(spec, List.of(unit(spec,0), unit(spec,1))));
    }
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void allChannelsParticipateInStatisticsAndScoring(EncoderSpec spec) {
        int c = spec.grid().channels();
        var model = fitted(spec);
        assertEquals(.5, model.local().mean()[0]);
        assertEquals(Math.sqrt(.5), model.local().effectiveStd()[0]);
        assertEquals(1. / c, model.raw(unit(spec,0))[0], 1e-16);
        double[] last = unit(spec,c-1);
        assertEquals((1e12 + 1) / c, model.raw(last)[195], 1e-5);
        var fit = M2Trainer.fitStatistics(spec, List.of(last, unit(spec,0)));
        assertEquals(.5, fit.mean()[spec.grid().elements()-1]);
        assertEquals(Math.sqrt(.5), fit.sampleStd()[spec.grid().elements()-1]);
        var cal = M2Trainer.calibrationStatistics(model, List.of(unit(spec,0),unit(spec,1)));
        var calibrated = M2Trainer.calibrated(model,cal);
        assertEquals(spec, calibrated.encoderSpec());
        assertEquals(1e-6, cal.effectiveStd()[195]);
        assertArrayEquals(new double[196], calibrated.calibrate(model.raw(unit(spec,0))));
    }
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void normalizationIncludesLastChannelAndRejectsWrongShape(EncoderSpec spec) {
        int c = spec.grid().channels();
        float[] values = new float[spec.grid().elements()];
        for (int p = 0; p < 196; p++) { values[p*c]=3; values[p*c+c-1]=4; }
        double[] unit = M2Model.normalize(values,spec);
        assertEquals(.6, unit[0]); assertEquals(.8, unit[unit.length-1]);
        assertThrows(IllegalArgumentException.class, () -> M2Model.normalize(new float[3],spec));
        assertThrows(IllegalArgumentException.class, () -> M2Model.normalize(new float[values.length],spec));
        assertThrows(IllegalStateException.class, () -> M2Trainer.fitStatistics(spec,List.of(unit)));
    }
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void roundTripIsExactAndWrongEncoderIsRejected(EncoderSpec spec) throws Exception {
        var model = fitted(spec);
        Path path = temp.resolve("model.bin"); M2ModelStore.write(path,model);
        var reloaded = M2ModelStore.read(path,spec);
        assertEquals(spec,reloaded.encoderSpec());
        assertArrayEquals(model.local().mean(),reloaded.local().mean());
        assertArrayEquals(model.local().effectiveStd(),reloaded.local().effectiveStd());
        assertArrayEquals(model.calibration().alpha(),reloaded.calibration().alpha());
        assertArrayEquals(model.calibration().beta(),reloaded.calibration().beta());
        assertArrayEquals(model.raw(unit(spec,spec.grid().channels()-1)),reloaded.raw(unit(spec,spec.grid().channels()-1)));
        var wrong = spec == EncoderSpec.SMALL_SPATIAL_14 ? EncoderSpec.LARGE_SPATIAL_14 : EncoderSpec.SMALL_SPATIAL_14;
        assertThrows(IOException.class, () -> M2ModelStore.read(path,wrong));
        assertThrows(IllegalArgumentException.class, () -> model.raw(unit(wrong,0)));
    }
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void channelHeaderMismatchIsRejectedWithCompletePayload(EncoderSpec spec) throws Exception {
        var model = fitted(spec); Path path = temp.resolve("bad.bin");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeUTF("anomalib4j-m2/v2"); out.writeUTF(spec.name()); out.writeUTF(spec.modelId());
            out.writeUTF(spec.sha256()); out.writeUTF(spec.preprocessingId());
            out.writeInt(14); out.writeInt(14); out.writeInt(spec.grid().channels()==96 ? 192 : 96);
            payload(out,model);
        }
        assertThrows(IOException.class, () -> M2ModelStore.read(path,spec));
    }
    @Test void legacySmallV1StillLoadsAndCannotBecomeLarge() throws Exception {
        var spec = EncoderSpec.SMALL_SPATIAL_14; var model = fitted(spec); Path path = temp.resolve("v1.bin");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeUTF("anomalib4j-m2/v1"); out.writeUTF(spec.sha256()); out.writeUTF(spec.preprocessingId());
            out.writeInt(14); out.writeInt(14); out.writeInt(96); payload(out,model);
        }
        assertArrayEquals(model.raw(unit(spec,0)), M2ModelStore.read(path,spec).raw(unit(spec,0)));
        assertThrows(IOException.class, () -> M2ModelStore.read(path,EncoderSpec.LARGE_SPATIAL_14));
    }
    @Test void legacyHelpersRemainExactlySmallAndCanonicalRejectsLarge() {
        var spec = EncoderSpec.SMALL_SPATIAL_14;
        var old = M2Trainer.uncalibrated(spec.sha256(), M2Trainer.fitStatistics(List.of(unit(spec,0),unit(spec,1))));
        assertArrayEquals(fitted(spec).raw(unit(spec,2)),old.raw(unit(spec,2)));
        assertThrows(IllegalArgumentException.class, () -> new CanonicalM2Bundle(fitted(EncoderSpec.LARGE_SPATIAL_14)));
    }
    static void payload(DataOutputStream out,M2Model model) throws IOException {
        for (double[] values : List.of(model.local().mean(),model.local().effectiveStd(),model.calibration().alpha(),model.calibration().beta()))
            for (double value : values) out.writeDouble(value);
    }
}
