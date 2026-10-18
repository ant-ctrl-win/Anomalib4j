# Controlled Java M2 robustness results

Completed 2026-10-18. **All 15 categories, 467 normal test images, 19,147 paired
image/level observations, 615 category/level summaries.** Java M2, encoder,
preprocessing, splits and score definitions are unchanged. No anomaly-quality
campaign or JMH run was performed. Operational wall time: **187.610 seconds
(3m07.610s)**, including input verification, model preparation, scoring and plots.
This duration is not an inference-performance measurement.

The broad measured weakness is **rotation/scale sensitivity**, with additional
translation sensitivity and severe category-specific photometric failures.
Brightness has the largest absolute macro score shifts because of Bottle;
that result must not be hidden behind the geometric conclusion. Rotation/scale
is the strongest family response in 13/15 categories and remains large on the
interior. Recommend exactly one next experiment: **controlled rotation/scale
canonicalization/alignment versus unchanged M2**. It is not implemented here.

## Protocol, provenance and identity

The [protocol](../experiments/M2_ROBUSTNESS_PLAN.md) was frozen before measurement.
Evidence root: `target/robustness/m2-controlled-20261018/` (ignored local evidence,
not included in a fresh clone). Inputs are bound to
`target/comparison/m0-all15-baseline-20261016` manifests and
`target/ablation/local-models-20261017` feature dumps. The previously validated
Java Bottle, Metal Nut and Hazelnut archives were copied; the other twelve
archives were fitted with the existing Java trainer from the same retained FIT
and HELD-OUT descriptors. No split was regenerated or tuned.

Retained provenance includes `plan.json`, `source-hashes.json`, `workspace/`,
copied `data/` manifests, per-category `input-hashes.json`, archives and exact
commands in `run.log`. Original M0/encoder/asset hashes and selected dataset
bytes were verified against the retained baseline; all five production M2
source hashes matched the validated Java run. Dataset location is in `plan.json`.
Exact ONNX export/checkpoint provenance remains unresolved; byte identity is
verified. See [Java M2 validation](JAVA_M2_RESULT_20261018.md).

Transformation order:

1. Existing image decode and whole-image bicubic RGB resize to 224x224.
2. Transform that canonical uint8 RGB image.
3. Existing ImageNet normalization, MobileNetV4 SPATIAL_14 encoder and Java M2.

The test-only `CanonicalRgb` adapter recovers RGB bytes from the existing
preprocessor's normalized float32 tensor. Round-trip recovery is tested for all
256 byte values in every channel and **tensor equality is checked on every real
image**. The encoder's same-size redraw leaves the canonical tensor unchanged.
No production preprocessing API or arithmetic was changed.

Each original native map was compared with production `M2Detector`; each of the
seven family identities was also independently encoded and compared with that
original. **3,736 map comparisons were bit-identical; maximum difference 0.**
All 15 categories' held-out calibration Z arrays passed the frozen Python/Java
tolerance (`atol=1e-8`, `rtol=1e-10`); largest absolute difference **3.192e-14**.
Newly fitted Java means/effective stds also passed comparison with the Python
formula. Category `identity.json` and `calibration-parity.json` retain the checks.

## Exact transform contract

| Family | Levels |
|---|---|
| Translation X, separately Y | -8, -4, -2, 0, +2, +4, +8 input pixels |
| Center rotation | -20, -10, -5, 0, +5, +10, +20 degrees |
| Center isotropic scale | 0.90, 0.95, 1.00, 1.05, 1.10 |
| Multiplicative brightness | 0.80, 0.90, 1.00, 1.10, 1.20 |
| Contrast about 127.5 | 0.80, 0.90, 1.00, 1.10, 1.20 |
| Gamma | 0.80, 0.90, 1.00, 1.10, 1.20 |

