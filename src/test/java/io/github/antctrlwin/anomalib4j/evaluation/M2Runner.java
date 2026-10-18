package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import java.nio.file.*;
import java.util.*;

/** Fits Java M2 from shared normalized descriptors; writes only a new namespace. */
public final class M2Runner {
    private M2Runner() { }
    public static void main(String[] args) throws Exception {
        Path inputs = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectory(output);
        String label = args.length > 2 ? args[2] : "[01/01] M2";
        long start = begin(label, "FIT");
        var fit = M2Trainer.fitStatistics(read(inputs.resolve("fit.npy")));
        var uncalibrated = M2Trainer.uncalibrated(M2ModelStore.encoderHash(), fit);
        complete(label, "FIT", start);
        start = begin(label, "CALIBRATE");
        var cal = M2Trainer.calibrationStatistics(uncalibrated, read(inputs.resolve("calibration.npy")));
        var model = M2Trainer.calibrated(uncalibrated, cal);
        complete(label, "CALIBRATE", start);
        start = begin(label, "PERSIST/RELOAD");
        M2ModelStore.write(output.resolve("model.bin"), model);
        var reloaded = M2ModelStore.read(output.resolve("model.bin"), M2ModelStore.encoderHash());
        complete(label, "PERSIST/RELOAD", start);
        write(output.resolve("mean.npy"), fit.mean(), 196);
        write(output.resolve("sample-std.npy"), fit.sampleStd(), 196);
        write(output.resolve("effective-std.npy"), fit.effectiveStd(), 196);
        write(output.resolve("alpha.npy"), cal.mean(), 1);
        write(output.resolve("beta.npy"), cal.effectiveStd(), 1);
        start = begin(label, "INFER");
        var tests = read(inputs.resolve("test.npy"));
        double[] raw = new double[tests.size() * 196], z = new double[raw.length], reload = new double[raw.length];
        double[] images = new double[tests.size()];
        for (int n = 0; n < tests.size(); n++) {
            double[] r = model.raw(tests.get(n)), scores = model.calibrate(r);
            System.arraycopy(r, 0, raw, n * 196, 196);
            System.arraycopy(scores, 0, z, n * 196, 196);
            System.arraycopy(reloaded.calibrate(reloaded.raw(tests.get(n))), 0, reload, n * 196, 196);
            images[n] = M2Model.imageScore(scores);
        }
        write(output.resolve("raw.npy"), raw, tests.size());
        write(output.resolve("native-z.npy"), z, tests.size());
        write(output.resolve("reload-z.npy"), reload, tests.size());
        write(output.resolve("image-scores.npy"), images, tests.size());
        complete(label, "INFER", start);
    }
    private static long begin(String label, String phase) {
        System.out.println(label + " - " + phase + " - started");
        return System.nanoTime();
    }
    private static void complete(String label, String phase, long start) {
        long seconds = (System.nanoTime() - start) / 1_000_000_000;
        System.out.printf("%s - %s - complete - %02d:%02d:%02d%n", label, phase, seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    private static List<double[]> read(Path path) throws Exception {
        var input = NpyMap.read(path);
        if (input.columns() != M2Model.ELEMENTS || input.rows() < 1) throw new IllegalArgumentException("Expected Nx18816 units");
        var result = new ArrayList<double[]>();
        for (int n = 0; n < input.rows(); n++) result.add(Arrays.copyOfRange(input.values(), n * input.columns(), (n + 1) * input.columns()));
        return result;
    }
    private static void write(Path path, double[] values, int rows) throws Exception {
        ComparisonArtifacts.writeMap(path, values, rows, values.length / rows, path.getFileName().toString(), "m2-diagnostic", "m2");
    }
}
