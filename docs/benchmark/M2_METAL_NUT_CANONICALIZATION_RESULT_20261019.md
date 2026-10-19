# Metal Nut: canonicalized training and inference with strict M2-P

Completed 2026-10-19. **NO-GO.** Quality, statistical coherence and controlled
rotation stability improve, but measured canonicalization costs **12.308 ms**
per image, failing the frozen **2 ms** preprocessing budget. This overhead is
not small relative to the project's existing few-millisecond detector. One
post-warp geometry check also exposes a framing limitation on an anomalous image.
Scientific and engineering conclusions are kept separate below: the observed
quality/coherence/robustness gain is positive, while the measured prototype fails
the cost gate. No broader canonicalization work is recommended under this pilot's
decision rule.

**Exactly one recommendation:** close this experimental phase with current
M2-P as the final detector, document its aligned-acquisition assumption, publish
the validated benchmark/robustness evidence, and place further invariance and
canonicalization work in future work. This is a recommendation; no publication,
production replacement or commit was performed.

The numerical results support the narrower scientific hypothesis that fitting
after alignment can make positional feature distributions more coherent. The
NO-GO does not mean that quality declined; the failed cost criterion must not
be hidden by the accuracy gain.

## Protocol, scope and retained evidence

The [frozen pilot plan](../experiments/M2_METAL_NUT_CANONICALIZATION_PLAN.md)
was saved before calibration/test quality evaluation. Only Metal Nut was run.
Production M2 mathematics and code, M0, encoder, existing preprocessing,
metric definitions, historical reports and artifacts remain unchanged.

Evidence root: `target/experiments/m2-metal-nut-canonicalization-20261019`.
All **133 captured source/protocol hashes match** the completed run. The archive
for baseline A is copied from `m2-controlled-20261018/metal_nut`; baseline quality
comes from `local-models-20261017/M2/metal_nut/metrics-unified.json`. Dataset
bytes and split identities were checked against `m0-all15-baseline-20261016`.
These target directories and the external dataset are not part of a fresh clone.
Exact ONNX export/checkpoint provenance is still unresolved; byte identity is verified.

| Role | Images | Use |
|---|---:|---|
| FIT normal | 176 | New B local statistics; FIT-only symmetry validation |
| HELD-OUT calibration normal | 44 | New B positional Z only |
| TEST normal | 22 | Quality and paired rotation diagnostics |
| TEST anomaly | 93 | Quality only |
| Controlled rotations | 88 | Four rotations of the same 22 test normals |

All **335 original images** were canonicalized with the same RGB-only function.
Every image has a pose, forward matrix and status. None was dropped. No anomaly
mask or label derives a transform. Filenames identify split membership and select
the requested normal-only robustness subset; they are not canonicalizer inputs.
No N1/G, learned detector, manual pose labels or all-15 campaign was used.

## Method and symmetry rule

Metal Nut's normal outline has four equivalent lobes. FIT-only verification
found harmonic4 dominant among angular harmonics2..12 in **176/176** images.
PCA would be poorly conditioned by this near-isotropic four-fold shape, so the
pilot uses its fourth angular moment rather than a principal axis.

1. Convert source700x700 RGB to luminance and BOX-reduce to175x175 for geometry
   estimation only. This does not resize the RGB image used for inference.
2. Deterministic Otsu threshold; largest 8-connected component; fill holes.
3. Estimate filled-mask centroid. Convert reduced pixel centers back to source
   coordinates with `(coordinate+.5)*4-.5`.
4. Compute `h4=mean(exp(4i*atan2(y-cy,x-cx)))` over the filled component.
   Orientation is `arg(h4)/4`, in **[-45,45) degrees**, clockwise image coordinates.
5. Rotate by negative estimated angle and translate the centroid to349.5,349.5.
   Canonical harmonic phase is zero, modulo90 degrees. Applied scale is always1.

Equivalent-area radius and area are recorded, but not used to rescale. FIT area
fraction std is0.002892; center std is1.353/1.195 source pixels. Scale correction
was unnecessary for this orientation pilot and could normalize away shape defects.

Failure criteria, fixed before test quality: filled area outside[.2,.8], foreground
touching the image frame, or abs(h4)<.03. There is no silent fallback. A source
estimation failure stops the pilot with statuses retained.

The modulo90 branch is deterministic. A branch crossing changes the numeric
angle by90 degrees but preserves the canonical outline. **Six of88** normal
rotation pairs cross an equivalent branch; all six agree with the crossings
predicted from the original angle and perturbation. **Zero unexpected branch
changes** were found. No intensity-based supervision chooses among equivalent
lobes. Surface texture and illumination are not necessarily four-fold symmetric;
the rule aligns geometry, not all photometric appearance.

