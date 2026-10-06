# Border-Exclusion Sensitivity Analysis

**Label: `POST_HOC_SENSITIVITY_ONLY`**

This document is a read-only, post-hoc sensitivity analysis on the frozen run
`target/comparison/final-comparison-20261003`. It does **not** replace, redefine
or re-benchmark any official metric. The frozen official values remain those in
the run's `metrics-unified.json` / `RESULT.md`.

Question addressed: **how much do the peripheral cells of the 14x14 grid
(rows 0/13 and columns 0/13) contribute to the Anomalib4j metrics for Bottle and
Metal Nut?**

- Frozen run: `final-comparison-20261003`
- Categories: `bottle` (900x900), `metal_nut` (700x700)
- Grid: `14x14` (`SPATIAL_14`), geometry ids `bottle-900x900-14x14`, `metal_nut-700x700-14x14`
- Analyzer: `tools/prerun/border_exclusion_sensitivity.py`
- Artifacts: `target/comparison/final-comparison-20261003/diagnostics/border-exclusion/`

---

## 1. Definitions

The 14x14 grid has **52 border cells**: rows 0 and 13 (28 cells) plus columns 0
and 13 (28 cells), minus the 4 shared corners. The **inner** region is the
`r=1..12, c=1..12` block (144 cells).

Two pixel-level supports are used.

| Support | Definition | Metal Nut (700) | Bottle (900) |
|---|---|---|---|
| **Nominal inner** | Pixels whose nominal cell index `floor(p*14/size)` is in `1..12` on both axes | `50..649` both axes (600x600) | `65..835` both axes (771x771) |
| **Strict inner** | Pixels whose bilinear interpolation (the same half-pixel mapping as `LocalizationMaps.upsample`) uses **no** source cell in row/col 0 or 13 | `75..624` both axes (550x550) | `96..803` both axes (708x708) |

The nominal support is derived analytically as `lo = ceil(size/14)`,
`hi = ceil(13*size/14)-1` (no hardcoding). The strict support is the set of
pixels for which every contributing source cell (`floor(x)`, `min(floor(x)+1,13)`
with positive weight) lies in `1..11`; it is a separable rectangle, so the exact
resulting range is a square.

**Image score** is `max(cell)` over the selected cells (full = all 196, inner =
144). Both `RAW` (`1 - compiledScore`) and `Z` (frozen native, `(raw-mu)/sigma`)
spaces are evaluated. `Z` is the official scoring space.

Classification scale for `|delta|` (AUROC / AUPRO): **MARGINAL** `< 0.01`,
**MODERATE** `0.01..0.05`, **LARGE** `>= 0.05`.

---

## 2. Reproducibility / validation

The analyzer only reads frozen artifacts and re-derives metrics from the native
and raw maps. Before any counterfactual it verifies:

| Check | Bottle | Metal Nut |
|---|---|---|
| `upsample(Z)` vs frozen `.evaluated.npy` (max abs) | `0.0` | `0.0` |
| `max(Z)` vs `predictions.csv image_score` (max abs) | `0.0` | `0.0` |
| Full Z Pixel AUROC vs frozen (abs delta) | `3.3e-13` | `5.8e-13` |
| Full Z AUPRO@0.30 vs frozen (abs delta) | `1.3e-12` | `1.8e-13` |
| Full Z Image AUROC vs frozen | `1.0` = `1.0` | `0.7575757575757576` = frozen |

Frozen reference values: Bottle `image=1.0`, `pixel=0.9616640201342855`,
`aupro=0.8774472899967225`; Metal Nut `image=0.7575757575757576`,
`pixel=0.6926300242781803`, `aupro=0.3797941159804979`. The analyzer's full-grid
`Z` metrics reproduce them to ~`1e-12`, so the counterfactual is computed on the
same semantics as the official metrics.

---

## 3. Image-level results (full grid vs inner 12x12)

