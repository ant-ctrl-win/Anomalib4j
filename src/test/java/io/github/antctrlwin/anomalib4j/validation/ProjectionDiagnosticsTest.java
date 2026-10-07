package io.github.antctrlwin.anomalib4j.validation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionDiagnosticsTest {
    @Test
    void canonicalBasisHasExactIsometry() {
        var projection = new DenseRademacherProjection(67, 10000);
        for (int c = 0; c < projection.inputDimensions(); c++) {
            float[] basis = new float[projection.inputDimensions()];
            basis[c] = 1;
            assertEquals(1.0, ProjectionDiagnostics.rho(projection, basis), 1e-12);
        }
    }

    @Test
    void batchReportsCountsMeanAndNearestRankPercentile() {
        var projection = new DenseRademacherProjection(7, 43);
        var policy = new NormalizationPolicy(1e-6);
        var random = new Random(17);
        List<float[]> batch = new ArrayList<>();
        List<Double> errors = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < 101; i++) {
            float[] x = new float[7];
            for (int c = 0; c < x.length; c++) x[c] = random.nextFloat() * 2 - 1;
            batch.add(x);
            double[] y = new double[43];
            projection.project(x, y);
            double nx = 0;
            double ny = 0;
            for (float value : x) nx += (double) value * value;
            for (double value : y) ny += value * value;
            double rho = Math.sqrt(ny) / (Math.sqrt(43) * Math.sqrt(nx));
            sum += rho;
            errors.add(Math.abs(rho - 1));
        }
        batch.add(new float[7]);
        batch.add(new float[]{1e-8f, 0, 0, 0, 0, 0, 0});
        RhoReport report = ProjectionDiagnostics.evaluate(projection, batch, policy);
        errors.sort(Comparator.naturalOrder());
        assertEquals(101, report.evaluatedPatches());
        assertEquals(2, report.excludedNearZeroPatches());
        assertEquals(sum / 101, report.meanRatio(), 1e-12);
        assertEquals(errors.get(99), report.p99Error(), 1e-12);
        assertEquals(errors.get(100), report.maxError(), 1e-12);
    }

    @Test
    void emptyOrExcludedBatchesReportUndefinedMetrics() {
        var projection = new DenseRademacherProjection(2, 3);
        for (List<float[]> batch : List.of(List.<float[]>of(), List.of(new float[2]))) {
            var report = ProjectionDiagnostics.evaluate(projection, batch, new NormalizationPolicy(1e-6));
            assertEquals(0, report.evaluatedPatches());
            assertEquals(batch.size(), report.excludedNearZeroPatches());
            assertTrue(Double.isNaN(report.meanRatio()));
            assertTrue(Double.isNaN(report.p99Error()));
            assertTrue(Double.isNaN(report.maxError()));
        }
    }

    @Test
    void rejectsUndefinedScalarRatioAndInvalidFeatures() {
        var projection = new DenseRademacherProjection(2, 3);
        assertThrows(IllegalArgumentException.class, () -> ProjectionDiagnostics.rho(projection, new float[2]));
        assertThrows(IllegalArgumentException.class, () -> ProjectionDiagnostics.rho(projection, new float[]{Float.NaN, 1}));
        assertThrows(IllegalArgumentException.class, () -> ProjectionDiagnostics.evaluate(
                projection, List.of(new float[1]), new NormalizationPolicy(1e-6)));
    }
}
