# README heatmap visualization audit

Status: **AUDIT ONLY / READ-ONLY**. No scientific map, metric, model or benchmark result was
changed or recomputed. All numbers below are read from existing artifacts under `target/`.

Scope: explain why the PNG overlays under `target/bottle-localization` show clear yellow/red
anomaly regions, while the overlays under `target/bottle-spatial14-positional-calibration` and
`target/bottle-spatial14-heldout-calibration` appear nearly monochromatic.

Samples inspected: `bottle_good_000.png`, `bottle_broken_large_000.png`,
`bottle_broken_small_000.png` (present in all three directories).

---

## 1. Which map each directory visualizes, and who generates it

| Directory | Generator (test class) | Underlying map visualized | Overlay score label |
| --- | --- | --- | --- |
| `target/bottle-localization` | `BottleLocalizationIT` (`evaluatesSavedSprint7MapsAgainstOriginalResolutionMasks`) | **RAW** discrepancy map `1 - compiledScore` read from `target/bottle-raw-heatmaps.csv` (Sprint 7 saved raw maps, full-209 model) | `Raw bilinear map (blue to red)` |
| `target/bottle-spatial14-positional-calibration` | `BottlePositionalCalibrationIT` | **positional Z** `z = (raw - mu)/sigma`, in-sample (209 training images used for mu/sigma) | `Positional z (bilinear, blue to red)` |
| `target/bottle-spatial14-heldout-calibration` | `BottleHeldOutCalibrationIT` | **positional Z** (held-out, 167 archetype-training + 42 calibration images) | `Held-out positional z (bilinear, blue to red)` |

In every case the 14x14 map is upsampled to original resolution with the same bilinear
half-pixel border-replicate routine (`LocalizationMaps.upsample`) before overlay.

All three call the **same rendering routine**: `LocalizationMaps.overlay(...)`
(`src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java:39`).

---

## 2. Exact visualization scaling (identical code in all three)

`LocalizationMaps.overlay` builds a 3-panel image (Original | Ground truth | Score overlay) and
colors each pixel with:

```
fraction = clamp((score[p] - low) / (high - low), 0, 1)      // low==high -> 0.5
color     = Color.getHSBColor((1 - fraction) * 0.66f, 1, 1)  // hue .66=blue -> 0=red
pixel     = blend(original_rgb, color_rgb)                   // 0.55 * color + 0.45 * original
```

- Colormap: HSB hue sweep blue (0.66) -> red (0.0), saturation 1, brightness 1.
- Blending/alpha: fixed 55% score color over 45% original image (`blend`, no alpha compositing).
- Range: **fixed/global min-max shared across all images** of the run, then **clamped to [0, 1]**.
- NOT per-image min-max. NOT percentile scaling. NOT hardcoded. No histogram/robust scaling.

The only thing that differs between directories is the **value of `low` and `high`**, which each
test computes as the union min and union max over the 83 test images:

- `BottleLocalizationIT`: `low = min_pixels min(map)`, `high = max_pixels max(map)` over the RAW maps
  (`BottleLocalizationIT.java:29-40`, passed at line 67; label defaults to "Raw bilinear map").
- `BottlePositionalCalibrationIT`: `displayMin/displayMax` over the Z maps, and it explicitly
  documents "Shared overlay color scale ... used only for visualization"
  (`BottlePositionalCalibrationIT.java:45-46, 88-89, 122-123, 154`).
- `BottleHeldOutCalibrationIT`: `low/high` over the Z maps
  (`BottleHeldOutCalibrationIT.java:135-136, 152-153`).

So all three are "shared global display scale"; none is per-image.

---

## 3. Measured map ranges for the three samples

Global display scale actually used by each test (union min / union max over all 83 images) and the
per-sample map range:

### RAW — `target/bottle-localization` (also the RAW column of positional/held-out)

Global scale: **low = -113.369931, high = 49.352997, range = 162.722928**

| Sample | map min | map max | map range | colormap fraction [lo, hi] (span) |
| --- | --- | --- | --- | --- |
| bottle_good_000.png | -111.618018 | -83.421775 | 28.196243 | [0.011, 0.184] (0.173) |
| bottle_broken_large_000.png | -111.863775 | 10.979815 | 122.843590 | [0.009, 0.764] (0.755) |
| bottle_broken_small_000.png | -111.273406 | -66.275325 | 44.998081 | [0.013, 0.289] (0.277) |