| Category | Space | Image AUROC full | Image AUROC inner | Delta | Lost pairs full | Lost pairs inner | Recovered | Argmax on border (removed) |
|---|---|---|---|---|---|---|---|---|
| Bottle | RAW | 0.985714 | 0.985714 | 0.000000 | 18 / 1260 | 18 / 1260 | 0 | 1 |
| Bottle | Z | 1.000000 | 1.000000 | 0.000000 | 0 / 1260 | 0 / 1260 | 0 | 8 |
| Metal Nut | RAW | 0.704790 | 0.713587 | +0.008798 | 604 / 2046 | 586 / 2046 | 18 | 19 |
| Metal Nut | Z | 0.757576 | 0.756109 | -0.001466 | 496 / 2046 | 499 / 2046 | -3 | 15 |

- **Bottle (Z):** perfect Image AUROC is fully preserved when the border is
  removed. Eight images (4 contamination defects + 4 good) have their argmax on
  the periphery, but re-taking the inner argmax keeps every pair correctly
  ranked. **No material change.**
- **Metal Nut (Z):** excluding the border leaves Image AUROC essentially
  unchanged (`-0.00147`, MARGINAL). The self-consistent counterfactual (inner
  argmax recomputed for **all** images) does **not** recover lost pairs — it adds
  3 (496 -> 499). Fifteen images (9 anomalies + 6 good) have a border argmax.

### 3.1 Per-defect image AUROC (full vs inner)

| Category | Space | Defect | n | Full | Inner | Delta | Class |
|---|---|---|---|---|---|---|---|
| Bottle | RAW | broken_large | 20 | 1.0000 | 1.0000 | 0.0000 | MARGINAL |
| Bottle | RAW | broken_small | 22 | 0.9841 | 0.9841 | 0.0000 | MARGINAL |
| Bottle | RAW | contamination | 21 | 0.9738 | 0.9738 | 0.0000 | MARGINAL |
| Bottle | Z | broken_large | 20 | 1.0000 | 1.0000 | 0.0000 | MARGINAL |
| Bottle | Z | broken_small | 22 | 1.0000 | 1.0000 | 0.0000 | MARGINAL |
| Bottle | Z | contamination | 21 | 1.0000 | 1.0000 | 0.0000 | MARGINAL |
| Metal Nut | RAW | bent | 25 | 0.6455 | 0.6364 | -0.0091 | MARGINAL |
| Metal Nut | RAW | color | 22 | 0.5289 | 0.5165 | -0.0124 | MODERATE |
| Metal Nut | RAW | flip | 23 | 0.9664 | 0.9664 | 0.0000 | MARGINAL |
| Metal Nut | RAW | scratch | 23 | 0.6759 | 0.7332 | +0.0573 | LARGE |
| Metal Nut | Z | bent | 25 | 0.5545 | 0.5182 | -0.0364 | MODERATE |
| Metal Nut | Z | color | 22 | 0.7190 | 0.7376 | +0.0186 | MODERATE |
| Metal Nut | Z | flip | 23 | 0.9012 | 0.9051 | +0.0040 | MARGINAL |
| Metal Nut | Z | scratch | 23 | 0.8715 | 0.8834 | +0.0119 | MODERATE |

Per-defect deltas are mixed in sign (some negative, some positive), i.e. the
border is not a systematic boost; the aggregate Metal Nut image effect is
MARGINAL. Bottle is exactly unchanged at every defect.

### 3.2 Images whose argmax lies on the border

Full list in `border_exclusion_argmax_removed.csv`. Z space:

- Bottle: `contamination_007` (r13,c10), `_013` (r6,c0), `_014` (r13,c5),
  `_017` (r0,c0); good `_000` (r1,c0), `_003` (r13,c7), `_013` (r3,c0),
  `_019` (r13,c12).
- Metal Nut: bent `_000,_002,_004,_008,_011,_013,_016`; color `_004,_008`;
  good `_000,_001,_002,_019,_020,_021`.

---

## 4. Pixel-level results

