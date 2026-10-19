package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.onnx.CanonicalRgb;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class OracleTiming {
    private static volatile BufferedImage sink;
    private OracleTiming() { }
    public static void main(String[] args) throws Exception {
        Path inputs = Path.of(args[0]), dataset = Path.of(args[1]), output = Path.of(args[2]);
        int fork = Integer.parseInt(args[3]);
        var images = new ArrayList<BufferedImage>();
        for (String name : Files.readAllLines(inputs.resolve("normal-test.txt")).subList(0, 8))
            images.add(CanonicalRgb.from(ImageIO.read(dataset.resolve("test/img").resolve(name).toFile())));
        var families = new ArrayList<RobustnessTransforms.Family>();
        var levels = new ArrayList<Double>();
        var fixtures = new ArrayList<List<BufferedImage>>();
        for (var family : List.of(RobustnessTransforms.Family.ROTATION, RobustnessTransforms.Family.SCALE))
            for (double level : family.levels()) if (level != family.identity()) {
                families.add(family); levels.add(level);
                fixtures.add(images.stream().map(i -> RobustnessTransforms.apply(i, family, level)).toList());
            }
        long warmup = System.nanoTime();
        int turn = 0;
        while (System.nanoTime()-warmup < 3_000_000_000L) {
            int k = turn++ % levels.size();
            sink = RobustnessTransforms.apply(fixtures.get(k).get(turn % 8), families.get(k), OracleGeometry.inverse(families.get(k), levels.get(k)));
        }
        var csv = new StringBuilder("fork,round,family,level,batch,us_per_warp\n");
        for (int round = 0; round < 9; round++) for (int step = 0; step < levels.size(); step++) {
            int k = (step + round + fork) % levels.size();
            double inverse = OracleGeometry.inverse(families.get(k), levels.get(k));
            long start = System.nanoTime();
            for (int i = 0; i < 16; i++) sink = RobustnessTransforms.apply(fixtures.get(k).get(i % 8), families.get(k), inverse);
            double micros = (System.nanoTime()-start)/16000.0;
            csv.append(String.join(",", Integer.toString(fork), Integer.toString(round), families.get(k).name(),
                Double.toString(levels.get(k)), "16", Double.toString(micros))).append('\n');
        }
        Files.writeString(output, csv, StandardOpenOption.CREATE_NEW);
    }
}
