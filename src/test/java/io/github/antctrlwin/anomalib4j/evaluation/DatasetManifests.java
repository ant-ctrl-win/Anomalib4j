package io.github.antctrlwin.anomalib4j.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

public final class DatasetManifests {
    private DatasetManifests() { }
    record Split(List<String> fit, List<String> calibration) { }

    static Split split(List<String> inputs, long seed) {
        if (inputs.size() < 3 || new HashSet<>(inputs).size() != inputs.size())
            throw new IllegalArgumentException("Split requires at least three distinct normal images");
        var names = new ArrayList<>(inputs.stream().sorted().toList());
        Collections.shuffle(names, new Random(seed));
        int fitCount = Math.max(1, Math.min(names.size() - 2, (int) ((long) names.size() * 4 / 5)));
        return new Split(List.copyOf(names.subList(0, fitCount)), List.copyOf(names.subList(fitCount, names.size())));
    }

    public static void main(String[] args) throws Exception {
        generate(Path.of(args[0]), Path.of(args[1]), Long.parseLong(args[2]), List.of(args[3].split(",")));
    }

    static void generate(Path dataset, Path output, long seed, List<String> categories) throws Exception {
        if (categories.isEmpty() || new HashSet<>(categories).size() != categories.size())
            throw new IllegalArgumentException("Distinct categories required");
        for (String category : categories) MvtecCategories.require(category);
        var metadata = new DatasetMetadata(dataset);
        var entries = new LinkedHashMap<String, List<DatasetMetadata.Image>>();
        for (String category : categories) {
            for (String role : List.of("train", "test")) {
                List<Path> paths;
                try (var stream = Files.list(dataset.resolve(role + "/img"))) {
                    paths = stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().startsWith(category + "_"))
                            .sorted().toList();
                }
                var images = new ArrayList<DatasetMetadata.Image>();
                for (Path path : paths) images.add(metadata.inspect(role, path.getFileName().toString()));
                if (role.equals("test") && (images.stream().noneMatch(DatasetMetadata.Image::good)
                        || images.stream().allMatch(DatasetMetadata.Image::good)))
                    throw new IllegalArgumentException("Test requires normal and anomalous images: " + category);
                entries.put(category + "/" + role, List.copyOf(images));
            }
            split(entries.get(category + "/train").stream().map(DatasetMetadata.Image::filename).toList(), seed);
        }
        Files.createDirectories(output);
        for (String category : categories) {
            Path directory = Files.createDirectory(output.resolve(category));
            List<String> normal = entries.get(category + "/train").stream().map(DatasetMetadata.Image::filename).toList();
            Split split = split(normal, seed);
            Files.write(directory.resolve("competitor-fit.txt"), normal, StandardOpenOption.CREATE_NEW);
            Files.write(directory.resolve("anomalib4j-fit.txt"), split.fit(), StandardOpenOption.CREATE_NEW);
            Files.write(directory.resolve("anomalib4j-calibration.txt"), split.calibration(), StandardOpenOption.CREATE_NEW);
            Files.write(directory.resolve("test.txt"), entries.get(category + "/test").stream()
                    .map(DatasetMetadata.Image::filename).toList(), StandardOpenOption.CREATE_NEW);
        }
        ComparisonArtifacts.json(output.resolve("images.json"), entries);
    }

    static Path fixture(Path dataset, Path manifests, boolean smoke) throws Exception {
        String role = smoke ? "train" : "test";
        return Files.readAllLines(manifests.resolve(smoke ? "competitor-fit.txt" : "test.txt")).stream()
                .sorted().filter(n -> EvaluationLabels.defect(n).equals("good")).findFirst()
                .map(n -> dataset.resolve(role + "/img/" + n))
                .orElseThrow(() -> new IllegalArgumentException("No normal timing fixture"));
    }
}
