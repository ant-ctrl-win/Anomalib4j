# Screw canonical framing feasibility

Completed 2026-10-20, entry HEAD `b9a843b`, initially clean worktree.
**GO_FOR_M2_QUALIFICATION** for a separately authorized Screw experiment:
scale1 with one global anchor `(395.50859765961616,349.2832413193618)` preserves
the segmented foreground in all253 valid FIT and63 valid CAL cases. No M2 was
fitted, calibrated or scored. This is experimental framing evidence, not a
production detector or an anomaly-quality result.

## 1. Question

Can one fixed framing preserve the normal Screw silhouette without changing its
audited axis/polarity or sacrificing resolution? Yes: the mass centroid is biased
toward the head, and moving its canonical anchor rightward fixes the tip clipping.
No image-dependent zoom or placement is needed. Read the
[pre-measurement protocol](SCREW_FRAMING_PROTOCOL.md),
[generality audit](CANONICALIZATION_GENERALITY_AUDIT.md),
[unchanged B contract](JAVA_CANONICAL_M2_INTEGRATION.md) and
[current Metal Nut performance evidence](JAVA_CANONICAL_BGR_FAST_PATH.md).

## 2. Frozen Screw geometry

`GeneralityGeometry` is byte-unchanged from the validated audit. Dark foreground,
first-maximum Otsu175, first largest row-major8-component,4-background hole fill,
filled centroid, second-moment major axis, anisotropy floor.50, absolute end-mass
polarity floor.10, heavier end toward+x, directed[-90,270] branch and all failure
rules are unchanged. Every freshly computed FIT/CAL pose record equals its
retained audit record exactly, including the original clipping fraction.

`GeneralityImages.view` remains unchanged: logical sRGB acquisition before
Graphics2D bicubic/quality1024->700. The source is grayscale; the audit's corrected
logical-color conversion is essential and reused. The frozen B sampler and CNN
are called directly for the selected scale1 placement. No new raster, scaled
kernel, production dimension, estimator threshold or polarity selector is added.

Reduced mask centers are `(4*x+1.5,4*y+1.5)` in700 coordinates. We retain the audit's
center-outside counts and additionally contain each full4x4 source-cell footprint.
After rotation its axis-aligned half-extent is
`r=2*(abs(cos(angle))+abs(sin(angle)))`. Containment is conservatively inside
`[0,699]^2`, with a fixed4-pixel margin chosen in the protocol before measurement.
It is stricter than merely containing pixel centers. These are segmentation-proxy
support bounds, not ground-truth proof that every physical object pixel is included.

## 3. FIT population and source-border distinction

Exact retained seed42 order:256 FIT,64 CAL, no new split. FIT has253 OK,
3 SOURCE_BORDER/SEGMENTATION_FAILURE,0 other failures. The three sources are
`screw_good_096.png`,158 and196. All256 retain names, states and poses in
`fit-population-extents.json`; the253 trusted cases receive framing measurements.
Source-border cases are not evidence against the canonical placement, and are
never treated as successfully preserved objects.

Frozen experimental failure policy: **reject incomplete/source-border acquisition
before CNN, return no score and no unaligned fallback; retain failure and coverage
counts.** Missing source content cannot be reconstructed. Future training must
account for256/64 attempted inputs and explicitly distinguish253/63 usable inputs
from failures; a detector evaluation cannot silently drop failures or describe
survivor-only metrics as complete-dataset validation. This policy is explicit,
not an unresolved fallback choice or a new production feature.

## 4. Scale1 extent analysis

All extents below are **before clipping**. The full support envelope relative to
the mass centroid is:

| Bound | Value,700 pixels | Worst FIT source |
|---|---:|---|
| minX | -386.7964182655983 | good159 |
| maxX | 294.7792229463659 | good150 |
| minY | -115.78072108411473 | good037 |
| maxY | 116.21423844539106 | good134 |

Its union bounding span is681.575641x231.994960; these extrema can come from
different images. With the original349.5 anchor, support spans
`[-37.296418,644.279223] x [233.719279,465.714238]`.
The corresponding center-only envelope is
`[-34.483288,641.584558] x [236.285190,462.888403]`.

Current framing clips253/253 valid FIT. Center-outside counts min/median/p95/max
are2/10/32/39 reduced cells. Fraction median/p95/max is
.002530364/.007643310/.009663033; worst source good159.
**All2,875 outside centers are tip-side; head-side count is0.** Thus small area
does not imply harmless loss. Full-cell support crossing fraction median/p95/max
is.003286771/.008933778/.010901883. Support clearance minimum is-37.296418pixels.
Per-image center/support min/max, width/height, four border distances, counts,
fractions and tip/head split are retained in `fit-A_CURRENT.json` and population
records. Counts are reduced cells, not native1024 pixels or a rasterized700 mask.

