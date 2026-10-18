package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.CanonicalRgb;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class M2RobustnessRunner {
    private record Original(String name, BufferedImage rgb, double[] z) { }
    private M2RobustnessRunner() { }

    public static void main(String[] args) throws Exception {
        Path dataset = Path.of(args[0]), directory = Path.of(args[1]);
        String category = args[2], label = args[3];
        long categoryStart = System.nanoTime();
        var model = M2ModelStore.read(directory.resolve("model.bin"), M2ModelStore.encoderHash());
        var cal = NpyMap.read(directory.resolve("calibration.npy"));
        if (cal.columns() != M2Model.ELEMENTS || cal.rows() < 2) throw new IllegalArgumentException("Calibration shape");
        double reference = Double.NEGATIVE_INFINITY;
        double[] calibrationZ = new double[cal.rows() * 196];
        for (int n = 0; n < cal.rows(); n++) {
            double[] unit = Arrays.copyOfRange(cal.values(), n * cal.columns(), (n + 1) * cal.columns());
            double[] z = model.calibrate(model.raw(unit));
            System.arraycopy(z, 0, calibrationZ, n * 196, 196);
            reference = Math.max(reference, M2Model.imageScore(z));
        }
        ComparisonArtifacts.writeMap(directory.resolve("calibration-z.npy"), calibrationZ, cal.rows(), 196,
                "calibration", "m2-z", "14x14");
        List<String> names = Files.readAllLines(directory.resolve("normal-test.txt"));
        if (names.isEmpty() || new HashSet<>(names).size() != names.size()
                || names.stream().anyMatch(n -> !n.matches(category + "_good_[0-9]+\\.png")))
            throw new IllegalArgumentException("Expected unique normal test filenames");
        var originals = new ArrayList<Original>();
        int identityChecks = 0;
        try (var encoder = new OnnxMobileNetV4Encoder();
             var detector = new M2Detector(model);
             var csv = Files.newBufferedWriter(directory.resolve("per-image.csv"), StandardOpenOption.CREATE_NEW)) {
            csv.write("category,image,family,level,s0,st,delta,interior_s0,interior_st,interior_delta,reference,exceeds\n");
            for (String name : names) {
                var image = ImageIO.read(dataset.resolve("test/img").resolve(name).toFile());
                if (image == null) throw new IllegalArgumentException("Cannot decode " + name);
                var rgb = CanonicalRgb.from(image);
                double[] original = detector.nativeMap(image), canonical = model.score(encoder.extract(rgb));
                if (!Arrays.equals(original, canonical)) throw new IllegalStateException("Identity map mismatch: " + name);
                identityChecks++;
                originals.add(new Original(name, rgb, original));
            }
            for (var family : RobustnessTransforms.Family.values()) {
                long phaseStart = System.nanoTime();
                double[] levels = family.levels();
                for (int l = 0; l < levels.length; l++) {
                    double level = levels[l];
                    for (var original : originals) {
                        double[] z = model.score(encoder.extract(RobustnessTransforms.apply(original.rgb(), family, level)));
                        if (level == family.identity()) {
                            if (!Arrays.equals(original.z(), z)) throw new IllegalStateException("Family identity: " + original.name());
                            identityChecks++;
                        }
                        double s0 = M2Model.imageScore(original.z()), st = M2Model.imageScore(z);
                        double i0 = RobustnessTransforms.interior(original.z()), it = RobustnessTransforms.interior(z);
                        csv.write(String.join(",", category, original.name(), family.name(), Double.toString(level),
                                Double.toString(s0), Double.toString(st), Double.toString(st - s0),
                                Double.toString(i0), Double.toString(it), Double.toString(it - i0),
                                Double.toString(reference), Boolean.toString(st > reference)) + "\n");
                    }
                    csv.flush();
                    System.out.printf(Locale.ROOT, "%s - %s - %d/%d - %+.2f - phase %.1fs - category %.1fs%n",
                            label, family, l + 1, levels.length, level,
                            (System.nanoTime() - phaseStart) / 1e9, (System.nanoTime() - categoryStart) / 1e9);
                }
            }
        }
        Files.writeString(directory.resolve("identity.json"), "{\"normal_images\":" + names.size()
                + ",\"map_checks\":" + identityChecks + ",\"max_abs_difference\":0,\"reference\":" + reference + "}",
                StandardOpenOption.CREATE_NEW);
    }
}
