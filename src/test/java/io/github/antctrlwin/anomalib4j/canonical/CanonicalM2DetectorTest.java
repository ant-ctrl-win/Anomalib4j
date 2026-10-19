package io.github.antctrlwin.anomalib4j.canonical;

import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalM2DetectorTest {
    @TempDir Path temporary;
    @Test void fastPathMatchesFrozenCompositionAndMapOwnsStorageAcrossReuseAndClose() throws Exception {
        var bundle = new CanonicalM2Bundle(CanonicalM2BundleTest.model(M2ModelStore.encoderHash()));
        var image = CanonicalPreprocessorTest.cross();
        float[] tensor = new float[AffineTensorSampler.ELEMENTS];
        var processor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        assertEquals(bundle.preprocessingId(), processor.preprocessingId());
        var pose = processor.prepare(image, tensor);
        double[] expected;
        try (var encoder = new OnnxMobileNetV4Encoder()) { expected = bundle.model().score(encoder.extractPrepared(tensor)); }
        CanonicalInference result;
        try (var detector = new CanonicalM2Detector(bundle)) {
            assertEquals(M2Model.imageScore(expected), detector.detectScore(image));
            result = detector.inferWithMap(image);
            assertArrayEquals(expected, result.nativeMap());
            assertEquals(pose.transform(), result.sourceToCanonical());
            assertEquals(bundle.identity(), result.modelIdentity());
            double[] external = result.nativeMap(); external[0] = 999;
            assertArrayEquals(expected, result.nativeMap());
            var other = CanonicalPreprocessorTest.cross(); other.setRGB(350,350,0);
            detector.inferWithMap(other);
            assertArrayEquals(expected, result.nativeMap());
            assertEquals(700, result.sourceWidth()); assertEquals(700, result.canonicalHeight());
            assertEquals(14, result.mapWidth()); assertEquals(14, result.mapHeight());
        }
        assertArrayEquals(expected, result.nativeMap());
    }
    @Test void successfulThenFailedGeometryNeverScoresStaleTensorAndCanRecover() throws Exception {
        var bundle = new CanonicalM2Bundle(CanonicalM2BundleTest.model(M2ModelStore.encoderHash()));
        try (var detector = new CanonicalM2Detector(bundle)) {
            var good = CanonicalPreprocessorTest.cross();
            double expected = detector.detectScore(good);
            var blank = new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB);
            var failure = assertThrows(CanonicalGeometryException.class, () -> detector.detectScore(blank));
            assertEquals(CanonicalGeometry.Status.FAILURE, failure.geometry().status());
            assertThrows(CanonicalGeometryException.class, () -> detector.inferWithMap(blank));
            assertEquals(expected, detector.detectScore(good));
            assertThrows(IllegalArgumentException.class, () -> detector.detectScore(new BufferedImage(224,224,1)));
            assertThrows(NullPointerException.class, () -> detector.detectScore(null));
            assertEquals(expected, detector.detectScore(good));
        }
    }
    @Test void closeIsIdempotentAndBothApisRejectAfterClose() throws Exception {
        var detector = new CanonicalM2Detector(new CanonicalM2Bundle(CanonicalM2BundleTest.model(M2ModelStore.encoderHash())));
        detector.close(); detector.close();
        assertThrows(IllegalStateException.class, () -> detector.detectScore(CanonicalPreprocessorTest.cross()));
        assertThrows(IllegalStateException.class, () -> detector.inferWithMap(CanonicalPreprocessorTest.cross()));
    }
    @Test void trainingUsesIdenticalJavaPreprocessingForBothRolesAndInference() throws Exception {
        var paths = new ArrayList<Path>(); var units = new ArrayList<double[]>();
        var processor = new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        float[] tensor = new float[AffineTensorSampler.ELEMENTS];
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            for (int i = 0; i < 4; i++) {
                var image = CanonicalPreprocessorTest.cross();
                image.setRGB(345+i,350,0x102030*(i+1));
                Path path = temporary.resolve(i+".png"); ImageIO.write(image,"png",path.toFile()); paths.add(path);
                processor.prepare(image,tensor); units.add(M2Model.normalize(encoder.extractPrepared(tensor)));
            }
        }
        var fit = M2Trainer.uncalibrated(M2ModelStore.encoderHash(), M2Trainer.fitStatistics(units.subList(0,2)));
        var expected = M2Trainer.calibrated(fit,M2Trainer.calibrationStatistics(fit,units.subList(2,4)));
        var actual = CanonicalM2Trainer.fit(paths.subList(0,2),paths.subList(2,4));
        assertArrayEquals(expected.local().mean(),actual.model().local().mean());
        assertArrayEquals(expected.local().effectiveStd(),actual.model().local().effectiveStd());
        assertArrayEquals(expected.calibration().alpha(),actual.model().calibration().alpha());
        assertArrayEquals(expected.calibration().beta(),actual.model().calibration().beta());
        try (var detector = new CanonicalM2Detector(actual)) {
            assertEquals(M2Model.imageScore(expected.calibrate(expected.raw(units.get(3)))),
                    detector.detectScore(ImageIO.read(paths.get(3).toFile())));
        }
        assertThrows(IllegalArgumentException.class, () -> CanonicalM2Trainer.fit(paths.subList(0,2),paths.subList(0,2)));
        assertThrows(IllegalArgumentException.class, () -> CanonicalM2Trainer.fit(paths.subList(0,1),paths.subList(2,4)));
        Path blank = temporary.resolve("blank.png"); ImageIO.write(new BufferedImage(700,700,1),"png",blank.toFile());
        assertThrows(CanonicalGeometryException.class, () -> CanonicalM2Trainer.fit(List.of(paths.get(0),blank),paths.subList(2,4)));
    }
}
