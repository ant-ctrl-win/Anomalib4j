package io.github.antctrlwin.anomalib4j.model;

import org.junit.jupiter.api.Test;
import io.github.antctrlwin.anomalib4j.memory.ProjectionStatistics;

import static org.junit.jupiter.api.Assertions.*;

class PreprocessingContractTest {
    @Test
    void preprocessingIdentityParticipatesInCompatibility() {
        var grid = new GridShape(1, 1, 2);
        var policy = new NormalizationPolicy(1e-6);
        var first = new ModelDescriptor("m", "cnn", "layer", grid, 3, 42, policy, 1, "resize-v1");
        var second = new ModelDescriptor("m", "cnn", "layer", grid, 3, 42, policy, 1, "crop-v1");
        var legacy = new ModelDescriptor("m", "cnn", "layer", grid, 3, 42, policy, 1);
        assertNotEquals(first, second);
        assertNotEquals(first, legacy);
        var statistics = new ProjectionStatistics(new double[3], new double[]{1, 1, 1}).withDescriptor(first);
        assertThrows(IllegalArgumentException.class, () -> statistics.requireCompatible(second));
        assertThrows(IllegalArgumentException.class, () -> new ModelDescriptor("m", "cnn", "layer", grid, 3, 42, policy, 1, " "));
    }
}