FIT validation on the first16 frozen images, four source rotations each, gave
median/p95/max modulo90 error **0.0323/0.0877/0.1311 degrees**. Geometry was then
frozen; test metrics were not used to tune it.

## Transform, inference and localization contract

Original RGB700 -> one geometric warp at700 -> unchanged Java whole-image
bicubic224/ImageNet preprocessing -> frozen encoder -> unchanged strict M2-P.
Pillow11.3.0 performs bilinear RGB sampling; sufficient edge-replicated padding
covers all inverse sample coordinates. Output is uint8 RGB700x700, with a fixed
canvas and no adaptive crop. Pixel-center matrices are converted explicitly to
Pillow's affine-coordinate convention. Exact quarter turns use exact sine/cosine
coefficients to avoid a one-byte floating-point truncation artifact. General
angles use the frozen continuous transform. Geometry uses SciPy1.16.0.

FIT, calibration and test all use this same transform before encoding. B is
fitted by the existing Java M2Trainer/M2Runner: normalized descriptors, FIT local
sample std floored at1e-6, mean squared standardized residual, separate held-out
positional Z, native maximum. Saved/reloaded B maps are bit-identical. Baseline
A is never refitted. Its22 freshly encoded original-normal scores match the
retained Java robustness scores exactly.

For localization, canonical14x14 Z is upsampled to700x700 with existing
LocalizationMaps, then sampled through the **source-to-canonical forward matrix**
onto original-image coordinates. This map transport uses bilinear interpolation
and replicated boundaries. The unchanged evaluator compares it with the original,
untouched masks. Image score remains the canonical native max, not a maximum
recomputed after backprojection. Identity and rotated-marker tests cover the
coordinate convention. Additional map resampling is part of candidate localization,
not an alteration of AUROC/AUPRO definitions.

The experimental model requires the external canonicalizer. Its ordinary M2
archive identifies the shared encoder/preprocessor; upstream geometry identity
and transform hashes are recorded separately in config.json. It is not a drop-in
replacement for the production model without that preprocessing contract.

## Primary quality comparison

Both rows use the same115 original test identities,22 normals/93 anomalies,
132 regions,6,602,541 foreground and49,747,459 background pixels. The test-manifest
hash matches. Baseline values are read from retained evidence, not hard-coded.

| Metric | Baseline A | Canonicalized B | Absolute delta | Relative delta |
|---|---:|---:|---:|---:|
| Image AUROC | 0.845552 | **0.964321** | **+0.118768** | **+14.05%** |
| Pixel AUROC | 0.849318 | **0.886982** | **+0.037664** | **+4.43%** |
| AUPRO@0.30 | 0.624208 | **0.648731** | **+0.024523** | **+3.93%** |

The largest gain is image-level separation. Localization improves more modestly.
This is one predeclared candidate on one retained test set, not independent
generalization evidence, a significance test or all-category validation.

## Local distribution coherence

Statistics cover all18,816 position/channel FIT sample standard deviations,
before flooring. Candidate NumPy diagnostic calculations agree with Java's saved
std and calibration statistics within the asserted floating-point tolerances.

| FIT local std | Baseline A | Canonicalized B |
|---|---:|---:|
| Mean | 0.037773 | 0.022049 |
| Median | 0.010995 | 0.008036 |
| p25 | 0.005686 | 0.004042 |
| p75 | 0.044606 | 0.028993 |
| p90 | 0.112282 | 0.064723 |
| p95 | 0.168750 | 0.086921 |
| Minimum | 0.000624 | 0.000528 |
| Maximum | 0.465779 | 0.260489 |

Median std decreases **26.91%**, mean std **41.63%**. Very-low-variance counts:

| Threshold | Baseline | Canonicalized |
|---|---:|---:|
| std<=1e-6 | 0 | 0 |
| std<=1e-5 | 0 | 0 |
| std<=1e-4 | 0 | 0 |
| std<=1e-3 | 27 /18,816 (0.1435%) | 109 /18,816 (0.5793%) |

The <=1e-3 channels occur in9 positions for A and33 for B, with maximum6/8 such
channels per position. No local std hits the numerical floor; tightening is not
a floor-saturation artifact.

