package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.adjoint.AdjointCompiler;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBank;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.projection.DenseRademacherProjection;
import io.github.antctrlwin.anomalib4j.training.NormalImageTrainer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

final class ComparisonModel {
    record Loaded(AdjointFilterBank filters, PositionalRawCalibration calibration) { }
    private ComparisonModel() { }

    static void write(Path directory, NormalImageTrainer.Result model, List<double[]> calibrationRaw) throws IOException {
        ComparisonArtifacts.json(directory.resolve("descriptor.json"), model.filters().descriptor());
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                directory.resolve("learning-state.bin"), StandardOpenOption.CREATE_NEW)))) {
            out.writeUTF("anomalib4j-comparison-learning/v1");
            int dimensions = model.statistics().dimensions();
            out.writeInt(dimensions);
            for (int d = 0; d < dimensions; d++) out.writeDouble(model.statistics().mean(d));
            for (int d = 0; d < dimensions; d++) out.writeDouble(model.statistics().stdDev(d));
            double[] archetype = new double[dimensions];
            out.writeInt(model.memory().grid().cells());
            for (int p = 0; p < model.memory().grid().cells(); p++) {
                out.writeLong(model.memory().sampleCount(p));
                model.memory().getArchetype(p, archetype);
                for (double value : archetype) out.writeDouble(value);
            }
            out.writeInt(calibrationRaw.size());
            for (double[] raw : calibrationRaw) for (double value : raw) out.writeDouble(value);
        }
    }

    static Loaded load(Path directory) throws IOException {
        ModelDescriptor descriptor = ComparisonArtifacts.JSON.readValue(directory.resolve("descriptor.json").toFile(), ModelDescriptor.class);
        int dimensions = descriptor.vsaDimensions();
        int cells = descriptor.grid().cells();
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(directory.resolve("learning-state.bin"))))) {
            if (!in.readUTF().equals("anomalib4j-comparison-learning/v1") || in.readInt() != dimensions) throw new IOException("Invalid model archive");
            double[] mean = doubles(in, dimensions);
            double[] sigma = doubles(in, dimensions);
            var statistics = new ProjectionStatistics(mean, sigma).withDescriptor(descriptor);
            if (in.readInt() != cells) throw new IOException("Invalid archive grid");
            double[] archetypes = new double[cells * dimensions];
            long[] counts = new long[cells];
            for (int p = 0; p < cells; p++) {
                counts[p] = in.readLong();
                for (int d = 0; d < dimensions; d++) archetypes[p * dimensions + d] = in.readDouble();
            }
            var memory = new PositionalMemoryBank(descriptor, descriptor.grid(), dimensions, archetypes, counts, statistics);
            int calibrationCount = in.readInt();
            if (calibrationCount < 2 || calibrationCount > 44) throw new IOException("Invalid calibration count");
            var rawMaps = new ArrayList<double[]>();
            for (int n = 0; n < calibrationCount; n++) rawMaps.add(doubles(in, cells));
            if (in.read() != -1) throw new IOException("Trailing archive data");
            var filters = new AdjointCompiler().compile(memory, statistics,
                    new DenseRademacherProjection(descriptor.grid().channels(), dimensions, descriptor.projectionSeed()), descriptor.normalization());
            return new Loaded(filters, PositionalRawCalibration.fit(descriptor, rawMaps, 1e-6));
        }
    }

    private static double[] doubles(DataInputStream in, int count) throws IOException {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) values[i] = in.readDouble();
        return values;
    }
}
