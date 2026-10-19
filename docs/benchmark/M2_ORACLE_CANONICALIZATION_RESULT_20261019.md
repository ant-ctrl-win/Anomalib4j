# M2-P oracle canonicalization results

Completed 2026-10-19. **B - PARTIALLY SUPPORTED.** Known inverse transforms
recover substantial score/map degradation in some regimes, especially shrinking
then restoring scale, but do not restore the original detector response reliably.
Texture rotations barely recover; enlargement followed by shrinking can make
scores worse. A production pose estimator is not justified yet.

**One recommended next step:** a controlled round-trip resampling/boundary
diagnostic on unchanged M2-P that separates interpolation damage from lost
boundary content, before implementing pose estimation.

## Coverage, identity and provenance

All 15 categories; the same **467 normal test images** as the
[controlled robustness experiment](M2_ROBUSTNESS_RESULT_20261018.md).
Six rotations and four scales give **4,670 image/level triples** and
**150 category/level summaries**. No anomalous image was scored, no quality
evaluation or JMH was rerun, and no model was fitted or recalibrated.

Evidence: `target/robustness/m2-oracle-canonicalization-20261019/`.
The [protocol](../experiments/M2_ORACLE_CANONICALIZATION_PLAN.md) was saved before
measurement. All 15 archived models are copied byte-for-byte from
`target/robustness/m2-controlled-20261018`; manifests and dataset bytes were
checked against `m0-all15-baseline-20261016`. All **128 captured source/protocol
hashes match** after the run. Production M0/M2, encoder, preprocessing, transform
implementation, calibration and metric definitions are unchanged.

- **1,401 exact native-map comparisons**, maximum difference **0**: each original
  image versus its canonical RGB input, plus rotation-zero and scale-one round
  trips for each image. Identity round trips also preserve RGB bytes exactly.
- **4,670 exact retained pairs**: S0, Sp and their interior counterparts match
  the previous robustness CSV by category/image/family/level.
- Saved baseline/perturbed/canonicalized maps are finite. Their maxima and all
  map-error diagnostics were independently checked against the CSV in Python.
- Source snapshots, model/input hashes, hardware, commands, progress, per-category
  parity/identity records and completion marker are retained. Dataset and target
  evidence are local/ignored, not guaranteed in a clone. Exact ONNX checkpoint
  and export provenance remains unresolved; encoder byte identity is established.

The [correspondence report](M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md) retains
its original N1 recommendation as history. The user's 2026-10-19 instruction
supersedes that recommendation: M2-P stays selected; N1/G development is not
continued here. No historical result or report was altered.

## Transform and measurement contract

For each image independently:

1. Decode and obtain the existing whole-image bicubic 224x224 uint8 RGB input I.
2. Existing ImageNet normalization -> frozen MobileNetV4 -> unchanged M2-P gives
   Z0 and S0=max(Z0).
3. Ip=T(I), using the existing `RobustnessTransforms` implementation; infer Zp/Sp.
4. Ic=T-inverse(Ip), with **-theta** for rotation and **1/s** for scale; infer Zc/Sc.

Both warps use center **(111.5,111.5)**, inverse pixel-center mapping, bilinear
sampling, coordinate clamping/edge replication, and RGB rounding/clipping to
[0,255] after each warp. Positive rotation is clockwise; scale >1 enlarges.
There is no fused transform, extra crop, padding change, map warp, neighborhood
search, heatmap interpolation or new normalization. Every level starts from I.
The inverse is exact in coordinate parameters, not a numerical inverse of the
resampling operation. Clipped content cannot be recovered.

Dp=Sp-S0; Dc=Sc-S0. Recovery=1-Dc/Dp only when **abs(Dp)>0.1 Z**, fixed before
measurement. No ratio clipping. **4,649/4,670** full-score ratios and
**4,618/4,670** interior ratios are eligible. Of the full-score pairs, 208 have
Dp < -0.1: the perturbation lowered their score, so their signed ratio is not a
literal fraction of a positive degradation recovered. Twenty-one near-zero
denominators are excluded, not replaced by zero.

