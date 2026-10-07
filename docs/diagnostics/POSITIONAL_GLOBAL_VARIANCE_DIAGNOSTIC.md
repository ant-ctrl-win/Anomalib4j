# Positional vs Global Variance Diagnostic (post-hoc, read-only)

**Label: `POST_HOC_STATISTICAL_DIAGNOSTIC`**

Documentation reconciliation recorded on **2026-10-07**; the frozen benchmark run remains `final-comparison-20261003`.

This document describes a post-hoc analysis, read-only with respect to production
and frozen inputs, on the frozen run
`target/comparison/final-comparison-20261003`. It does **not** modify the
detector, scoring, normalization, projection, bundling, model identity, the
dataset or the splits. It re-runs **no** benchmark and produces **no** new
metrics. New artifacts are written only under
`target/comparison/final-comparison-20261003/diagnostics/positional-global-variance/`.

> **Correction (Astra audit).** The earlier "CNN" branch used patch descriptors
> **before** L2 normalization, whereas the VSA branch used the L2-normalized
> descriptors. That made the CNN-vs-VSA comparison non-comparable. The CNN branch
> now uses the **same L2-normalized descriptors that feed the projection**
> (`u = x / max(||x||, 1e-6)`); the original pre-L2 computations are retained
> under an explicit `preL2_legacy` label for contrast. The VSA calculations are
> unchanged. The correction does not claim that anything after the projection is
> "new information"; it only makes the two spaces comparable.

## 1. Question and terminology

The M0 positional archetype for cell `p` is the direction of the positional
offset in VSA space:

```
offset[p, d] = (mu_pos[p, d] - mu_global[d]) / sigma_global[d]
A_p          = offset[p] / ||offset[p]||
```

`H[d]` and `||offset[p]||` are reported here as **observed quantities**. They
describe how the fit-normal sample sum-of-squares is distributed between and
within positions; they are not, by themselves, a proof that an archetype is
statistically supported, nor a predictor of detector performance.

Following the review, this diagnostic **never** calls `sigma_pos / sigma_global`
a variance ratio. Throughout:

```
std_ratio[p, d]      = sigma_pos[p, d] / sigma_global[d]
variance_ratio[p, d] = std_ratio[p, d]^2
```

## 2. Exact within/between decomposition

For every dimension `d` (CNN channel or VSA dimension), with `N` fit images,
`P = 14*14 = 196` cells and `n = N*P` samples:

```
(n - 1) * s_global[d]^2
    = (N - 1) * sum_p s_pos[p, d]^2
    + N       * sum_p (mu_pos[p, d] - mu_global[d])^2
      \___________________/   \___________________________/
             within                      between

H[d] = N * sum_p (mu_pos[p, d] - mu_global[d])^2
       / ((n - 1) * s_global[d]^2)
```

`H[d]` is the fraction of the total sample sum-of-squares attributable to
differences between positional means. `s_pos` and `s_global` here are the
**observed** (pre-floor, sample) standard deviations.

The identity is verified numerically. Relative residual
`|lhs - within - between| / max_d lhs`:

| case | VSA space | CNN space (normalized) | CNN space (pre-L2 legacy) |
| --- | ---: | ---: | ---: |
| Bottle (N=167, n=32 732) | 1.4e-15 | 4.1e-15 | 4.0e-14 |
| Metal Nut (N=176, n=34 496) | 1.3e-15 | 5.8e-15 | 5.1e-14 |

(VSA and normalized-CNN residuals are at double-precision round-off; the pre-L2
residual is larger in absolute terms because the raw CNN magnitudes are ~1e8
before normalization, i.e. ~1e-14 relative.)

## 3. `H[d]` distributions

`n` is the number of dimensions; fractions are over all dimensions. The
**normalized** CNN branch is the comparable one for CNN-vs-VSA statements.

### Bottle (N = 167)

| space | n | median | p10 | p25 | p75 | p90 | min | max | H>0.25 | H>0.50 | H>0.75 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| VSA | 10000 | 0.9684 | 0.9390 | 0.9530 | 0.9765 | 0.9809 | 0.8830 | 0.9896 | 100% | 100% | 100% |
| CNN (normalized) | 96 | 0.9299 | 0.8162 | 0.8842 | 0.9610 | 0.9779 | 0.6366 | 0.9944 | 100% | 100% | 97.92% |
| CNN (pre-L2 legacy) | 96 | 0.9092 | 0.7892 | 0.8517 | 0.9495 | 0.9652 | 0.5655 | 0.9913 | 100% | 100% | 95.83% |

