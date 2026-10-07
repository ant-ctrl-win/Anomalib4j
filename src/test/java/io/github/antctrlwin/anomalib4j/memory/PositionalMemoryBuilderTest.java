package io.github.antctrlwin.anomalib4j.memory;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PositionalMemoryBuilderTest {
    private final GridShape grid = new GridShape(1, 2, 3);
    private final NormalizationPolicy policy = new NormalizationPolicy(1e-6);
    private final ModelDescriptor descriptor = new ModelDescriptor("normal", "cnn", "layer", grid, 5, 42, policy, 1);
    private final DenseRademacherProjection projection = new DenseRademacherProjection(3, 5);
    private final ProjectionStatistics stats = new ProjectionStatistics(
            new double[]{0.2, -0.3, 0.1, 0.4, -0.1}, new double[]{0.5, 1, 2, 0.7, 1.3})
            .withDescriptor(descriptor);

    private PositionalMemoryBuilder builder() {
        return new PositionalMemoryBuilder(descriptor, grid, projection, stats, policy);
    }

    @Test
    void bundlingMatchesNormalizedSumAndBuildDoesNotAlterAccumulators() {
        float[][] maps = {{1, 2, 3, -1, 0.2f, 0.7f}, {-2, 1, 0.1f, 4, 2, -1}, {0.3f, -1, 2, 1, 3, 5}};
        var builder = builder();
        builder.observe(maps[0]);
        var first = builder.build();
        double[] firstCopy = new double[5];
        first.getArchetype(0, firstCopy);
        builder.observe(maps[1]);
        builder.build();
        builder.observe(maps[2]);
        var memory = builder.build();
        for (int p = 0; p < grid.cells(); p++) {
            double[] expected = new double[5];
            for (float[] map : maps) {
                float[] patch = Arrays.copyOfRange(map, p * 3, p * 3 + 3);
                double norm = 0;
                for (float value : patch) norm += (double) value * value;
                double[] y = new double[5];
                projection.project(patch, y);
                for (int d = 0; d < 5; d++) {
                    expected[d] += (y[d] / Math.sqrt(norm) / Math.sqrt(5) - stats.mean(d)) / stats.stdDev(d);
                }
            }
            double norm = 0;
            for (double value : expected) norm += value * value;
            norm = Math.sqrt(norm);
            for (int d = 0; d < 5; d++) expected[d] /= norm;
            double[] actual = new double[5];
            memory.getArchetype(p, actual);
            assertArrayEquals(expected, actual, 1e-12);
            double actualNorm = 0;
            for (double value : actual) actualNorm += value * value;
            assertEquals(1, Math.sqrt(actualNorm), 1e-12);
            assertEquals(3, memory.sampleCount(p));
        }
        double[] unchanged = new double[5];
        first.getArchetype(0, unchanged);
        assertArrayEquals(firstCopy, unchanged);
        assertEquals(1, first.sampleCount(0));
    }

    @Test
    void bankProtectsBuffersAndRejectsInvalidArchetypes() {
        double[] archetypes = {1, 0, 0, 0, 0, 0, 1, 0, 0, 0};
        long[] counts = {1, 2};
        var bank = new PositionalMemoryBank(descriptor, grid, 5, archetypes, counts, stats);
        archetypes[0] = 9;
        counts[0] = 99;
        double[] destination = new double[5];
        bank.getArchetype(0, destination);
        destination[0] = -1;
        bank.getArchetype(0, destination);
        assertEquals(1, destination[0]);
        assertEquals(1, bank.sampleCount(0));
        assertThrows(IllegalArgumentException.class, () -> bank.getArchetype(0, new double[4]));
        assertThrows(IndexOutOfBoundsException.class, () -> bank.getArchetype(2, destination));
        assertThrows(IllegalArgumentException.class, () -> new PositionalMemoryBank(
                descriptor, grid, 5, new double[10], new long[]{1, 1}, stats));
        assertThrows(IllegalArgumentException.class, () -> new PositionalMemoryBank(
                descriptor, new GridShape(2, 1, 3), 5, new double[10], new long[]{1, 1}, stats));
    }

    @Test
    void emptyOrCancellingAccumulationCannotProduceUnitArchetype() {
        assertThrows(IllegalStateException.class, () -> builder().build());
        var zeroStats = new ProjectionStatistics(new double[5], new double[]{1, 1, 1, 1, 1}).withDescriptor(descriptor);
        var builder = new PositionalMemoryBuilder(descriptor, grid, projection, zeroStats, policy);
        builder.observe(new float[]{1, 2, 3, 4, 5, 6});
        builder.observe(new float[]{-1, -2, -3, -4, -5, -6});
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void invalidObservationAndMetadataAreRejected() {
        var builder = builder();
        float[] map = {1, 2, 3, 4, 5, 6};
        builder.observe(map);
        map[5] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> builder.observe(map));
        assertThrows(IllegalArgumentException.class, () -> builder.observe(new float[1]));
        assertEquals(1, builder.build().sampleCount(0));
        assertThrows(IllegalArgumentException.class, () -> new PositionalMemoryBuilder(
                descriptor, new GridShape(2, 1, 3), projection, stats, policy));
        assertThrows(IllegalArgumentException.class, () -> new PositionalMemoryBuilder(
                descriptor, grid, projection, new ProjectionStatistics(stats.mean(), stats.stdDev()), policy));
        assertThrows(IllegalArgumentException.class, () -> new ModelDescriptor(
                "normal", "cnn", "layer", grid, 0, 42, policy, 1));
    }
}
