package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.model.ModelDescriptor;
import io.github.antctrlwin.anomalib4j.model.NormalizationPolicy;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.util.Locale;
import java.util.Set;

final class Spatial28Evaluation {
    static final int SIDE = 28;
    static final Set<String> EXAMPLES = Set.of("bottle_good_000.png", "bottle_broken_large_000.png",
            "bottle_broken_small_000.png", "bottle_contamination_000.png");

    private Spatial28Evaluation() { }

    static ModelDescriptor descriptor(OnnxMobileNetV4Encoder encoder) {
        if (encoder.variant() != OnnxMobileNetV4Encoder.Variant.SPATIAL_28) {
            throw new IllegalArgumentException("SPATIAL_28 encoder required");
        }
        return new ModelDescriptor("bottle-spatial28-normal-v1", encoder.modelId(), encoder.variant().name(),
                encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1, encoder.preprocessingId());
    }

    static BottleEvaluation.Summary summarize(double[] raw) {
        if (raw.length != SIDE * SIDE) throw new IllegalArgumentException("Expected 28x28 raw map");
        double min = Double.POSITIVE_INFINITY;
        double sum = 0;
        int maximum = 0;
        for (int p = 0; p < raw.length; p++) {
            if (!Double.isFinite(raw[p])) throw new IllegalArgumentException("Nonfinite discrepancy");
            min = Math.min(min, raw[p]);
            sum += raw[p];
            if (raw[p] > raw[maximum]) maximum = p;
        }
        return new BottleEvaluation.Summary(min, sum / raw.length, raw[maximum], maximum / SIDE, maximum % SIDE);
    }

    static String comparison(double imageAuRoc, double pixelAuRoc, double auPro, double error, int comparisons) {
        return String.format(Locale.ROOT, """
                # Bottle: SPATIAL_28 compared with frozen Sprint 8 baseline

                | Metric | SPATIAL_14 (14x14x96) | SPATIAL_28 (28x28x64) | Delta (28 - 14) |
                | --- | --- | --- | --- |
                | Image AUROC | %.17g | %.17g | %+.17g |
                | Pixel AUROC | %.17g | %.17g | %+.17g |
                | AUPRO@0.30 | %.17g | %.17g | %+.17g |

                Baseline values are frozen Sprint 8 results; SPATIAL_28 values are measured by this run.
                SPATIAL_28 is an earlier 64-channel feature stage; SPATIAL_14 has 96 channels.
                Differences cannot be attributed solely to spatial resolution.

                Training: 209 normal images, 163856 patch observations; test: 83 images (20 good, 63 anomaly).
                D=10000, seed=42, norm epsilon=1e-6, std floor=1e-8, backbone frozen.
                Preprocessing: imagenet-rgb-resize224-bicubic-v1. Image score: max(1 - compiledScore).
                Localization follows docs/benchmark/LOCALIZATION_CONVENTIONS.md (bilinear half-pixel, 8-connectivity,
                exact threshold sweep, AUPRO integrated to FPR 0.30 and normalized by 0.30).
                Explicit/compiled comparisons: %d (all cells of the four named overlay examples).
                Maximum absolute score error: %.17g; asserted tolerance: 1e-9. No scoring benchmark.
                """, .9857142857142858, imageAuRoc, imageAuRoc - .9857142857142858,
                .9569495750935939, pixelAuRoc, pixelAuRoc - .9569495750935939,
                .8653474657258569, auPro, auPro - .8653474657258569, comparisons, error);
    }
}
