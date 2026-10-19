package io.github.antctrlwin.anomalib4j.canonical;

import java.util.Objects;

/** Sequential scalar reference samplers; caller owns the output tensor. */
public final class AffineTensorSampler {
    public enum Kernel { BILINEAR, BICUBIC, WIDENED_TENT }
    public static final int SOURCE_SIZE = 700, INPUT_SIZE = 224;
    public static final int PLANE = INPUT_SIZE*INPUT_SIZE, ELEMENTS = 3*PLANE;
    public static final double SCALE = 700.0/224;
    private final Kernel kernel;
    private final double[] rgb = new double[3];

    public AffineTensorSampler(Kernel kernel) { this.kernel = Objects.requireNonNull(kernel); }
    public Kernel kernel() { return kernel; }
    public String preprocessingId() {
        return switch (kernel) {
            case BILINEAR -> "canonical700-to224-bilinear-imagenet-v1";
            case BICUBIC -> "canonical700-to224-keys-a-0.5-clip-imagenet-v1";
            case WIDENED_TENT -> "canonical700-to224-rotated-scaled-tent-imagenet-v1";
        };
    }

    public void sample(int[] source, RigidTransform forward, float[] destination) {
        Objects.requireNonNull(forward);
        if (source.length != SOURCE_SIZE*SOURCE_SIZE || destination.length != ELEMENTS) {
            throw new IllegalArgumentException("Expected RGB700 source and NCHW224 destination");
        }
        checkCoordinates(forward);
        double stepX = SCALE*forward.cos(), stepY = -SCALE*forward.sin();
        for (int y = 0; y < INPUT_SIZE; y++) {
            double qx = SCALE*.5-.5, qy = SCALE*(y+.5)-.5;
            double sx = forward.sourceX(qx, qy), sy = forward.sourceY(qx, qy);
            for (int x = 0; x < INPUT_SIZE; x++, sx += stepX, sy += stepY) {
                switch (kernel) {
                    case BILINEAR -> point(source, sx, sy, false);
                    case BICUBIC -> point(source, sx, sy, true);
                    case WIDENED_TENT -> tent(source, forward, sx, sy);
                }
                int p = y*INPUT_SIZE+x;
                destination[p] = (float) ((clipped(rgb[0])/255.0-.485)/.229);
                destination[PLANE+p] = (float) ((clipped(rgb[1])/255.0-.456)/.224);
                destination[2*PLANE+p] = (float) ((clipped(rgb[2])/255.0-.406)/.225);
            }
        }
    }

    private static double clipped(double value) {
        if (!Double.isFinite(value)) throw new IllegalStateException("Nonfinite sample");
        return Math.clamp(value, 0, 255);
    }

    private static void checkCoordinates(RigidTransform transform) {
        for (int y = 0; y <= 1; y++) for (int x = 0; x <= 1; x++) {
            double sx = transform.sourceX(x*699.0, y*699.0), sy = transform.sourceY(x*699.0, y*699.0);
            if (!Double.isFinite(sx) || !Double.isFinite(sy) || Math.abs(sx) > Integer.MAX_VALUE-32.0
                    || Math.abs(sy) > Integer.MAX_VALUE-32.0) {
                throw new IllegalArgumentException("Sampling coordinates exceed integer tap range");
            }
        }
    }

    private void point(int[] source, double x, double y, boolean cubic) {
        int baseX = (int) Math.floor(x), baseY = (int) Math.floor(y);
        int start = cubic ? -1 : 0, end = cubic ? 2 : 1;
        rgb[0] = 0; rgb[1] = 0; rgb[2] = 0;
        double weightSum = 0;
        for (int j = start; j <= end; j++) {
            double wy = cubic ? cubic(y-baseY-j) : (j == 0 ? 1-(y-baseY) : y-baseY);
            for (int i = start; i <= end; i++) {
                double wx = cubic ? cubic(x-baseX-i) : (i == 0 ? 1-(x-baseX) : x-baseX);
                double weight = wx*wy;
                accumulate(source, baseX+i, baseY+j, weight);
                weightSum += weight;
            }
        }
        normalize(weightSum);
    }

    static double cubic(double coordinate) {
        double t = Math.abs(coordinate);
        if (t <= 1) return ((1.5*t-2.5)*t)*t+1;
        if (t < 2) return ((-.5*t+2.5)*t-4)*t+2;
        return 0;
    }

    private void tent(int[] source, RigidTransform transform, double x, double y) {
        double radius = SCALE*(Math.abs(transform.cos())+Math.abs(transform.sin()));
        int minX = (int) Math.ceil(x-radius), maxX = (int) Math.floor(x+radius);
        int minY = (int) Math.ceil(y-radius), maxY = (int) Math.floor(y+radius);
        rgb[0] = 0; rgb[1] = 0; rgb[2] = 0;
        double weightSum = 0;
        for (int j = minY; j <= maxY; j++) for (int i = minX; i <= maxX; i++) {
            double dx = i-x, dy = j-y;
            double ux = (transform.cos()*dx-transform.sin()*dy)/SCALE;
            double uy = (transform.sin()*dx+transform.cos()*dy)/SCALE;
            double weight = Math.max(0, 1-Math.abs(ux))*Math.max(0, 1-Math.abs(uy));
            accumulate(source, i, j, weight);
            weightSum += weight;
        }
        normalize(weightSum);
    }

    private void accumulate(int[] source, int x, int y, double weight) {
        int pixel = source[Math.clamp(y, 0, SOURCE_SIZE-1)*SOURCE_SIZE+Math.clamp(x, 0, SOURCE_SIZE-1)];
        rgb[0] += weight*((pixel>>>16)&255);
        rgb[1] += weight*((pixel>>>8)&255);
        rgb[2] += weight*(pixel&255);
    }

    private void normalize(double weightSum) {
        if (!Double.isFinite(weightSum) || weightSum <= 0) throw new IllegalStateException("Invalid filter weights");
        rgb[0] /= weightSum; rgb[1] /= weightSum; rgb[2] /= weightSum;
    }
}
