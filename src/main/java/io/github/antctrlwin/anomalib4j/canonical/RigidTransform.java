package io.github.antctrlwin.anomalib4j.canonical;

/** Source-to-canonical transform in integer pixel-center coordinates, y down. */
public record RigidTransform(double cos, double sin, double tx, double ty) {
    public RigidTransform {
        if (!Double.isFinite(cos) || !Double.isFinite(sin) || !Double.isFinite(tx) || !Double.isFinite(ty)
                || Math.abs(cos*cos + sin*sin - 1) > 1e-12) {
            throw new IllegalArgumentException("Expected finite rigid transform");
        }
    }

    public static RigidTransform center(double cx, double cy, double angleDegrees) {
        double radians = Math.toRadians(-angleDegrees);
        double c = Math.cos(radians), s = Math.sin(radians);
        if (angleDegrees % 90 == 0) {
            int quarter = Math.floorMod((int) ((-angleDegrees % 360) / 90), 4);
            c = switch (quarter) { case 0 -> 1; case 2 -> -1; default -> 0; };
            s = switch (quarter) { case 1 -> 1; case 3 -> -1; default -> 0; };
        }
        return new RigidTransform(c, s, 349.5-c*cx+s*cy, 349.5-s*cx-c*cy);
    }

    public double canonicalX(double x, double y) { return cos*x-sin*y+tx; }
    public double canonicalY(double x, double y) { return sin*x+cos*y+ty; }
    public double sourceX(double x, double y) { return cos*(x-tx)+sin*(y-ty); }
    public double sourceY(double x, double y) { return -sin*(x-tx)+cos*(y-ty); }
}
