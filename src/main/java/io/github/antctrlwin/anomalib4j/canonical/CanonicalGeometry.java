package io.github.antctrlwin.anomalib4j.canonical;

/** Immutable pose; failed estimates cannot supply a sampling transform. */
public record CanonicalGeometry(Status status, Reason reason, double cx, double cy,
                                double angle, double areaFraction, double harmonic4, double radius) {
    public enum Status { SUCCESS, FAILURE }
    public enum Reason { OK, AREA_OR_BORDER, WEAK_FOURFOLD_HARMONIC }

    public CanonicalGeometry {
        java.util.Objects.requireNonNull(status);
        java.util.Objects.requireNonNull(reason);
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(angle)
                || !Double.isFinite(areaFraction) || !Double.isFinite(harmonic4) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("Nonfinite geometry");
        }
        if ((status == Status.SUCCESS) != (reason == Reason.OK)) {
            throw new IllegalArgumentException("Inconsistent geometry status");
        }
    }

    public RigidTransform transform() {
        if (status != Status.SUCCESS) throw new IllegalStateException("Geometry failed: " + reason);
        return RigidTransform.center(cx, cy, angle);
    }
}