## 5. Placement-only feasibility

For each axis, let `[m,M]` be the FIT support union relative to the centroid.
At fixed scale`s` and safety margin`h`, the allowed global target interval is
`[h-s*m,699-h-s*M]`. Intersecting every FIT image is exactly the same union-bound
calculation; no image has its own placement.

At scale1, zero-margin target intervals are
x`[386.7964182655983,404.2207770536341]`,
y`[115.78072108411473,582.7857615546089]`.
At the frozen4-pixel margin they are
x`[390.7964182655983,400.2207770536341]`,
y`[119.78072108411473,578.7857615546089]`.
Both intersect. Their midpoint fixes the target at
`(395.50859765961616,349.2832413193618)`, a global offset
`(+46.00859765961616,-0.2167586806382)` from the original anchor.
The realized minimum FIT clearance is8.712179pixels.

## 6. Scale-only feasibility

Keeping349.5 on both axes, the largest scale<=1 is
`min(1,(349.5-h)/max(-minX,maxX,-minY,maxY))`.
Zero-margin bound: **.9035761022999229**.
With4-pixel margin: **.8932347449059324**, governed by good159's tip.
A single practical downward-rounded candidate,**.89**, is also evaluated.
Both contain every trusted FIT support; their minimum clearances are4.000000
and5.251188pixels. No scale sweep, CAL selection or scaled CNN extraction occurs.
These are geometry-only alternatives to the preferable scale1 placement.

## 7. Scale plus placement feasibility

With a free fixed target, the largest scale<=1 is
`min(1,(699-2*h)/(maxX-minX),(699-2*h)/(maxY-minY))`.
It is **1.0**. Family D therefore equals family C, rather than providing another
scientific candidate. Midpoint placement is deterministic. No secondary compactness
objective, adaptive zoom or per-image bounding-box alignment is used.

## 8. Minimum scale1 canvas diagnostic

A mass-centered **symmetric** square needs side
`ceil(2*max(abs(FIT support extrema))+1)` = **775**, or**783** with the4-pixel
margin. Its target becomes the enlarged canvas midpoint; no such canvas is sampled.

If "current placement" literally means retaining target`(349.5,349.5)` and origin0
while only extending right/bottom, **no finite larger side fixes negative x**.
The775 diagnostic means symmetric expansion/re-centering, not a claim that an
unchanged literal anchor fits775. The fixed700 canvas already fits at scale1 with
the placement correction above. Production B stays700->224.

## 9. Content-preservation metrics

All rows use the same253 valid non-source-border FIT names. Occupancy is full
reduced-cell area`16*filledCells*s^2/700^2`, including filled holes; it is not the
axis-aligned bbox area. Quantiles use linear interpolation on sorted observations.

| Contract | Center/support clipping cases | Max outside cells | Outside fraction p50/p95/max | Support clearance min/p05 | Occupancy p05/median/p95 |
|---|---:|---:|---|---|---|
| A current1,349.5 |253/253|39|.002530/.007643/.009663|-37.296/-29.751|.112516/.123396/.136170|
| B centered.8932347449 |0/0|0|0/0/0|4.000/10.740|.089773/.098454/.108646|
| B rounded.89 |0/0|0|0/0/0|5.251/11.966|.089124/.097742/.107860|
| C placement1 |0/0|0|0/0/0|8.712/12.456|.112516/.123396/.136170|
| D scale+placement1 (=C) |0/0|0|0/0/0|8.712/12.456|.112516/.123396/.136170|

All safe alternatives also have zero full-support outside counts/fractions and
zero tip/head outside centers. Clipping counts are per image; the maximum column
is a per-image reduced-cell count. Complete distributions and individual rows
exist for every contract, including duplicated D, in `fit-summaries.json` and
`fit-<contract>.json`.

Support bbox distributions at700, shown as min/p05/median/p95/max:

| Contract | Width | Height |
|---|---|---|
| A/C/D scale1 |621.024/623.124/627.692/668.163/677.169|193.073/195.353/198.279/200.476/204.790|
| B centered safe |554.720/556.596/560.676/596.826/604.871|172.459/174.496/177.110/179.072/182.925|
| B rounded.89 |552.711/554.580/558.646/594.665/602.680|171.835/173.864/176.469/178.424/182.263|

## 10. Effective-resolution cost

Approximate tensor support sizes multiply700-coordinate extents by224/700.
They describe geometric size, not detector quality or guarantees about CNN detail.

