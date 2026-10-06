# HELDOUT_MULTI_SEED_RESULT

Real validation of the already-implemented multi-seed held-out positional calibration.
This report only records the values measured by the test run.

## Command executed

```
mvn -B "-Dtest=*Test,BottleHeldOutMultiSeedIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

Split seeds: 1, 2, 3, 4, 5. Projection seed remains 42. Each seed: sort the 209
training-good filenames lexicographically, `Collections.shuffle(list, new Random(seed))`,
first 167 for VSA statistics/archetypes/Adjoint, remaining 42 for calibration only.
Each seed evaluates paired RAW and calibrated scores on the same 83 test images
(20 good / 63 anomaly). Delta = calibrated - raw within each seed.

## Build and test outcome

- Result: **BUILD SUCCESS**
- Tests run: **84**, Failures: 0, Errors: 0, Skipped: 0
- Total time: **21:28 min** (1288 s)
- `BottleHeldOutMultiSeedIT`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, duration **1285 s**

## Per-seed metrics

| seed | RAW Image | CAL Image | Δ Image | RAW Pixel | CAL Pixel | Δ Pixel | RAW AUPRO@0.30 | CAL AUPRO@0.30 | Δ AUPRO@0.30 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0.98571428571428580 | 1.0000000000000000 | +0.014285714285714235 | 0.95692084602517140 | 0.96194563144550190 | +0.0050247854203305800 | 0.86548538415979860 | 0.87843462082166750 | +0.012949236661868868 |
| 2 | 0.98571428571428580 | 1.0000000000000000 | +0.014285714285714235 | 0.95695898419271660 | 0.95895370372249630 | +0.0019947195297796982 | 0.86559200810176170 | 0.87083566559853500 | +0.0052436574967732460 |
| 3 | 0.98571428571428580 | 1.0000000000000000 | +0.014285714285714235 | 0.95710090300217090 | 0.95855819624591380 | +0.0014572932437428854 | 0.86536159395394240 | 0.87339665237558540 | +0.0080350584216429820 |
| 4 | 0.98571428571428580 | 1.0000000000000000 | +0.014285714285714235 | 0.95692435573788700 | 0.96191068604253720 | +0.0049863303046501220 | 0.86551238694338840 | 0.88156435720666470 | +0.016051970263276316 |
| 5 | 0.98571428571428580 | 1.0000000000000000 | +0.014285714285714235 | 0.95698231191437170 | 0.96074688596048700 | +0.0037645740461152988 | 0.86561518533731230 | 0.87803472690747790 | +0.012419541570165560 |

## Summary across the 5 seeds

Sample standard deviation uses N-1 = 4.

| evaluation | metric | n | mean | sample_std_dev | min | max |
| --- | --- | --- | --- | --- | --- | --- |
| raw | image_auroc | 5 | 0.98571428571428580 | 0.0000000000000000 | 0.98571428571428580 | 0.98571428571428580 |
| raw | pixel_auroc | 5 | 0.95697748017446350 | 7.3532771738572140e-05 | 0.95692084602517140 | 0.95710090300217090 |
| raw | aupro030 | 5 | 0.86551331169924070 | 0.00010046198133467776 | 0.86536159395394240 | 0.86561518533731230 |
| calibrated | image_auroc | 5 | 1.0000000000000000 | 0.0000000000000000 | 1.0000000000000000 | 1.0000000000000000 |
| calibrated | pixel_auroc | 5 | 0.96042302068338710 | 0.0016025638041582697 | 0.95855819624591380 | 0.96194563144550190 |
| calibrated | aupro030 | 5 | 0.87645320458198610 | 0.0042851592722647890 | 0.87083566559853500 | 0.88156435720666470 |
| delta | image_auroc | 5 | 0.014285714285714235 | 0.0000000000000000 | 0.014285714285714235 | 0.014285714285714235 |
| delta | pixel_auroc | 5 | 0.0034455405089237170 | 0.0016604094964567870 | 0.0014572932437428854 | 0.0050247854203305800 |
| delta | aupro030 | 5 | 0.010939892882745394 | 0.0042791739455247580 | 0.0052436574967732460 | 0.016051970263276316 |

## Stability of improvement

| metric | positive | zero | negative | positive_for_all_seeds |
| --- | --- | --- | --- | --- |
| image_auroc | 5 | 0 | 0 | true |
| pixel_auroc | 5 | 0 | 0 | true |
| aupro030 | 5 | 0 | 0 | true |

- Seeds with delta Image AUROC > 0: **5 of 5**
- Seeds with delta Pixel AUROC > 0: **5 of 5**
- Seeds with delta AUPRO@0.30 > 0: **5 of 5**

## Other recorded values

- Sigma floor: 1.0000000000000000e-06 raw units.
- Sigma floor hits: **0** in every seed (`Floored positions: 0` for seeds 1-5).

## Artifacts produced

Directory: `target/bottle-spatial14-heldout-multiseed/`

- `per-seed.csv` — the per-seed table above.
- `summary.csv` — the summary table above.
- `delta-signs.csv` — sign counts.
- `summary.md` — narrative summary produced by the test.
- `seed-1/` ... `seed-5/` each containing: `archetype-training.txt`, `calibration.txt`,
  `parameters.csv`, `parameter-summary.csv`, `image-scores.csv`, `test-scores.csv`,
  `metrics.csv`, `comparison.md`, `raw-pro-curve.csv`, `z-pro-curve.csv`, and overlay PNGs
  `bottle_good_000.png`, `bottle_broken_large_000.png`, `bottle_broken_small_000.png`,
  `bottle_contamination_000.png`.

## Problems

None. No errors or failures occurred during the run.
