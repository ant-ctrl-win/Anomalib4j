// Run: jshell tools/prerun/splits.jsh (repository root). No model building.
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.security.*;
var data = Path.of("../Anomalib4j_md/mvtec-ad-DatasetNinja");
var out = Path.of("docs/benchmark/benchmark-prerun/data");
for (var category : List.of("bottle", "metal_nut")) {
    var count = category.equals("bottle") ? 209 : 220;
    var fitCount = category.equals("bottle") ? 167 : 176;
    List<String> names;
    try (var stream = Files.list(data.resolve("train/img"))) {
        names = new ArrayList<>(stream.map(p -> p.getFileName().toString()).filter(n -> n.matches(category + "_good_[0-9]+\\.png")).sorted().toList());
    }
    if (names.size() != count || new HashSet<>(names).size() != count) throw new IllegalStateException(category);
    Files.createDirectories(out.resolve(category));
    Files.write(out.resolve(category).resolve("competitor-fit.txt"), names, StandardCharsets.UTF_8);
    Collections.shuffle(names, new Random(42L));
    var fit = names.subList(0, fitCount);
    var calibration = names.subList(fitCount, count);
    if (!Collections.disjoint(fit, calibration) || new HashSet<>(names).size() != count) throw new IllegalStateException("Split overlap");
    Files.write(out.resolve(category).resolve("anomalib4j-fit.txt"), fit, StandardCharsets.UTF_8);
    Files.write(out.resolve(category).resolve("anomalib4j-calibration.txt"), calibration, StandardCharsets.UTF_8);
    if (category.equals("bottle")) {
        var old = Path.of("target/bottle-spatial14-heldout-calibration");
        if (!Files.readAllLines(old.resolve("archetype-training.txt")).equals(fit) || !Files.readAllLines(old.resolve("calibration.txt")).equals(calibration)) throw new IllegalStateException("Bottle regression");
    }
    try (var stream = Files.list(data.resolve("test/img"))) {
        var tests = stream.map(p -> p.getFileName().toString()).filter(n -> n.startsWith(category + "_") && n.endsWith(".png")).sorted().toList();
        if (tests.size() != (category.equals("bottle") ? 83 : 115)) throw new IllegalStateException("Test census");
        Files.write(out.resolve(category).resolve("test.txt"), tests, StandardCharsets.UTF_8);
    }
    System.out.println(category + ": fit=" + fit.size() + " calibration=" + calibration.size() + "; disjoint/complete; training-good only");
}
/exit