| Safe contract | Relative scale | Median long/narrow at700 | Approximate long/narrow at224 | Reduction across each dimension |
|---|---:|---|---|---:|
| C/D placement |1|627.692/198.279|200.861/63.449|0%|
| B centered safe |.8932347449|560.676/177.110|179.416/56.675|10.6765%|
| B rounded |.89|558.646/176.469|178.767/56.470|11%|

The chosen placement introduces no additional scale loss. It still uses the audit's
existing1024->700 source conversion and700->224 B sampling. Translation changes
which tensor/CNN cells see the object, so unchanged scale alone cannot imply
unchanged features or detection. No detection impact is inferred from these sizes.

## 11. FIT feature-coherence sanity check

Exactly one final viable candidate, C, was encoded. Matched253 names are identical
and in the retained order for all three rows. The current canonical control was
freshly re-encoded on all253, with **bit-exact descriptors, maximum error0**.
B_IDENTITY/current statistics and units are retained audit inputs with hashes.
Only normalized CNN descriptors and descriptive statistics are computed; no local
M2 model or positional calibration exists in this checkpoint.

| Input | Pooled same-position cosine mean/median | Channel std median | Mean dispersion | Mean position variance | LOO same-cell /drift |
|---|---|---:|---:|---:|---|
| B_IDENTITY |.469488238/.541020793|.015200533|.528414877|.005526164|.166913702/1.001650480|
| Current canonical |.782811395/.828356141|.011031817|.216330152|.002262381|.637489021/.385909407|
| Safe placement |.784784379/.830231805|.010932814|.214364967|.002241829|.618247694/.407708870|

Cosine median pools all6,248,088 unordered same-position pairs; it is not the
median of196 position means. Std is sample ddof1 without a diagnostic floor.
The unchanged LOO diagnostic uses other FIT images' mean-direction templates,
interior12x12 cells and3x3 neighbors. Drift units are14-grid cells. Safe placement
retains95.91% of the current same-cell gain over identity and96.46% of its drift
reduction, while cosine/variance are slightly better. The modest LOO loss is
disclosed, not optimized away. Adjacent contrast is.559431 versus.565104 current;
no collapse is indicated (minimum channel std.000396720, none at<=1e-4).
Coupled cosine/dispersion/variance statistics are not independent proofs.
These normal-only results retain the registration benefit, not anomaly sensitivity.

## 12. Frozen selected contract

`A(p)=R(-auditedAngle)*(p-auditedCentroid)+(395.50859765961616,349.2832413193618)`.
Global isotropic scale1; canvas700; unchanged B NCHW224/ImageNet; unchanged
MobileNetV4 SPATIAL_14. The four-pixel containment margin is a qualification
criterion, not a new confidence test, runtime crop or geometry rejection rule.

`selected-contract.json` SHA-256:
`e4af7c946a0ac980a12936541e2825b262e8ff6082b52690613a14b214ee6928`.
Frozen at`2026-10-20T10:51:10.442172600Z`, before new CAL pixel access. It binds the
pose/source-view descriptions, exact framing, encoder hash, source-border policy,
FIT envelope hash and feature-coherence hash. Full unchanged estimator/sampler
source copies and hashes additionally bind the executable contract. This is an
experimental artifact, **not** an invented production preprocessing/bundle identity.
No contract, envelope, safety margin or source rule changed after CAL.

## 13. CAL validation

All64 CAL images accounted:63 OK,1 SOURCE_BORDER (`screw_good_179.png`),0 other
failures. All retained pose records reproduce exactly. Among63 valid cases:
zero center/support clipping, max/median/p95 clipped fraction0, zero tip/head loss.
Conservative clearance min/p05/median is**7.310381/11.910131/30.676414**pixels,
still above the fixed4-pixel requirement. Occupancy p05/median/p95 is
.112604/.122612/.137322.

**One empirical FIT-envelope exceedance:** good074's head-side relative support
maxX is296.18102120469177 versus FIT294.7792229463659, a1.401798pixel extension.
Its canonical maxX691.689619 leaves7.310381pixels. Thus CAL exceeds the observed
FIT extrema but does **not** violate the frozen canvas/margin contract. No refit,
margin adjustment or post-CAL target change is made. Full64 rows and distributions
live in `cal-population-extents.json` and `cal-summary.json`. CAL receives no CNN
extraction, learned statistics, scores or anomaly metrics.

## 14. Decision

