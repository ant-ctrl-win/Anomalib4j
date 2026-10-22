package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.EncoderSpec;
import java.nio.file.*;
import java.util.*;

/** Fits Java M2 from shared normalized descriptors; writes only a new namespace. */
public final class M2Runner {
    private M2Runner() { }
    public static void main(String[] args) throws Exception {
        Path inputs = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String label = args.length > 2 ? args[2] : "[01/01] M2";
        var spec = args.length > 3 ? EncoderSpec.valueOf(args[3]) : EncoderSpec.SMALL_SPATIAL_14;
        String mode = args.length > 4 ? args[4] : "all";
        if (!Set.of("all", "fit", "infer").contains(mode)) throw new IllegalArgumentException("Unknown runner mode");
        M2Model model;
        if (mode.equals("infer")) model = M2ModelStore.read(output.resolve("model.bin"), spec);
        else {
            Files.createDirectory(output);
            model = fit(inputs, output, label, spec);
        }
        if (!mode.equals("fit")) infer(inputs, output, label, spec, model);
    }
    private static M2Model fit(Path inputs, Path output, String label, EncoderSpec spec) throws Exception {
        long start = begin(label, "FIT");
        var fit = M2Trainer.fitStatistics(spec, read(inputs.resolve("fit.npy"), spec));
        var uncalibrated = M2Trainer.uncalibrated(spec, fit);
        double fitSeconds = complete(label, "FIT", start);
        start = begin(label, "CALIBRATE");
        var cal = M2Trainer.calibrationStatistics(uncalibrated, read(inputs.resolve("calibration.npy"), spec));
        var model = M2Trainer.calibrated(uncalibrated, cal);
        double calibrationSeconds = complete(label, "CALIBRATE", start);
        start = begin(label, "PERSIST/RELOAD");
        M2ModelStore.write(output.resolve("model.bin"), model);
        var reloaded = M2ModelStore.read(output.resolve("model.bin"), spec);
        double persistenceSeconds = complete(label, "PERSIST/RELOAD", start);
        write(output.resolve("mean.npy"), fit.mean(), 196);
        write(output.resolve("sample-std.npy"), fit.sampleStd(), 196);
        write(output.resolve("effective-std.npy"), fit.effectiveStd(), 196);
        write(output.resolve("alpha.npy"), cal.mean(), 1);
        write(output.resolve("beta.npy"), cal.effectiveStd(), 1);
        ComparisonArtifacts.json(output.resolve("fit-timing.json"), Map.of(
                "fit_statistics_seconds", fitSeconds, "calibration_seconds", calibrationSeconds,
                "serialization_reload_seconds", persistenceSeconds,
                "scope", "Prepared normal descriptors; excludes CNN; phases include descriptor I/O and validation"));
        return reloaded;
    }
    private static void infer(Path inputs, Path output, String label, EncoderSpec spec, M2Model model) throws Exception {
        var reloaded = M2ModelStore.read(output.resolve("model.bin"), spec);
        long start = begin(label, "INFER");
        var tests = read(inputs.resolve("test.npy"), spec);
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
    private static double complete(String label, String phase, long start) {
        long nanos = System.nanoTime() - start;
        long seconds = nanos / 1_000_000_000;
        System.out.printf("%s - %s - complete - %02d:%02d:%02d%n", label, phase, seconds / 3600, seconds / 60 % 60, seconds % 60);
        return nanos / 1_000_000_000.0;
    }
    private static List<double[]> read(Path path, EncoderSpec spec) throws Exception {
        var input = NpyMap.read(path);
        if (input.columns() != spec.grid().elements() || input.rows() < 1) throw new IllegalArgumentException("Unexpected descriptor grid");
        var result = new ArrayList<double[]>();
        for (int n = 0; n < input.rows(); n++) result.add(Arrays.copyOfRange(input.values(), n * input.columns(), (n + 1) * input.columns()));
        return result;
    }
    private static void write(Path path, double[] values, int rows) throws Exception {
        ComparisonArtifacts.writeMap(path, values, rows, values.length / rows, path.getFileName().toString(), "m2-diagnostic", "m2");
    }
}
