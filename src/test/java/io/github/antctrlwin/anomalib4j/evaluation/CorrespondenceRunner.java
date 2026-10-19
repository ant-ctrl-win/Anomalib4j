package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import java.nio.file.*;
import java.util.*;

public final class CorrespondenceRunner {
    private CorrespondenceRunner() { }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), dataset = Path.of(args[1]);
        String category = args[2], label = args[3];
        Path inputs = root.resolve("inputs").resolve(category);
        var base = M2ModelStore.read(inputs.resolve("model.bin"), M2ModelStore.encoderHash());
        var heldOut = read(inputs.resolve("calibration.npy"));
        var tests = read(inputs.resolve("test.npy"));
        var names = Files.readAllLines(root.resolve("data").resolve(category).resolve("test.txt"));
        if (names.size() != tests.size()) throw new IllegalArgumentException("Test cardinality");
        var scorers = new ArrayList<SpatialCorrespondence>();
        for (var mode : SpatialCorrespondence.Mode.values()) {
            long start = System.nanoTime();
            System.out.println(label + " - " + mode.id() + " CALIBRATE/SCORE - started");
            var scorer = new SpatialCorrespondence(base, mode).calibrated(heldOut);
            scorers.add(scorer);
            Path output = root.resolve(mode.id()).resolve(category);
            write(output.resolve("alpha.npy"), scorer.calibration().alpha(), 1);
            write(output.resolve("beta.npy"), scorer.calibration().beta(), 1);
            double[] calZ = new double[heldOut.size() * 196];
            for (int i = 0; i < heldOut.size(); i++)
                System.arraycopy(scorer.z(scorer.raw(heldOut.get(i))), 0, calZ, i * 196, 196);
            write(output.resolve("calibration-z.npy"), calZ, heldOut.size());
            double[] raw = new double[tests.size() * 196], z = new double[raw.length];
            try (var csv = Files.newBufferedWriter(output.resolve("correspondence.csv"), StandardOpenOption.CREATE_NEW)) {
                csv.write("image,same_position_fraction,mean_displacement\n");
                for (int i = 0; i < tests.size(); i++) {
                    var result = scorer.raw(tests.get(i));
                    var scores = scorer.z(result);
                    if (mode == SpatialCorrespondence.Mode.P && (!Arrays.equals(base.raw(tests.get(i)), result.values())
                            || !Arrays.equals(base.calibrate(base.raw(tests.get(i))), scores)))
                        throw new IllegalStateException("M2-P exact parity failure");
                    System.arraycopy(result.values(), 0, raw, i * 196, 196);
                    System.arraycopy(scores, 0, z, i * 196, 196);
                    csv.write(names.get(i) + "," + SpatialCorrespondence.samePosition(result) + ","
                            + SpatialCorrespondence.displacement(result) + "\n");
                }
            }
            write(output.resolve("raw.npy"), raw, tests.size());
            write(output.resolve("native-z.npy"), z, tests.size());
            System.out.printf(Locale.ROOT, "%s - %s CALIBRATE/SCORE - complete - %.1fs%n", label, mode.id(), (System.nanoTime()-start)/1e9);
        }
        CorrespondenceRobustness.run(new CorrespondenceRobustness.Context(root, dataset, category, label), scorers);
    }
    static List<double[]> read(Path path) throws Exception {
        var array = NpyMap.read(path);
        if (array.columns() != 18816 || array.rows() < 1) throw new IllegalArgumentException("Expected Nx18816");
        var rows = new ArrayList<double[]>();
        for (int i = 0; i < array.rows(); i++) rows.add(Arrays.copyOfRange(array.values(), i*18816, (i+1)*18816));
        return rows;
    }
    static void write(Path path, double[] values, int rows) throws Exception {
        ComparisonArtifacts.writeMap(path, values, rows, values.length/rows, path.getFileName().toString(), "correspondence", "diagnostic");
    }
}
