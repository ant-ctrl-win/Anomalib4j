# POSITIONAL_CALIBRATION_RESULT

Real validation of the already-implemented positional raw-score calibration.
This report only records the output produced by the test run; metrics are not
reinterpreted or corrected.

## Command executed

```
mvn -B "-Dtest=*Test,BottlePositionalCalibrationIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

## Build and test outcome

- Result: **BUILD SUCCESS**
- Tests run: **74**, Failures: 0, Errors: 0, Skipped: 0
- Total time: **05:13 min** (313 s)
- `BottlePositionalCalibrationIT`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, duration **311.0 s**

## Calibrated metrics (SPATIAL_14, in-sample positional calibration)

| Metric | Calibrated | Frozen raw SPATIAL_14 baseline | Delta (calibrated - raw) |
| --- | --- | --- | --- |
| Image AUROC | 1.0000000000000000 | 0.9857142857142858 | +0.0142857142857142 |
| Pixel AUROC | 0.96110176985058840 | 0.9569495750935939 | +0.0041521947569945 |
| AUPRO@0.30 | 0.87666953889530960 | 0.8653474657258569 | +0.0113220731694527 |

Raw image AUROC recomputed in this run: 0.98571428571428580 (matches the frozen baseline).
Raw pixel metrics in the baseline column are frozen Sprint 8 results, not recomputed in this run.

Test set: 83 images (20 good / 63 anomaly). Calibration images: 209, positions: 196.
Regions: 68, foreground pixels: 3886731, background pixels: 63343269.

## Statistics of the 196 mu[p] and sigma[p] produced by the test

Sigma floor: 1.0000000000000000e-06 raw-score units. Floored positions: 0
(so `sigma_sample` and `sigma_effective` are identical for every position).

| Parameter | positions | min | p25 | median | mean | p75 | max | sample_std_across_positions |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| mu | 196 | -111.64589407775631 | -100.47488222566810 | -95.947445304829570 | -97.014128863826740 | -92.987032869392410 | -86.539800661713100 | 5.4056086646636590 |
| sigma_sample | 196 | 0.19083504209046956 | 0.95066326687645890 | 1.4083015881722460 | 1.5705002995288780 | 1.9039791052930970 | 6.2154853308840750 | 0.90069998167628320 |
| sigma_effective | 196 | 0.19083504209046956 | 0.95066326687645890 | 1.4083015881722460 | 1.5705002995288780 | 1.9039791052930970 | 6.2154853308840750 | 0.90069998167628320 |

## Artifacts and overlays generated

Directory: `target/bottle-spatial14-positional-calibration/`

- `parameters.csv` — per-position `row,column,count,mu,sigma_sample,sigma_effective,floored` (196 positions).
- `parameter-summary.csv` — the mu/sigma summary table reproduced above.
- `image-scores.csv` — `filename,ground_truth,defect,image_raw,image_z,z_max_row,z_max_column,z_min,z_mean,z_max`.
- `test-scores.csv` — `filename,row,column,raw,z` (16268 cell rows + header).
- `metrics.csv` — `image_auroc,pixel_auroc,aupro030,raw_image_auroc,calibration_images,positions,sigma_floor,floored_positions,regions,foreground_pixels,background_pixels`.
- `comparison.md` — calibrated-vs-baseline comparison produced by the test.
- `pro-curve.csv` — PRO curve.
- Overlay PNGs: `bottle_good_000.png`, `bottle_broken_large_000.png`, `bottle_broken_small_000.png`, `bottle_contamination_000.png`.
  Shared overlay color scale: -3.9272813249496568 to 363.97249720652655 z units, used only for visualization.

## Important note: in-sample positional calibration

This is **in-sample positional calibration**. The same 209 training-good images
contribute both to building the VSA archetypes and to estimating the per-position
means and sample deviations (mu/sigma). No held-out calibration set,
cross-validation or leave-one-out is used. Test images do not enter the
calibration estimation.

## Problems

None. No errors or failures occurred during the run.
