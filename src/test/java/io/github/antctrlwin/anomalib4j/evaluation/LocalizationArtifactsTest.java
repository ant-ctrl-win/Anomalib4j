package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationArtifactsTest {
    @TempDir Path temporary;

    @Test void readsCompleteUnorderedMapWithoutChangingRawScores() throws Exception {
        var text = new StringBuilder("filename,row,column,raw_discrepancy\n");
        for (int p = 195; p >= 0; p--) text.append("bottle_good_000.png,").append(p / 14).append(',')
                .append(p % 14).append(',').append(p - 100).append('\n');
        var maps = LocalizationArtifacts.readRaw(Files.writeString(temporary.resolve("raw.csv"), text));
        assertEquals(1, maps.size());
        for (int p = 0; p < 196; p++) assertEquals(p - 100, maps.get("bottle_good_000.png")[p]);
    }

    @Test void rejectsMissingDuplicateAndNonfiniteCells() throws Exception {
        String header = "filename,row,column,raw_discrepancy\n";
        String cell = "bottle_good_000.png,0,0,-5\n";
        for (String body : new String[]{cell, cell + cell, "bottle_good_000.png,0,0,NaN\n", "bottle_good_000.png,14,0,2\n"}) {
            Path file = Files.writeString(temporary.resolve("invalid.csv"), header + body);
            assertThrows(IOException.class, () -> LocalizationArtifacts.readRaw(file));
        }
    }
}
