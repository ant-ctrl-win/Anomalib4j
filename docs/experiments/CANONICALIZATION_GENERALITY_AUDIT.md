# Canonicalization generality audit

Completed2026-10-20. Entry HEAD `3af68f2`, initially clean worktree.
**The common pipeline has promising normal-feature evidence beyond Metal Nut,
but no second category is ready for full M2 qualification under the current
source/framing contract.** Metal Nut remains GO/already qualified; Bottle and
Screw are MAYBE; the tested Hazelnut/Cable strategies and whole-object Grid
canonicalization are NO-GO. No production code or scientific detector changed.

## 1. Question and scope

Does pose normalization followed by strict local M2 generalize, or is Metal Nut
a special case? This is a six-category geometry/normal-feature screening audit,
not a detector benchmark. No new M2 model, calibration, anomaly score, Image
AUROC, Pixel AUROC or AUPRO was computed. No performance campaign, sampler
optimization, new production category/framework or commit was performed.

Read alongside the [frozen protocol](CANONICALIZATION_GENERALITY_AUDIT_PROTOCOL.md),
[BGR result](JAVA_CANONICAL_BGR_FAST_PATH.md),
[canonical integration](JAVA_CANONICAL_M2_INTEGRATION.md),
[Phase2 qualification](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md),
[Phase1 contract](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md) and
[architecture review](JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md).
Current all-15 context is the retained M0
`target/comparison/m0-all15-baseline-20261016/{RESULT.md,summary-by-category.csv}`
and local-model `target/ablation/local-models-20261017/{RESULT.md,comparison.csv}`,
indexed in [EXPERIMENT_INDEX](../EXPERIMENT_INDEX.md). Historical detection metrics
were context, never a geometry-selection gate. The
[dataset census](../benchmark/MVTEC_DATASET_CENSUS.md) establishes category sizes
and image geometry; this audit reads no annotation masks.

## 2. Why Metal Nut alone is insufficient

H4 matches a fourfold outline, fixed700 acquisition and nearly constant framing.
A principal axis is almost undefined for Metal Nut (FIT median anisotropy.0023)
while H4 strength is strong (.1458). That does not imply another circular object
has an observable rotation, or that a long object fits the canonical canvas.
Neither low normal variance nor good synthetic equivariance establishes anomaly
preservation. A defect can alter the segmentation used to define pose.

## 3. Coverage, source contract and controls

Exactly the retained seed42 FIT/CAL/TEST manifests, including order and byte hashes.
No new split or label-based sampling. All FIT data are normal; CAL gets geometry
only. TEST images get geometry only, after the estimator and feature plan freeze.

| Category | Native square size | FIT | CAL | TEST normal/anomaly | Screened families |
|---|---:|---:|---:|---:|---|
| metal_nut |700|176|44|22/93|unchanged H4|
| bottle |900|167|42|20/63|CENTER_ONLY; axis/polarity viability|
| screw |1024|256|64|41/119|AXIS_WITH_POLARITY|
| hazelnut |1024|312|79|40/70|CENTER_ONLY; AXIS|
| cable |1024|179|45|58/92|CENTER_ONLY; axis viability|
| grid |1024|211|53|21/57|NONE; no fabricated segmentation|

Totals:1,301 FIT/327 CAL/696 TEST images. Geometry covers the five objects:
2,188 training strategy records and961 TEST strategy records, corresponding to
618 distinct object TEST images. Grid's78 TEST images are inventoried, not posed.
No claim is made about the nine unaudited categories.

The unchanged direct B sampler accepts700 only. For other square acquisitions
the **experimental** adapter applies the legacy logical-sRGB conversion policy
first, then Graphics2D bicubic/quality resize to700. Native700 is not resized.
This is fixed raster-size conversion, not estimated object-scale normalization,
and it is not new production size support. Three comparisons remain separate:

- **LEGACY/NONE:** native source through the unchanged current bicubic tensor path.
- **B_IDENTITY:** the same700 view through unchanged B at identity geometry.
- **Candidate:** that700 view through frozen pose and the same B.

All use the identical frozen14x14x96 encoder and normalized CNN descriptors.
Candidate versus B_IDENTITY is the alignment comparison. LEGACY versus candidate
also changes filtering/resolution and cannot alone identify registration benefit.
No category-specific learned preprocessing/model state or persistence was added.

## 4. Estimators and failure/branch rules

