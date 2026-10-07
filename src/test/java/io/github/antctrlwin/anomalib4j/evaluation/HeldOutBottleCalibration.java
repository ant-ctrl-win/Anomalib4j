package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.adjoint.AdjointFilterBank;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

final class HeldOutBottleCalibration {
    private HeldOutBottleCalibration() { }

    static double[] rawScores(AdjointFilterBank filters, float[] features) {
        double[] raw = new double[filters.grid().cells()];
        for (int p = 0; p < raw.length; p++) {
            raw[p] = 1 - filters.scoreCell(features, p);
            if (!Double.isFinite(raw[p])) throw new ArithmeticException("Nonfinite raw discrepancy");
        }
        return raw;
    }

    static Scores score(AdjointFilterBank filters, float[] features, PositionalRawCalibration calibration) {
        calibration.requireCompatible(filters.descriptor());
        double[] raw = rawScores(filters, features);
        return new Scores(raw, calibration.calibrate(raw));
    }

    record Scores(double[] raw, double[] z) { }

    static Split split(List<Path> images) {
        return split(images, 42L);
    }

    static Split split(List<Path> images, long seed) {
        if (images.size() != 209) throw new IllegalArgumentException("Expected 209 bottle training-good images");
        var names = new HashSet<String>();
        var shuffled = new ArrayList<>(images);
        for (Path path : shuffled) {
            String name = path.getFileName().toString();
            if (!name.matches("bottle_good_[0-9]+\\.png") || !names.add(name)) {
                throw new IllegalArgumentException("Expected unique bottle_good filenames: " + name);
            }
        }
        shuffled.sort(Comparator.comparing(path -> path.getFileName().toString()));
        Collections.shuffle(shuffled, new Random(seed));
        return new Split(shuffled.subList(0, 167), shuffled.subList(167, 209));
    }

    record Split(List<Path> archetypeTraining, List<Path> calibration) {
        Split {
            archetypeTraining = List.copyOf(archetypeTraining);
            calibration = List.copyOf(calibration);
        }
    }

    record Metrics(double imageAuRoc, double pixelAuRoc, double auPro) {
        Metrics minus(Metrics raw) {
            return new Metrics(imageAuRoc - raw.imageAuRoc, pixelAuRoc - raw.pixelAuRoc, auPro - raw.auPro);
        }
    }

    static String comparison(Metrics raw, Metrics calibrated) {
        return comparison(raw, calibrated, 42L);
    }

    static String comparison(Metrics raw, Metrics calibrated, long splitSeed) {
        Metrics delta = calibrated.minus(raw);
        return String.format(Locale.ROOT, """
                # Bottle SPATIAL_14: held-out positional calibration

                ## Primary comparison: identical model trained on 167 images

                | Metric | RAW_167 measured | CALIBRATED_167+42 measured | CALIBRATED_167+42 - RAW_167 |
                | --- | --- | --- | --- |
                | Image AUROC | %.17g | %.17g | %+.17g |
                | Pixel AUROC | %.17g | %.17g | %+.17g |
                | AUPRO@0.30 | %.17g | %.17g | %+.17g |

                Both branches use the same frozen filters and the same raw scores for all 83 test images
                (20 good / 63 anomaly). The 42 calibration images never enter VSA statistics, archetypes,
                or Adjoint compilation. Positional raw mu/sigma use only those 42 images (sample N-1).
                Sigma floor = 1e-6 raw units. z[p]=(raw[p]-mu[p])/sigma[p], image score=max(z).
                No clamp, sigmoid, smoothing, threshold or per-image normalization.
                Bilinear interpolation is applied separately to raw and z AFTER positional calibration;
                localization follows docs/benchmark/LOCALIZATION_CONVENTIONS.md without changes.

                ## Context only: full-209 references, not used for the primary delta

                | Reference | Image AUROC | Pixel AUROC | AUPRO@0.30 |
                | --- | --- | --- | --- |
                | Full 209 raw | 0.9857142857142858 | 0.9569495750935939 | 0.8653474657258569 |
                | Full 209 in-sample calibrated | 1.0 | 0.9611017698505884 | 0.8766695388953096 |

                Split: lexicographically sort filenames, Collections.shuffle(list, new Random(%dL)),
                first 167 for archetype training, remaining 42 for calibration. Keep shuffled order.
                Exact ordered memberships: archetype-training.txt and calibration.txt.
                mu/sigma diagnostics and floor flags: parameters.csv and parameter-summary.csv.
                """, raw.imageAuRoc(), calibrated.imageAuRoc(), delta.imageAuRoc(),
                raw.pixelAuRoc(), calibrated.pixelAuRoc(), delta.pixelAuRoc(),
                raw.auPro(), calibrated.auPro(), delta.auPro(), splitSeed);
    }
}