Positive translations move content right/down. Positive rotation is clockwise
in image coordinates. Geometry uses inverse pixel-center mapping around
**(111.5,111.5)**. Scale above one enlarges content. Rotation and scaling use
the same bilinear sampler, with **edge replication** by clamping source
coordinates. Integer translation samples exact source pixels. There is no black
fill. Channels are rounded to nearest integer and clipped to [0,255].
Gamma is `output=255*(input/255)^gamma`; gamma above one darkens. Each level
starts from the original canonical RGB, never a previous transformed image.
No X/Y Cartesian combinations, hue/saturation, per-category settings or tuning.

`S0=max(Z_original)`, `St=max(Z_transformed)`, `delta=St-S0`. The only spatial
diagnostic is the corresponding max on rows/columns 1..12 of the native 14x14
map, excluding one outer ring. This is not a replacement detector. No heatmap
interpolation is required for these native-grid measurements.

Each category's diagnostic reference is the **empirical maximum of its held-out
calibration image max-Z scores**, fixed without test images. Exceedance is strict
`St > reference`. It is **not a deployed threshold, production FPR or probability**.
Those same calibration observations estimate positional Z, so this reference
is not an independently calibrated false-alarm guarantee. Its dependence on
calibration sample size is especially relevant to Toothbrush (12 observations).

## Category coverage and original scores

`Exceed0` is the fraction of original normal tests above the diagnostic reference.
All images below are test-good; no anomalous image was scored in this experiment.

| Category | FIT / CAL | Normal tests | Reference | Median S0 | Exceed0 |
|---|---:|---:|---:|---:|---:|
| Bottle | 167 / 42 | 20 | 6.1464 | 5.6710 | 45.00% |
| Cable | 179 / 45 | 58 | 6.0304 | 4.7113 | 31.03% |
| Capsule | 175 / 44 | 23 | 6.4172 | 5.4230 | 39.13% |
| Carpet | 224 / 56 | 28 | 6.1375 | 4.9150 | 10.71% |
| Grid | 211 / 53 | 21 | 5.3840 | 4.8626 | 38.10% |
| Hazelnut | 312 / 79 | 40 | 7.8583 | 7.3463 | 45.00% |
| Leather | 196 / 49 | 32 | 6.1756 | 5.1301 | 28.12% |
| Metal Nut | 176 / 44 | 22 | 6.0552 | 5.1466 | 45.45% |
| Pill | 213 / 54 | 26 | 6.5206 | 5.9521 | 38.46% |
| Screw | 256 / 64 | 41 | 5.8236 | 4.1031 | 29.27% |
| Tile | 184 / 46 | 33 | 5.4483 | 4.8986 | 36.36% |
| Toothbrush | 48 / 12 | 12 | 3.1518 | 6.5375 | 91.67% |
| Transistor | 170 / 43 | 60 | 5.6389 | 4.3960 | 25.00% |
| Wood | 197 / 50 | 19 | 5.8717 | 4.8949 | 42.11% |
| Zipper | 192 / 48 | 32 | 5.4952 | 4.9460 | 31.25% |

Original macro exceedance is **38.44%**, versus **34.69%** pooled over images.
The large original exceedance makes it essential to report score changes and
original exceedance alongside transformed exceedance. It is not evidence of
38.44% operational false positives: no operational threshold exists here.

## Geometric results

Tables use the **equal-category mean of category median paired deltas**, not
the median of all images or a difference of score medians. `Interior` uses the
same aggregation on its own paired scores. `Exceed` is mean category diagnostic
exceedance. Every identity has delta 0 and macro exceedance 38.44%.
The pre-run descriptive convention calls a category median rise above **+1 Z**
material; it is not a significance test or operational criterion.