Metal Nut calls the production estimator unchanged:175 luminance/BOX, Otsu
bright foreground, first largest8-component,4-background hole fill, centroid,
H4 modulo90, existing area/border/strength failures.

Other object hypotheses use that exact diagnostic175 gray reduction, followed
by experimental Otsu/connected-component/hole-fill code. Bottle/Screw use dark
foreground; Hazelnut/Cable use bright foreground. First threshold maximum and
first largest row-major component win ties. Centroid is from filled pixels.
Area[.005,.95], nonempty mask and no source-border contact are broad sanity
checks, **not evidence that a component is the correct object**.

Major axis is half atan2 of the second central moments; confidence is
`(lambda_max-lambda_min)/(lambda_max+lambda_min)`. Weak flags are anisotropy<.10
for Bottle/Hazelnut/Cable and<.50 for Screw. Axis ties retain deterministic
angle0 but remain weak. Directed polarity compares mass in the outer25% of each
projected end, using signed relative mass difference. Absolute contrast<.10 is
weak; ties are not trusted. The heavier end points toward canonical+x.

Explicit states:OK,SEGMENTATION_FAILURE,WEAK_AXIS,WEAK_POLARITY. Axis weakness
takes precedence over the polarity flag; actual mass contrasts are also retained.
Only OK poses produce a transform. CENTER_ONLY does not consume a weak angle.
H4 is modulo90, undirected AXIS modulo180, directed Screw modulo360; raw directed
angles have a deterministic[-90,270] branch. No intensity-based H4 branch selector,
polarity learning from TEST, filename input, defect-mask input or fallback exists.
All thresholds/rules were fixed from the protocol/FIT inspection and unchanged
after TEST was opened.

## 5. Geometry viability before CNN extraction

| Category | FIT/CAL trusted pose | Orientation evidence | Interpretation |
|---|---|---|---|
| Metal Nut |176/176;44/44|H4 median/p95 .145751/.147031|fourfold pose observable; principal axis unnecessary|
| Bottle center |167/167;42/42|axis median/p95 .009068/.023367; abs polarity .002922/.008642|end-on circular bottle, not side-view neck/body;0/209 training axes trusted|
| Screw directed axis |253/256;63/64|axis median/p95 .930964/.942457; abs polarity .358119/.398399|all valid silhouettes have strong axis/head-tip signal;3 FIT/1 CAL source-border failures|
| Hazelnut center/axis |center312/312;79/79; axis229/312;64/79|axis median/p95 .142315/.286991|83 FIT/15 CAL weak axes;180-degree identity unresolved|
| Cable center/axis |center179/179;45/45; axis5/179;1/45|axis median/p95 .041269/.088822|97.2% FIT axes weak; cross-section/internal variation poorly modeled by one axis|
| Grid |not applicable|no bounded object/polarity|NONE; no silhouette invented|

Screw polarity is strong on all253 valid FIT and63 valid CAL cases; no valid
weak-polarity case. Bottle's valid training contrasts are all below.10. The broad
segmentation flag does not validate Hazelnut/Cable as complete silhouettes.

Coordinates below are pixels in the700 view, on valid segmentations. Wrapped
angle distributions and all confidence quantiles are retained in
`summary-geometry.json`; linear angle means are not interpreted as circular means.

| Category | FIT cx: median [p25,p75]; range | FIT cy: median [p25,p75]; range |
|---|---|---|
| Metal Nut | 348.407 [347.559,349.385]; range342.756..351.926 | 352.802 [351.921,353.502]; range349.393..356.283 |
| Bottle | 349.972 [349.425,350.670]; range346.841..355.417 | 350.558 [350.005,351.588]; range347.874..356.755 |
| Screw | 350.237 [328.645,374.581]; range297.688..406.874 | 353.480 [334.061,374.322]; range301.873..404.254 |
| Hazelnut | 347.631 [339.963,356.169]; range281.492..409.363 | 332.677 [324.202,340.836]; range278.747..391.532 |
| Cable | 350.055 [347.928,352.126]; range339.718..373.246 | 350.867 [349.035,352.473]; range336.692..378.923 |

The FIT H4 angle range is[-43.953,43.772] degrees. Screw directed angles span
the360-degree branch, while Hazelnut/Cable axes span the180-degree branch.
Bottle's arbitrary weak-axis angles are recorded but never used for rotation.

