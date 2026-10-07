package io.github.antctrlwin.anomalib4j.adjoint;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBuilder;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AdjointCompilerTest {
    private final GridShape grid = new GridShape(2, 3, 7);
    private final NormalizationPolicy policy = new NormalizationPolicy(1e-6);
    private final int dimensions = 19;
    private final ModelDescriptor descriptor = new ModelDescriptor(
            "normal", "cnn", "spatial", grid, dimensions, 42L, policy, 1);
    private final DenseRademacherProjection projection = new DenseRademacherProjection(7, dimensions, 42L);

    private ProjectionStatistics statistics(ModelDescriptor contract) {
        double[] mean = new double[contract.vsaDimensions()];
        double[] std = new double[mean.length];
        for (int d = 0; d < mean.length; d++) {
            mean[d] = (d - 7) * 0.03;
            std[d] = 0.4 + d * 0.07;
        }
        return new ProjectionStatistics(mean, std).withDescriptor(contract);
    }

    private float[] map(Random random) {
        float[] values = new float[grid.elements()];
        for (int i = 0; i < values.length; i++) values[i] = random.nextFloat() * 2 - 1;
        return values;
    }

    @Test
    void fiveTrainingMapsCompileToExplicitForwardScore() {
        ProjectionStatistics stats = statistics(descriptor);
        var builder = new PositionalMemoryBuilder(descriptor, grid, projection, stats, policy);
        var random = new Random(712);
        for (int i = 0; i < 5; i++) builder.observe(map(random));
        var memory = builder.build();
        var filters = new AdjointCompiler().compile(memory, stats, projection, policy);
        float[] query = map(random);
        for (int p = 0; p < grid.cells(); p++) {
            double[] archetype = new double[dimensions];
            memory.getArchetype(p, archetype);
            assertEquals(5, memory.sampleCount(p));
            assertEquals(explicitScore(query, p, archetype, stats), filters.scoreCell(query, p), 1e-9);
        }
        for (float scale : new float[]{0, 1e-8f}) {
            float[] tiny = query.clone();
            for (int i = 0; i < tiny.length; i++) tiny[i] *= scale;
            for (int p = 0; p < grid.cells(); p++) {
                double[] archetype = new double[dimensions];
                memory.getArchetype(p, archetype);
                assertEquals(explicitScore(tiny, p, archetype, stats), filters.scoreCell(tiny, p), 1e-9);
            }
        }
    }

    private double explicitScore(float[] map, int patch, double[] archetype, ProjectionStatistics stats) {
        double[][] matrix = new double[dimensions][grid.channels()];
        Random signs = new Random(42);
        for (int c = 0; c < grid.channels(); c++) {
            for (int d = 0; d < dimensions; d++) matrix[d][c] = signs.nextBoolean() ? 1 : -1;
        }
        double normSquared = 0;
        for (int c = 0; c < grid.channels(); c++) {
            double value = map[patch * grid.channels() + c];
            normSquared += value * value;
        }
        double denominator = Math.max(Math.sqrt(normSquared), policy.normEpsilon());
        double score = 0;
        for (int d = 0; d < dimensions; d++) {
            double projected = 0;
            for (int c = 0; c < grid.channels(); c++) {
                projected += matrix[d][c] * (map[patch * grid.channels() + c] / denominator);
            }
            double z = (projected / Math.sqrt(dimensions) - stats.mean(d)) / stats.stdDev(d);
            score += z * archetype[d];
        }
        return score;
    }

    @Test
    void compilerRejectsIncompatibleDimensionsSeedPolicyAndStatistics() {
        var stats = statistics(descriptor);
        var builder = new PositionalMemoryBuilder(descriptor, grid, projection, stats, policy);
        builder.observe(map(new Random(9)));
        var memory = builder.build();
        var compiler = new AdjointCompiler();
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory, stats,
                new DenseRademacherProjection(7, dimensions + 1), policy));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory, stats,
                new DenseRademacherProjection(8, dimensions), policy));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory, stats,
                new DenseRademacherProjection(7, dimensions, 43), policy));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory, stats, projection,
                new NormalizationPolicy(1e-5)));
        var differentGrid = new ModelDescriptor("normal", "cnn", "spatial",
                new GridShape(3, 2, 7), dimensions, 42, policy, 1);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory,
                statistics(differentGrid), projection, policy));
        var wrongD = new ProjectionStatistics(new double[]{0}, new double[]{1});
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory, wrongD, projection, policy));
        double[] changedMean = stats.mean();
        changedMean[0] += 0.1;
        var changed = new ProjectionStatistics(changedMean, stats.stdDev()).withDescriptor(descriptor);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(memory, changed, projection, policy));
        var equivalentCopy = new ProjectionStatistics(stats.mean(), stats.stdDev()).withDescriptor(descriptor);
        assertDoesNotThrow(() -> compiler.compile(memory, equivalentCopy, projection, policy));
    }
}