Diagnostic pixel metrics recomputed with the same region-weighted Pixel AUROC
and AUPRO@0.30 as `LocalizationMetrics`, where region areas and the region count
are recomputed **inside** the evaluated support.

| Category | Space | Scope | Pixel AUROC | AUPRO@0.30 | Foreground px | Background px | Regions |
|---|---|---|---|---|---|---|---|
| Bottle | Z | full | 0.961664 | 0.877447 | 3,886,731 | 63,343,269 | 68 |
| Bottle | Z | nominal inner | 0.952890 | 0.850613 | 3,864,147 | 45,474,456 | 68 |
| Bottle | Z | strict inner | 0.946875 | 0.832576 | 3,824,416 | 37,780,496 | 68 |
| Bottle | RAW | full | 0.956927 | 0.865098 | 3,886,731 | 63,343,269 | 68 |
| Bottle | RAW | nominal inner | 0.944749 | 0.829045 | 3,864,147 | 45,474,456 | 68 |
| Bottle | RAW | strict inner | 0.935524 | 0.805360 | 3,824,416 | 37,780,496 | 68 |
| Metal Nut | Z | full | 0.692630 | 0.379794 | 6,602,541 | 49,747,459 | 132 |
| Metal Nut | Z | nominal inner | 0.690687 | 0.379690 | 6,520,078 | 34,879,922 | 132 |
| Metal Nut | Z | strict inner | 0.707846 | 0.393365 | 6,122,174 | 28,665,326 | 132 |
| Metal Nut | RAW | full | 0.625562 | 0.345386 | 6,602,541 | 49,747,459 | 132 |
| Metal Nut | RAW | nominal inner | 0.601384 | 0.321939 | 6,520,078 | 34,879,922 | 132 |
| Metal Nut | RAW | strict inner | 0.606235 | 0.338557 | 6,122,174 | 28,665,326 | 132 |

Notes:
- **Bottle:** the peripheral band carries a small but real amount of signal; the
  nominal exclusion costs a little Pixel AUROC and more AUPRO.
- **Metal Nut (nominally):** removing the border band barely moves Pixel AUROC or
  AUPRO (both MARGINAL).
- **Metal Nut (strictly):** excluding *all* pixels whose interpolation touches
  row/col 0 or 13 slightly **improves** Pixel AUROC (`+0.0152`) and AUPRO
  (`+0.0136`), both MODERATE — i.e. in the strict support the border-interpolated
  pixels were mildly diluting localization quality.

---

## 5. Final comparison table — official `Z` space

| Category | Metric | Full | Inner | Delta | Class |
|---|---|---|---|---|---|
| Bottle | Image AUROC | 1.000000 | 1.000000 | 0.000000 | MARGINAL |
| Bottle | Pixel AUROC (nominal inner) | 0.961664 | 0.952890 | -0.008774 | MARGINAL |
| Bottle | AUPRO@0.30 (nominal inner) | 0.877447 | 0.850613 | -0.026834 | MODERATE |
| Bottle | Pixel AUROC (strict inner) | 0.961664 | 0.946875 | -0.014789 | MODERATE |
| Bottle | AUPRO@0.30 (strict inner) | 0.877447 | 0.832576 | -0.044872 | MODERATE |
| Metal Nut | Image AUROC | 0.757576 | 0.756109 | -0.001466 | MARGINAL |
| Metal Nut | Pixel AUROC (nominal inner) | 0.692630 | 0.690687 | -0.001943 | MARGINAL |
| Metal Nut | AUPRO@0.30 (nominal inner) | 0.379794 | 0.379690 | -0.000104 | MARGINAL |
| Metal Nut | Pixel AUROC (strict inner) | 0.692630 | 0.707846 | +0.015216 | MODERATE |
| Metal Nut | AUPRO@0.30 (strict inner) | 0.379794 | 0.393365 | +0.013570 | MODERATE |

---

## 6. Answers

