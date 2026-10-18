package io.github.antctrlwin.anomalib4j.m2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class M2ModelTest {
    @TempDir Path temp;
    static double[] unit(int channel) {
        double[] values = new double[M2Model.ELEMENTS];
        for (int p = 0; p < 196; p++) values[p * 96 + channel] = 1;
        return values;
    }
    static M2Model model() {
        return M2Trainer.uncalibrated("a".repeat(64), M2Trainer.fitStatistics(List.of(unit(0), unit(1))));
    }
    @Test void sampleStatisticsAndFloors() {
        var stats = M2Trainer.fitStatistics(List.of(unit(0), unit(1)));
        assertEquals(.5, stats.mean()[0]);
        assertEquals(Math.sqrt(.5), stats.sampleStd()[0]);
        assertEquals(0, stats.sampleStd()[2]);
        assertEquals(1e-6, stats.effectiveStd()[2]);
        assertThrows(IllegalStateException.class, () -> M2Trainer.fitStatistics(List.of(unit(0))));
    }
    @Test void rawCalibrationAndImageScore() {
        var fitted = model();
        assertEquals(1. / 96, fitted.raw(unit(0))[0], 1e-16);
        assertEquals((1e12 + 1) / 96, fitted.raw(unit(2))[0], 1e-5);
        var cal = M2Trainer.calibrationStatistics(fitted, List.of(unit(0), unit(1)));
        var calibrated = M2Trainer.calibrated(fitted, cal);
        assertEquals(1e-6, cal.effectiveStd()[0]);
        assertArrayEquals(new double[196], calibrated.calibrate(fitted.raw(unit(0))));
        assertEquals(0, M2Model.imageScore(calibrated.calibrate(fitted.raw(unit(1)))));
    }
    @Test void reloadAndDefensiveCopies() throws Exception {
        var model = model();
        double[] expected = model.raw(unit(0));
        model.local().mean()[0] = 9;
        Path file = temp.resolve("m2.bin");
        M2ModelStore.write(file, model);
        assertArrayEquals(expected, M2ModelStore.read(file, "a".repeat(64)).raw(unit(0)));
        assertThrows(java.io.IOException.class, () -> M2ModelStore.read(file, "b".repeat(64)));
        Files.write(file, new byte[]{1}, StandardOpenOption.APPEND);
        assertThrows(java.io.IOException.class, () -> M2ModelStore.read(file, "a".repeat(64)));
    }
    @Test void invalidDescriptorsAndState() {
        assertThrows(IllegalArgumentException.class, () -> M2Model.normalize(new float[M2Model.ELEMENTS]));
        var values = unit(0); values[1] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> model().raw(values));
        assertThrows(IllegalArgumentException.class, () -> model().raw(new double[3]));
        assertThrows(IllegalArgumentException.class, () -> new M2Model.Local(unit(0), new double[M2Model.ELEMENTS]));
        var beta = new double[196]; Arrays.fill(beta, Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> new M2Model.Calibration(new double[196], beta));
    }
    @Test void floatNormalizationAndCalibrationIsolation() {
        float[] features = new float[M2Model.ELEMENTS];
        for (int p = 0; p < 196; p++) features[p * 96] = 3;
        assertArrayEquals(unit(0), M2Model.normalize(features));
        var fitted = model();
        double[] before = fitted.local().mean();
        M2Trainer.calibrationStatistics(fitted, List.of(unit(2), unit(3)));
        assertArrayEquals(before, fitted.local().mean());
    }
    @Test void truncatedAndNonfiniteArchives() throws Exception {
        Path file = temp.resolve("model.bin");
        M2ModelStore.write(file, model());
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(bytes, bytes.length - 1));
        assertThrows(java.io.IOException.class, () -> M2ModelStore.read(file, "a".repeat(64)));
        java.nio.ByteBuffer.wrap(bytes, bytes.length - 8, 8).putDouble(Double.NaN);
        Files.write(file, bytes);
        assertThrows(java.io.IOException.class, () -> M2ModelStore.read(file, "a".repeat(64)));
    }
}