| Family | Level | Delta | Interior | Exceed |
|---|---:|---:|---:|---:|
| X translation | -8 | 29.591 | 20.969 | 99.19% |
| X translation | -4 | 6.229 | 4.930 | 76.17% |
| X translation | -2 | 1.245 | 1.279 | 51.85% |
| X translation | +2 | 1.416 | 1.278 | 53.23% |
| X translation | +4 | 7.665 | 4.957 | 75.68% |
| X translation | +8 | 33.147 | 17.971 | 95.76% |
| Y translation | -8 | 26.051 | 17.150 | 99.09% |
| Y translation | -4 | 6.657 | 3.970 | 74.49% |
| Y translation | -2 | 1.450 | 0.954 | 52.33% |
| Y translation | +2 | 1.559 | 0.925 | 55.11% |
| Y translation | +4 | 8.245 | 4.725 | 79.19% |
| Y translation | +8 | 36.767 | 20.453 | 93.53% |
| Rotation | -20 | 71.949 | 67.050 | 100.00% |
| Rotation | -10 | 50.833 | 28.730 | 99.33% |
| Rotation | -5 | 14.822 | 7.904 | 94.17% |
| Rotation | +5 | 17.780 | 8.573 | 95.83% |
| Rotation | +10 | 49.148 | 26.021 | 100.00% |
| Rotation | +20 | 82.280 | 68.847 | 100.00% |
| Scale | 0.90 | 62.983 | 36.475 | 100.00% |
| Scale | 0.95 | 18.463 | 9.590 | 98.85% |
| Scale | 1.05 | 9.521 | 5.242 | 72.68% |
| Scale | 1.10 | 45.256 | 17.736 | 76.89% |

**Translation onset:** a macro rise is already visible at 2 pixels, but only
5-7/15 categories cross the descriptive +1 threshold, depending on axis/sign.
At 4 pixels, 11-13/15 do so; +8 X affects all 15. The response is roughly
sign-symmetric near 2 pixels, but not at large displacements: Y -8/+8 gives
26.051/36.767 Z and X -8/+8 gives 29.591/33.147 Z. Thus there is no common
sign-invariant tolerance radius. Interior changes remain substantial: +8 X/Y
give 17.971/20.453 Z, so excluding the outer ring does not remove the failure.

**Rotation:** -5/+5 degrees already give 14.822/17.780 Z, with material rises
in 14/15 and 15/15 categories respectively. Interior rises are material in
13/15 and 12/15. At 10 degrees the macro increase is about 49-51 Z; at 20 it
is 72-82 Z. All categories have material full and interior rises at +10/+20.
The +20 Toothbrush maximum paired image delta is 556.087 Z.

**Scale:** +/-5% is not broadly tolerated: shrink to .95 affects all 15
categories materially; enlarge to 1.05 affects 11/15. Shrinking has the larger
macro effect. At .90/1.10 full deltas become 62.983/45.256 Z, with interior
36.475/17.736. Threshold exceedance is not strictly monotone with mean score
rise because it saturates and aggregates heterogeneous categories.

## Photometric results and exceptional categories

| Family | Level | Delta | Interior | Exceed |
|---|---:|---:|---:|---:|
| Brightness | 0.80 | 154.151 | 4.735 | 49.69% |
| Brightness | 0.90 | 93.824 | 2.441 | 47.40% |
| Brightness | 1.10 | 0.193 | 0.172 | 41.64% |
| Brightness | 1.20 | 4.693 | 2.989 | 54.20% |
| Contrast | 0.80 | 95.574 | 2.998 | 48.56% |
| Contrast | 0.90 | 80.390 | 1.895 | 48.80% |
| Contrast | 1.10 | 0.291 | 0.304 | 42.20% |
| Contrast | 1.20 | 7.483 | 7.145 | 51.69% |
| Gamma | 0.80 | 0.235 | 0.237 | 44.40% |
| Gamma | 0.90 | -0.002 | 0.016 | 40.06% |
| Gamma | 1.10 | 0.056 | 0.033 | 38.42% |
| Gamma | 1.20 | 0.148 | 0.092 | 40.84% |

**Brightness and contrast are not uniformly harmless.** Bottle dominates their
largest absolute macro shifts. Its brightness .80/.90 median paired deltas are
**2209.086/1362.231 Z**, versus interior **58.315/31.506 Z**. At brightness .80,
Bottle p95/max paired deltas are **2257.401/2270.631 Z**. Contrast .80/.90 gives
Bottle **1383.168/1177.482 Z**, versus interior **34.543/24.043 Z**. All Bottle
normals exceed the diagnostic reference at these four levels.