### positional Z — `target/bottle-spatial14-positional-calibration`

Global scale: **low = -3.927281, high = 363.972497, range = 367.899779**

| Sample | map min | map max | map range | colormap fraction [lo, hi] (span) |
| --- | --- | --- | --- | --- |
| bottle_good_000.png | -2.148075 | 3.764045 | 5.912119 | [0.005, 0.021] (0.016) |
| bottle_broken_large_000.png | -1.678063 | 55.613597 | 57.291660 | [0.006, 0.162] (0.156) |
| bottle_broken_small_000.png | -2.210923 | 14.458334 | 16.669258 | [0.005, 0.050] (0.045) |

### held-out Z — `target/bottle-spatial14-heldout-calibration`

Global scale: **low = -4.172908, high = 364.117293, range = 368.290201**

| Sample | map min | map max | map range | colormap fraction [lo, hi] (span) |
| --- | --- | --- | --- | --- |
| bottle_good_000.png | -2.438738 | 4.357266 | 6.796005 | [0.005, 0.023] (0.018) |
| bottle_broken_large_000.png | -1.816967 | 62.018697 | 63.835664 | [0.006, 0.180] (0.173) |
| bottle_broken_small_000.png | -2.150702 | 15.219808 | 17.370510 | [0.005, 0.053] (0.047) |

(`fraction` = position on the shared min-max colormap; 0 ≈ blue, 1 ≈ red.)

---

## 4. Cause of the visual difference

The renderer, colormap, blend factor and clamping are **identical** in all three directories. The
difference is entirely a consequence of *which map* is drawn and *the global display scale*:

1. **RAW maps are used in `bottle-localization`; the calibration directories draw Z maps.**
2. The shared display scale is the union over all 83 images. For Z, that union is dominated by a
   single extreme outlier: the largest Z value in the whole test set is **363.97** (positional) /
   **364.12** (held-out), produced by `bottle_contamination_017.png`; the next largest are
   `bottle_contamination_007.png` (~299 / ~255). Because `high` is set by that one cell, every
   "normal" Z map is squeezed against the blue end of the colormap.
   - `bottle_broken_large_000` reaches only fraction **0.16–0.18** -> faint blue with a slight
     shift, not the clear yellow of the RAW overlay (fraction **0.76**).
   - `bottle_good_000` spans only **0.016–0.018** of the colormap -> effectively monochrome blue.
3. RAW, by contrast, has no comparably pathological outlier: its global max (49.35) is only ~1.5x
   the broken_large sample max (10.98), so the same shared-scale strategy still yields good visible
   contrast.

In short: **the visible difference is a rendering/scaling artifact, not a difference in the anomaly
signal.** It is caused by (a) visualizing RAW in one directory and Z in the others, and (b) a
global (not per-image, not percentile) color scale that Z's heavy tail collapses.

---

## 5. Are the underlying anomaly maps scientifically different?

They are distinct entities, but the clarity difference is not scientific:

- `bottle-localization` and `bottle-spatial14-positional-calibration` share the **same RAW maps**:
  a cell-by-cell comparison of the RAW columns found **0 mismatches** (same full-209 SPATIAL_14
  filters, `raw = 1 - compiledScore`).
- `bottle-spatial14-heldout-calibration` uses a **different model** (167 archetype-training images
  + 42 calibration) and therefore different RAW values (588/588 compared cells differ); its Z is a
  different calibration. This is expected and is a real experimental difference (it is the source
  of the published held-out delta), but it is not the cause of the monochromatic appearance.
- The Z-vs-RAW difference is a genuine transform (`z = (raw-mu)/sigma`), already documented in the
  calibration reports; it changes scores but does not by itself make the image "monochrome" — the
  global display scale does.

---

## 6. Is `bottle-localization` the canonical SPATIAL_14 Sprint-8 localization baseline?

**Yes.** `target/bottle-localization/metrics.txt` records:

```
imageAuRoc=0.98571428571428580
pixelAuRoc=0.95694957509359390
auPro030=0.86534746572585690
regions=68  positivePixels=3886731  negativePixels=63343269
rawMaps=target/bottle-raw-heatmaps.csv
```

