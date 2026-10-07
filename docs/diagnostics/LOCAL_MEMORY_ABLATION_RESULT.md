# Local-Memory Ablation Result (post-hoc internal ablation)

**Label: `POST_HOC_INTERNAL_ABLATION`**

Documentation reconciliation recorded on **2026-10-07**; the frozen benchmark run remains `final-comparison-20261003`.

Internal, controlled, post-hoc ablation on the frozen run
`target/comparison/final-comparison-20261003`. This does **not** modify the
production detector, the dataset, the split, or any frozen artifact. No
benchmark was rerun; nothing was tuned after observing test results; nothing was
committed. The runner is *read-only with respect to production and frozen
artifacts*, but it is not literally side-effect-free: it computes derived
outputs and writes them under
`target/comparison/final-comparison-20261003/diagnostics/local-memory-ablation/`.

> **Documentation status.** This revision applies the Astra audit errata. No
> accepted M0/M1/M2/M3 metric was changed, and no frozen artifact was touched.
> Corrections: Bottle test counts are 20 good / 63 anomalous; PRO uses equal
> weight per connected region (not area-weighted); M1 norms distinguish the norm
> of the **sum** from the norm of the **mean**; the projection is described as a
> rectangular full-column-rank Rademacher embedding (not a "rotation"); the
> claim that the projection "loses information" was removed; the M0 **explicit**
> validation runtime is distinguished from the compiled production runtime; and
> learning state is distinguished from minimal inference state.

Scientific classification used throughout:

- **VERIFIED** — reconstruction agrees with frozen artifacts at floating-point
  tolerance.
- **DERIVED** — algebraic identity / deterministic consequence of the frozen
  formulas.
- **OBSERVED IN THIS ABLATION** — measured on the frozen test set.
- **INTERPRETATION / HYPOTHESIS** — a reading, not a causal proof.

Bottle and Metal Nut are **already-observed** test sets. This is not independent
evidence of generalization.

---

## 1. Frozen experimental contract (recorded before evaluation)

### Common descriptor

```
u = x / max(||x||_2, 1e-6)
```

Same frozen MobileNetV4 `SPATIAL_14` encoder and preprocessing
(`imagenet-rgb-resize224-bicubic-v1`) as M0. Patch grid 14x14x96; each image
yields 196 patches of 96 channels.

### Model definitions

| id | name | RAW score |
| --- | --- | --- |
| M0 | current model | `raw0[p] = 1 - A_p . z_p`, `z_p = (y_p - mu_global)/sigma_global`, `y = R u / sqrt(D)` |
| M1 | local CNN centroid | `raw1[p] = 1 - dot(c[p], u[p])`, `c[p] = m[p]/||m[p]||`, `m[p] = mean_k u[p,k]` |
| M2 | local CNN dispersion | `raw2[p] = mean_c ((u_c - mu2[p,c]) / s2[p,c])^2`, `s2 = max(std2, 1e-6)` |
| M3 | local VSA dispersion | `raw3[p] = mean_d ((y_d - mu3[p,d]) / s3[p,d])^2`, `s3 = max(std3, 1e-8)` |

M0 statistics (`mu_global`, `sigma_global`, archetypes `A_p`) are the frozen
production ones; M2/M3 use per-position sample mean / sample standard deviation
(ddof=1) from fit normals only. `R` is the fixed rectangular full-column-rank
Rademacher embedding, `D = 10000`.

### Floors and constants (frozen)

| constant | value |
| --- | --- |
| CNN normalization epsilon | `1e-6` |
| `std_floor_CNN` (M2) | `1e-6` |
| `std_floor_VSA` (M3, M0 global sigma) | `1e-8` |
| `calibration_std_floor` (all models) | `1e-6` |
| VSA dim `D` | `10000` |
| projection seed | `42` |

### Splits (frozen manifests)

| category | fit normal | calibration normal | test | test good | test anomalous |
| --- | ---: | ---: | ---: | ---: | ---: |
| Bottle | 167 | 42 | 83 | 20 | 63 |
| Metal Nut | 176 | 44 | 115 | 22 | 93 |