**Framing blocker discovered before TEST:** all253 valid Screw FIT masks and
all63 valid CAL masks have some filled-pixel centers outside the700 canvas after
scale1 mass-centering/rotation. Valid FIT clipped-area fraction median.002530,
p95.007643,max.009663. This small fraction can contain the tip; it is not harmless
because its area is small. Three FIT/one CAL inputs already touch the source
frame. No pose, canvas, scale, rejection rule or image selection was repaired.

## 6. Controlled source perturbations

First12 retained FIT normals per object, eight transformations each: native
translations +/-2% along x/y and rotations +/-5,+/-10 degrees.480 distinct source
perturbations give768 strategy records. Estimators are blind to the transform;
known geometry enters only subsequent error calculation. Pixel hashes and exact
parameters/source identities are retained. This is a small deterministic screen,
not exhaustive coverage or a confidence interval.

**Recovery measures equivariance relative to the original estimated anchor,
not absolute ground-truth object pose.** A wrong anchor can transform consistently.

| Strategy | Comparable center pairs | Center error median/p95/max (pixels700) | Trusted pose pairs/96 | Angular error median/p95/max (degrees) |
|---|---:|---|---:|---|
| Metal Nut H4 | 96 | 0.153/0.289/0.302 | 96 | 0.039/0.128/0.165 |
| Bottle CENTER_ONLY | 96 | 0.098/0.230/0.268 | 96 | not observable/not estimated |
| Screw AXIS_WITH_POLARITY | 96 | 0.344/0.876/1.026 | 96 | 0.043/0.103/0.134 |
| Hazelnut CENTER_ONLY | 96 | 0.387/2.569/5.953 | 96 | not observable/not estimated |
| Cable CENTER_ONLY | 96 | 0.135/0.344/0.762 | 96 | not observable/not estimated |
| Hazelnut AXIS | 96 | 0.387/2.569/5.953 | 56 | 0.325/2.354/5.857 |
| Cable AXIS diagnostic | 96 | 0.135/0.344/0.762 | 8 | 0.156/0.315/0.319 |
| Bottle AXIS_WITH_POLARITY diagnostic | 96 | 0.098/0.230/0.268 | 0 | not observable/not estimated |

Screw:96/96 trusted pairs, no180-degree polarity reversals; one360-degree
representation wrap is not a reversal. H4:three equivalent90-degree branch
crossings, no modulo90 recovery failure. Hazelnut:40/96 transformed poses weak,
only56 comparable angles; no claim is made for the other40. Cable:88/96 weak,
eight trusted angles are insufficient to support a category-wide axis. Bottle
has no comparable angle. CENTER_ONLY intentionally makes no rotation-correction
claim. All96 perturbations/category avoid source-border failures in this subset;
that does not erase Screw's four failures in the complete training population.

## 7. FIT coherence: exact pooled pair statistics

All candidate decisions for extraction were frozen in `screen-plan.json` after
geometry and before features/TEST.3918 FIT encodings were performed, plus one
separate untimed CPU proof. No CAL/TEST descriptors or M2 scores were extracted.
All1,301 FIT images retain both baseline controls. Screw candidate excludes its
three explicit source-border failures; Hazelnut AXIS excludes83 weak poses.
Their comparisons use the same253/229 names on both controls. Exclusions are
listed, not silently dropped from a detector benchmark.

Unordered-pair cosine is computed exactly over every pair at every location.
Pooled median differs from median of196 position means: Metal Nut H4 values are
.905313 versus.874022. The latter reproduces the older Phase2 statistic; they
must not be silently interchanged.

