package org.vaimee.vsa.anomalib.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vaimee.vsa.anomalib.adjoint.AdjointCompiler;
import org.vaimee.vsa.anomalib.memory.PositionalMemoryBank;
import org.vaimee.vsa.anomalib.memory.ProjectionStatistics;
import org.vaimee.vsa.anomalib.model.GridShape;
import org.vaimee.vsa.anomalib.model.ModelDescriptor;
import org.vaimee.vsa.anomalib.model.NormalizationPolicy;
import org.vaimee.vsa.anomalib.projection.DenseRademacherProjection;
import org.vaimee.vsa.anomalib.training.NormalImageTrainer;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonModelTest {
    @TempDir Path temporary;

    @Test void archiveReloadPreservesCompiledAndCalibratedScoresExactly() throws Exception {
        var grid = new GridShape(1, 2, 2);
        var descriptor = new ModelDescriptor("metal_nut-smoke", "encoder", "layer", grid, 3, 42,
                new NormalizationPolicy(1e-6), 1);
        var statistics = new ProjectionStatistics(new double[]{.1, -.3, .2}, new double[]{1, 2, 3}).withDescriptor(descriptor);
        var memory = new PositionalMemoryBank(descriptor, grid, 3, new double[]{1, 0, 0, 0, 1, 0}, new long[]{2, 2}, statistics);
        var filters = new AdjointCompiler().compile(memory, statistics, new DenseRademacherProjection(2, 3, 42), descriptor.normalization());
        var maps = List.of(new double[]{-.1, .3}, new double[]{.5, -.8});
        var calibration = PositionalRawCalibration.fit(descriptor, maps, 1e-6);
        ComparisonModel.write(temporary, new NormalImageTrainer.Result(2, 4, statistics, memory, filters), maps);
        var loaded = ComparisonModel.load(temporary);
        var before = HeldOutBottleCalibration.score(filters, new float[]{.3f, -.7f, 1, 2}, calibration);
        var after = HeldOutBottleCalibration.score(loaded.filters(), new float[]{.3f, -.7f, 1, 2}, loaded.calibration());
        assertArrayEquals(before.raw(), after.raw(), 0);
        assertArrayEquals(before.z(), after.z(), 0);
    }
}
