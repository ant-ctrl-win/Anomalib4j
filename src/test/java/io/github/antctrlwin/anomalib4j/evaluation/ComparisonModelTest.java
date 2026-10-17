package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointCompiler;
import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBank;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonModelTest {
    @TempDir Path temporary;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {2, 42, 44, 79})
    void archiveReloadPreservesCompiledAndCalibratedScoresExactly(int calibrationCount) throws Exception {
        var grid = new GridShape(1, 2, 2);
        var descriptor = new ModelDescriptor("metal_nut-smoke", "encoder", "layer", grid, 3, 42,
                new NormalizationPolicy(1e-6), 1);
        var statistics = new ProjectionStatistics(new double[]{.1, -.3, .2}, new double[]{1, 2, 3}).withDescriptor(descriptor);
        var memory = new PositionalMemoryBank(descriptor, grid, 3, new double[]{1, 0, 0, 0, 1, 0}, new long[]{2, 2}, statistics);
        var filters = new AdjointCompiler().compile(memory, statistics, new DenseRademacherProjection(2, 3, 42), descriptor.normalization());
        var maps = java.util.stream.IntStream.range(0, calibrationCount)
                .mapToObj(i -> new double[]{i * .1, -i * .2}).toList();
        var calibration = PositionalRawCalibration.fit(descriptor, maps, 1e-6);
        ComparisonModel.write(temporary, new NormalImageTrainer.Result(2, 4, statistics, memory, filters), maps);
        var loaded = ComparisonModel.load(temporary);
        var before = HeldOutBottleCalibration.score(filters, new float[]{.3f, -.7f, 1, 2}, calibration);
        var after = HeldOutBottleCalibration.score(loaded.filters(), new float[]{.3f, -.7f, 1, 2}, loaded.calibration());
        assertArrayEquals(before.raw(), after.raw(), 0);
        assertArrayEquals(before.z(), after.z(), 0);
        assertEquals(calibrationCount, loaded.calibration().count());
        Path archive = temporary.resolve("learning-state.bin");
        byte[] valid = java.nio.file.Files.readAllBytes(archive);
        long gridOffset = 2 + "anomalib4j-comparison-learning/v1".length() + 4 + 16 * 3;
        try (var file = new java.io.RandomAccessFile(archive.toFile(), "rw")) {
            file.seek(gridOffset);
            file.writeInt(3);
        }
        assertThrows(java.io.IOException.class, () -> ComparisonModel.load(temporary));
        java.nio.file.Files.write(archive, valid);
        try (var file = new java.io.RandomAccessFile(archive.toFile(), "rw")) {
            file.seek(gridOffset + 4);
            file.writeLong(0);
        }
        assertThrows(java.io.IOException.class, () -> ComparisonModel.load(temporary));
        java.nio.file.Files.write(archive, java.util.Arrays.copyOf(valid, valid.length - 1));
        assertThrows(java.io.IOException.class, () -> ComparisonModel.load(temporary));
        java.nio.file.Files.write(archive, java.util.Arrays.copyOf(valid, valid.length + 1));
        assertThrows(java.io.IOException.class, () -> ComparisonModel.load(temporary));
        java.nio.file.Files.write(archive, valid);
        try (var file = new java.io.RandomAccessFile(archive.toFile(), "rw")) {
            file.seek(valid.length - 8);
            file.writeDouble(Double.NaN);
        }
        assertThrows(java.io.IOException.class, () -> ComparisonModel.load(temporary));
    }
}