This is strong outer-ring concentration, but the interior is not unaffected.
Photometric transforms introduce no geometric fill, so this result cannot be
called an artifact of synthetic geometric padding. Neither variance floors nor
CNN padding were causally isolated in this task.

Other meaningful failures remain: Zipper brightness .80 gives **99.815 full /
10.006 interior Z**, and contrast .80 gives **46.129 / 7.352**. Brightness 1.20
gives Capsule **36.690 / 24.549** and Screw **27.904 / 15.440**. Contrast 1.20
gives Toothbrush **91.666 / 92.820**, so its response is not an outer-ring-only
effect. Clipping is part of the frozen experiment and was not separately ablated.

At brightness .80 and contrast .80 only **3/15** category medians rise above
1 Z, versus 14-15/15 at +/-5 degrees. Their medians across categories are only
**0.229/0.154 Z**, despite macro means 154.151/95.574. Excluding Bottle as a
*disclosed descriptive sensitivity check*, those macro means are **7.370/3.603**;
the primary all-15 results above retain Bottle. Geometry is therefore broader,
while photometric severity is extreme for particular categories.

**Gamma is comparatively well tolerated over this grid:** macro shifts stay
between -0.002 and 0.235 Z. Only Toothbrush at .80 crosses the +1 convention
(1.195 full / 1.297 interior); no category does at 1.20. This does not establish
robustness to reflections, local glare, nonuniform illumination or color changes,
which were not tested.

## Texture/object comparison and category ranking

Textures: Carpet, Grid, Leather, Tile, Wood. Objects: the remaining ten.
Numbers below first average category median deltas equally over nonidentity
levels, then equally over categories in the group. They describe the tested
ranges; physical magnitudes across families are not commensurate.

| Family | Texture full / interior | Object full / interior |
|---|---:|---:|
| X translation | 4.799 / 3.810 | 17.424 / 10.941 |
| Y translation | 4.185 / 2.081 | 18.090 / 11.004 |
| Rotation | 19.163 / 16.746 | 62.122 / 43.408 |
| Scale | 9.214 / 7.490 | 46.476 / 22.146 |
| Brightness | 0.069 / 0.124 | 94.788 / 3.815 |
| Contrast | 0.097 / 0.093 | 68.853 / 4.582 |
| Gamma | 0.051 / 0.064 | 0.138 / 0.110 |

Textures are more stable on average, especially photometrically, but are not
rotation invariant: their mean diagnostic exceedance is 100% at every nonzero
rotation level. Objects have larger average geometric changes and contain the
large photometric exceptions. Group means must not be read as uniform behavior.

Predeclared descriptive ranking: mean of seven family-wise means of nonidentity
median deltas, divided by the category's calibration reference. Each family has
equal weight. References depend on finite calibration samples; the ranking is
not accuracy, production risk or a statistical significance ordering.

| Category, most to least fragile | Ranking value | Largest mean family response |
|---|---:|---|
| Bottle | 41.7087 | Brightness |
| Toothbrush | 24.6278 | Rotation |
| Capsule | 5.5771 | Rotation |
| Zipper | 4.0329 | Rotation |
| Transistor | 2.3196 | Rotation |
| Pill | 2.0496 | Rotation |
| Metal Nut | 1.7330 | Y translation |
| Grid | 1.1661 | Rotation |
| Leather | 1.1184 | Rotation |
| Carpet | 1.0844 | Rotation |
| Tile | 0.7286 | Rotation |
| Screw | 0.6827 | Rotation |
| Cable | 0.6665 | Scale |
| Wood | 0.5162 | Rotation |
| Hazelnut | 0.3793 | Rotation |

Thus **Bottle and Toothbrush** are most fragile by the frozen summary, followed
by Capsule and Zipper. **Hazelnut and Wood**, then Cable/Screw/Tile, are relatively
most robust. Relative robustness does not mean immunity: Hazelnut's mean
rotation delta is 9.212 Z, Wood's 12.654, and Screw has the brightness exception
above. Bottle is much less rotation-sensitive (family mean 4.578) than its
photometric/scale sensitivity would suggest.

## Interpretation and exactly one next experiment

