package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class DatasetInfrastructureTest {
    @org.junit.jupiter.api.io.TempDir Path temporary;

    @Test void generatesValidatedManifestsForRectangularImagesAndDynamicDefects() throws Exception {
        Path dataset = temporary.resolve("dataset");
        DatasetFixture.metadata(dataset, "wood", "new_defect");
        for (int i = 7; i < 12; i++) DatasetFixture.image(dataset, "train", "wood_good_%03d.png".formatted(i), 9, 6);
        DatasetFixture.image(dataset, "test", "wood_good_015.png", 13, 8);
        DatasetFixture.image(dataset, "test", "wood_new_defect_016.png", 7, 11);
        Path output = temporary.resolve("manifests");
        DatasetManifests.generate(dataset, output, 42, java.util.List.of("wood"));
        assertEquals(3, Files.readAllLines(output.resolve("wood/anomalib4j-fit.txt")).size());
        assertEquals(2, Files.readAllLines(output.resolve("wood/anomalib4j-calibration.txt")).size());
        assertEquals("wood_good_015.png", DatasetManifests.fixture(dataset, output.resolve("wood"), false).getFileName().toString());
        var metadata = new DatasetMetadata(dataset);
        var image = metadata.inspect("test", "wood_new_defect_016.png");
        assertEquals(7, image.width());
        assertEquals(11, image.height());
        Path ann = dataset.resolve("test/ann/wood_new_defect_016.png.json");
        String valid = Files.readString(ann);
        for (String invalid : new String[]{valid.replace("\"classId\":10", "\"classId\":99"),
                valid.replace("\"width\":7", "\"width\":8"), valid.replace("\"name\":\"wood\"", "\"name\":\"cable\"")}) {
            Files.writeString(ann, invalid);
            assertThrows(java.io.IOException.class, () -> metadata.inspect("test", "wood_new_defect_016.png"));
        }
    }
    @Test void acceptsCategoriesAndDynamicDefects() {
        assertEquals(15, MvtecCategories.names().size());
        for (String category : new String[]{"cable", "capsule", "wood", "metal_nut"}) {
            assertEquals(category, EvaluationLabels.category(category + "_new_defect_001.png"));
            assertEquals("new_defect", EvaluationLabels.defect(category + "_new_defect_001.png"));
        }
    }

    @Test void splitMatchesBothHistoricalManifestsExactly() throws Exception {
        for (String category : new String[]{"bottle", "metal_nut"}) {
            Path old = Path.of("docs/benchmark/benchmark-prerun/data", category);
            var split = DatasetManifests.split(Files.readAllLines(old.resolve("competitor-fit.txt")), 42);
            assertEquals(Files.readAllLines(old.resolve("anomalib4j-fit.txt")), split.fit());
            assertEquals(Files.readAllLines(old.resolve("anomalib4j-calibration.txt")), split.calibration());
        }
    }

    @Test void splitIsProportionalDeterministicAndValidForSmallCounts() {
        var names = IntStream.range(0, 391).mapToObj(i -> "hazelnut_good_%03d.png".formatted(i)).toList();
        var split = DatasetManifests.split(names, 17);
        assertEquals(312, split.fit().size());
        assertEquals(79, split.calibration().size());
        assertEquals(split, DatasetManifests.split(names.reversed(), 17));
        assertNotEquals(split, DatasetManifests.split(names, 18));
        assertEquals(1, DatasetManifests.split(names.subList(0, 3), 42).fit().size());
        assertEquals(2, DatasetManifests.split(names.subList(0, 3), 42).calibration().size());
        assertThrows(IllegalArgumentException.class, () -> DatasetManifests.split(names.subList(0, 2), 42));
        assertThrows(IllegalArgumentException.class, () -> DatasetManifests.split(java.util.List.of("x", "x", "y"), 42));
    }
}
