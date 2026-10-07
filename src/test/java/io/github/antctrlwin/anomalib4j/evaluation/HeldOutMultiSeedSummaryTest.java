package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class HeldOutMultiSeedSummaryTest {
    @Test void splitsMatchSortedJavaShuffleAndRemainDisjointForAllFiveSeeds() {
        var images = new ArrayList<Path>();
        for (int i = 0; i < 209; i++) images.add(Path.of(String.format(Locale.ROOT, "bottle_good_%03d.png", i)));
        var original = List.copyOf(images);
        var memberships = new HashSet<List<Path>>();
        for (long seed : HeldOutMultiSeedSummary.SEEDS) {
            var expected = new ArrayList<>(original);
            Collections.shuffle(expected, new Random(seed));
            Collections.reverse(images);
            var before = List.copyOf(images);
            var split = HeldOutBottleCalibration.split(images, seed);
            assertEquals(expected.subList(0, 167), split.archetypeTraining());
            assertEquals(expected.subList(167, 209), split.calibration());
            assertEquals(before, images);
            assertTrue(Collections.disjoint(split.archetypeTraining(), split.calibration()));
            var union = new HashSet<>(split.archetypeTraining());
            union.addAll(split.calibration());
            assertEquals(new HashSet<>(original), union);
            memberships.add(split.calibration());
        }
        assertEquals(5, memberships.size());
        assertEquals(HeldOutBottleCalibration.split(images), HeldOutBottleCalibration.split(images, 42L));
    }

    @Test void computesSampleStandardDeviationAndExtrema() {
        var s = HeldOutMultiSeedSummary.statistics(new double[]{1, 2, 3, 4, 5});
        assertEquals(3, s.mean());
        assertEquals(Math.sqrt(2.5), s.sampleStdDev(), 1e-15);
        assertEquals(1, s.min());
        assertEquals(5, s.max());
        assertEquals(0, HeldOutMultiSeedSummary.statistics(new double[]{2, 2, 2, 2, 2}).sampleStdDev());
        assertThrows(IllegalArgumentException.class, () -> HeldOutMultiSeedSummary.statistics(new double[]{1}));
        assertThrows(IllegalArgumentException.class, () -> HeldOutMultiSeedSummary.statistics(new double[]{1, Double.NaN}));
    }

    @Test void aggregatesPairedDeltasAndReportsMixedSignsWithoutRejectingThem() {
        var runs = new ArrayList<HeldOutMultiSeedSummary.Run>();
        for (long seed : HeldOutMultiSeedSummary.SEEDS) {
            double raw = seed / 8.0;
            runs.add(new HeldOutMultiSeedSummary.Run(seed, metrics(raw), metrics(.375)));
        }
        Collections.reverse(runs);
        var summary = new HeldOutMultiSeedSummary(runs);
        String[] rows = summary.summaryCsv().lines().toArray(String[]::new);
        assertEquals(10, rows.length);
        for (int i = 7; i < 10; i++) {
            String[] fields = rows[i].split(",");
            assertEquals("delta", fields[0]);
            assertEquals(5, Integer.parseInt(fields[2]));
            assertEquals(0, Double.parseDouble(fields[3]), 1e-15);
            assertEquals(Math.sqrt(2.5) / 8, Double.parseDouble(fields[4]), 1e-15);
            assertEquals(-.25, Double.parseDouble(fields[5]));
            assertEquals(.25, Double.parseDouble(fields[6]));
        }
        assertEquals(4, summary.signsCsv().lines().count());
        for (String line : summary.signsCsv().lines().skip(1).toList()) assertTrue(line.endsWith(",2,1,2,false"));
        assertEquals(6, summary.perSeedCsv().lines().count());
        var lines = summary.perSeedCsv().lines().skip(1).toList();
        for (int i = 0; i < 5; i++) {
            String[] fields = lines.get(i).split(",");
            assertEquals(10, fields.length);
            assertEquals(i + 1, Long.parseLong(fields[0]));
            for (int metric = 0; metric < 3; metric++) assertEquals(
                    Double.parseDouble(fields[4 + metric]) - Double.parseDouble(fields[1 + metric]),
                    Double.parseDouble(fields[7 + metric]), 1e-15);
        }
    }

    @Test void reportsStablePositiveAndNegativeAndZeroSignsPerMetric() {
        var raw = new HeldOutBottleCalibration.Metrics(.5, .5, .5);
        var calibrated = new HeldOutBottleCalibration.Metrics(.75, .25, .5);
        var summary = new HeldOutMultiSeedSummary(HeldOutMultiSeedSummary.SEEDS.stream()
                .map(seed -> new HeldOutMultiSeedSummary.Run(seed, raw, calibrated)).toList());
        assertTrue(summary.signsCsv().contains("image_auroc,5,0,0,true"));
        assertTrue(summary.signsCsv().contains("pixel_auroc,0,0,5,false"));
        assertTrue(summary.signsCsv().contains("aupro030,0,5,0,false"));
        assertTrue(summary.report().contains("N-1 = 4"));
        assertTrue(HeldOutBottleCalibration.comparison(raw, calibrated, 3L).contains("new Random(3L)"));
        assertTrue(HeldOutBottleCalibration.comparison(raw, calibrated).contains("new Random(42L)"));
    }

    @Test void rejectsIncompleteDuplicateUnexpectedOrNonfiniteResults() {
        var run = new HeldOutMultiSeedSummary.Run(1L, metrics(.5), metrics(.6));
        assertThrows(IllegalArgumentException.class, () -> new HeldOutMultiSeedSummary(List.of(run)));
        assertThrows(IllegalArgumentException.class, () -> new HeldOutMultiSeedSummary(Collections.nCopies(5, run)));
        assertThrows(IllegalArgumentException.class, () -> new HeldOutMultiSeedSummary(List.of(1L, 2L, 3L, 4L, 42L).stream()
                .map(seed -> new HeldOutMultiSeedSummary.Run(seed, metrics(.5), metrics(.6))).toList()));
        assertThrows(IllegalArgumentException.class, () -> new HeldOutMultiSeedSummary.Run(1L, metrics(Double.NaN), metrics(.6)));
    }

    private static HeldOutBottleCalibration.Metrics metrics(double value) {
        return new HeldOutBottleCalibration.Metrics(value, value, value);
    }
}