Tables use equal-category means of category statistics. Thus a column labeled
median is the mean of 15 within-category paired medians, not a pooled median.
Recovery is the mean of category median eligible ratios, **not** 1 minus a ratio
of macro deltas. This distinction explains why large drops in macro Dc coexist
with modest or negative recovery averages. p90/p95 likewise mean category
quantiles (NumPy linear interpolation), not pooled tail probabilities.

Complete requested statistics for **every category and level**, including
median Dp/Dc, mean/p90/p95 Dc, median recovery and valid count, abs(Dc)<=1/2/5
fractions, full/interior map errors and RGB diagnostics, are in
[aggregate.csv](../../target/robustness/m2-oracle-canonicalization-20261019/aggregate.csv).
All/texture/object summaries are in
[macro.csv](../../target/robustness/m2-oracle-canonicalization-20261019/macro.csv).

## Rotation and scale score recovery

Units are native positional Z; percentages are diagnostic fractions, not FPR.

| Transform | Median Dp | Median Dc | Mean Dc | p90 Dc | p95 Dc | Recovery | abs(Dc)<=1 | <=2 | <=5 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Rotation -20 | 71.949 | 24.194 | 23.934 | 29.366 | 30.935 | 30.2% | 3.1% | 4.9% | 9.7% |
| Rotation -10 | 50.833 | 13.496 | 13.667 | 18.890 | 19.997 | 37.9% | 2.4% | 5.5% | 14.9% |
| Rotation -5 | 14.822 | 5.199 | 5.595 | 9.451 | 11.036 | 42.9% | 16.2% | 26.7% | 57.3% |
| Rotation +5 | 17.780 | 5.113 | 5.324 | 8.983 | 10.154 | 47.3% | 13.0% | 27.5% | 56.3% |
| Rotation +10 | 49.148 | 13.272 | 13.258 | 17.838 | 19.268 | 32.7% | 2.6% | 5.8% | 15.8% |
| Rotation +20 | 82.280 | 28.995 | 28.901 | 36.911 | 38.858 | 29.1% | 3.6% | 5.8% | 8.9% |
| Scale .90 | 62.983 | 1.172 | 1.155 | 3.392 | 4.001 | 97.0% | 41.8% | 66.9% | 92.7% |
| Scale .95 | 18.463 | 1.278 | 1.392 | 3.339 | 4.050 | 92.3% | 39.9% | 64.8% | 92.0% |
| Scale 1.05 | 9.521 | 6.433 | 6.772 | 11.128 | 12.484 | -21.1% | 10.2% | 21.7% | 43.1% |
| Scale 1.10 | 45.256 | 28.500 | 29.714 | 40.869 | 45.686 | -220.6% | 0.3% | 0.8% | 7.9% |

Numerical answers: at -5/+5 degrees recovery is **42.9/47.3%** with about +5 Z
remaining; at -10/+10 it is **37.9/32.7%**, about +13 Z remaining; at -20/+20 it
is **30.2/29.1%**, +24 to +29 Z remaining. For -5%/+5% scale it is
**92.3/-21.1%**; for -10%/+10%, **97.0/-220.6%**. These signed summaries must
not be averaged into an apparent symmetric scale tolerance.

The scale asymmetry is important: shrinking first preserves the object in the
canvas, while enlarging first discards outer content which inverse shrinking
replaces by replicated edges. Texture scale 1.05/1.10 has small original macro
Dp (0.572/0.624) but corrected Dc 10.195/21.200. Ratios amplify that mismatch;
the absolute scores independently demonstrate the failure. There are 79/62
eligible negative-Dp observations at those two scales.

## Native-map recovery and interior diagnostic

