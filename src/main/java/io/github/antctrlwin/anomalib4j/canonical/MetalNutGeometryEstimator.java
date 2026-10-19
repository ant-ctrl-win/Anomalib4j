package io.github.antctrlwin.anomalib4j.canonical;

import java.util.Arrays;
import static io.github.antctrlwin.anomalib4j.canonical.CanonicalGeometry.*;

/** Sequential reusable Pillow11.3.0/SciPy1.16.0 geometry workspace. Not thread-safe. */
public final class MetalNutGeometryEstimator {
    public static final int SIZE = 175;
    private static final int COUNT = SIZE*SIZE;
    private final byte[] gray = new byte[COUNT];
    private final int[] labels = new int[COUNT], queue = new int[COUNT];
    private final boolean[] mask = new boolean[COUNT], exterior = new boolean[COUNT];
    private final int[] histogram = new int[256];
    private int selectedLabel;

    public CanonicalGeometry estimate(int[] rgb) {
        if (rgb.length != 700*700) throw new IllegalArgumentException("Expected packed RGB700");
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int vertical = 0;
            for (int dy = 0; dy < 4; dy++) {
                int horizontal = 0, start = (4*y+dy)*700+4*x;
                for (int dx = 0; dx < 4; dx++) {
                    int p = rgb[start+dx];
                    horizontal += (19595*((p>>>16)&255)+38470*((p>>>8)&255)+7471*(p&255)+32768)>>>16;
                }
                // Pillow BOX quantizes the horizontal pass before the vertical pass.
                vertical += (horizontal+2)>>>2;
            }
            gray[y*SIZE+x] = (byte) ((vertical+2)>>>2);
        }
        return analyzeReduced();
    }

    CanonicalGeometry estimateReduced(byte[] reduced) {
        if (reduced.length != COUNT) throw new IllegalArgumentException("Expected gray175");
        System.arraycopy(reduced, 0, gray, 0, COUNT);
        return analyzeReduced();
    }

    public byte[] reducedImage() { return gray.clone(); }
    public boolean[] filledMask() { return mask.clone(); }
    public boolean[] selectedComponent() {
        boolean[] selected = new boolean[COUNT];
        for (int p = 0; p < COUNT; p++) selected[p] = selectedLabel != 0 && labels[p] == selectedLabel;
        return selected;
    }

    private CanonicalGeometry analyzeReduced() {
        Arrays.fill(histogram, 0);
        for (byte v : gray) histogram[v&255]++;
        int threshold = threshold();
        Arrays.fill(labels, 0);
        int label = 0, largest = 0, largestSize = 0;
        for (int p = 0; p < COUNT; p++) {
            if ((gray[p]&255) <= threshold || labels[p] != 0) continue;
            label++;
            int head = 0, tail = 1;
            queue[0] = p; labels[p] = label;
            while (head < tail) {
                int cell = queue[head++], x = cell%SIZE, y = cell/SIZE;
                for (int ny = Math.max(0,y-1); ny <= Math.min(SIZE-1,y+1); ny++)
                    for (int nx = Math.max(0,x-1); nx <= Math.min(SIZE-1,x+1); nx++) {
                        int neighbor = ny*SIZE+nx;
                        if (labels[neighbor] == 0 && (gray[neighbor]&255) > threshold) {
                            labels[neighbor] = label; queue[tail++] = neighbor;
                        }
                    }
            }
            if (tail > largestSize) { largestSize = tail; largest = label; }
        }
        selectedLabel = largest;
        for (int p = 0; p < COUNT; p++) mask[p] = largest != 0 && labels[p] == largest;
        fillHoles();
        return moments();
    }

    private int threshold() {
        double totalSum = 0;
        for (int i = 0; i < 256; i++) totalSum += histogram[i]*i;
        double weight = 0, sum = 0, best = -1;
        int selected = 0;
        for (int i = 0; i < 256; i++) {
            weight += histogram[i]; sum += histogram[i]*i;
            double denominator = weight*(COUNT-weight);
            double numerator = totalSum*weight-sum*COUNT;
            double value = denominator > 0 ? numerator*numerator/denominator : 0;
            if (value > best) { best = value; selected = i; }
        }
        return selected;
    }

    private void fillHoles() {
        Arrays.fill(exterior, false);
        int tail = 0;
        for (int p = 0; p < COUNT; p++) {
            if ((p/SIZE == 0 || p/SIZE == SIZE-1 || p%SIZE == 0 || p%SIZE == SIZE-1) && !mask[p]) {
                exterior[p] = true; queue[tail++] = p;
            }
        }
        int head = 0;
        while (head < tail) {
            int p = queue[head++];
            if (p%SIZE > 0) tail = visitBackground(p-1, tail);
            if (p%SIZE < SIZE-1) tail = visitBackground(p+1, tail);
            if (p >= SIZE) tail = visitBackground(p-SIZE, tail);
            if (p < COUNT-SIZE) tail = visitBackground(p+SIZE, tail);
        }
        for (int p = 0; p < COUNT; p++) mask[p] = !exterior[p];
    }

    private int visitBackground(int p, int tail) {
        if (!mask[p] && !exterior[p]) { exterior[p] = true; queue[tail++] = p; }
        return tail;
    }

    private CanonicalGeometry moments() {
        int area = 0;
        double sumX = 0, sumY = 0;
        boolean border = false;
        for (int p = 0; p < COUNT; p++) if (mask[p]) {
            int x = p%SIZE, y = p/SIZE;
            area++; sumX += x; sumY += y;
            border |= x == 0 || y == 0 || x == SIZE-1 || y == SIZE-1;
        }
        double fraction = (double) area/COUNT;
        if (fraction < .2 || fraction > .8 || border) {
            return new CanonicalGeometry(Status.FAILURE, Reason.AREA_OR_BORDER, 349.5, 349.5, 0, fraction, 0, 0);
        }
        double cx = sumX/area, cy = sumY/area, re = 0, im = 0;
        for (int p = 0; p < COUNT; p++) if (mask[p]) {
            double dx = p%SIZE-cx, dy = p/SIZE-cy, a = dx*dx, b = dy*dy, r2 = a+b;
            if (r2 == 0) { re += 1; continue; }
            double inverse = 1/(r2*r2);
            re += (a*a-6*a*b+b*b)*inverse;
            im += 4*dx*dy*(a-b)*inverse;
        }
        re /= area; im /= area;
        double strength = Math.hypot(re, im), angle = Math.toDegrees(Math.atan2(im, re))/4;
        return new CanonicalGeometry(strength < .03 ? Status.FAILURE : Status.SUCCESS,
                strength < .03 ? Reason.WEAK_FOURFOLD_HARMONIC : Reason.OK,
                (cx+.5)*4-.5, (cy+.5)*4-.5, angle, fraction, strength, Math.sqrt(area/Math.PI)*4);
    }
}
