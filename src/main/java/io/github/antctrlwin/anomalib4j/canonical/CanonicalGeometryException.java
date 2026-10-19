package io.github.antctrlwin.anomalib4j.canonical;

/** No score exists for this input; no encoder invocation or fallback follows failure. */
public final class CanonicalGeometryException extends IllegalArgumentException {
    private final CanonicalGeometry geometry;
    public CanonicalGeometryException(CanonicalGeometry geometry) {
        super("Canonical geometry failed: " + geometry.reason());
        if (geometry.status() != CanonicalGeometry.Status.FAILURE) throw new IllegalArgumentException("Expected failed geometry");
        this.geometry = geometry;
    }
    public CanonicalGeometry geometry() { return geometry; }
}