Maps are compared at corresponding positions in the recovered frame. MAE/RMSE
below are equal-category means of per-image errors across 196 or 144 cells.
Max is the mean of per-image maximum absolute errors, not a maximum of means.
The artifact also retains the observed maximum for every category/level.

| Transform | Perturbed RMSE | Corrected MAE | Corrected RMSE | Corrected max | Interior MAE | Interior RMSE | Interior max | Interior median Dc |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Rotation -20 | 15.135 | 3.100 | 6.018 | 29.805 | 1.992 | 4.171 | 20.190 | 15.719 |
| Rotation -10 | 8.804 | 1.854 | 3.655 | 19.279 | 1.141 | 2.276 | 12.795 | 8.577 |
| Rotation -5 | 3.583 | 1.051 | 1.839 | 10.311 | .753 | 1.163 | 5.781 | 2.339 |
| Rotation +5 | 3.820 | 1.068 | 1.863 | 10.115 | .767 | 1.215 | 6.544 | 2.834 |
| Rotation +10 | 8.853 | 1.893 | 3.700 | 18.683 | 1.171 | 2.392 | 14.480 | 9.784 |
| Rotation +20 | 15.883 | 3.126 | 6.255 | 34.814 | 1.981 | 4.210 | 21.670 | 17.152 |
| Scale .90 | 13.788 | .710 | .970 | 4.305 | .714 | .953 | 3.655 | .959 |
| Scale .95 | 4.870 | .778 | 1.053 | 4.574 | .791 | 1.049 | 4.013 | 1.242 |
| Scale 1.05 | 2.815 | 1.668 | 2.535 | 11.509 | 1.102 | 1.615 | 6.925 | 3.407 |
| Scale 1.10 | 8.188 | 3.691 | 6.519 | 35.667 | 2.142 | 3.655 | 15.647 | 10.873 |

**Interior recovery is substantially better for rotations/enlargements**, but
not uniformly close to baseline. At +/-5 degrees the interior abs(Dc)<=2
fraction is 49.5/50.1%, versus full 26.7/27.5%; at scale1.10 it is 17.3%, versus
0.8%. Shrink/restore RMSE barely changes after excluding one ring. Worst observed
corrected map error is **266.611 Z** at scale1.10; the interior maximum there is
**43.410 Z**. At +20 degrees the corresponding maxima are 198.061/66.569 Z.
Receptive fields extend beyond the selected interior: this is not a causal
isolation of image borders or an alternative production score.

## RGB round-trip damage

RGB units are byte levels [0,255], across all channels and pixels. Mean max is
the equal-category mean of per-image maxima; observed max is over all images.

| Transform | MAE | RMSE | Mean max | Observed max |
|---|---:|---:|---:|---:|
| Rotation -20 | 4.102 | 8.049 | 89.234 | 214 |
| Rotation -10 | 3.432 | 6.139 | 85.258 | 212 |
| Rotation -5 | 3.154 | 5.386 | 77.897 | 210 |
| Rotation +5 | 3.157 | 5.402 | 79.631 | 215 |
| Rotation +10 | 3.435 | 6.158 | 86.498 | 213 |
| Rotation +20 | 4.114 | 8.094 | 90.201 | 212 |
| Scale .90 | 3.186 | 5.112 | 63.452 | 164 |
| Scale .95 | 3.072 | 4.937 | 60.775 | 158 |
| Scale 1.05 | 3.324 | 5.983 | 81.571 | 213 |
| Scale 1.10 | 3.992 | 7.590 | 87.738 | 223 |

The remaining error is compatible with resampling/boundary damage: exact
transform knowledge removes estimation uncertainty, but changes RGB content;
identity produces zero error. Texture detail, cropped content and replicated
boundaries are plausible contributors. **Their relative contributions are not
established.** Across 150 within-category/level Pearson correlations, RGB RMSE
versus map RMSE has median **0.075**, mean **0.086**, positive in **89/150**;
RGB RMSE versus abs(Dc) has median **0.036**, mean **0.033**, positive in **82/150**.
These weak associations do not support a simple scalar RGB-error explanation
of M2 sensitivity, nor prove that borders alone dominate. Position, local
variance weighting and receptive fields matter. No causal or significance claim
is made from these descriptive correlations.

