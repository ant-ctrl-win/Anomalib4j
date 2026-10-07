package io.github.antctrlwin.anomalib4j.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class HeldOutMultiSeedSummary {
    static final List<Long> SEEDS = List.of(1L, 2L, 3L, 4L, 5L);
    private static final List<String> METRICS = List.of("image_auroc", "pixel_auroc", "aupro030");
    private static final List<String> BRANCHES = List.of("raw", "calibrated", "delta");
    private final List<Run> runs;

    record Run(long seed, HeldOutBottleCalibration.Metrics raw, HeldOutBottleCalibration.Metrics calibrated) {
        Run {
            for (double value : values(raw)) requireMetric(value);
            for (double value : values(calibrated)) requireMetric(value);
        }
        HeldOutBottleCalibration.Metrics delta() { return calibrated.minus(raw); }
    }

    record Statistics(double mean, double sampleStdDev, double min, double max) { }

    HeldOutMultiSeedSummary(List<Run> runs) {
        this.runs = runs.stream().sorted(Comparator.comparingLong(Run::seed)).toList();
        if (!this.runs.stream().map(Run::seed).toList().equals(SEEDS)) {
            throw new IllegalArgumentException("Expected exactly one result for each split seed 1, 2, 3, 4, 5");
        }
    }

    static Statistics statistics(double[] values) {
        if (values.length < 2) throw new IllegalArgumentException("Sample standard deviation requires N >= 2");
        double mean = 0, m2 = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite metric");
            double difference = value - mean;
            mean += difference / ++n;
            m2 += difference * (value - mean);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new Statistics(mean, Math.sqrt(m2 / (n - 1)), min, max);
    }

    String perSeedCsv() {
        var csv = new StringBuilder("seed");
        for (String branch : BRANCHES) for (String metric : METRICS) csv.append(',').append(branch).append('_').append(metric);
        csv.append('\n');
        for (Run run : runs) {
            csv.append(run.seed());
            for (var metrics : List.of(run.raw(), run.calibrated(), run.delta())) {
                for (double value : values(metrics)) csv.append(String.format(Locale.ROOT, ",%.17g", value));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    String summaryCsv() {
        var csv = new StringBuilder("evaluation,metric,n,mean,sample_std_dev,min,max\n");
        for (int branch = 0; branch < BRANCHES.size(); branch++) {
            for (int metric = 0; metric < METRICS.size(); metric++) {
                double[] observations = new double[runs.size()];
                for (int i = 0; i < runs.size(); i++) {
                    Run run = runs.get(i);
                    observations[i] = values(switch (branch) {
                        case 0 -> run.raw();
                        case 1 -> run.calibrated();
                        default -> run.delta();
                    })[metric];
                }
                Statistics s = statistics(observations);
                csv.append(String.format(Locale.ROOT, "%s,%s,%d,%.17g,%.17g,%.17g,%.17g%n",
                        BRANCHES.get(branch), METRICS.get(metric), runs.size(), s.mean(), s.sampleStdDev(), s.min(), s.max()));
            }
        }
        return csv.toString();
    }

    String signsCsv() {
        var csv = new StringBuilder("metric,positive,zero,negative,positive_for_all_seeds\n");
        for (int metric = 0; metric < METRICS.size(); metric++) {
            int positive = 0, zero = 0, negative = 0;
            for (Run run : runs) {
                double delta = values(run.delta())[metric];
                if (delta > 0) positive++;
                else if (delta < 0) negative++;
                else zero++;
            }
            csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%s%n", METRICS.get(metric),
                    positive, zero, negative, positive == runs.size()));
        }
        return csv.toString();
    }

    String report() {
        return """
                # Bottle SPATIAL_14 held-out calibration: five split seeds

                Split seeds: 1, 2, 3, 4, 5. For each seed, sort the 209 training-good filenames
                lexicographically, then Collections.shuffle(list, new Random(seed)); preserve shuffled
                order, first 167 for VSA statistics/archetypes/Adjoint, remaining 42 for calibration only.
                Projection seed remains 42. Positional raw-score calibration uses sample sigma (N-1),
                floor 1e-6. Encoder, scoring and docs/benchmark/LOCALIZATION_CONVENTIONS.md are unchanged.
                Each seed evaluates paired RAW and calibrated scores on the same 83 test images
                (20 good / 63 anomaly) using one model trained on its 167-image subset.
                Delta = calibrated - raw within each seed; no full-209 baseline enters the delta.
                Summary standard deviations use N-1 = 4, including the five paired deltas.
                Sign counts use exact >0, ==0, <0 comparisons. Positive-for-all means improvement
                in all five observed splits; it is not a statistical significance test.
                Per-seed memberships and diagnostics are in seed-1 through seed-5.

                ## Per-seed metrics

                ```csv
                """ + perSeedCsv() + "```\n\n## Summary\n\n```csv\n" + summaryCsv()
                + "```\n\n## Stability of improvement\n\n```csv\n" + signsCsv() + "```\n";
    }

    private static double[] values(HeldOutBottleCalibration.Metrics metrics) {
        return new double[]{metrics.imageAuRoc(), metrics.pixelAuRoc(), metrics.auPro()};
    }

    private static void requireMetric(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1 + 1e-10) {
            throw new IllegalArgumentException("Expected finite AUROC/AUPRO in [0, 1]");
        }
    }
}
