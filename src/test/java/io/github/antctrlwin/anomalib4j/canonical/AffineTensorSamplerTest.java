package io.github.antctrlwin.anomalib4j.canonical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.antctrlwin.anomalib4j.canonical.AffineTensorSampler.*;

class AffineTensorSamplerTest {
    private static final int[][] POINTS = {{0,0},{223,0},{0,223},{223,223},{111,111},{112,112},{101,87},{20,170}};

    @ParameterizedTest @EnumSource(Kernel.class)
    void identityXAndYRampsUseHalfPixelNchwAndNormalization(Kernel kernel) {
        int[] source = new int[700*700];
        for (int y = 0; y < 700; y++) for (int x = 0; x < 700; x++) source[y*700+x] = ((x/4)<<16)|((y/4)<<8)|73;
        check(source, new RigidTransform(1,0,0,0), kernel);
    }

    @ParameterizedTest @EnumSource(Kernel.class)
    void constantRgbIsPreservedAtInteriorAndBeyondEveryBoundary(Kernel kernel) {
        int[] source = new int[700*700]; Arrays.fill(source, 0x206080);
        var sampler = new AffineTensorSampler(kernel); float[] output = new float[ELEMENTS];
        for (double shift : new double[]{0, .5, 500, -500, 1600, -1600}) {
            sampler.sample(source, new RigidTransform(1,0,shift,-shift), output);
            for (int p = 0; p < PLANE; p++) {
                assertEquals(normalized(32,0), output[p]);
                assertEquals(normalized(96,1), output[PLANE+p]);
                assertEquals(normalized(128,2), output[2*PLANE+p]);
            }
        }
    }

    @ParameterizedTest @EnumSource(Kernel.class)
    void impulseIntegerAndHalfPixelTranslationsMatchIndependentWeights(Kernel kernel) {
        int[] source = new int[700*700]; source[273*700+317] = 0xffffff;
        check(source, new RigidTransform(1,0,0,0), kernel);
        check(source, new RigidTransform(1,0,1,-1), kernel);
        check(source, new RigidTransform(1,0,.5,-.5), kernel);
        var output = new float[ELEMENTS]; new AffineTensorSampler(kernel).sample(source, new RigidTransform(1,0,0,0), output);
        assertTrue(output[87*224+101] > normalized(0,0));
    }

    @ParameterizedTest @EnumSource(Kernel.class)
    void quarterTurnsOffCenterRotationAndAllFourBordersMatchIndependentPull(Kernel kernel) {
        int[] source = new int[700*700];
        for (int y = 0; y < 700; y++) for (int x = 0; x < 700; x++)
            source[y*700+x] = (((3*x+y)%256)<<16)|(((x+2*y)%256)<<8)|((x+y)%256);
        for (double angle : new double[]{90,-90,31.7}) check(source, RigidTransform.center(330.25,360.5,angle), kernel);
        for (double shift : new double[]{.5,-.5,700,-700}) check(source, new RigidTransform(1,0,shift,shift), kernel);
    }

    @ParameterizedTest @EnumSource(Kernel.class)
    void outputsAreFiniteDeterministicAndCallerOwned(Kernel kernel) {
        int[] source = new int[700*700];
        for (int i = 0; i < source.length; i++) source[i] = i*7919;
        int[] original = source.clone(); var sampler = new AffineTensorSampler(kernel);
        var transform = RigidTransform.center(350,346,-43.75);
        float[] first = new float[ELEMENTS], second = new float[ELEMENTS];
        sampler.sample(source, transform, first); sampler.sample(source, transform, second);
        assertArrayEquals(first, second); assertArrayEquals(original, source);
        for (float value : first) assertTrue(Float.isFinite(value));
        first[0] = Float.NaN; sampler.sample(source, transform, second);
        assertTrue(Float.isFinite(second[0]));
    }

    @Test void sourceCenterIsFractionalTensorCenterAndRigidInverseIsAnalytic() {
        var transform = RigidTransform.center(330.25,360.75,27.3);
        double center = SCALE*(111.5+.5)-.5;
        assertEquals(349.5, center);
        assertEquals(330.25, transform.sourceX(center,center), 1e-12);
        assertEquals(360.75, transform.sourceY(center,center), 1e-12);
        assertEquals(349.5, transform.canonicalX(330.25,360.75), 1e-12);
        assertEquals(349.5, transform.canonicalY(330.25,360.75), 1e-12);
        assertEquals(347.9375, SCALE*111.5-.5);
        assertEquals(351.0625, SCALE*112.5-.5);
    }