## Categories, textures and objects

The table averages within-category median deltas equally over six rotations
or four scales. It is a range summary; individual levels remain in aggregate.csv.

| Category | Normals | Rotation Dp -> Dc | Scale Dp -> Dc |
|---|---:|---:|---:|
| bottle | 20 | 4.578 -> 1.590 | 163.005 -> 13.846 |
| cable | 58 | 8.849 -> 7.958 | 11.703 -> 4.678 |
| capsule | 23 | 146.498 -> 13.188 | 49.244 -> 9.335 |
| carpet | 28 | 22.421 -> 23.684 | 11.425 -> 10.224 |
| grid | 21 | 25.765 -> 24.087 | 10.352 -> 9.539 |
| hazelnut | 40 | 9.213 -> 7.680 | 6.918 -> 3.729 |
| leather | 32 | 22.469 -> 23.126 | 12.465 -> 11.401 |
| metal_nut | 22 | 15.814 -> 11.769 | 11.143 -> 4.348 |
| pill | 26 | 34.406 -> 5.159 | 29.704 -> 1.920 |
| screw | 41 | 13.192 -> 10.437 | 4.584 -> 3.045 |
| tile | 33 | 12.504 -> 12.036 | 6.768 -> 6.373 |
| toothbrush | 12 | 255.443 -> 14.227 | 156.946 -> 44.885 |
| transistor | 60 | 65.398 -> 17.496 | 15.026 -> 2.168 |
| wood | 19 | 12.654 -> 13.202 | 5.059 -> 4.838 |
| zipper | 32 | 67.825 -> 40.035 | 16.492 -> 9.856 |

Poor-recovery examples despite perfect parameters:

- Zipper +20 degrees: median Dp **125.502**, Dc **144.818**, p95 Dc **180.394**;
  interior Dc **20.128**. Correction worsens full score here.
- Toothbrush scale1.10: Dc **167.783**, p95 **239.975**; interior Dc **9.831**.
- Bottle scale1.10: Dc **48.304**, interior **2.961**.
- Grid -20/+20: Dc **42.447/42.047**, interior **42.805/43.840**; one-ring exclusion
  offers no solution. Carpet/leather rotations can also worsen after correction.
- In contrast, Bottle rotation has mean level-median Dc **1.590**; Pill scale
  **1.920** and Transistor scale **2.168**. Large percentage recovery in Capsule
  or Toothbrush rotation still leaves double-digit absolute residuals.

Textures are Carpet, Grid, Leather, Tile, Wood; objects are the other ten.

| Group/family | Median Dp | Median Dc | Recovery | Full / interior map RMSE | RGB RMSE |
|---|---:|---:|---:|---:|---:|
| Textures / rotation | 19.163 | 19.227 | -2.1% | 5.509 / 4.223 | 10.278 |
| Objects / rotation | 62.122 | 12.954 | 56.0% | 3.078 / 1.745 | 4.668 |
| Textures / scale | 9.214 | 8.475 | -140.2% | 3.585 / 2.796 | 9.351 |
| Objects / scale | 46.476 | 9.781 | 50.5% | 2.361 / 1.329 | 4.183 |

These additionally average levels equally within a family. Textures suffer more
RGB round-trip damage and recover much less. Group averages do not guarantee
any individual category, and negative signed scale ratios have the limitations
described above. The normal-only experiment cannot establish preservation of
anomaly sensitivity under a future real canonicalizer.

## Lightweight inverse-warp timing

