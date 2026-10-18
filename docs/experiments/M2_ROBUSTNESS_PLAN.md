# Controlled Java M2 robustness protocol

Frozen before measurement, 2026-10-18. Score only normal test images from all
15 categories, preserving the `m0-all15-baseline-20261016` manifests and M2 math.
Reuse Java archives for Bottle/Metal Nut/Hazelnut; fit remaining archives from
retained FIT/calibration descriptors using the same Java trainer. Validate
calibration Z against the Python formula before accepting a category result.

Apply transforms to canonical 224x224 RGB after the production bicubic resize,
then use production ImageNet normalization and encoder. The test-only adapter
recovers uint8 RGB from the production normalized tensor; all 256 channel values
are tested and every image must reproduce the tensor bit for bit. The same-size
224 redraw in the encoder must be an identity. Compare every original native map
against `M2Detector`, and every transform-family identity against that original.

- Independent X and Y translation: -8,-4,-2,0,2,4,8 input pixels; positive right/down.
- Center rotation: -20,-10,-5,0,5,10,20 degrees; positive clockwise.
- Center isotropic scale: .90,.95,1,1.05,1.10; above one enlarges content.
- Brightness, contrast, gamma: .80,.90,1,1.10,1.20.
- Contrast midpoint 127.5. Gamma: `255*(input/255)^gamma` (above one darkens).
- Geometry: inverse pixel-center mapping around (111.5,111.5), bilinear sampling
  for rotation/scale, edge replication by coordinate clamping; integer translations
  remain exact. One final nearest-integer rounding and clipping to [0,255].
- Each transform starts from its original RGB, never another transformed copy.

Primary score: native max Z. Sole spatial diagnostic: max on rows/columns 1..12,
excluding exactly one ring, without refitting. Each paired delta uses the same
image and same support in original/transformed maps. No new detector or metric.

Diagnostic reference: empirical maximum of the held-out calibration image max-Z
scores, separately by category, fixed before seeing normal test scores. Exceedance
uses strict `>` and is not a production FPR or deployed threshold. The calibration
set also estimates positional Z, so this is a descriptive reference, not an
independent calibrated false-alarm guarantee.

Retain per-image CSV, per-category/family/level aggregates (median original and
transformed scores, median/mean/p90/p95/max paired delta, also interior), references,
source/input hashes, manifests, identity/parity records, progress and operational
duration. Quantiles use NumPy linear interpolation. Macro curves give equal weight
to categories; texture/object groups use the predefined five/ten split.

For descriptive interpretation, call a category-level rise material when median
paired delta exceeds +1 Z unit; also report reference exceedance and tails rather
than treating this descriptive convention as an operational threshold. Rank
fragility by the mean of the seven family-wise means of nonidentity median deltas
divided by that category's calibration reference; this gives each family equal
weight and reduces differences in category score scale. Report absolute deltas
alongside rankings. This range-specific ranking is not model-selection accuracy.

No JMH, anomalous-image evaluation, model modification or category tuning.
Recommend exactly one next experiment from measured evidence. Artifacts must use
a new namespace; production and retained artifacts remain untouched.