| Category/input | FIT n | Pooled cosine mean | Pooled median | Channel std median |
|---|---:|---:|---:|---:|
| metal_nut/LEGACY | 176 | 0.548333 | 0.685659 | 0.010995 |
| metal_nut/B_IDENTITY | 176 | 0.548280 | 0.685616 | 0.010970 |
| metal_nut/H4 | 176 | 0.870472 | 0.905313 | 0.008075 |
| bottle/LEGACY | 167 | 0.972962 | 0.981866 | 0.003964 |
| bottle/B_IDENTITY | 167 | 0.973798 | 0.982654 | 0.003873 |
| bottle/CENTER_ONLY | 167 | 0.974945 | 0.983924 | 0.003788 |
| screw/LEGACY_PAIRED_AXIS_WITH_POLARITY | 253 | 0.472814 | 0.543428 | 0.015416 |
| screw/B_IDENTITY_PAIRED_AXIS_WITH_POLARITY | 253 | 0.469488 | 0.541021 | 0.015201 |
| screw/AXIS_WITH_POLARITY | 253 | 0.782811 | 0.828356 | 0.011032 |
| hazelnut/LEGACY | 312 | 0.711323 | 0.802903 | 0.010855 |
| hazelnut/B_IDENTITY | 312 | 0.711013 | 0.803614 | 0.010758 |
| hazelnut/CENTER_ONLY | 312 | 0.667944 | 0.755850 | 0.011640 |
| hazelnut/B_IDENTITY_PAIRED_AXIS | 229 | 0.711664 | 0.803106 | 0.010792 |
| hazelnut/AXIS | 229 | 0.624708 | 0.695592 | 0.012460 |
| cable/LEGACY | 179 | 0.722866 | 0.797930 | 0.010891 |
| cable/B_IDENTITY | 179 | 0.721502 | 0.796591 | 0.010909 |
| cable/CENTER_ONLY | 179 | 0.721871 | 0.797844 | 0.010929 |
| grid/LEGACY | 211 | 0.856385 | 0.892235 | 0.009239 |
| grid/B_IDENTITY | 211 | 0.856458 | 0.892394 | 0.009219 |
| hazelnut/CENTER_ONLY paired to AXIS | 229 | 0.660303 | 0.746906 | 0.011760 |

Metal Nut's176 H4 normalized descriptor rows match retained Phase2 B **exactly**,
max error0. Mean cosine.8704721217924394 and median std.008074812092298697
reproduce its retained coherence. No scientific quality campaign was repeated.

Hazelnut's229-image center comparison was additionally derived read-only from its
saved CENTER_ONLY units and AXIS names: same-set center.660303 ->axis.624708.
Thus the axis loss cannot be explained by comparing different populations.
`hazelnut/center-paired-to-axis.json` retains values, input hashes, NumPy2.2.5
version and the exact read-only analysis command. No fresh encoding or model.

## 8. Local dispersion, variance and collapse controls

Sample channel std uses ddof1 with no diagnostic floor. Each row summarizes all
18,816 position/channel coordinates; counts are at the four displayed thresholds.

| Input | n | Std mean | Median | p25 | p75 | p90 | p95 | Counts <=1e-6/1e-5/1e-4/.001 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| metal_nut/B_IDENTITY | 176 | 0.037744 | 0.010970 | 0.005661 | 0.044592 | 0.112226 | 0.169084 | 0/0/0/28 |
| metal_nut/H4 | 176 | 0.022123 | 0.008075 | 0.004057 | 0.029030 | 0.065080 | 0.087220 | 0/0/0/108 |
| bottle/B_IDENTITY | 167 | 0.009696 | 0.003873 | 0.001889 | 0.011501 | 0.028113 | 0.039195 | 0/0/12/1639 |
| bottle/CENTER_ONLY | 167 | 0.009400 | 0.003788 | 0.001788 | 0.011045 | 0.027033 | 0.038154 | 0/0/19/1934 |
| screw/B_IDENTITY_PAIRED_AXIS_WITH_POLARITY | 253 | 0.045557 | 0.015201 | 0.008349 | 0.062101 | 0.136024 | 0.183276 | 0/0/0/3 |
| screw/AXIS_WITH_POLARITY | 253 | 0.029793 | 0.011032 | 0.005825 | 0.043286 | 0.084594 | 0.110360 | 0/0/0/204 |
| hazelnut/B_IDENTITY | 312 | 0.032946 | 0.010758 | 0.005853 | 0.045099 | 0.095288 | 0.131641 | 0/0/0/0 |
| hazelnut/CENTER_ONLY | 312 | 0.035818 | 0.011640 | 0.006342 | 0.049663 | 0.105876 | 0.140946 | 0/0/0/0 |
| hazelnut/B_IDENTITY_PAIRED_AXIS | 229 | 0.032923 | 0.010792 | 0.005860 | 0.045198 | 0.095225 | 0.131991 | 0/0/0/0 |
| hazelnut/AXIS | 229 | 0.038561 | 0.012460 | 0.006681 | 0.054727 | 0.115709 | 0.149957 | 0/0/0/0 |
| cable/B_IDENTITY | 179 | 0.032224 | 0.010909 | 0.006158 | 0.042468 | 0.095368 | 0.125809 | 0/0/0/0 |
| cable/CENTER_ONLY | 179 | 0.032197 | 0.010929 | 0.006153 | 0.042458 | 0.095241 | 0.125803 | 0/0/0/0 |
| grid/LEGACY | 211 | 0.024466 | 0.009239 | 0.004954 | 0.034220 | 0.069972 | 0.085876 | 0/0/0/327 |
| grid/B_IDENTITY | 211 | 0.024458 | 0.009219 | 0.004920 | 0.034173 | 0.069738 | 0.085990 | 0/0/0/320 |