Fit = detector statistics/memory only. Calibration = held-out scalar score
calibration only. Test = final evaluation only.

### Calibration and evaluation (frozen conventions)

Per model independently, from its own calibration-normal RAW maps:

```
a_m[p] = mean calibration raw_m[p]
t_m[p] = sample std calibration raw_m[p]
Z_m[p] = (raw_m[p] - a_m[p]) / max(t_m[p], 1e-6)
```

"Same calibration" = same algorithm, same held-out images, same floor — **not**
shared `a/t` between models.

Evaluation, identical to the frozen benchmark conventions: image score
`max_p score[p]`; localization on the native 14x14 map with the current
full-resolution upsampling (half-pixel, border-replicate bilinear) and the same
masks/tie handling; metrics Image AUROC, Pixel AUROC, AUPRO@0.30.

PRO (per-region overlap) uses **equal weight per connected region**: the region
weight is `1 / (area(region) * num_regions)`, so each of the `num_regions`
8-connected regions contributes total weight `1 / num_regions` to the overlap
integral; it is **not** area-weighted. No top-k, percentile, regional pooling, or
new calibration.

---

## 2. M0 reconstruction fidelity — VERIFIED

M0 is reconstructed through the production components (production ONNX encoder,
Java-LCG Rademacher projection, production global statistics and archetypes).

Against the frozen `learning-state.bin`:

| quantity | Bottle max abs diff | Metal Nut max abs diff |
| --- | ---: | ---: |
| global mean `mu_global` | 1.84e-16 | 1.46e-16 |
| global floored `sigma_global` | 1.13e-16 | 1.09e-16 |
| every archetype `A_p` | 4.23e-16 | 4.41e-16 |

Against the frozen per-image maps for all test images:

| quantity | Bottle max abs | Metal Nut max abs |
| --- | ---: | ---: |
| recomputed `raw0` vs frozen `.raw.npy` | 1.21e-12 | 1.21e-12 |
| recomputed `z` vs frozen `.native.npy` | 1.56e-11 | 6.25e-13 |

Gold-metric agreement: the recomputed M0 metrics reproduce the frozen values
exactly to the printed precision (Bottle Z: image 1.0, pixel 0.9616640201342855,
AUPRO 0.8774472899967225; Metal Nut Z: image 0.7575757575757576, pixel
0.6926300242781803, AUPRO 0.3797941159804979). No discrepancy.

---

## 3. Results — Image / Pixel AUROC / AUPRO@0.30

### Bottle (n_good = 20, n_anomaly = 63)

| model | form | Image AUROC | Pixel AUROC | AUPRO@0.30 |
| --- | --- | ---: | ---: | ---: |
| M0 | RAW | 0.985714 | 0.956927 | 0.865098 |
| M0 | Z | 1.000000 | 0.961664 | 0.877447 |
| M1 | RAW | 0.991270 | 0.969249 | 0.899351 |
| M1 | Z | 0.999206 | 0.963259 | 0.890405 |
| M2 | RAW | 1.000000 | 0.975425 | 0.910852 |
| M2 | Z | 1.000000 | 0.975697 | 0.915738 |
| M3 | RAW | 0.998413 | 0.962282 | 0.886158 |
| M3 | Z | 0.999206 | 0.964170 | 0.891606 |

### Metal Nut (n_good = 22, n_anomaly = 93)

| model | form | Image AUROC | Pixel AUROC | AUPRO@0.30 |
| --- | --- | ---: | ---: | ---: |
| M0 | RAW | 0.704790 | 0.625562 | 0.345386 |
| M0 | Z | 0.757576 | 0.692630 | 0.379794 |
| M1 | RAW | 0.711632 | 0.625267 | 0.348977 |
| M1 | Z | 0.800098 | 0.656094 | 0.372387 |
| M2 | RAW | 0.851417 | 0.838738 | 0.594611 |
| M2 | Z | 0.845552 | 0.849318 | 0.624208 |
| M3 | RAW | 0.849462 | 0.739887 | 0.459409 |
| M3 | Z | 0.849951 | 0.744457 | 0.504555 |

