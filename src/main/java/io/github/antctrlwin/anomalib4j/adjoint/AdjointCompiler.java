package io.github.antctrlwin.anomalib4j.adjoint;

import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBank;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.projection.ProjectionStrategy;

import java.util.Objects;

public final class AdjointCompiler {
    public AdjointFilterBank compile(PositionalMemoryBank memory, ProjectionStatistics statistics,
                                     ProjectionStrategy projection, NormalizationPolicy normalization) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(statistics, "statistics");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(normalization, "normalization");
        ModelDescriptor descriptor = memory.descriptor();
        descriptor.requireCompatible(memory.grid(), projection.inputDimensions(), projection.outputDimensions(),
                projection.seed(), normalization);
        memory.requireStatistics(statistics);
        int channels = memory.grid().channels();
        int dimensions = memory.vsaDimensions();
        double scale = 1.0 / Math.sqrt(dimensions);
        double[] weights = new double[memory.grid().elements()];
        double[] biases = new double[memory.grid().cells()];
        double[] q = new double[dimensions];
        double[] transposed = new double[channels];
        for (int p = 0; p < memory.grid().cells(); p++) {
            memory.getArchetype(p, q);
            double bias = 0;
            for (int d = 0; d < dimensions; d++) {
                q[d] /= statistics.stdDev(d);
                bias -= statistics.mean(d) * q[d];
            }
            projection.adjoint(q, transposed);
            for (int c = 0; c < channels; c++) weights[p * channels + c] = scale * transposed[c];
            biases[p] = bias;
        }
        return new AdjointFilterBank(descriptor, memory.grid(), normalization, weights, biases);
    }
}