Full min/max and quantiles, raw descriptor-norm distributions, low-std counts
and all196 position records live alongside each cohort's units. Every descriptor
passes the unchanged normalization guard; zero coordinates hit1e-6/1e-5 in
these comparisons. There is no constant collapsed representation. Nonzero
variance alone also does not prove retention of defect information.

Before/after below means B_IDENTITY/candidate on matched names; Grid is
LEGACY/B_IDENTITY as a representation-only control.

| Comparison | Position cosine p25/median/p75, before ->after | Mean dispersion before ->after | Mean position variance before ->after |
|---|---|---|---|
| Metal Nut | 0.315066/0.477296/0.833894 ->0.814161/0.874022/0.939181 | 0.449153 ->0.128792 | 0.004705 ->0.001349 |
| Bottle | 0.963237/0.980364/0.989034 ->0.963312/0.981318/0.991216 | 0.026045 ->0.024905 | 0.000273 ->0.000261 |
| Screw (253 paired) | 0.350419/0.416728/0.584651 ->0.714143/0.799610/0.857993 | 0.528415 ->0.216330 | 0.005526 ->0.002262 |
| Hazelnut center | 0.604855/0.768500/0.841209 ->0.572512/0.717820/0.779172 | 0.288061 ->0.330992 | 0.003010 ->0.003459 |
| Hazelnut axis (229 paired) | 0.612745/0.768474/0.839369 ->0.561216/0.646829/0.713748 | 0.287077 ->0.373653 | 0.003003 ->0.003909 |
| Cable | 0.649928/0.753823/0.843003 ->0.651654/0.755167/0.844148 | 0.276942 ->0.276575 | 0.002901 ->0.002897 |
| Grid control | 0.778617/0.904069/0.908465 ->0.779110/0.904277/0.909089 | 0.142934 ->0.142862 | 0.001496 ->0.001495 |

Cosine mean, mean variance and directional dispersion are mathematically coupled
for unit vectors; these are not three independent validations. Spatial
consistency and contrast are additional diagnostics.

## 9. Spatial consistency and interpretation

LOO uses other FIT images' mean-direction templates, comparing each interior
cell at its own position versus its3x3 neighborhood. Drift is mean best-cell
distance in14-grid cells; same-cell fraction and recoverable cosine gain are
retained. This diagnostic is neither a new scorer nor a learned correspondence
model. Adjacent contrast is mean squared directional distance between neighboring
patches; tensor gradient is mean squared neighboring normalized input difference.

| Comparison | LOO drift before ->after (cells) | Same-cell fraction before ->after | Adjacent feature contrast before ->after | Tensor gradient before ->after |
|---|---|---|---|---|
| Metal Nut | 0.653523 ->0.038165 | 0.445470 ->0.964646 | 0.805225 ->0.768090 | 0.038171 ->0.038392 |
| Bottle | 0.017720 ->0.016653 | 0.983159 ->0.984157 | 0.380701 ->0.380365 | 0.034810 ->0.034870 |
| Screw (253 paired) | 1.001650 ->0.385909 | 0.166914 ->0.637489 | 0.546867 ->0.565104 | 0.005423 ->0.005124 |
| Hazelnut center | 0.614040 ->0.688914 | 0.457688 ->0.401465 | 0.585992 ->0.597416 | 0.006422 ->0.006432 |
| Hazelnut axis (229 paired) | 0.616516 ->0.727190 | 0.454391 ->0.365084 | 0.586617 ->0.619982 | 0.006290 ->0.006321 |
| Cable | 0.369683 ->0.365909 | 0.662826 ->0.668529 | 0.792957 ->0.792341 | 0.087683 ->0.087663 |
| Grid control | 0.931197 ->0.929836 | 0.197604 ->0.197670 | 0.154223 ->0.154238 | 0.083590 ->0.077766 |