| Per-position mean channel variance | Baseline | Canonicalized |
|---|---:|---:|
| Mean over196 positions | 0.004705 | 0.001340 |
| Median | 0.005457 | 0.001290 |
| Minimum | 0.000137 | 0.000156 |
| Maximum | 0.009038 | 0.003894 |
| Interior12x12 mean | 0.005160 | 0.001351 |

Variance decreases at **161/196 positions**. The improvement is not confined to
the outer ring. Lower variance alone would not justify adoption, but here it
coincides with improved anomaly quality. The pilot combines centering, orientation
and resampling; it does not isolate their individual causal contributions.

![FIT variance maps, shared color scale](../../target/experiments/m2-metal-nut-canonicalization-20261019/variance-maps.png)

## Positional direction consistency and calibration spread

For unit descriptors, mean same-position cosine across all unordered pairs is
`(N*||mean(u)||^2-1)/(N-1)`. Directional dispersion is`1-||mean(u)||^2`.
Both use FIT normals only, with no test fitting or pair selection.

| Diagnostic across196 positions | Baseline | Canonicalized |
|---|---:|---:|
| Pairwise cosine mean | 0.548333 | 0.871387 |
| Pairwise cosine median | 0.476093 | 0.876198 |
| Pairwise cosine p25/p75 | 0.316422 /0.835465 | 0.814108 /0.939305 |
| Directional dispersion mean | 0.449101 | 0.127883 |
| Directional dispersion median | 0.520930 | 0.123098 |
| CAL raw-score sample-std mean | 0.454702 | 0.419435 |
| CAL raw-score sample-std median | 0.413407 | 0.392380 |
| CAL raw-score std p25/p75 | 0.342273 /0.546515 | 0.332871 /0.481430 |
| CAL raw-score std p90/p95 | 0.653544 /0.715143 | 0.615831 /0.684472 |

Feature clouds become materially tighter and more directionally consistent.
Calibration raw spread improves less: median **5.09%**, mean **7.76%** reduction.
Complete per-position variance, cosine, dispersion, low-std counts and calibration
spread are retained in position-statistics.csv and the two variance-map arrays.

## Real canonicalizer rotation robustness

For each of22 test normals: perturb the **source700 image**, estimate geometry
from that perturbed RGB alone, canonicalize, encode and score with B. No known
angle is passed into the canonicalizer; it is used only to generate the challenge
and evaluate orientation error afterward. Four angles yield88 pairs.

Baseline A was measured on exactly the same source perturbations. The earlier
robustness run warped after resizing to224, so its perturbations are **not directly
comparable**; its original scores are used only for exact parity. Both A and B
deltas below subtract their own unperturbed normal-image score.

| Rotation | A median delta | B median delta | A mean | B mean | A p95 | B p95 |
|---|---:|---:|---:|---:|---:|---:|
| -10 | 10.246 | 0.691 | 12.045 | 0.900 | 21.623 | 2.814 |
| -5 | 5.921 | 0.217 | 5.196 | 0.509 | 9.823 | 2.506 |
| +5 | 7.945 | 0.552 | 8.211 | 0.370 | 16.373 | 1.511 |
| +10 | 11.937 | 0.816 | 11.132 | 0.606 | 18.758 | 2.294 |

Relative reduction from A to B at each angle:

| Rotation | Median delta | Mean delta | p95 delta |
|---|---:|---:|---:|
| -10 | 93.26% | 92.52% | 86.99% |
| -5 | 96.33% | 90.19% | 74.49% |
| +5 | 93.06% | 95.50% | 90.77% |
| +10 | 93.16% | 94.55% | 87.77% |

Average absolute level-median delta decreases **9.012 ->0.569 Z**, a **93.69%**
reduction. Maximum B delta is5.028 Z at-5 degrees; robustness is improved, not
perfect. A/B have separately fitted calibration scales, so these are each model's
standardized score changes, not physical distances or operational false-positive
rates. Orientation error median/p95/max is **0.0353/0.0944/0.1029 degrees**;
all88 perturbed images pass source geometry estimation.

## Reliability, distributions and outliers

All335 original estimates are deterministic on repeat and pass source gates.
Raw orientation spans almost the full90-degree symmetry period in all splits.
Canonical angle residual is obtained by re-estimating geometry after the warp;
it is a self-consistency diagnostic, not external pose ground truth.

| Split | Raw angle range | Raw angle std | Post-warp abs-angle median /p95 /max |
|---|---:|---:|---:|
| FIT176 | -43.953 to43.772 | 25.485 | 0.0216 /0.0673 /0.1119 |
| CAL44 | -43.777 to43.325 | 27.982 | 0.0247 /0.0680 /0.1521 |
| TEST115 | -44.844 to44.174 | 27.150 | 0.0298 /0.0995 /0.5561,114 valid checks |

