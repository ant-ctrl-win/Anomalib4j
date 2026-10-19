package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.onnx.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

final class CorrespondenceRobustness {
    record Context(Path root, Path dataset, String category, String label) { }
    private record Original(String name, BufferedImage rgb, double[][] scores) { }
    private CorrespondenceRobustness() { }
    static void run(Context context, List<SpatialCorrespondence> scorers) throws Exception {
        Path inputs = context.root().resolve("inputs").resolve(context.category());
        var names = Files.readAllLines(inputs.resolve("normal-test.txt"));
        var originals = new ArrayList<Original>();
        double[] references = new double[3];
        var buffers = new StringBuilder[3];
        for (int m = 0; m < 3; m++) {
            Path output = context.root().resolve(scorers.get(m).mode().id()).resolve(context.category());
            references[m] = Arrays.stream(NpyMap.read(output.resolve("calibration-z.npy")).values()).max().orElseThrow();
            buffers[m] = new StringBuilder("category,image,family,level,s0,st,delta,interior_s0,interior_st,interior_delta,reference,exceeds,same_position_fraction,mean_displacement\n");
        }
        long categoryStart = System.nanoTime();
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            for (String name : names) {
                var rgb = CanonicalRgb.from(ImageIO.read(context.dataset().resolve("test/img").resolve(name).toFile()));
                double[] unit = M2Model.normalize(encoder.extract(rgb));
                double[][] scores = new double[3][];
                for (int m = 0; m < 3; m++) scores[m] = scorers.get(m).z(scorers.get(m).raw(unit));
                originals.add(new Original(name, rgb, scores));
            }
            for (var family : List.of(RobustnessTransforms.Family.TRANSLATION_X, RobustnessTransforms.Family.TRANSLATION_Y,
                    RobustnessTransforms.Family.ROTATION, RobustnessTransforms.Family.SCALE)) {
                long phaseStart = System.nanoTime();
                var levels = Arrays.stream(family.levels()).filter(v -> v != family.identity()).toArray();
                for (int l = 0; l < levels.length; l++) {
                    for (var original : originals) {
                        double[] unit = M2Model.normalize(encoder.extract(RobustnessTransforms.apply(original.rgb(), family, levels[l])));
                        for (int m = 0; m < 3; m++) {
                            var scorer = scorers.get(m);
                            var raw = scorer.raw(unit); double[] z = scorer.z(raw);
                            double s0 = M2Model.imageScore(original.scores()[m]), st = M2Model.imageScore(z);
                            double i0 = RobustnessTransforms.interior(original.scores()[m]), it = RobustnessTransforms.interior(z);
                            buffers[m].append(String.join(",", context.category(), original.name(), family.name(), Double.toString(levels[l]),
                                Double.toString(s0), Double.toString(st), Double.toString(st-s0), Double.toString(i0), Double.toString(it),
                                Double.toString(it-i0), Double.toString(references[m]), Boolean.toString(st > references[m]),
                                Double.toString(SpatialCorrespondence.samePosition(raw)), Double.toString(SpatialCorrespondence.displacement(raw)))).append('\n');
                        }
                    }
                    System.out.printf(Locale.ROOT, "%s - GEOMETRY %s - %d/%d - %+.2f - phase %.1fs - category %.1fs%n",
                            context.label(), family, l+1, levels.length, levels[l], (System.nanoTime()-phaseStart)/1e9, (System.nanoTime()-categoryStart)/1e9);
                }
            }
        }
        for (int m = 0; m < 3; m++) Files.writeString(context.root().resolve(scorers.get(m).mode().id())
                .resolve(context.category()).resolve("robustness.csv"), buffers[m], StandardOpenOption.CREATE_NEW);
    }
}
