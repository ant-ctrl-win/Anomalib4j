package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;
import io.github.antctrlwin.anomalib4j.validation.ExplicitVsaScorer;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class BottleRealEvaluationTest {
    private static volatile double consumedScores;

    @Test
    void evaluatesRawBottleScoresAndMeasuresScoringPaths() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        Path output = Files.createDirectories(Path.of("target"));
        Path heatmaps = Files.createDirectories(output.resolve("bottle-heatmaps"));
        var testPaths = new ArrayList<Path>();
        try (var entries = Files.newDirectoryStream(dataset.resolve("test/img"), "bottle_*.png")) {
            for (Path path : entries) if (Files.isRegularFile(path)) testPaths.add(path);
        }
        testPaths.sort(Comparator.comparing((Path path) -> !BottleEvaluation.defect(path.getFileName().toString()).equals("good"))
                .thenComparing(path -> path.getFileName().toString()));
        assertEquals(83, testPaths.size());
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            var descriptor = new ModelDescriptor("bottle-normal-v1", encoder.modelId(), encoder.variant().name(),
                    encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
            var projection = new DenseRademacherProjection(descriptor.grid().channels(), descriptor.vsaDimensions(), descriptor.projectionSeed());
            var trainingPaths = NormalImageTrainer.discoverBottleImages(dataset);
            assertEquals(209, trainingPaths.size());
            var model = new NormalImageTrainer(encoder, descriptor, projection, 1e-8).train(trainingPaths, completed -> {
                if (completed % 20 == 0 || completed == 418) System.out.println("Sprint 7 training image passes " + completed + "/418");
            });
            assertEquals(40_964, model.statisticObservations());
            var explicit = new ExplicitVsaScorer(model.memory(), model.statistics(), projection, descriptor.normalization());
            var samples = new ArrayList<BottleEvaluation.Sample>();
            double maximumError = 0;
            int comparisons = 0;
            for (Path path : testPaths) {
                String filename = path.getFileName().toString();
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, filename);
                float[] features;
                try { features = encoder.extract(image); } finally { image.flush(); }
                double[] raw = new double[196];
                for (int p = 0; p < raw.length; p++) {
                    double compiledScore = model.filters().scoreCell(features, p);
                    double explicitScore = explicit.scoreCell(features, p);
                    assertTrue(Double.isFinite(compiledScore));
                    assertTrue(Double.isFinite(explicitScore));
                    maximumError = Math.max(maximumError, Math.abs(compiledScore - explicitScore));
                    assertEquals(explicitScore, compiledScore, 1e-9, filename + ": cell " + p);
                    raw[p] = 1 - compiledScore;
                    comparisons++;
                }
                samples.add(new BottleEvaluation.Sample(filename, BottleEvaluation.defect(filename), features, raw));
                if (samples.size() % 10 == 0) System.out.println("Sprint 7 evaluated " + samples.size() + "/83");
            }
            assertEquals(16_268, comparisons);
            double[] good = scores(samples, "good");
            double[] anomaly = samples.stream().filter(sample -> !sample.defect().equals("good"))
                    .mapToDouble(sample -> BottleEvaluation.summarize(sample.raw()).max()).toArray();
            assertEquals(20, good.length);
            assertEquals(63, anomaly.length);
            assertEquals(20, scores(samples, "broken_large").length);
            assertEquals(22, scores(samples, "broken_small").length);
            assertEquals(21, scores(samples, "contamination").length);
            writeTables(output, samples);
            double low = samples.stream().mapToDouble(sample -> BottleEvaluation.summarize(sample.raw()).min()).min().orElseThrow();
            double high = samples.stream().mapToDouble(sample -> BottleEvaluation.summarize(sample.raw()).max()).max().orElseThrow();
            var benchmarkSamples = new ArrayList<BottleEvaluation.Sample>();
            for (String group : List.of("good", "broken_large", "broken_small", "contamination")) {
                var matching = samples.stream().filter(sample -> sample.defect().equals(group)).toList();
                benchmarkSamples.add(matching.getFirst());
                int examples = group.equals("good") ? 2 : 1;
                for (int i = 0; i < examples; i++) {
                    var sample = matching.get(i);
                    BottleEvaluation.heatmap(heatmaps.resolve(sample.filename()), sample.filename(), sample.raw(), low, high);
                }
            }
            CellScorer explicitScorer = explicit::scoreCell;
            CellScorer compiledScorer = model.filters()::scoreCell;
            measure(explicitScorer, benchmarkSamples, 4);
            measure(compiledScorer, benchmarkSamples, 1024);
            double[] explicitNanos = new double[9];
            double[] compiledNanos = new double[9];
            var benchmarkCsv = new StringBuilder("round,explicit_ns_per_map,compiled_ns_per_map\n");
            for (int round = 0; round < 9; round++) {
                if (round % 2 == 0) {
                    explicitNanos[round] = measure(explicitScorer, benchmarkSamples, 1);
                    compiledNanos[round] = measure(compiledScorer, benchmarkSamples, 256);
                } else {
                    compiledNanos[round] = measure(compiledScorer, benchmarkSamples, 256);
                    explicitNanos[round] = measure(explicitScorer, benchmarkSamples, 1);
                }
                benchmarkCsv.append(String.format(Locale.ROOT, "%d,%.17g,%.17g%n", round + 1, explicitNanos[round], compiledNanos[round]));
                System.out.println("Sprint 7 benchmark round " + (round + 1) + "/9");
            }
            Files.writeString(output.resolve("bottle-benchmark.csv"), benchmarkCsv);
            double explicitMedian = BottleEvaluation.percentile(explicitNanos, .5);
            double compiledMedian = BottleEvaluation.percentile(compiledNanos, .5);
            String metrics = String.format(Locale.ROOT,
                    "images=83%ngood=20%nanomaly=63%nauroc=%.17g%nmaxError=%.17g%ncomparisons=%d%n"
                            + "explicitMedianNs=%.17g%ncompiledMedianNs=%.17g%nspeedup=%.17g%n"
                            + "explicitP25Ns=%.17g%nexplicitP75Ns=%.17g%ncompiledP25Ns=%.17g%ncompiledP75Ns=%.17g%n"
                            + "displayMin=%.17g%ndisplayMax=%.17g%njava=%s%nos=%s%nprocessors=%d%n",
                    BottleEvaluation.auroc(good, anomaly), maximumError, comparisons, explicitMedian, compiledMedian,
                    explicitMedian / compiledMedian, BottleEvaluation.percentile(explicitNanos, .25), BottleEvaluation.percentile(explicitNanos, .75),
                    BottleEvaluation.percentile(compiledNanos, .25), BottleEvaluation.percentile(compiledNanos, .75),
                    low, high, System.getProperty("java.runtime.version"), System.getProperty("os.name"), Runtime.getRuntime().availableProcessors());
            Files.writeString(output.resolve("bottle-metrics.txt"), metrics);
            System.out.println("SPRINT7_RESULT\n" + metrics);
        }
    }

    private static double[] scores(List<BottleEvaluation.Sample> samples, String group) {
        return samples.stream().filter(sample -> sample.defect().equals(group))
                .mapToDouble(sample -> BottleEvaluation.summarize(sample.raw()).max()).toArray();
    }

    private static void writeTables(Path output, List<BottleEvaluation.Sample> samples) throws Exception {
        var csv = new StringBuilder("filename,ground_truth,defect,image_score_raw,max_row,max_column,raw_min,raw_mean,raw_max\n");
        var rawCsv = new StringBuilder("filename,row,column,raw_discrepancy\n");
        for (var sample : samples) {
            var summary = BottleEvaluation.summarize(sample.raw());
            csv.append(String.format(Locale.ROOT, "%s,%s,%s,%.17g,%d,%d,%.17g,%.17g,%.17g%n",
                    sample.filename(), sample.defect().equals("good") ? "good" : "anomaly", sample.defect(), summary.max(),
                    summary.row(), summary.column(), summary.min(), summary.mean(), summary.max()));
            for (int p = 0; p < 196; p++) {
                rawCsv.append(String.format(Locale.ROOT, "%s,%d,%d,%.17g%n", sample.filename(), p / 14, p % 14, sample.raw()[p]));
            }
        }
        Files.writeString(output.resolve("bottle-evaluation.csv"), csv);
        Files.writeString(output.resolve("bottle-raw-heatmaps.csv"), rawCsv);
        var distributions = new StringBuilder("group,count,min,p25,median,mean,p75,max\n");
        for (String group : List.of("good", "broken_large", "broken_small", "contamination")) {
            distributions.append(BottleEvaluation.distribution(group, scores(samples, group)));
        }
        distributions.append(BottleEvaluation.distribution("anomaly", samples.stream().filter(sample -> !sample.defect().equals("good"))
                .mapToDouble(sample -> BottleEvaluation.summarize(sample.raw()).max()).toArray()));
        Files.writeString(output.resolve("bottle-distributions.csv"), distributions);
    }

    private static double measure(CellScorer scorer, List<BottleEvaluation.Sample> samples, int repetitions) {
        double checksum = 0;
        long start = System.nanoTime();
        for (int iteration = 0; iteration < repetitions; iteration++) {
            for (var sample : samples) {
                for (int p = 0; p < 196; p++) checksum += scorer.score(sample.features(), p);
            }
        }
        long elapsed = System.nanoTime() - start;
        consumedScores = checksum;
        return (double) elapsed / (repetitions * samples.size());
    }

    @FunctionalInterface
    private interface CellScorer {
        double score(float[] features, int cell);
    }
}
