package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that the Java evaluation-space resize ({@link LocalizationMaps#upsample}) is
 * numerically equivalent to the frozen Python convention
 * {@code F.interpolate(..., mode="bilinear", align_corners=False)}.
 *
 * <p>The synthetic oracle is generated once by {@code tools/prerun/resize_equivalence.py}
 * and committed as {@code docs/benchmark/benchmark-prerun/resize-equivalence.json}. Neither
 * algorithm is modified to force agreement.</p>
 */
class ResizeEquivalenceTest {
    private static final Path ORACLE = Path.of("docs", "benchmark", "benchmark-prerun", "resize-equivalence.json");

    @Test void javaUpsampleMatchesTorchBilinearAlignCornersFalse() throws Exception {
        assertTrue(Files.exists(ORACLE), "Missing resize oracle: " + ORACLE.toAbsolutePath());
        JsonNode root = new ObjectMapper().readTree(Files.readString(ORACLE));
        double tolerance = root.path("tolerance").asDouble();
        assertTrue(tolerance > 0, "Explicit tolerance must be positive");

        double overallMax = 0;
        double overallSum = 0;
        long overallCount = 0;
        int cases = 0;
        for (JsonNode node : root.withArray("cases")) {
            int sourceWidth = node.path("sourceWidth").asInt();
            int sourceHeight = node.path("sourceHeight").asInt();
            int width = node.path("width").asInt();
            int height = node.path("height").asInt();
            double[] source = doubles(node.withArray("source"));
            double[] expected = doubles(node.withArray("expected"));
            assertEquals(Math.multiplyExact(sourceWidth, sourceHeight), source.length, "source length");
            assertEquals(Math.multiplyExact(width, height), expected.length, "expected length");

            double[] actual = LocalizationMaps.upsample(source, sourceWidth, sourceHeight, width, height);
            double max = 0;
            double sum = 0;
            for (int p = 0; p < expected.length; p++) {
                double difference = Math.abs(actual[p] - expected[p]);
                max = Math.max(max, difference);
                sum += difference;
            }
            double mean = expected.length == 0 ? 0 : sum / expected.length;
            System.out.printf(Locale.ROOT, "resize-equivalence %s max_abs=%.3e mean_abs=%.3e%n",
                    node.path("id").asText(), max, mean);
            assertTrue(max <= tolerance, String.format(Locale.ROOT,
                    "%s max abs difference %.3e exceeds tolerance %.3e", node.path("id").asText(), max, tolerance));

            overallMax = Math.max(overallMax, max);
            overallSum += sum;
            overallCount += expected.length;
            cases++;
        }
        assertTrue(cases > 0, "Oracle must contain at least one fixture");
        double overallMean = overallSum / overallCount;
        System.out.printf(Locale.ROOT, "resize-equivalence SUMMARY cases=%d max_abs=%.3e mean_abs=%.3e tolerance=%.3e%n",
                cases, overallMax, overallMean, tolerance);
        assertTrue(overallMax <= tolerance);
    }

    private static double[] doubles(JsonNode array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).doubleValue();
        return values;
    }
}