One secondary post-warp check fails: **metal_nut_bent_011.png**. Its initial
source estimate succeeds; after warping, the foreground component touches13
samples of the right175-pixel diagnostic border, with area0.51853. The warped
image visibly includes replicated edge content near the bent extension. It is
retained and evaluated, not dropped. The recorded residual0 for this failed
check is the failed Pose default, **not a measured zero angle**; the table excludes
it from angle statistics and reports the failure explicitly.

**Post-warp diagnostic status.** The failure is a self-consistency diagnostic and
reporting limitation, not a mathematical invalidation of the canonicalizer or of
the quality metrics. The source geometry estimate succeeded and its transform was
applied unchanged. The image was retained in the 115-image test set and in all
quality and map outputs; it was not dropped, retried, replaced or given a fallback
transform. The candidate metrics therefore include it. The failure is caused by
edge-replicated content touching the right diagnostic border after warping one
anomalous bent shape; it does not alter the forward transform, the positional
calibration, the anomaly score or AUROC/AUPRO. It is best described as a framing
limitation on one anomalous image.

Other descriptive outliers remain in the dataset:

- color_010: estimated center(321.089,346.236), area0.39833, versus FIT median
  center(348.407,352.802), area0.50552.
- color_009: area0.32741, center(348.740,358.664).
- bent_000: center(361.844,351.332), area0.53156.
- FIT good_022: center(342.756,353.453), retained without special treatment.

Appearance/defect changes can affect threshold-based geometry. No image-specific
correction was added after observing these outliers. All raw records, including
transform coefficients and success/failure statuses, are in geometry.json and
rotation-geometry.json; per-split geometry records are also retained.

## Separate canonicalizer CPU timing

Three fresh Python processes, the first8 frozen FIT images preloaded as RGB,
two seconds full-pipeline warmup/process, nine interleaved rounds/stage with16
images/batch. Stage order rotates by round/process. Source resolution700x700.
Geometry includes luminance/downsample/threshold/component/moments. Warp includes
matrix inversion, padding, conversion, bilinear resampling and RGB materialization.
Total includes geometry, forward-matrix construction and warp and is **measured
directly**, not summed. Disk I/O, decode, CNN/M2 and heatmap backprojection are excluded.

Measured composition, pooled median of batch averages:

- **Geometry/orientation estimation: 2.113 ms/image.** Includes PIL luminance
  conversion, BOX reduction to175x175, Otsu threshold, largest 8-connected
  component, hole filling, centroid and the harmonic4 orientation estimate.
- **Image warp/crop: 9.995 ms/image.** Includes matrix inversion, edge-replicated
  padding, affine-coordinate conversion, Pillow bilinear RGB resampling and uint8
  materialization on a fixed700x700 canvas. Applied scale is always1; there is no
  adaptive crop.
- **Resize/preprocessing: no standalone stage was measured.** The only resize in
  the canonicalizer is the geometry-only175x175 BOX reduction, already included in
  geometry. The downstream Java whole-image224 bicubic/ImageNet preprocessing,
  encoder and M2 scoring are outside this timing.
- **Python/runtime orchestration: not a separately measured stage.** Python
  startup, imports, file I/O, image decode and external orchestration are excluded;
  in-process function-call and NumPy/Pillow overhead are included inside the
  measured stages.
- **Total canonicalization: 12.308 ms/image, measured directly.** It includes
  geometry, forward-matrix construction and warp. The pooled median total minus
  the sum of the two component medians is0.200 ms; this is consistent with the
  uninstrumented forward-matrix construction and is **not** a separately measured
  stage.

The harness is a **Python3.13.5 + NumPy/SciPy/Pillow prototype**, not a
Java production-like implementation. No Java canonicalizer timing exists in this
pilot, so no future Java speed is inferred or projected.

Hardware: Ryzen AI9 HX370 host, Windows; detailed environment/hardware snapshot
retained. No affinity/power controls or JMH. Values are medians of batch-average
milliseconds/image, not per-call latency percentiles.

| Process | Geometry | Warp | Total |
|---|---:|---:|---:|
| 0 | 2.152 | 9.930 | 12.180 |
| 1 | 2.096 | 9.971 | 12.356 |
| 2 | 2.113 | 10.072 | 12.361 |
| Pooled27 batches/stage | **2.113** | **9.995** | **12.308** |
| Pooled mean | 2.129 | 10.023 | 12.367 |