(OBSERVED IN THIS ABLATION.) The resulting ordering is specific to these two
already-observed test sets and this frozen feature configuration; it is **not** a
universal ranking of the M1/M2/M3 model families.

---

## 4. Floor and degeneracy diagnostics — DERIVED

Recorded BEFORE applying the floors (fit normals only).

| diagnostic | Bottle | Metal Nut |
| --- | ---: | ---: |
| M1 norm of the **sum** `||sum_k u[p,k]||` (min / median / max) | 156.389 / 165.293 / 166.908 | 65.215 / 121.818 / 174.841 |
| M1 norm of the **mean** `||m[p]|| = ||sum||/N` (min / p1 / p5 / median / max) | 0.936462 / 0.944844 / 0.968719 / 0.989779 / 0.999449 | 0.370541 / 0.378076 / 0.469644 / 0.692148 / 0.993416 |
| M1 degenerate mean centroids (`||m[p]|| < 1e-12`) | 0 | 0 |
| M2 std min | 4.60e-5 | 6.24e-4 |
| M2 std p1 / p5 / median | 4.19e-4 / 8.54e-4 / 3.96e-3 | 1.60e-3 / 2.61e-3 / 1.10e-2 |
| M2 std `< 1e-6` (count / fraction) | 0 / 0 | 0 / 0 |
| M2 std exactly zero | 0 | 0 |
| M3 std min | 7.83e-5 | 5.64e-4 |
| M3 std p1 / p5 / median | 3.06e-4 / 5.82e-4 / 1.40e-3 | 1.16e-3 / 1.83e-3 / 5.75e-3 |
| M3 std `< 1e-8` (count / fraction) | 0 / 0 | 0 / 0 |
| M3 std exactly zero | 0 | 0 |
| M0 global sigma `< 1e-8` / zero | 0 / 0 | 0 / 0 |
| calibration positions floored (per model) | 0 | 0 |

The M1 direction is `c[p] = m[p]/||m[p]||`; the unit direction is invariant to
the `1/N` between the sum and the mean, so only the reported **magnitude**
depends on that distinction. The mean norms show how concentrated each
positional patch cloud is: Bottle means are close to unit (median 0.990), while
Metal Nut spans 0.371-0.993 (median 0.692), i.e. more per-position angular
spread.

No dimension is floored in either space or category. The 10,000 VSA coordinates
are **not** interpreted as independent degrees of freedom (the projection is a
fixed rectangular full-column-rank Rademacher embedding, not an i.i.d. basis).

---

## 5. Latency and state size

### Latency (single-run measurement on this machine, indicative only)

Detector scoring latency per test image, excluding the shared encoder
(seconds, as measured in this harness):

| path | Bottle | Metal Nut | status |
| --- | ---: | ---: | --- |
| M1 CNN centroid, explicit vectorized scoring | 5.9e-6 | 5.3e-6 | measured |
| M2 CNN dispersion, explicit vectorized scoring | 6.2e-5 | 6.4e-5 | measured |
| M0 **explicit projected** scoring (reconstruction only) | 2.63e-2 | 2.55e-2 | measured |
| M3 explicit projected scoring | 2.63e-2 | 2.55e-2 | measured |

