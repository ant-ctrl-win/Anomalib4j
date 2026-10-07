package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

class BottleHeldOutMultiSeedIT {
    @Test void comparesFiveHeldOutSplits() throws Exception {
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        Path output = Files.createDirectories(Path.of("target/bottle-spatial14-heldout-multiseed"));
        var runs = new ArrayList<HeldOutMultiSeedSummary.Run>();
        for (long seed : HeldOutMultiSeedSummary.SEEDS) {
            System.out.println("Held-out split seed " + seed);
            runs.add(BottleHeldOutCalibrationIT.run(dataset, output.resolve("seed-" + seed), seed));
        }
        var summary = new HeldOutMultiSeedSummary(runs);
        Files.writeString(output.resolve("per-seed.csv"), summary.perSeedCsv());
        Files.writeString(output.resolve("summary.csv"), summary.summaryCsv());
        Files.writeString(output.resolve("delta-signs.csv"), summary.signsCsv());
        Files.writeString(output.resolve("summary.md"), summary.report());
        System.out.println(summary.report());
    }
}