Metal Nut's large drift reduction with stable tensor gradient supports registration
rather than blanket smoothing. Screw shows the same qualitative effect and
increased spatial feature contrast, but clipping still blocks qualification.
Bottle/Cable changes are tiny. Hazelnut drift and dispersion both worsen, even
when no exception occurs. Assigning a trusted axis worsens the matched center
cohort further. Grid's filtering-only control changes little and defines no
object pose. Lower std is never treated automatically as improved detection.

## 10. Blind anomaly-side pose reliability

No estimator changed after the screening freeze. All618 object TEST images,
181 normals/437 anomalies, were posed before labels were used to group diagnostics.
There are no new anomaly metrics or encoded TEST features.

| Candidate | Normal OK | Anomaly OK | Centroid outside FIT box normal/anomaly | Area outside FIT range normal/anomaly | Anomaly source-border failures |
|---|---:|---:|---:|---:|---:|
| Metal Nut H4 |22/22|93/93|1/29|1/27|0|
| Bottle center |20/20|61/63|0/10|0/2|2|
| Screw directed axis |41/41|119/119|0/6|0/0|0|
| Hazelnut center |40/40|70/70|0/4|0/6|0|
| Cable center |58/58|92/92|0/10|0/9|0|

Outliers use descriptive FIT extrema, not a universal acceptance threshold.
Counts include every test record, including explicit failures. No true pose
labels exist; these counts are not measured pose errors or false-alarm rates.

H4 strength normal/anomaly medians .145765/.145436, minima.115848/.095411:
all above the unchanged.03 floor. Screw normal/anomaly anisotropy medians
.931474/.932033 (p95.942718/.941121), absolute polarity medians.350886/.356551
(minima.293991/.262027): no weak axes/polarities. Its mass-sign branch is
negative on19/41 normals and65/119 anomalies; differing source orientations mean
this is **not** a measured polarity-error rate. Without ground-truth anomaly
orientation, actual anomaly polarity flips cannot be certified as zero.

Hazelnut axes are weak on12/40 normals and20/70 anomalies; anisotropy medians
.134744/.154264. Cable axes are weak on57/58 and88/92; medians.030166/.030934.
Bottle has zero trusted TEST axes. Complete confidence distributions, defect
groupings, poses, branch signs, framing fractions and outlier records are retained
in `summary-test.json`, `poses-test.json`, `test-outliers.json`.

Disclosed visual inspections after freeze, with no repair:

- `bottle_contamination_007.png` and013: a contaminant reaches the source border;
  strict segmentation rejects both. A normal-only100% success rate hid this.
- `hazelnut_print_006.png`: selected filled area.015673, centroid(307.267,431.942).
  Print008: area.009045, centroid(411.305,290.338). The visible object is much
  larger; bright print fragments dominate the selected region. Both return OK
  under the broad sanity checks. Good anisotropy can describe a defect rather
  than the object. FIT good108 already shows dark-cap/bright-shell anchor bias.
- `cable_cut_outer_insulation_003.png`: broken outer ring gives centroid
  (321.307,443.769) and3.129% canonical mask clipping; filled region no longer
  represents a stable whole cross-section. Other internal/deformation outliers
  remain recorded. FIT good112 already shows an appearance-dependent anchor.
- Screw remains geometrically confident across defects, but39/41 normal and
 100/119 anomaly masks clip in canonical coordinates. Valid-pose clipping is not
  source-border failure and is not omitted from assessment.
- Existing Metal Nut color010 centroid(321.089,346.236), bent000 and other
  deformation/photometry outliers remain. H4 success does not establish perfect
  absolute pose or photometric immunity. The coarse filled175 pixel-center
  clipping diagnostic does not supersede the retained bent011 post-warp border
  finding in the pilot.

## 11. Screening decisions

| Category | Decision | Full M2 refit now? | Reason |
|---|---|---|---|
| metal_nut | **GO, already qualified** | no repeat needed | exact positive control and strong registration benefit; retain known outliers |
| bottle | **MAYBE, center only** | no | axis/head-bottom premise false in this view; tiny normal benefit and two anomaly-side failures |
| screw | **MAYBE, axis with polarity** | no, framing first | strong normal/perturbed/anomaly geometry and material coherence gain; current frame clips tip-side content and rejects four training sources |
| hazelnut | **NO-GO for tested center/axis** | no | both worsen normal coherence/drift; axis ambiguity; print can become the selected foreground |
| cable | **NO-GO for minimal whole-object rigid model** | no | weak global axis, negligible centering gain, internal/deformation sensitivity and broken-ring anchor |
| grid | **NO-GO for whole-object pose; retain NONE** | no | no finite object centroid/polarity; texture invariance is a different problem |