Three fresh JVMs, eight preloaded canonical Bottle normal images, ten precomputed
perturbation fixtures per image. Three seconds mixed warmup per JVM, nine rounds
of 16 inverse warps per level, level order rotated by round/fork. Both families
use the same fixtures/runtime/hardware. Timer covers the unchanged transform's
RGB extraction, arrays/output allocation, sampling, rounding and volatile output
reference publication. Excludes decode, fixture creation, CNN, M2 and I/O.
Hardware is the same Ryzen AI 9 HX 370 system; no affinity/power control.

Microseconds per warp below are **medians/means of batch-average timings**, not
per-call p50/p95. Detailed 270 batch records are in timing-0/1/2.csv.

| JVM | Rotation median / mean | Scale median / mean |
|---|---:|---:|
| 0 | 1031.372 / 1036.128 | 1037.594 / 1071.879 |
| 1 | 1029.475 / 1032.495 | 1027.763 / 1038.095 |
| 2 | 1020.050 / 1035.572 | 1041.631 / 1047.706 |
| Pooled | **1029.888 / 1034.731** | **1037.047 / 1052.560** |

Approximate measured cost is **1.030 ms rotation / 1.037 ms scale** per inverse
warp. Pooled batch-average ranges: 1011.706-1256.906 us rotation,
1022.781-1652.550 us scale. This is the existing diagnostic implementation,
not an optimized production warp or complete-detector measurement.

Unchanged M2-P scorer references already exist: JMH raw scorer p50 **17.696 us**
in [Java M2 validation](JAVA_M2_RESULT_20261018.md); the later correspondence
harness measured pooled batch median **28.403 us**, including its identity
argmin bookkeeping. These are separately measured historical references, not
contemporaneously matched denominators for a warp speed ratio. No JMH was rerun,
no stage medians were added, and no pose-estimator or full-pipeline timing is
claimed. Pose estimation is not implemented.

## Execution, tests and limitations

Measurement/verification/summarization wall time: **164.769 s**. Total including
the lightweight timing subprocesses: **179.881 s (2m59.881s)**. Both come from
complete.json; these operational durations are not per-image inference latency.
The shell wait timed out while the process continued; completion artifacts and
all validated outputs establish success. No category or campaign was rerun.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/oracle/run.py --output target/robustness/m2-oracle-canonicalization-new
```

Omitting --category selects all 15; `--category bottle` selects one. Existing
output roots are rejected. Never use `mvn clean` on retained local evidence.
Progress has category index, ORACLE ROUND TRIP start/complete, family/level and
elapsed category seconds, followed by pair/map validation and timing phases.

**136 Java tests passed, 32 Python tests passed** (two upstream Typer/Click
deprecation warnings). Focused additions cover identity pixels, inverse parameters,
deterministic round trips, full/interior map-error arithmetic, score pairing and
recovery-denominator exclusion. Source-hash guards before/after the real run
verify production is unchanged. Tests ran with:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools -q --basetemp target/test-tmp/oracle-final
```

Logs: `target/oracle-java-tests.log`, `target/oracle-python-tests.log`;
campaign log: `target/oracle-campaign.log`. Python rule checks and
`git diff --check` passed. An initial compile caught an incorrect call to the
existing map-writer API; it was fixed before measurement, without changing tests.
The two new Python tests first failed for missing implementation, then passed.
No benchmark, production or retained-artifact modification was needed.

Per-category `baseline-z.npy` rows follow normal-test.txt. `perturbed-z.npy`
and `canonicalized-z.npy` rows follow per-image.csv (family, level, image).
All maps are float64 [N,196]. CSV holds scores and map/RGB errors; Dp/Dc/recovery
are derived from the retained scores with the frozen guard. RGB images themselves
are reconstructed deterministically from the frozen inputs/parameters.

This is an oracle test of double resampling on canonical images, not natural
pose variation, operational false positives, reflection robustness or anomaly
quality. Perfect parameter knowledge still leaves important residuals; boundary
loss and interpolation have not yet been separated. The verdict and sole next
step at the top apply within this scope. No real canonicalizer was implemented
and no commit was made.
