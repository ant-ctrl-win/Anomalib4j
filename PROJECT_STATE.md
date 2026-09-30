@'
# Project State

Baseline checkpoint:

git tag: sprint7-bottle-baseline
commit: 5caca96

## Completed

Bottle model:
- 209 normal training images
- frozen MobileNetV4 SPATIAL_14
- grid 14x14x96
- positional VSA archetypes
- compiled Adjoint filters

Sprint 7 real evaluation:
- test images: 83
- good: 20
- anomaly: 63
- Image AUROC: 0.9857142857142858
- explicit/compiled comparisons: 16268
- maximum absolute error: 1.3074e-12
- preliminary scoring-only benchmark:
  explicit median ~651 ms/map
  compiled median ~13 us/map

Five qualitative heatmaps were generated and look spatially meaningful.

## Not yet established

- quantitative anomaly localization
- Pixel AUROC
- AUPRO
- 28x28 comparison
- fine-tuning
- alignment/cropping improvements
- calibrated anomaly thresholds

## Current scientific priority

Measure the localization quality of the existing frozen 14x14 baseline before changing the model.
'@ | Set-Content -Encoding utf8 PROJECT_STATE.md