These are screening judgments without a universal numerical score threshold.
NO-GO rejects the tested strategies, not the logical possibility of every future
foreground estimator or a different invariance model. Successful primitive
execution must not be described as reliable semantic pose.

## 12. Reusable conceptual families

- **N_FOLD_SYMMETRY(4):** qualified Metal Nut only.
- **AXIS_WITH_POLARITY:** supported by Screw pose recovery and normal feature
  registration; not yet a qualified detector because framing remains unsafe.
- **CENTER_ONLY:** geometrically computable for Bottle/Hazelnut/Cable, but no
  second material, reliable detector benefit is established; Hazel worsens.
- **AXIS without polarity:** rejected for the examined near-symmetric/cross-section
  cases; a stable180-degree branch is not a consistent visual identity.
- **NONE:** appropriate control for Grid and current fallback recommendation for
  the rejected Hazelnut/Cable experiments, without adding a production fallback.

No productive strategy has two independently qualified categories. No production
enum, registry, estimator framework or generalized detector is warranted.

## 13. What is general, and what remains specific

General today: separating pose estimation from rigid sampling/encoding,
geometry-independent B at its fixed700 contract, prepared frozen CNN extraction,
normalized-feature coherence diagnostics and unchanged strict local M2 as a
downstream architecture. Screw's normal result supports that architectural
hypothesis beyond the fourfold Metal Nut outline.

Still specific/unqualified: foreground model, confidence/failure meaning,
symmetry/polarity, canonical placement/framing, native-resolution adapter and
anomaly-side semantic stability. No scale/illumination/reflection invariance was
added. Translation equivariance of the wrong bright component is not semantic
registration. Even exact pose cannot restore clipped source content. The only
complete canonical Java detector remains Metal-Nut-only. Exact ONNX checkpoint/
export provenance remains unresolved; encoder byte identity is preserved.

## 14. Qualification recommendations and rejected work

**Immediate new full M2 qualifications: none.** Metal Nut already has retained
qualification; do not rerun it. Screw is the first conditional future candidate
after its normal-only framing/source-border contract is resolved.

Do not advance the current Hazelnut/Cable strategies to refitting, or spend the
next phase on their rigid rotations. Do not pursue Bottle axis/head-bottom
canonicalization in this end-on acquisition. Bottle centering remains MAYBE,
low priority until its tiny benefit and explicit failure behavior justify further
work. Do not build object segmentation/centering for Grid; texture invariance
needs a different framing of the problem. These decisions use this audit's
geometry/normal features, not new TEST anomaly scores.

## 15. Exact next experiment

**Screw FIT-only framing feasibility checkpoint**, before any M2 fit:

1. Keep the audited silhouette/axis/polarity rules, encoder and sampler B fixed.
2. Measure projected foreground extrema and tip/head containment for every frozen
   FIT source under the current rigid pose, with the source-border cases retained.
3. Determine whether a fixed declared scale1 canonical placement can retain the
   full normal object within700, or whether the current canvas/scale contract
   makes that impossible. Do not silently resize, clip, drop or score failed
   acquisitions. Freeze any proposed framing/source-failure policy using FIT
   only; CAL can validate the frozen choice.
4. If a safe contract exists, separately authorize/freeze a full Screw M2
   FIT/CAL/TEST qualification with new preprocessing identity and all images
   accounted for. If not, retain MAYBE/NO-GO without fitting an unsafe model.

This report implements none of that checkpoint. Previously inspected TEST poses
are not independent held-out evidence; future anomaly metrics must not tune
framing. The earlier B tensor performance recommendation remains deferred.

## 16. Tests, provenance and recovered tooling discrepancy

**197 Java tests pass**,0 failures/errors/skips, including ten new focused tests.
Legacy M0/VSA/Adjoint/M2, encoder, sampling, persistence and metrics tests remain.
The two pre-existing expensive Bottle experiment classes are excluded by the
standard relevant-suite command; no unrelated campaign runs. No Python source
was changed. Read-only paired statistics use NumPy2.2.5 and saved unit arrays.

Tests cover polarity, weak/undefined axes, ties/border rejection, deterministic
state reuse, unchanged H4 pose, angular symmetries, sample-ddof1/true pair median,
degenerate templates, input geometry, legacy/prepared encoder equivalence and
source-format invariance. An initial hard-edge synthetic test assumed<2-pixel
error for a14-pixel shift despite the4-pixel reduction grid. Its observed2.137
pixel phase error is now explicitly tested; grid-aligned16-pixel translations
retain the tight recovery test. No scientific tolerance was relaxed.