**How much Metal Nut error is associated with the peripheral cells?**
Very little. Image-level: the perimeter contributes essentially nothing to the
ranking error — the loss does not shrink when the border is removed (496 -> 499
pairs; AUROC `-0.00147`). Nominally at pixel level the border band accounts for a
`-0.0019` Pixel AUROC and `-0.0001` AUPRO shift. Only the strict, interpolation-aware
support shows a MODERATE effect, and it is an **improvement** (`+0.015` AUROC,
`+0.014` AUPRO) when border-touching pixels are removed.

**Does excluding the peripheral cells materially change Metal Nut Image AUROC?**
No. `0.757576 -> 0.756109`, delta `-0.00147` (**MARGINAL**). The peripheral cells
are not a material driver of the image-level ranking loss.

**Metal Nut Pixel AUROC / AUPRO?**
Nominally no (both **MARGINAL**, `|delta| < 0.002`). Strictly, a MODERATE
*improvement* of `~+0.015` AUROC / `~+0.014` AUPRO.

**Does Bottle stay substantially unchanged?**
At image level, exactly unchanged (delta `0.0`, still `1.0`). At pixel level it
degrades slightly but only at MARGINAL/MODERATE scale (nominal: Pixel AUROC
`-0.0088` MARGINAL, AUPRO `-0.0268` MODERATE). So Bottle is substantially stable,
with a small genuine border contribution to localization, not to detection.

**Is the previous upper bound of 69/496 ranking pairs for Metal Nut consistent
with the observed counterfactual?**
No, it is not realized. The earlier audit's 69/496 figure is an *attribution*
obtained by deleting the border from those six good images while keeping the
inner scores fixed. The self-consistent counterfactual — recomputing the inner
argmax for **all** images (goods and anomalies) — recovers **0** pairs and loses
3 more (496 -> 499, `-0.00147` AUROC). The border-argmax goods' inner maxima
remain low and their comparison anomalies also lose their border peaks, so the
pairs are not recovered. The observed counterfactual sits far below the
69-pair hypothesis, which should therefore be treated as an upper estimate that
is **not** supported as a causal contribution.

---

## 7. Conclusion

| Metric (Metal Nut, official Z) | Result | Classification |
|---|---|---|
| Image AUROC | `0.757576 -> 0.756109` (`-0.00147`) | **MARGINAL** |
| Pixel AUROC (nominal) | `0.692630 -> 0.690687` (`-0.00194`) | **MARGINAL** |
| AUPRO@0.30 (nominal) | `0.379794 -> 0.379690` (`-0.00010`) | **MARGINAL** |
| Pixel AUROC (strict) | `0.692630 -> 0.707846` (`+0.01522`) | MODERATE |
| AUPRO@0.30 (strict) | `0.379794 -> 0.393365` (`+0.01357`) | MODERATE |

**Overall Metal Nut: MARGINAL.** The peripheral cells of the 14x14 grid do not
materially drive Metal Nut's Anomalib4j error. Excluding them leaves Image AUROC,
nominally-supported Pixel AUROC and AUPRO essentially unchanged; the strict
support even improves localization slightly.

**Bottle: MARGINAL.** Detection (`1.0`) is fully preserved; localization shifts
by a small MODERATE amount (`AUPRO -0.0268` nominal, `-0.0449` strict) — the only
place where the border carries non-trivial signal, and it is a positive
contribution rather than an artifact.

**Report path:** `docs/diagnostics/BORDER_EXCLUSION_SENSITIVITY.md`
**Artifacts path:** `target/comparison/final-comparison-20261003/diagnostics/border-exclusion/`
(`border_exclusion_image_level.csv`, `border_exclusion_argmax_removed.csv`,
`border_exclusion_image_per_defect.csv`, `border_exclusion_pixel_level.csv`,
`border_exclusion_deltas.csv`, `border_exclusion_summary.json`)

All figures above are `POST_HOC_SENSITIVITY_ONLY` and do not modify the official
frozen metrics.