The measured system is translation-sensitive, rotation-sensitive and
scale-sensitive; it is also photometrically sensitive in particular categories.
Gamma is comparatively stable at the tested levels. This measures the joint
encoder plus strictly positional M2 response; it does not causally assign all
changes to wrong-cell comparisons. Interpolation, cropped content and CNN
receptive fields are part of this experiment.

The interior diagnostic reduces many responses but leaves large geometric
changes. Excluding one ring cannot isolate all padding effects because CNN
receptive fields extend beyond those cells. It must remain a diagnostic.

**Next experiment: one controlled rotation/scale canonicalization/alignment
ablation against unchanged M2**, keeping the frozen data splits and scorer
definition. Rotation is the strongest average family in twelve categories,
scale in Cable; only Metal Nut favors translation and Bottle favors brightness.
This prioritizes the broad interior-supported failure, rather than selecting
solely by a macro mean dominated by one category. Do not combine it with a
3x3 neighborhood or photometric intervention. Bottle's severe photometric
failure remains an explicit limitation; this recommendation does not claim
alignment will fix it. No additional model experiment was implemented.

## Artifacts, plots and reproducibility

- Per category: `per-image.csv` (identity, family/level, original/transformed
  full/interior scores and paired deltas, reference/exceedance), `normal-test.txt`,
  archive, calibration descriptors/Z, input hashes, parity/identity and completion.
- `aggregate.csv` / `aggregate.json`: all 615 category/level rows, including N,
  median S0/St, median/mean/p90/p95/max paired delta and the same interior fields,
  reference and exceedance. Quantiles use NumPy linear interpolation.
- `macro.csv`: equal-category all/texture/object curves; `progress.log`, exact
  Java commands, `complete.json` with operational duration. A shell wait timed
  out while the process continued; the final markers and complete outputs
  establish completion, not the waiting tool's status.
- Plots: [X translation](../../target/robustness/m2-controlled-20261018/plots/translation_x.png),
  [Y translation](../../target/robustness/m2-controlled-20261018/plots/translation_y.png),
  [rotation](../../target/robustness/m2-controlled-20261018/plots/rotation.png),
  [scale](../../target/robustness/m2-controlled-20261018/plots/scale.png),
  [brightness](../../target/robustness/m2-controlled-20261018/plots/brightness.png),
  [contrast](../../target/robustness/m2-controlled-20261018/plots/contrast.png),
  [gamma](../../target/robustness/m2-controlled-20261018/plots/gamma.png).

Reproduction requires the external DatasetNinja dataset and retained feature/model
artifacts above. Never use `mvn clean` on this evidence-bearing workspace.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/robustness/run.py --output target/robustness/m2-controlled-new --all-categories
```

Use `--category bottle` instead for one category. Existing output roots are
rejected. Example progress: `[04/15] carpet - ROTATION - 5/7 - +5.00 - phase
0.3s - category 3.5s`; the number is degrees for rotation and the documented
family unit otherwise. This line is retained in `progress.log`.

## Validation and change scope

**123 Java tests passed; 18 Python tests passed**, with two upstream Typer/Click
deprecation warnings. Commands and logs:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools/robustness tools/ablation -q --basetemp target/test-tmp/robustness-20261018-final
```

Logs: `target/robustness-java-final.log`, `target/robustness-python-final.log`.
Tests cover tensor and family identities, exact translation direction/magnitude,
clockwise center rotation, centered bilinear enlargement, deterministic sampling,
uniform-color replicated boundaries, clipping/gamma, one-ring exclusion, image
pair consistency/duplicate rejection and paired rather than marginal summaries.
The actual all-15 CLI execution additionally verifies complete grid coverage,
all real identities and calibration parity. Expensive unrelated M0 real-data
tests were excluded; the dataset was present for this robustness campaign.
Python compilation, the Python rule checker and `git diff --check` passed.

Changes are additive test-source harnesses, Python experiment tooling and
documentation. Production Java, JMH, M0/M2 mathematics, preprocessing, encoder,
localization/quality metrics, README and historical results remain unchanged.
No commit was made.