A second, genuine audit-tool issue was caught before features/TEST:
direct grayscale Graphics2D resizing differed from an equivalent getRGB-derived
RGB image. The failing-first regression reported pixel0 expected-16382458,
actual-16711423; the two native logical RGB arrays were identical. Converting to
logical sRGB first fixes both storage and identity-perturbation parity. This is
an experimental adapter correction, not a production preprocessing change.
Screw recovery median changed3.098251->.344183 pixels in the real CLI rerun,
confirming the original error was a conversion confound.

The first geometry-only root
`target/experiments/canonical-generality-audit-20261020` is retained with its
original source/protocol snapshots and outputs, **diagnostic only**.
No feature screening or TEST geometry ran there. Final evidence is exclusively
`target/experiments/canonical-generality-audit-20261020-validated`.
The corrected protocol/code were frozen before that run and never changed
afterward. Original hashes were not rewritten to conceal the correction.

Final root retains162 source/resource hashes and matching full `workspace/src`
copies,2,347 input hashes, exact18 manifests,2,324 source-image identities/hashes,
config/protocol,2,188 training poses,480 controlled transformations/768 pose rows,
screen-plan/start/completion hashes, FIT normalized units/cohort names/coherence
and196 position rows per cohort, representation controls,961 blind TEST pose
rows, grouping/outlier summaries, CPU proof and `classification.json`.
The plan SHA-256 is
`0190609eb60df78e0ed1eccbdbc4ee5ad1521e5259a9f0f9f70d7def79865d23`.

Source/input hashes are rechecked between phases and at completion. No shared
production infrastructure changed, so the old423-case parity/quality campaigns
were not rerun; the current176-image exact descriptor control and unchanged
production hashes anchor this audit. Historical results and artifacts are
untouched. Source, new-file whitespace, Markdown links and diff checks are
validated separately. No debug journal/instrumentation is retained in the
worktree; red/green logs remain intended audit evidence.

## 17. Tooling and reproduction

Seven new focused test-source classes under the evaluation package:

| Class | Responsibility |
|---|---|
| GeneralityGeometry | six-category experimental silhouettes/pose families |
| GeneralityImages | logical-color700 view and controlled source transforms |
| GeneralityCoherence | descriptive normalized FIT statistics |
| GeneralityInputs | original manifests, source/input snapshots and guards |
| GeneralityAudit | ordered geometry/features/blind TEST stages |
| GeneralityDiagnostics | pose/recovery/outlier summaries |
| GeneralityAuditTest | focused behavioral regressions |

Commands require the external dataset and retained baseline/Metal Nut evidence.
Use a new namespace. No `mvn clean`, historical runner source-guard bypass or
artifact overwrite:

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$auditCp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
$auditOut = "target/experiments/canonical-generality-audit-new"
$auditDataset = "C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja"
$auditBaseline = "target/comparison/m0-all15-baseline-20261016"
java -Xmx2g -cp $auditCp io.github.antctrlwin.anomalib4j.evaluation.GeneralityAudit geometry $auditDataset $auditBaseline $auditOut
java -Xmx2g -cp $auditCp io.github.antctrlwin.anomalib4j.evaluation.GeneralityDiagnostics train $auditOut
# Inspect geometry against retained evidence, then copy the same frozen plan for exact reproduction.
Copy-Item target/experiments/canonical-generality-audit-20261020-validated/screen-plan.json "$auditOut/screen-plan.json"
java -Xmx2g -cp $auditCp io.github.antctrlwin.anomalib4j.evaluation.GeneralityAudit features $auditOut
java -Xmx2g -cp $auditCp io.github.antctrlwin.anomalib4j.evaluation.GeneralityAudit anomalies $auditOut
java -Xmx2g -cp $auditCp io.github.antctrlwin.anomalib4j.evaluation.GeneralityDiagnostics test $auditOut
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
git diff --check
```

All candidate weak/failure cohorts and raw normal statistics remain available.
The saved paired-center derivation is read-only; change only its root for a
new output namespace. Final report/classification are screening judgments, not
model-selection metrics. Ignored evidence/dataset must be restored separately
in a fresh clone; repository source, this report and protocol are canonical.
