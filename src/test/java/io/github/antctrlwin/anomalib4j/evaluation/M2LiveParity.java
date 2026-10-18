package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import java.nio.file.*;
import java.util.*;

/** Two real images per regression category verify the complete encoder/model path. */
public final class M2LiveParity {
    private M2LiveParity() { }
    public static void main(String[] args) throws Exception {
        Path run = Path.of(args[0]), dataset = Path.of(args[1]);
        var report = new StringBuilder("category,filename,native_max_abs,native_mean_abs,image_max_abs\n");
        for (String category : List.of("bottle", "metal_nut", "hazelnut")) {
            var names = Files.readAllLines(run.resolve("data/" + category + "/test.txt"));
            var expected = NpyMap.read(run.resolve("M2/" + category + "/native-z.npy"));
            var model = M2ModelStore.read(run.resolve("M2/" + category + "/model.bin"), M2ModelStore.encoderHash());
            var selected = List.of(names.stream().filter(n -> n.startsWith(category + "_good_")).findFirst().orElseThrow(),
                    names.stream().filter(n -> !n.startsWith(category + "_good_")).findFirst().orElseThrow());
            try (var detector = new M2Detector(model)) {
                for (String name : selected) {
                    var image = ComparisonRunner.read(dataset.resolve("test/img/" + name));
                    double[] actual = detector.nativeMap(image);
                    image.flush();
                    int index = names.indexOf(name);
                    double[] reference = Arrays.copyOfRange(expected.values(), index * 196, (index + 1) * 196);
                    double max = 0, sum = 0;
                    for (int p = 0; p < 196; p++) {
                        double difference = Math.abs(actual[p] - reference[p]);
                        if (difference > 1e-8 + 1e-10 * Math.abs(reference[p])) throw new IllegalStateException("Live path parity failed: " + name);
                        max = Math.max(max, difference); sum += difference;
                    }
                    report.append(category).append(',').append(name).append(',').append(max).append(',').append(sum / 196)
                        .append(',').append(Math.abs(M2Model.imageScore(actual) - M2Model.imageScore(reference))).append('\n');
                }
            }
        }
        Files.writeString(run.resolve("live-parity.csv"), report.toString(), StandardOpenOption.CREATE_NEW);
        System.out.print(report);
    }
}