Important label distinction: the two values marked "explicit projected" are the
cost of the **explicit** pipeline `y = R u / sqrt(D)` followed by M0's
`A_p . z_p` (or M3's residual) that this harness uses to **reconstruct and
validate** M0. Production M0 does **not** run this path at inference: it scores
through the compiled adjoint filter bank (`AdjointCompiler` / `AdjointFilterBank`
/ `PatchScoreKernel`, `score = dot(W, x)/max(||x||, eps) + b`), which is a
per-patch dot product in CNN space and is not timed by this harness. The
"explicit projected" numbers must not be read as the production M0 runtime.
Moreover, the M0 and M3 "explicit projected" values come from a single harness
call that computes both explicit models over a **shared** projection; they are
indicative of this shared loop, **not** independent per-model latency
measurements. All latency values are measured (not derived) and are indicative
of one run.

### State size (double precision, deterministic estimates — derived)

| state | bytes | note |
| --- | ---: | --- |
| M0 **learning** state (archetypes + global mean/std) | 15,840,000 | 196 x 10000 + 2 x 10000 |
| M0 **minimal inference** state (compiled `W` + `b`) | 152,096 | 196 x 96 + 196 |
| M1 minimal inference state (centroid) | 150,528 | 196 x 96 |
| M2 minimal inference state (mean + std) | 301,056 | 2 x 196 x 96 |
| M3 learning state (mean + std) | 31,360,000 | 2 x 196 x 10000 |
| per-model calibration `a/t` | 3,136 | 196 x 2 |

The M0 **learning** state and the M0 **minimal inference** state are distinct:
production inference needs only the compiled weights `W` and biases `b`
(152,096 bytes), whereas the 15,840,000-byte learning state stores the
archetypes and global statistics.

---

## 6. Optional compiled-M3 validation — VERIFIED (implementation only)

M3 was compiled into the exact CNN-space quadratic form
`raw3[p] = u^T Q[p] u + w[p]^T u + b[p]` (with the `1/D` implied by the
mean-of-squared-residual convention) and compared against the explicit
projected M3:

| quantity | Bottle | Metal Nut |
| --- | ---: | ---: |
| max abs error | 1.17e-11 | 7.64e-13 |
| median abs error | 1.00e-13 | 4.66e-15 |
| relative to explicit max | 6.8e-15 | 4.0e-14 |

Agreement is at floating-point tolerance. This is a validation of the algebra,
not a separate detector.

---

## 7. Derived equivalence between M1-Z and isotropic/M2-Z

**DERIVED.** Fix one position `p` and drop the index. Let `mu` be the
per-position mean, `rho = ||mu||`, and `c = mu / rho` the M1 centroid direction.
For a unit-norm descriptor `||u|| = 1`:

```
r1(u)   = 1 - c^T u
rIso(u) = ||u - mu||^2
```

Expanding and using `||u|| = 1`:

```
rIso(u) = ||u||^2 - 2 mu^T u + ||mu||^2
        = 1 - 2 rho c^T u + rho^2
        = (1 - rho)^2 + 2 rho (1 - c^T u)
        = (1 - rho)^2 + 2 rho r1(u)
```

So `rIso` is a position-constant positive-affine transform of `r1`
(`2 rho > 0` when `rho > 0`). With the positional Z calibration
`Z(r) = (r - a)/max(t, floor)`, an affine transform of the score leaves the
per-position standardized values unchanged as long as the calibration floor is
inactive:

```
Z(rIso) = Z( (1-rho)^2 + 2 rho r1 ) = Z(r1)
```

Consequences (within the assumptions below):

- After positional Z calibration, the **isotropic Euclidean distance from the
  non-unit mean adds no information beyond M1**. Hence comparing M1-Z with M2-Z
  isolates the effect of **anisotropic per-coordinate weighting** (per-channel
  `sigma`), not the effect of adding a radial term.
- RAW scores are **not** equivalent across positions, because the affine
  constant `(1 - rho)^2` depends on the position through `rho`; raw `rIso` and
  raw `r1` therefore need not induce the same cross-position ranking.

Equal-variance special case: if at a fixed position all channel standard
deviations are equal (`sigma_1 = ... = sigma_C = s`), then

```
M2(u) = mean_c ((u_c - mu_c)/s)^2
      = (1 / (C s^2)) * ||u - mu||^2
      = (1 / (C s^2)) * rIso(u)
```

so `Z(M2) = Z(M1)` under the same assumptions.

Current caveats: the normalization epsilon is inactive (no descriptor hits
`1e-6`); `rho` is non-zero for every position; the calibration floor is inactive
(no calibration position floored); the M2 variance floor is inactive (no M2
dimension floored); and the diagonal covariance assumption of M2 remains.

---

## 8. Required comparisons

### 8.1 M0 vs M1 — does the VSA positional direction beat a direct CNN centroid?

**No uniform advantage across metrics on the measured test sets.** On Bottle, M1 RAW is slightly better than
M0 RAW (AUPRO 0.8994 vs 0.8651), and after Z the two are comparable (AUPRO
0.8904 vs 0.8774). On Metal Nut they are close in RAW (AUPRO 0.3490 vs 0.3454)
and split after Z (M0 AUPRO 0.3798 vs M1 0.3724; M1 Image AUROC 0.8001 vs M0
0.7576). **OBSERVED:** on these two already-observed test sets the current
VSA/global-statistics positional direction has no uniform advantage over a direct
CNN local centroid. M0 wins some metrics, including Metal Nut Z localization;
results are comparable and metric-dependent.

### 8.2 M1 vs M2 — does local per-channel dispersion add anomaly information?

**Yes, on the measured test sets.** Adding local CNN dispersion (M2) improves
every metric over the centroid (M1) on both categories; the difference is large
on Metal Nut (RAW pixel 0.6253 -> 0.8387, AUPRO 0.3490 -> 0.5946; Z AUPRO 0.3724
-> 0.6242) and smaller on Bottle (RAW AUPRO 0.8994 -> 0.9109). **OBSERVED:** in
this experiment, modelling per-channel local dispersion separates the frozen
test images more strongly than a centroid direction alone.

### 8.3 M2 vs M3 — does diagonal dispersion after the VSA projection help?

**No localization improvement on the measured test sets.** Direct CNN-space dispersion
(M2) exceeds post-projection VSA dispersion (M3) on both categories (Metal Nut
Z: pixel 0.8493 vs 0.7445, AUPRO 0.6242 vs 0.5046; Bottle Z: AUPRO 0.9157 vs
0.8916). M3's ordering relative to M1 and M2 depends on the metric and RAW/Z form: Bottle RAW localization is below M1, while Metal Nut Z Image AUROC is above M2. By the identity in section 7, the comparison
M1-Z vs M2-Z already isolates CNN per-channel weighting; the additional step of
measuring the same diagonal statistic after a fixed rectangular Rademacher
embedding does not add measured value here. **OBSERVED:** on these test sets the
fixed embedding does not improve localization with this diagonal residual
statistic relative to direct CNN-space dispersion. (This is a statement about the measured comparison,
not a general claim that the embedding is information-losing.)

### 8.4 RAW vs Z — geometry vs calibration recovery

Held-out positional Z is algorithm-identical across models (same calibration
images, same `1e-6` floor). It recovers a meaningful portion of the weaker RAW
geometries (Metal Nut M0: pixel 0.6256 -> 0.6926, AUPRO 0.3454 -> 0.3798; Metal
Nut M3: AUPRO 0.4594 -> 0.5046) and is roughly neutral or a small change on
already-strong models (Bottle M2: AUPRO 0.9109 -> 0.9157; Metal Nut M2: AUPRO
0.5946 -> 0.6242, Image 0.8514 -> 0.8456). **OBSERVED:** on these test sets the
scalar calibration changes positional score offsets and scales, improving some
metrics; M1 and M3 still remain below M2 on localization in both categories.

### 8.5 Bottle vs Metal Nut — is M0 strongest where between-position structure dominates?

**Consistent with that reading, limited to the measured comparison.** In the
companion positional variance diagnostic, Bottle fit-normal statistics are
dominated by between-position structure (`H_vsa` median 0.968; `H_cnn`
normalized median 0.930, all offset norms tight and large) while Metal Nut is
balanced (`H_vsa` median 0.491, `H_cnn` normalized median 0.506, several comparatively small
observed positional offsets). Here M0 is comparatively strongest on Bottle
and comparatively weakest on Metal Nut. **INTERPRETATION / HYPOTHESIS:** M0's localization gap to M2 is smaller on Bottle, where fit-normal statistics
are dominated by between-position structure. This is a post-hoc association on
already-observed test sets, not a causal claim.

---

## 9. Scientific statements (classification)

- **VERIFIED:** reconstructed M0 matches frozen mean/sigma/archetypes and frozen
  test maps/metrics at floating-point tolerance; compiled M3 matches explicit M3
  at floating-point tolerance.
- **DERIVED:** the M1-Z / isotropic-distance equivalence and the equal-variance
  M2 special case (section 7); no M2/M3/M0 dimension is floored and no mean
  centroid is degenerate on either category; state sizes in section 5.
- **OBSERVED IN THIS ABLATION:** on the two measured test sets, M2 exceeds
  {M0, M1, M3} on localization; M1 and M0 have metric-dependent results; M3's ordering relative to M1/M2
  depends on metric and RAW/Z form. Z changes positional score scales and offsets;
  M2 remains strongest on localization.
- **INTERPRETATION / HYPOTHESIS:** for a diagonal per-channel dispersion
  statistic, the fixed rectangular Rademacher embedding did not add measured
  value in this experiment, and M0's relative strength tracks between-position
  dominance. These are readings limited to the measured comparisons.

This ablation does **not** establish that "M2 solves Metal Nut", that "VSA is
unnecessary", that "VSA is superior", or that "global standardization caused the
benchmark failure". No such claim is made.

---

## 10. Reproducibility

These tools require the external DatasetNinja dataset at the scripts' fixed relative
path `../Anomalib4j_md/mvtec-ad-DatasetNinja` and the preserved local frozen run.
The ablation reads fit/calibration/test manifests, `learning-state.bin`,
`predictions.csv` and RAW/native maps; these are not included in a fresh clone.
Run [bootstrap.ps1](../../tools/comparison/bootstrap.ps1) to compile the Java
harness and generate `target/comparison-classpath.txt`; `test-compile` alone does
not create that file or restore the frozen artifacts. Python needs NumPy and,
through the imported evaluator module, SciPy, Pillow, scikit-image and Matplotlib.
The interpreter path below belongs to the prepared local environment, not tracked
source. See the [reproducibility map](../TECHNICAL_OVERVIEW.md#12-riproducibilità-artefatti-e-limiti).
Generated outputs stay ignored under `target/`; no generated target file belongs
in the publication commit.


- Runner: `tools/prerun/local_memory_ablation.py` (read-only on production and
  frozen artifacts; it writes only derived outputs under `target/...`; reuses the
  verified LCG Rademacher signs and learning-state reader from
  `positional_global_variance_diagnostic`, and the validated evaluator port from
  `metal_nut_pose_diagnostic`).
- Feature harness: `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/PositionalVarianceFeatureDump.java`
  (production `OnnxMobileNetV4Encoder`, `SPATIAL_14`).
- Encoder: `src/main/resources/models/mobilenetv4_spatial_14x14.onnx`
  SHA256 `48DA88A8DA5F1F1F1CE2308D19128C0F302498AA1889882263620CAD21BDD26E`.
- Manifests: `target/comparison/final-comparison-20261003/data/<category>/`
  (`anomalib4j-fit.txt` 167/176, `anomalib4j-calibration.txt` 42/44, `test.txt` 83/115).
- Projection seed 42, `D = 10000`; normalization epsilon `1e-6`; floors: CNN
  `1e-6`, VSA `1e-8`, calibration `1e-6`.

```
powershell -NoProfile -File tools/comparison/bootstrap.ps1
target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe \
    tools/prerun/local_memory_ablation.py
```

Machine-readable outputs under
`target/comparison/final-comparison-20261003/diagnostics/local-memory-ablation/`:
`summary.json`, `bottle_summary.json`, `metal_nut_summary.json`,
`bottle_metrics.csv`, `metal_nut_metrics.csv`, and
`features/<category>/<role>/{features.npy,images.txt,meta.json}`.