**GO_FOR_M2_QUALIFICATION**, experimental only. A single fixed rigid placement
preserves all trusted FIT/CAL mask supports, passes the predeclared margin, costs
no extra scale resolution and retains most of the audited normal-registration
gain. The four source-border failures remain explicit fail-closed acquisitions.
No TEST pixels, masks, encodings, new pose measurements or anomaly-derived
parameter choices were used. Previous TEST poses are not used to claim additional
validation here. The CAL envelope exceedance and limits of the reduced-mask proxy
remain visible; this does not guarantee future acquisitions or anomalous silhouettes.

## 15. Exact next experiment

Separately authorize/freeze a **Screw canonical M2 qualification**: assign a
distinct preprocessing identity for this exact source/pose/scale1/placement/B
contract; fit unchanged M2 on253 usable FIT normals and calibrate on63 usable
CAL normals, with all256/64 attempted acquisitions and the3/1 failures recorded.
Compare against retained legacy Screw M2 on the exact160 TEST names using the
existing evaluator and conventions; account for every attempted test and any
geometry/framing failure, with no silent survivor-only claim. Freeze evaluation
and failure reporting before scoring. Do not tune this framing on TEST or use
the existing blind TEST pose records as independent evidence. A future unsafe
case must be reported, not repaired inside that qualification. This checkpoint
authorizes no such fit/run, production Screw port, registry or localization.

## 16. Tests and provenance

Dedicated ignored evidence root:
`target/experiments/screw-framing-feasibility-20261020`.
All165 source/resource hashes and full source copies remain stable; the162 audit
source entries match unchanged, plus three new test-source files. Initial269
input hashes bind256 FIT sources, retained manifests/metadata/poses, paired units/
coherence and protocol. CAL adds its64 image hashes only after freeze. Staged
FIT/features/freeze/CAL evidence hashes guard outputs between phases; retained
numerical artifacts are read-only. Dataset/ignored evidence require separate
restoration in a clone. Exact ONNX export/checkpoint provenance remains unresolved.

- Focused `ScrewFramingTest`:9 pass. Together with existing generality tests:
  **19 pass**,0 failures/errors/skips.
- Relevant full Java suite: **206 pass**,0 failures/errors/skips. Existing expensive
  Bottle training/evaluation classes are excluded by the standard suite command;
  no unrelated scientific/timing campaign runs. Production source is unchanged.
- Tests cover rotated full-cell bounds, outside side accounting, analytic global
  intersection/infeasibility, scale optima, unchanged B/identity placement parity,
  source-failure transform rejection, center/support distinction, quantiles,
  stage ordering and tamper detection. Generality's ten regressions also pass.
- Fresh253 current-control descriptors match retained bytes exactly. A separate
  untimed ORT profile has25 provider-tagged executed-node events, allCPUExecutionProvider.
- Independent NumPy2.2.5 read-only checks: max unit-norm error1.33e-15; errors in
  cosine mean/median std/mean dispersion/mean variance <=1.12e-16/1.74e-18/
  2.78e-17/4.34e-19. No Python source changes or Python detector fitting.
- Source/input/stage verification, added-file whitespace, local Markdown links
  and `git diff --check` pass. No benchmark, saved historical result, configuration,
  production M0/M2, sampler, encoder or bundle file changed; no commit.

Exact manifest SHA-256:
FIT`6a51242ae9f1760126c4ae7dfcab802f687a24f77926f9739c3987c4884b5160`;
CAL`4108d760cc7d39fea55c4c0848a6bf24b89b42b1bd91cf1eb26eb533e1aac3a6`.
Protocol SHA-256`c12cc56f53bb9e4dcab05343d6293995ab595c951607f53255466b0226a51d38`.

Reproduction uses restored retained audit evidence and dataset, a new output
namespace, and no`mvn clean`. Stop after FIT if geometry does not support rigid
placement: the tool intentionally refuses scaled CNN sampling rather than
altering qualified B. Review FIT coherence before freeze; only then open CAL.

```powershell
mvn -B -Pbenchmark "-Dtest=ScrewFramingTest,GeneralityAuditTest" test dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
$audit = "target/experiments/canonical-generality-audit-20261020-validated"
$out = "target/experiments/screw-framing-feasibility-new"
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewFramingCheckpoint fit $audit $out
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewFramingCheckpoint features $out
# Inspect the saved FIT geometry and coherence, then freeze before CAL.
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewFramingCheckpoint freeze $out
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewFramingCheckpoint cal $out
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewFramingCheckpoint verify $out
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
git diff --check
```

Each actual phase command/classpath/JDK/UTC is retained in`command-*.json`.
Logs: `target/screw-framing-{focused,fit,features,cal,java-tests}.log`.
All output writes are CREATE_NEW; existing evidence cannot be regenerated in place.