Total batch-average range11.882-13.644 ms. This Python/SciPy/Pillow prototype
fails the frozen<=2ms budget. The [previous Java M2 report](JAVA_M2_RESULT_20261018.md)
measured complete detection p50 around2.761ms on its Bottle fixture; that is
context, not a matched Metal Nut end-to-end result. No pipeline latency is inferred
by adding these separately measured times. No optimization was attempted after
the pilot result.

## Separate scientific and engineering verdicts

### Scientific verdict: positive within the pilot

Canonicalization improves the three measured quality metrics, narrows the FIT
local feature distributions, increases positional direction consistency and
substantially reduces paired score changes under controlled rotation. The
scientific hypothesis examined here is therefore supported: fitting and scoring
after alignment can make positional normal distributions more coherent and more
stable under rotation. This is one category and one retained test split, with no
significance test and no separation of centering, orientation and resampling
effects. It is positive pilot evidence for coherence, quality and robustness, not
independent generalization evidence and not a production claim.

### Current engineering verdict: NO-GO

The only measured implementation is the Python/SciPy/Pillow prototype, whose
total canonicalization median is12.308 ms/image. That fails the frozen2 ms
budget; the observed batch-average range is11.882-13.644 ms. The scientific gain
does not override the predeclared cost gate. No Java production-like
canonicalizer was implemented or timed in this pilot, so this verdict applies to
the measured prototype and does not assert that every conceivable implementation
must exceed2 ms.

## Frozen decision gates

| Gate, fixed before test quality | Observed | Result |
|---|---|---|
| No quality loss greater than.01 in any primary metric | All three improve | PASS |
| At least10% lower median FIT local std | 26.91% lower | PASS |
| At least50% lower average absolute median rotation delta | 93.69% lower | PASS |
| Normal orientation p95<=1 degree,max<=2 | 0.0944 /0.1029; zero source failures | PASS, with post-warp anomaly limitation documented |
| Total canonicalization median<=2ms | 12.308ms | **FAIL** |

All conditions were required. **Procedural verdict: NO-GO**, driven solely by the
timing gate; the scientific quality/coherence/robustness gates passed. The sole
recommendation is the project-closing package at the top, not another model
variant or a new canonicalization optimization phase.

## Tests, duration and reproduction

The retained logs record **138 Java tests passed and37 Python tests passed**, with
two upstream Typer/Click deprecation warnings. An earlier interim count of35
Python tests is superseded by the retained37-passed log. Added checks cover deterministic four-fold estimation,
identity/input preservation, explicit geometry failure, exact pixel-marker
coordinates, split-record persistence, and identity/rotated/subpixel heatmap
backprojection. The full existing M2 tests remain intact. Python rule checks,
new-file whitespace, document links and git diff --check passed.

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools -q --basetemp target/test-tmp/canonical-repaired
```

Successful campaign wall time: **116.585s** through quality/diagnostics;
**136.159s (2m16.159s)** including canonicalizer timing. Preparation/encoding/I/O
are included; these are operational durations, not detector latency.

An initial preparation attempt failed because an exclusive-write manifest helper
was called repeatedly for geometry.json. It is preserved at
`target/experiments/m2-metal-nut-canonicalization-20261019-preparation-failed`.
A regression reproduced it; per-split manifests plus one final aggregate fixed it.
The pixel-marker test also exposed one-byte floating-point truncation at exact
quarter turns; exact coefficients fixed it without weakening the assertion.
No model or quality result existed in that failed attempt. The successful run
uses the corrected source snapshot; its133 hashes all match. Its timing excludes
the failed preparation attempt and report writing. The shell wait timed out while
execution continued; complete.json and validated artifacts establish completion.

Reproduce with a new output namespace; never clean retained target evidence:

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/canonicalization/run.py --output target/experiments/m2-metal-nut-canonicalization-new
```

The CLI is intentionally Metal-Nut-only. Logs are in target/canonical-campaign.log,
target/canonical-java-tests.log and target/canonical-python-tests.log. Run artifacts
include workspace/source/input hashes, frozen manifests, all canonical PNGs and
poses, baseline/candidate normalized features, B model/state/raw/Z/reload arrays,
original-frame evaluation maps, metrics, variance maps, per-position statistics,
paired rotation scores,81 timing records, summary.json and complete.json.

Production Java/M2, prior artifacts and pre-existing oracle work remain preserved.
No all-category campaign, JMH, production deployment, publication or commit was run.
