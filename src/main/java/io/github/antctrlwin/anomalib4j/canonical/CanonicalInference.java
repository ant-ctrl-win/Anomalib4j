package io.github.antctrlwin.anomalib4j.canonical;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import java.util.Objects;

/** Owns only a native map and immutable metadata; accessor copies prevent external mutation. */
public record CanonicalInference(double score, double[] nativeMap, RigidTransform sourceToCanonical,
                                 String modelIdentity) {
    public CanonicalInference {
        if (Double.compare(score, M2Model.imageScore(nativeMap)) != 0) throw new IllegalArgumentException("Score/map mismatch");
        Objects.requireNonNull(sourceToCanonical);
        if (modelIdentity == null || !modelIdentity.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Model identity required");
        nativeMap = nativeMap.clone();
    }
    @Override public double[] nativeMap() { return nativeMap.clone(); }
    public int sourceWidth() { return 700; }
    public int sourceHeight() { return 700; }
    public int canonicalWidth() { return 700; }
    public int canonicalHeight() { return 700; }
    public int mapWidth() { return 14; }
    public int mapHeight() { return 14; }
    public String preprocessingId() { return CanonicalM2Contract.PREPROCESSING; }
}
