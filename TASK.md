@'
# Task - Sprint 8 Localization Baseline

Implement quantitative localization evaluation for the existing Bottle SPATIAL_14 model.

Do not change the model, training, encoder, preprocessing, VSA math, Adjoint compilation or raw scoring.

## Ground truth

Dataset:
../Anomalib4j_md/mvtec-ad-DatasetNinja

Bottle annotations:
test/ann/<image>.png.json

Decode DatasetNinja bitmap annotations:
- bitmap.data: base64 PNG
- bitmap.origin: [x,y]
- combine multiple bitmap objects by union
- good images -> empty mask

Add focused unit tests for mask reconstruction.

## Prediction map

Use the existing raw 14x14 discrepancy:

raw = 1 - compiledScore

Upsample it bilinearly to original image dimensions.

No smoothing, calibration, thresholding or per-patch normalization.

## Metrics

Implement:
- Pixel AUROC
- standard MVTec-style PRO/AUPRO

Document the exact AUPRO convention used.

## Regression

The localization evaluation must not alter Sprint 7 behavior.

Image AUROC should remain approximately:
0.9857142857142858

## Qualitative output support

Provide code to generate comparison overlays for:
- bottle_good_000
- bottle_broken_large_000
- bottle_broken_small_000
- bottle_contamination_000

## Scope for Astra

Implement the code and unit tests only.

Do not run the expensive full Bottle training/evaluation unless necessary to debug correctness.

At completion, report only:
- files changed
- unit tests run
- expensive command OpenCode should execute afterward
- any unresolved decision

Do not write a long report.
'@ | Set-Content -Encoding utf8 TASK.md