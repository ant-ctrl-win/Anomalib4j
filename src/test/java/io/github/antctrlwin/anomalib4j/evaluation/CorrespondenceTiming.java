package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.M2ModelStore;
import java.nio.file.*;
import java.util.*;

public final class CorrespondenceTiming {
    private static volatile double sink;
    private CorrespondenceTiming() { }
    public static void main(String[] args) throws Exception {
        Path inputs = Path.of(args[0]), output = Path.of(args[1]);
        int fork = Integer.parseInt(args[2]);
        var model = M2ModelStore.read(inputs.resolve("model.bin"), M2ModelStore.encoderHash());
        var descriptors = CorrespondenceRunner.read(inputs.resolve("calibration.npy")).subList(0, 8);
        var modes = SpatialCorrespondence.Mode.values();
        var scorers = Arrays.stream(modes).map(m -> new SpatialCorrespondence(model, m)).toList();
        for (int k = 0; k < 3; k++) {
            var scorer = scorers.get((k+fork)%3);
            long end = System.nanoTime() + 2_000_000_000L;
            int i = 0;
            while (System.nanoTime() < end) consume(scorer.raw(descriptors.get(i++ % 8)));
        }
        try (var csv = Files.newBufferedWriter(output, StandardOpenOption.CREATE_NEW)) {
            csv.write("fork,round,method,maps,us_per_map\n");
            for (int round = 0; round < 9; round++) for (int k = 0; k < 3; k++) {
                var scorer = scorers.get((k+round+fork)%3);
                long start = System.nanoTime();
                for (int i = 0; i < 64; i++) consume(scorer.raw(descriptors.get(i%8)));
                double micros = (System.nanoTime()-start)/64000.0;
                csv.write(fork + "," + round + "," + scorer.mode().id() + ",64," + micros + "\n");
            }
        }
        System.out.println("Timing complete fork=" + fork + " sink=" + sink + " Java=" + System.getProperty("java.version"));
    }
    private static void consume(SpatialCorrespondence.Raw raw) {
        double total = 0;
        for (int p = 0; p < 196; p++) total += raw.values()[p] + raw.reference()[p];
        sink = total;
    }
}