### Metal Nut (N = 176)

| space | n | median | p10 | p25 | p75 | p90 | min | max | H>0.25 | H>0.50 | H>0.75 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| VSA | 10000 | 0.4912 | 0.4007 | 0.4460 | 0.5314 | 0.5651 | 0.2168 | 0.6561 | 99.95% | 44.27% | 0% |
| CNN (normalized) | 96 | 0.5056 | 0.3242 | 0.4120 | 0.5992 | 0.6315 | 0.1736 | 0.8038 | 97.92% | 51.04% | 1.04% |
| CNN (pre-L2 legacy) | 96 | 0.4797 | 0.3313 | 0.3838 | 0.5669 | 0.6448 | 0.1908 | 0.7738 | 98.96% | 43.75% | 1.04% |

Reading (comparable inputs): in Bottle the between-position structure accounts
for the large majority of the total sum-of-squares in **both** spaces (VSA
median `H = 0.97`, normalized-CNN median `H = 0.93`; nearly every dimension
above 0.75). In Metal Nut it is close to balanced in both spaces (VSA median
`H = 0.49`, normalized-CNN median `H = 0.51`; only ~44-51% of dimensions exceed
0.50 and almost none exceed 0.75). With the correction, the CNN and VSA `H`
distributions are now mutually consistent.

## 4. `std_ratio[p, d]`

Observed (unfloored) standard deviations only; no ratio is computed from a
floor-substituted denominator.

### VSA space

| case | median | p10 | p25 | p75 | p90 | min | max | >0.25 | >0.50 | >0.75 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Bottle | 0.1543 | 0.0749 | 0.1054 | 0.2190 | 0.2878 | 0.0072 | 1.1019 | 16.88% | 0.39% | 0.011% |
| Metal Nut | 0.6313 | 0.2614 | 0.4042 | 0.8604 | 1.0657 | 0.0541 | 2.1523 | 91.05% | 64.92% | 36.35% |

### CNN space (normalized descriptors)

| case | median | p10 | p25 | p75 | p90 | min | max | >0.25 | >0.50 | >0.75 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Bottle | 0.2053 | 0.0826 | 0.1264 | 0.3210 | 0.4648 | 0.0042 | 1.4000 | 38.87% | 7.89% | 1.50% |
| Metal Nut | 0.6375 | 0.2973 | 0.4507 | 0.8364 | 1.0302 | 0.0549 | 2.0386 | 93.16% | 68.76% | 35.35% |

Corresponding `variance_ratio = std_ratio^2` medians: VSA **Bottle 0.0238**,
**Metal Nut 0.3986**; normalized CNN **Bottle 0.0422**, **Metal Nut 0.4064**.

In Bottle the per-position spread is a small fraction of the global spread
(VSA median 0.15, CNN median 0.21), consistent with `H` near 1. In Metal Nut the
per-position spread is comparable to the global spread (VSA median 0.63, CNN
median 0.64), consistent with `H` near 0.5.

## 5. `||offset[p]||` per position (VSA space)

Threshold used to flag unusually small observed offset magnitudes:
`max(0.05, p10(||offset||))`; 20 of 196 cells fall below it in each case.

| case | median | p10 | min | max | min / median | flagged |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Bottle | 97.02 | 92.05 | 87.45 | 112.70 | 0.90 | 20 |
| Metal Nut | 69.03 | 43.41 | 32.61 | 108.44 | 0.47 | 20 |

Weakest positions (smallest `||offset[p]||`):

| case | position (row, col) | `||offset[p]||` | ratio to median |
| --- | --- | ---: | ---: |
| Bottle | 146 (10, 6) | 87.45 | 0.90 |
| Bottle | 147 (10, 7) | 87.89 | 0.91 |
| Bottle | 104 (7, 6) | 88.44 | 0.91 |
| Metal Nut | 45 (3, 3) | 32.61 | 0.47 |
| Metal Nut | 32 (2, 4) | 34.39 | 0.50 |
| Metal Nut | 58 (4, 2) | 35.09 | 0.51 |
| Metal Nut | 128 (9, 2) | 35.48 | 0.51 |
| Metal Nut | 67 (4, 11) | 36.31 | 0.53 |

In Bottle the observed offset magnitudes are tightly clustered (all within 13%
of the median). In Metal Nut the observed spread is much larger;
`||offset[45]|| = 32.6` is under half the median, and 2 cells are below
`0.5 x median` and 16 cells below `0.6 x median`. These are observed magnitudes
only: they are not interpreted here as proof of statistical support or as a
predictor of score behaviour.

