# HELDOUT_CALIBRATION_RESULT

Real validation of the already-implemented held-out positional calibration experiment.
This report only records the values measured by the test run.

## Command executed

```
mvn -B "-Dtest=*Test,BottleHeldOutCalibrationIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

## Build and test outcome

- Result: **BUILD SUCCESS**
- Tests run: **79**, Failures: 0, Errors: 0, Skipped: 0
- Total time: **04:15 min** (255 s)
- `BottleHeldOutCalibrationIT`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, duration **253.2 s**

## Split confirmation

- Archetype-training images: **167** (`archetype-training.txt`, 167 entries)
- Calibration images: **42** (`calibration.txt`, 42 entries)
- Test images: **83** (20 good / 63 anomaly; `image-scores.csv` = header + 83 rows)

Split procedure: lexicographic sort of filenames, `Collections.shuffle(list, new Random(42L))`,
first 167 for archetype training, remaining 42 for calibration, shuffled order preserved.
Training VSA observations: 32732 = 167 x 196. Archetype samples per cell: 167.
Calibration observations per position: 42.

## Primary comparison: identical model trained on 167 images

| Metric | RAW_167 measured | CALIBRATED_167+42 measured | CALIBRATED_167+42 - RAW_167 |
| --- | --- | --- | --- |
| Image AUROC | 0.98571428571428580 | 1.0000000000000000 | +0.014285714285714235 |
| Pixel AUROC | 0.95692682637216930 | 0.96166402013428550 | +0.0047371937621162320 |
| AUPRO@0.30 | 0.86509764563611720 | 0.87744728999672250 | +0.012349644360605260 |

Both branches use the same frozen filters and the same raw scores for all 83 test images.
The 42 calibration images never enter VSA statistics, archetypes, or Adjoint compilation.
Positional raw mu/sigma use only those 42 images (sample N-1).
z[p]=(raw[p]-mu[p])/sigma[p], image score=max(z). No clamp, sigmoid, smoothing, threshold
or per-image normalization. Bilinear interpolation is applied separately to raw and z after
positional calibration; localization follows LOCALIZATION_CONVENTIONS.md without changes.

## Separate references (not used for the primary delta)

| Reference | Image AUROC | Pixel AUROC | AUPRO@0.30 |
| --- | --- | --- | --- |
| Full-209 raw | 0.9857142857142858 | 0.9569495750935939 | 0.8653474657258569 |
| Full-209 in-sample calibrated | 1.0 | 0.9611017698505884 | 0.8766695388953096 |

## Positional parameter statistics (196 positions)

```csv
parameter,positions,min,p25,median,mean,p75,max,sample_std_across_positions
mu,196,-111.61849604219766,-100.48403941439360,-96.094195667630770,-97.041329561345590,-93.051335516897640,-86.766998085404720,5.4004828891945330
sigma_sample,196,0.19065769809561303,0.91778202978115530,1.4017774161065435,1.5220728692657932,1.8465416471189025,5.2971342686129710,0.84711098189919530
sigma_effective,196,0.19065769809561303,0.91778202978115530,1.4017774161065435,1.5220728692657932,1.8465416471189025,5.2971342686129710,0.84711098189919530
```

- Sigma floor: 1.0000000000000000e-06 raw-score units.
- Positions hit by the sigma floor: **0** (`sigma_sample` equals `sigma_effective` for all positions).

## Artifacts and overlays generated

Directory: `target/bottle-spatial14-heldout-calibration/`

- `archetype-training.txt`, `calibration.txt` — exact ordered split memberships.
- `parameters.csv` — per-position `row,column,count,mu,sigma_sample,sigma_effective,floored`.
- `parameter-summary.csv` — the summary table reproduced above.
- `image-scores.csv` — header + 83 test-image rows.
- `test-scores.csv` — per-cell `filename,row,column,raw,z` (16268 cell rows + header).
- `metrics.csv` — `evaluation,image_auroc,pixel_auroc,aupro030` for RAW_167, CALIBRATED_167+42 and the delta.
- `comparison.md` — comparison produced by the test.
- `raw-pro-curve.csv`, `z-pro-curve.csv` — PRO curves (raw and z).
- Overlay PNGs: `bottle_good_000.png`, `bottle_broken_large_000.png`, `bottle_broken_small_000.png`, `bottle_contamination_000.png`.

## Problems

None. No errors or failures occurred during the run.
