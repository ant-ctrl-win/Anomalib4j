package io.github.antctrlwin.anomalib4j.training;

import ai.onnxruntime.OrtException;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointCompiler;
import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBank;
import io.github.antctrlwin.anomalib4j.memory.PositionalMemoryBuilder;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatisticsAccumulator;
import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import io.github.antctrlwin.anomalib4j.projection.NormalizedPatchProjection;
import io.github.antctrlwin.anomalib4j.projection.ProjectionStrategy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

public final class NormalImageTrainer {
    private final OnnxMobileNetV4Encoder encoder;
    private final ModelDescriptor descriptor;
    private final ProjectionStrategy projection;
    private final double standardDeviationFloor;

    /** The caller owns the encoder and must keep it open throughout training. */
    public NormalImageTrainer(OnnxMobileNetV4Encoder encoder, ModelDescriptor descriptor,
                              ProjectionStrategy projection, double standardDeviationFloor) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.projection = Objects.requireNonNull(projection, "projection");
        descriptor.requireCompatible(encoder.grid(), projection.inputDimensions(),
                projection.outputDimensions(), projection.seed(), descriptor.normalization());
        if (!encoder.modelId().equals(descriptor.encoderId())
                || !encoder.variant().name().equals(descriptor.layerId())
                || !encoder.preprocessingId().equals(descriptor.preprocessingId())) {
            throw new IllegalArgumentException("Encoder or preprocessing identity does not match descriptor");
        }
        if (!Double.isFinite(standardDeviationFloor) || standardDeviationFloor <= 0) {
            throw new IllegalArgumentException("Standard deviation floor must be finite and positive");
        }
        this.standardDeviationFloor = standardDeviationFloor;
    }

    public static List<Path> discoverBottleImages(Path datasetRoot) throws IOException {
        Path directory = datasetRoot.resolve("train").resolve("img");
        var paths = new ArrayList<Path>();
        try (var entries = Files.newDirectoryStream(directory, "bottle_good_*.png")) {
            for (Path path : entries) {
                if (Files.isRegularFile(path)) paths.add(path);
            }
        }
        paths.sort(Comparator.comparing(path -> path.getFileName().toString()));
        if (paths.isEmpty()) throw new IOException("No bottle_good training PNGs in " + directory);
        return List.copyOf(paths);
    }

    public Result train(List<Path> normalImages) throws IOException, OrtException {
        return train(normalImages, completed -> { });
    }

    /** Reports completed image passes from 1 through 2N, synchronously on the calling thread. */
    public Result train(List<Path> normalImages, IntConsumer progress) throws IOException, OrtException {
        List<Path> paths = List.copyOf(normalImages);
        Objects.requireNonNull(progress, "progress");
        if (paths.isEmpty()) throw new IllegalArgumentException("Training requires normal images");
        var accumulator = new ProjectionStatisticsAccumulator(descriptor.vsaDimensions());
        var transform = new NormalizedPatchProjection(projection, descriptor.normalization());
        double[] projected = new double[descriptor.vsaDimensions()];
        int completed = 0;
        for (Path path : paths) {
            float[] features = extract(path);
            for (int p = 0; p < descriptor.grid().cells(); p++) {
                transform.project(features, p * descriptor.grid().channels(), projected);
                accumulator.observe(projected);
            }
            progress.accept(++completed);
        }
        ProjectionStatistics statistics = accumulator.finalizeStatistics(standardDeviationFloor)
                .withDescriptor(descriptor);
        var builder = new PositionalMemoryBuilder(descriptor, descriptor.grid(), projection,
                statistics, descriptor.normalization());
        for (Path path : paths) {
            builder.observe(extract(path));
            progress.accept(++completed);
        }
        PositionalMemoryBank memory = builder.build();
        AdjointFilterBank filters = new AdjointCompiler().compile(memory, statistics,
                projection, descriptor.normalization());
        return new Result(paths.size(), accumulator.count(), statistics, memory, filters);
    }

    private float[] extract(Path path) throws IOException, OrtException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Unsupported or invalid image: " + path);
        try {
            return encoder.extract(image);
        } finally {
            image.flush();
        }
    }

    public record Result(int imageCount, long statisticObservations, ProjectionStatistics statistics,
                         PositionalMemoryBank memory, AdjointFilterBank filters) { }
}