    @Test void catmullRomKernelAndOvershootClippingAreExplicit() {
        assertEquals(1, AffineTensorSampler.cubic(0));
        assertEquals(.5625, AffineTensorSampler.cubic(.5));
        assertEquals(-.0625, AffineTensorSampler.cubic(1.5));
        assertEquals(0, AffineTensorSampler.cubic(2));
        int[] step = new int[700*700];
        for (int y = 0; y < 700; y++) for (int x = 0; x < 700; x++) step[y*700+x] = x < 317 ? 0 : 0xffffff;
        check(step, new RigidTransform(1,0,.5,0), Kernel.BICUBIC);
    }

    @Test void invalidShapesAndNonfiniteOrNonrigidGeometryAreRejected() {
        var sampler = new AffineTensorSampler(Kernel.BILINEAR);
        assertThrows(IllegalArgumentException.class, () -> sampler.sample(new int[3],new RigidTransform(1,0,0,0),new float[ELEMENTS]));
        assertThrows(IllegalArgumentException.class, () -> sampler.sample(new int[700*700],new RigidTransform(1,0,0,0),new float[3]));
        assertThrows(IllegalArgumentException.class, () -> new RigidTransform(Double.NaN,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new RigidTransform(2,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> sampler.sample(new int[700*700],new RigidTransform(1,0,1e300,0),new float[ELEMENTS]));
    }

    private static void check(int[] source, RigidTransform transform, Kernel kernel) {
        float[] output = new float[ELEMENTS]; new AffineTensorSampler(kernel).sample(source, transform, output);
        for (int[] point : POINTS) {
            double[] expected = oracle(source, transform, point, kernel);
            int offset = point[1]*224+point[0];
            for (int c = 0; c < 3; c++) assertEquals(normalized(expected[c],c), output[c*PLANE+offset], 1e-6,
                    kernel+" point="+Arrays.toString(point)+" channel="+c);
        }
    }

    // Independent fixed-window filter sum uses absolute forward coordinates, without affine stepping.
    private static double[] oracle(int[] source, RigidTransform transform, int[] point, Kernel kernel) {
        double qx = (point[0]+.5)*700/224-.5, qy = (point[1]+.5)*700/224-.5;
        double x = transform.sourceX(qx,qy), y = transform.sourceY(qx,qy);
        double[] rgb = new double[3]; double sum = 0;
        for (int j = (int)Math.floor(y)-10; j <= (int)Math.floor(y)+10; j++)
            for (int i = (int)Math.floor(x)-10; i <= (int)Math.floor(x)+10; i++) {
                double dx = i-x, dy = j-y, weight = switch (kernel) {
                    case BILINEAR -> Math.max(0,1-Math.abs(dx))*Math.max(0,1-Math.abs(dy));
                    case BICUBIC -> referenceCubic(dx)*referenceCubic(dy);
                    case WIDENED_TENT -> Math.max(0,1-Math.abs((transform.canonicalX(i,j)-qx)/SCALE))
                            *Math.max(0,1-Math.abs((transform.canonicalY(i,j)-qy)/SCALE));
                };
                int pixel = source[Math.max(0,Math.min(699,j))*700+Math.max(0,Math.min(699,i))];
                for (int c = 0; c < 3; c++) rgb[c] += weight*((pixel>>>(16-8*c))&255);
                sum += weight;
            }
        for (int c = 0; c < 3; c++) rgb[c] = Math.max(0,Math.min(255,rgb[c]/sum));
        return rgb;
    }

    private static double referenceCubic(double value) {
        double t = Math.abs(value), a = -.5;
        return t < 1 ? (a+2)*Math.pow(t,3)-(a+3)*t*t+1
                : t < 2 ? a*Math.pow(t,3)-5*a*t*t+8*a*t-4*a : 0;
    }
    private static float normalized(double value, int channel) {
        return switch (channel) {
            case 0 -> (float)((value/255.0-.485)/.229);
            case 1 -> (float)((value/255.0-.456)/.224);
            case 2 -> (float)((value/255.0-.406)/.225);
            default -> throw new IllegalArgumentException();
        };
    }
}
