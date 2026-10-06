# Task - Sprint 9 SPATIAL_28 comparison

Evaluate the existing MobileNetV4 SPATIAL_28 variant on the same Bottle experiment.

This is a comparison with the frozen SPATIAL_14 baseline, not a redesign.

## Fixed

Keep unchanged:
- preprocessing
- Bottle train/test split
- VSA dimensions = 10000
- projection seed = 42
- normalization/statistics policy
- positional archetype construction
- Adjoint compilation
- raw discrepancy = 1 - compiledScore
- image score = max(raw)
- localization metric conventions from LOCALIZATION_CONVENTIONS.md

## SPATIAL_28

Use the existing encoder variant:
SPATIAL_28 -> 28x28x64.

Train a new model from the same 209 bottle_good images.
All statistics, archetypes and Adjoint filters must be rebuilt for this variant.

Do not reuse the SPATIAL_14 model.

## Evaluation

On the same 83 Bottle test images compute:
- Image AUROC
- Pixel AUROC
- AUPRO@0.30

Generate the same four localization overlays.

Write outputs separately from the 14x14 artifacts.

Verify explicit-VSA vs compiled-Adjoint numerical equivalence on a bounded representative sample; do not repeat the expensive Sprint 7 scoring benchmark.

## Comparison

Report the measured SPATIAL_28 results beside the frozen Sprint 8 baseline:

SPATIAL_14:
- Image AUROC = 0.9857142857142858
- Pixel AUROC = 0.9569495750935939
- AUPRO@0.30 = 0.8653474657258569

Do not reinterpret a difference as caused solely by spatial resolution:
SPATIAL_28 is also an earlier 64-channel feature stage, while SPATIAL_14 is 96-channel.

## Astra scope

Implement code and focused tests only.

Do not run the long real-data experiment unless required for debugging.

At completion report only:
- files changed
- tests run
- exact validation command for OpenCode
- unresolved decisions