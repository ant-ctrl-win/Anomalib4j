package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalM2BenchmarkTest {
    @TempDir Path temporary;
    @Test void repeatedProcessIdGetsDistinctEvidenceWithoutOverwritingFirstTrial() throws Exception {
        Path first = CanonicalM2Benchmark.trialEvidence(temporary);
        Files.writeString(first.resolve("runtime.json"),"first",StandardOpenOption.CREATE_NEW);
        Path second = CanonicalM2Benchmark.trialEvidence(temporary);
        Files.writeString(second.resolve("runtime.json"),"second",StandardOpenOption.CREATE_NEW);
        assertNotEquals(first,second);
        assertEquals("first",Files.readString(first.resolve("runtime.json")));
        assertEquals("second",Files.readString(second.resolve("runtime.json")));
        assertEquals(temporary,first.getParent()); assertEquals(temporary,second.getParent());
    }
}