## 6. Numerical diagnostics

| diagnostic | Bottle | Metal Nut |
| --- | ---: | ---: |
| CNN descriptors with `norm < 1e-6` | 0 (0%) | 0 (0%) |
| CNN dimensions with zero observed std (pre-floor, both branches) | 0 / 96 | 0 / 96 |
| VSA dimensions with zero observed std (pre-floor) | 0 / 10000 | 0 / 10000 |
| VSA dimensions affected by the `1e-8` std floor | 0 (0%) | 0 (0%) |

No descriptor hits the normalization epsilon, no dimension has a zero observed
standard deviation before flooring, and no dimension is affected by the
standard-deviation floor. The weak Metal Nut structure in sections 3-5 is
therefore genuine statistical structure, not a floored/degenerate artifact.

## 7. Fidelity check against the frozen model

The CNN descriptors are re-encoded with the **production** Java encoder
(`OnnxMobileNetV4Encoder`, SPATIAL_14) through the read-only harness
`PositionalVarianceFeatureDump`, then the Java LCG Rademacher projection and all
statistics are recomputed in Python. The reconstruction is validated
end-to-end against the frozen `learning-state.bin`:

| quantity | Bottle max abs diff | Metal Nut max abs diff |
| --- | ---: | ---: |
| global mean `mu_global` | 1.8e-16 | 1.5e-16 |
| global floored `sigma_global` | 1.1e-16 | 1.1e-16 |
| every archetype `A_p` | 3.3e-16 | 2.7e-16 |

All at machine precision, so the re-encode is numerically identical to the
frozen run (mean/std included). Both fit splits use the independent sampling
unit = one fit-normal image: Bottle 167 images, Metal Nut 176 images.

## 8. Observed structure relative to M0

These are **observed** fit-normal statistics; they do not by themselves
establish a causal link to detector performance.

- **Bottle**: positional means dominate in both spaces (VSA `H ~ 0.97`,
  normalized-CNN `H ~ 0.93`) and the observed `||offset[p]||` is large and
  tightly clustered across cells.
- **Metal Nut**: between-position and within-position structure are comparable
  in both spaces (`H ~ 0.49-0.51`), the per-position spread is a large fraction
  of the global spread (`std_ratio` median ~0.63 in both spaces), and a
  non-trivial subset of cells has comparatively small observed offset magnitudes
  (`||offset[p]||` down to 0.47x median). A smaller offset magnitude is a weaker
  geometric premise for a positional archetype direction, but this diagnostic
  does not measure its effect on the score.
- The observed structure is **not** numerical: no near-zero norms, no
  zero-variance dimensions, no floor substitutions. The companion
  [local-memory ablation](LOCAL_MEMORY_ABLATION_RESULT.md) is the document that
  measures detector behaviour on these same features.

## 9. Artifacts and reproduction

The script requires the external dataset at
`../Anomalib4j_md/mvtec-ad-DatasetNinja`, the frozen fit manifests and each
category's `learning-state.bin` under the original local run. These inputs are
not supplied by a fresh clone. [bootstrap.ps1](../../tools/comparison/bootstrap.ps1)
compiles the harness and writes `target/comparison-classpath.txt`; it does not
restore the run. The interpreter path below identifies a prepared local Python
environment. Generated arrays/CSV/JSON stay under ignored `target/`, not in the
source commit. Full prerequisites are in the
[technical overview](../TECHNICAL_OVERVIEW.md#12-riproducibilità-artefatti-e-limiti).


Script: `tools/prerun/positional_global_variance_diagnostic.py`
(reads frozen inputs and writes derived outputs; requires Python 3.11+ and `numpy`). Feature harness:
`src/test/java/io/github/antctrlwin/anomalib4j/evaluation/PositionalVarianceFeatureDump.java`.

```
powershell -NoProfile -File tools/comparison/bootstrap.ps1
target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe \
    tools/prerun/positional_global_variance_diagnostic.py
```

Outputs:

```
target/comparison/final-comparison-20261003/diagnostics/positional-global-variance/
  summary.json
  bottle_summary.json,                 metal_nut_summary.json
  bottle_vsa_h.csv,                    metal_nut_vsa_h.csv
  bottle_cnn_normalized_h.csv,         metal_nut_cnn_normalized_h.csv
  bottle_cnn_preL2_legacy_h.csv,       metal_nut_cnn_preL2_legacy_h.csv
  bottle_vsa_offset.csv,               metal_nut_vsa_offset.csv
  dump/<category>/features.npy, meta.json, images.txt
```