These are exactly the frozen full-209 RAW baseline values (`Image 0.9857142857142858`,
`Pixel 0.9569495750935939`, `AUPRO 0.8653474657258569`) recorded in
`docs/development/SPRINT8_RESULT.md:95-97`, `docs/benchmark/LOCALIZATION_CONVENTIONS.md` context,
and reused as the "Full 209 raw" reference row in
`docs/benchmark/HELDOUT_CALIBRATION_RESULT.md:49` and `docs/PROJECT_STATE.md:123`.
`SPRINT8_RESULT.md` also documents that `BottleLocalizationIT` is the test that reads
`target/bottle-raw-heatmaps.csv` and computes these metrics. The four published example PNGs live in
`target/bottle-localization/`.

Note: the official benchmark *quality* metric for Anomalib4j Bottle in
`docs/benchmark/BENCHMARK_FINAL_RESULT.md` is the calibrated Z value
(`pixel_auroc=0.9616640201342855`). The `bottle-localization` directory is the **RAW Sprint-8
localization baseline**, which is a different (upstream, pre-calibration) artifact — not the
benchmark headline. Captions must not conflate the two.

---

## 7. README visualization strategy (recommendation)

Preferred outcome, and it is scientifically valid:

**Use the `target/bottle-localization` RAW overlays for the README qualitative panels, with an
explicit caption.**

Rationale:
- They visualize the canonical SPATIAL_14 RAW discrepancy maps (`1 - compiledScore`) that underlie
  the Sprint-8 localization baseline; they are real outputs, not cosmetic edits of scientific data.
- Their shared display scale is well-behaved, so the anomaly regions are actually visible.
- The color mapping is explicitly a display choice; metrics are computed from score values and are
  independent of the colormap (already true today).

Required caption wording (for every heatmap panel):
- State that the panel is the **RAW discrepancy map `1 - compiledScore`** (not Z).
- State that the blue→red color range is a **shared display scale for visualization only** and
  **does not affect any metric**; the numbers come from the score maps.
- Do not imply probability or calibrated anomaly confidence.
- For Metal Nut keep its own separate scale and wording (already noted in README.it.md: scales are
  different and not comparable).

If the README should instead showcase the *calibrated Z* story (the stronger Pixel AUROC 0.962 /
Image AUROC 1.000), the Z overlays must first be regenerated with a **visualization-only** scale
that is robust to the Z tail — e.g. per-image min-max, or a high percentile (p99/p99.5) cap instead
of the union max — while leaving metrics untouched. That requires a code change and a re-run, so it
is out of scope of this read-only audit.

Concretely recommended source files for the README Bottle panels:

| README panel | Recommended source | Map | Caption must say |
| --- | --- | --- | --- |
| bottle-good.png | `target/bottle-localization/bottle_good_000.png` | RAW | RAW, shared display scale, visualization-only |
| bottle-broken-large.png | `target/bottle-localization/bottle_broken_large_000.png` | RAW | RAW, shared display scale, visualization-only |
| bottle-broken-small.png | `target/bottle-localization/bottle_broken_small_000.png` | RAW | RAW, shared display scale, visualization-only |

`docs/images/bottle-*.png` currently contain the **held-out Z** overlays (SHA-256 identical to
`target/bottle-spatial14-heldout-calibration/*.png`), which is why they look monochromatic; they
would need to be replaced (copy, not move) to adopt this recommendation. `target/` remains excluded
from the repository.

---

## 8. Provenance / reproducibility

- Renderer: `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java` (`overlay`, `blend`).
- Generators: `BottleLocalizationIT.java`, `BottlePositionalCalibrationIT.java`,
  `BottleHeldOutCalibrationIT.java`.
- Maps: `target/bottle-raw-heatmaps.csv` (RAW),
  `target/bottle-spatial14-positional-calibration/test-scores.csv` (raw, z),
  `target/bottle-spatial14-heldout-calibration/test-scores.csv` (raw_167, z_167_42).
- Metrics references: `docs/development/SPRINT8_RESULT.md`, `docs/benchmark/HELDOUT_CALIBRATION_RESULT.md`,
  `docs/benchmark/LOCALIZATION_CONVENTIONS.md`, `docs/PROJECT_STATE.md`,
  `docs/benchmark/BENCHMARK_FINAL_RESULT.md`.
- No file was modified by this audit; the only artifacts produced were in a temporary directory.
