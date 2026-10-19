package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class OracleRunner {
    private record Original(String name, BufferedImage rgb, double[] z) { }
    private OracleRunner() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]), dataset = Path.of(args[1]);
        String category = args[2];
        var model = M2ModelStore.read(output.resolve("model.bin"), M2ModelStore.encoderHash());
        var originals = new ArrayList<Original>();
        var perturbedMaps = new ArrayList<double[]>();
        var correctedMaps = new ArrayList<double[]>();
        var csv = new StringBuilder("category,image,family,level,s0,sp,sc,interior_s0,interior_sp,interior_sc,map_mae,map_rmse,map_max,interior_map_mae,interior_map_rmse,interior_map_max,rgb_mae,rgb_rmse,rgb_max,perturbed_map_mae,perturbed_map_rmse,perturbed_map_max,perturbed_interior_map_mae,perturbed_interior_map_rmse,perturbed_interior_map_max\n");
        int identities = 0;
        long start = System.nanoTime();
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            for (String name : Files.readAllLines(output.resolve("normal-test.txt"))) {
                var original = ImageIO.read(dataset.resolve("test/img").resolve(name).toFile());
                var rgb = CanonicalRgb.from(original);
                double[] z = score(model, encoder, rgb);
                if (!Arrays.equals(z, score(model, encoder, original)))
                    throw new IllegalStateException("Canonical input changed M2 map");
                identities++;
                for (var family : List.of(RobustnessTransforms.Family.ROTATION, RobustnessTransforms.Family.SCALE)) {
                    var id = RobustnessTransforms.apply(rgb, family, family.identity());
                    id = RobustnessTransforms.apply(id, family, OracleGeometry.inverse(family, family.identity()));
                    if (OracleGeometry.rgbDifference(rgb, id).max() != 0 || !Arrays.equals(z, score(model, encoder, id)))
                        throw new IllegalStateException("Oracle identity changed pixels/map");
                    identities++;
                }
                originals.add(new Original(name, rgb, z));
            }
            for (var family : List.of(RobustnessTransforms.Family.ROTATION, RobustnessTransforms.Family.SCALE)) {
                for (double level : family.levels()) {
                    if (level == family.identity()) continue;
                    for (var original : originals) {
                        var perturbed = RobustnessTransforms.apply(original.rgb(), family, level);
                        var corrected = RobustnessTransforms.apply(perturbed, family, OracleGeometry.inverse(family, level));
                        double[] zp = score(model, encoder, perturbed), zc = score(model, encoder, corrected);
                        perturbedMaps.add(zp); correctedMaps.add(zc);
                        csv.append(String.join(",", category, original.name(), family.name(), Double.toString(level),
                            Double.toString(M2Model.imageScore(original.z())), Double.toString(M2Model.imageScore(zp)), Double.toString(M2Model.imageScore(zc)),
                            Double.toString(RobustnessTransforms.interior(original.z())), Double.toString(RobustnessTransforms.interior(zp)), Double.toString(RobustnessTransforms.interior(zc))));
                        for (var error : List.of(OracleGeometry.difference(original.z(), zc, false), OracleGeometry.difference(original.z(), zc, true),
                                OracleGeometry.rgbDifference(original.rgb(), corrected), OracleGeometry.difference(original.z(), zp, false), OracleGeometry.difference(original.z(), zp, true)))
                            csv.append(',').append(error.mae()).append(',').append(error.rmse()).append(',').append(error.max());
                        csv.append('\n');
                    }
                    System.out.printf(Locale.ROOT, "%s - ORACLE %s - %+.2f - category %.1fs%n", category, family, level, (System.nanoTime()-start)/1e9);
                }
            }
        }
        writeMaps(output.resolve("baseline-z.npy"), originals.stream().map(Original::z).toList());
        writeMaps(output.resolve("perturbed-z.npy"), perturbedMaps);
        writeMaps(output.resolve("canonicalized-z.npy"), correctedMaps);
        Files.writeString(output.resolve("per-image.csv"), csv, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("identity.json"), "{\"exact_map_comparisons\":"+identities+",\"maximum_difference\":0}", StandardOpenOption.CREATE_NEW);
    }

    static double[] score(M2Model model, OnnxMobileNetV4Encoder encoder, BufferedImage rgb) throws ai.onnxruntime.OrtException {
        return model.calibrate(model.raw(M2Model.normalize(encoder.extract(rgb))));
    }

    private static void writeMaps(Path path, List<double[]> maps) throws Exception {
        double[] values = new double[maps.size()*196];
        for (int i = 0; i < maps.size(); i++) System.arraycopy(maps.get(i), 0, values, i*196, 196);
        ComparisonArtifacts.writeMap(path, values, maps.size(), 196, path.getFileName().toString(), "oracle", "diagnostic");
    }
}
