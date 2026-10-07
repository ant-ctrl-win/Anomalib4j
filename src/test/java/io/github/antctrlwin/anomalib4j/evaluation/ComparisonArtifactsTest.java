package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonArtifactsTest {
    @TempDir Path temporary;

    @Test void losslessJavaExportCanBeReadWithVerifiedSidecar() throws Exception {
        Path path = temporary.resolve("metal_nut_flip_000.native.npy");
        double[] values = {-3.5, Math.PI, 1e-12, 9};
        ComparisonArtifacts.writeMap(path, values, 2, 2, "metal_nut_flip_000.png", "metal_nut-700x700-14x14", "native");
        assertArrayEquals(values, NpyMap.read(path).values(), 0);
        assertEquals("npy+json/v1", NpyMap.readSidecar(path).format());
        assertEquals(NpyMap.sha256(path), NpyMap.readSidecar(path).sha256());
    }

    @Test void evaluationUsesNativeImageScoreRatherThanMapMaximum() {
        assertEquals(1, BottleEvaluation.auroc(new double[]{2}, new double[]{9}));
        assertEquals(0, BottleEvaluation.auroc(new double[]{9}, new double[]{2}));
    }
}
