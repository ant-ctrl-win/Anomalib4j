package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class HeldOutBottleCalibrationTest {
    @Test void splitHasStableGoldenOrderAndIgnoresDiscoveryOrder() {
        var images = images();
        var original = List.copyOf(images);
        var split = HeldOutBottleCalibration.split(images);
        assertEquals(original, images);
        assertEquals(List.of("bottle_good_005.png", "bottle_good_177.png", "bottle_good_199.png", "bottle_good_081.png", "bottle_good_148.png"),
                split.archetypeTraining().subList(0, 5).stream().map(path -> path.getFileName().toString()).toList());
        assertEquals("bottle_good_119.png", split.archetypeTraining().getLast().getFileName().toString());
        assertEquals("bottle_good_203.png", split.calibration().getFirst().getFileName().toString());
        assertEquals("bottle_good_007.png", split.calibration().getLast().getFileName().toString());
        Collections.reverse(images);
        assertEquals(split, HeldOutBottleCalibration.split(images));
    }

    @Test void splitIsDisjointCompleteAndImmutable() {
        var input = images();
        var split = HeldOutBottleCalibration.split(input);
        assertEquals(167, split.archetypeTraining().size());
        assertEquals(42, split.calibration().size());
        assertTrue(Collections.disjoint(split.archetypeTraining(), split.calibration()));
        var union = new HashSet<>(split.archetypeTraining());
        union.addAll(split.calibration());
        assertEquals(new HashSet<>(input), union);
        input.clear();
        assertEquals(167, split.archetypeTraining().size());
        assertThrows(UnsupportedOperationException.class, () -> split.archetypeTraining().clear());
        assertThrows(UnsupportedOperationException.class, () -> split.calibration().clear());
    }

    @Test void splitRejectsWrongCountDuplicateFilenamesAndNonGoodImages() {
        var input = images();
        assertThrows(IllegalArgumentException.class, () -> HeldOutBottleCalibration.split(input.subList(0, 208)));
        input.set(208, Path.of("different-directory", "bottle_good_000.png"));
        assertThrows(IllegalArgumentException.class, () -> HeldOutBottleCalibration.split(input));
        input.set(208, Path.of("bottle_broken_large_000.png"));
        assertThrows(IllegalArgumentException.class, () -> HeldOutBottleCalibration.split(input));
    }

    @Test void calibratedBranchUsesTheSameRawScoresAndFrozenFilters() {
        var grid = new GridShape(1, 2, 1);
        var policy = new NormalizationPolicy(1e-6);
        var descriptor = new ModelDescriptor("held-out", "cnn", "layer", grid, 10000, 42L, policy, 1);
        var filters = new AdjointFilterBank(descriptor, grid, policy, new double[]{2, -3}, new double[]{1, -2});
        var calibration = PositionalRawCalibration.fit(descriptor, List.of(new double[]{-3, 4}, new double[]{-1, 8}), 1e-6);
        float[] features = {1, 1};
        double[] expectedRaw = HeldOutBottleCalibration.rawScores(filters, features);
        var scores = HeldOutBottleCalibration.score(filters, features, calibration);
        assertArrayEquals(new double[]{-2, 6}, scores.raw());
        assertArrayEquals(expectedRaw, scores.raw());
        assertArrayEquals(new double[]{0, 0}, scores.z(), 1e-12);
        scores.z()[0] = 100;
        assertArrayEquals(expectedRaw, scores.raw());
        assertArrayEquals(expectedRaw, HeldOutBottleCalibration.rawScores(filters, features));
    }

    @Test void primaryDeltaUsesMeasuredRaw167NotFull209References() {
        var raw = new HeldOutBottleCalibration.Metrics(.7, .8, .6);
        var calibrated = new HeldOutBottleCalibration.Metrics(.9, .75, .7);
        var delta = calibrated.minus(raw);
        assertEquals(.2, delta.imageAuRoc(), 1e-12);
        assertEquals(-.05, delta.pixelAuRoc(), 1e-12);
        assertEquals(.1, delta.auPro(), 1e-12);
        String report = HeldOutBottleCalibration.comparison(raw, calibrated);
        String primary = report.substring(0, report.indexOf("## Context only"));
        assertTrue(primary.contains("CALIBRATED_167+42 - RAW_167"));
        assertFalse(primary.contains("0.9857142857142858"));
        assertTrue(report.contains("0.9611017698505884"));
        assertTrue(report.contains("0.8766695388953096"));
    }

    private static ArrayList<Path> images() {
        var paths = new ArrayList<Path>();
        for (int i = 0; i < 209; i++) paths.add(Path.of("train", "img", String.format(Locale.ROOT, "bottle_good_%03d.png", i)));
        return paths;
    }
}
