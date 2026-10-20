# Screw canonical M2 qualification

Completed2026-10-20. Entry HEAD `dddc06f`, initially clean worktree.
**MIXED_RESULT:** all three category metrics improve, with large thread/localization
gains, but head scratches worsen on all three metrics and overall Image AUROC
remains.663456. Screw stays experimental. No geometry, framing, scoring, calibration
or evaluation parameter changed after TEST; no production promotion or commit.

## 1. Scientific question

Does Screw's improved normal registration translate into anomaly-quality gains?
Yes at category level, but unevenly across defects. This is the first complete
canonical strict-local M2 quality experiment beyond Metal Nut, on the existing
observed split. It is not independent unseen-category or acquisition validation.
Read the [frozen protocol](SCREW_CANONICAL_M2_PROTOCOL.md),
[framing checkpoint](SCREW_CANONICAL_FRAMING_FEASIBILITY.md),
[generality audit](CANONICALIZATION_GENERALITY_AUDIT.md),
[Metal Nut integration](JAVA_CANONICAL_M2_INTEGRATION.md) and
[separate performance report](JAVA_CANONICAL_BGR_FAST_PATH.md).

## 2. Frozen preprocessing contract

Distinct preprocessing identity:
`screw-logical-srgb1024-bicubic700-otsu175-axis-polarity-scale1-safe-placement-v1/canonical700-to224-bilinear-imagenet-v1`.
The25 exact fields are retained in `preprocessing-contract.json` and the bundle.

- Native1024x1024 Screw sources. Unchanged `GeneralityImages.view`: logical
  default-sRGB conversion first, then Graphics2D bicubic/quality resize to700.
  No crop, letterboxing, augmentation or illumination normalization.
- Unchanged `GeneralityGeometry`: dark Otsu175 with first-maximum semantics,
  first largest row-major8-connected component,4-background hole fill, filled
  centroid, second-moment principal axis, anisotropy>=.50, outer25%-end mass
  polarity with absolute floor.10, heavier end+x and directed[-90,270] branch.
- **Scale1; fixed target(395.50859765961616,349.2832413193618); canvas700.**
  `A(p)=R(-theta)*(p-centroid)+target`. No per-image scale/placement or branch repair.
- Unchanged production B/four-tap direct bilinear, replicated tap indices,
  half-pixel700->224, no intermediate byte image, existing ImageNet normalization,
  float32 NCHW. Same frozen MobileNetV4 SPATIAL_14, HWC14x14x96.
- Unchanged normalized-descriptor strict M2, local sample std floor1e-6,
  held-out positional Z floor1e-6 and unclamped native max196 image score.

The selected framing artifact SHA-256 remains
`e4af7c946a0ac980a12936541e2825b262e8ff6082b52690613a14b214ee6928`.
New preprocessing-contract JSON SHA-256:
`0d9e708a3421145e6645d580d5bd4ac49616860bb761f6440658ea5d8282a88c`.
All original165 source/resource entries remain byte-unchanged; four new test-source
files bring this experiment's frozen source set to169. No production source changes.

## 3. Failure policy

Geometry/source-border/weak-axis/weak-polarity failure occurs before encoder
invocation: **no CNN, score, map or unaligned fallback**. Every attempted identity
and available pose/confidence remain recorded. Failures never receive fabricated
scores or disappear from coverage. Shape/decode/encoder/integrity errors stop the
experiment rather than being silently converted into observations.

The four-pixel full-cell support margin is a framing diagnostic, not a new runtime
rejection rule. Every OK pose is scored even if support would violate it; such a
violation must be reported and considered in qualification. No violation occurred.

If TEST preprocessing had failed, conventional metrics would use only an explicit
usable manifest and be labelled SURVIVOR_ONLY, alongside full attempted coverage
and failure names/classes. Here all160 succeed, so reported metrics cover the
complete frozen TEST population. Synthetic coverage/failure tests exercise the
unscored-failure path without imposing scores on it.

## 4. FIT/CAL coverage

| Role | Attempted | Usable | Failed | Failure identities / reason |
|---|---:|---:|---:|---|
| FIT |256|253|3|good096,158,196; source-border / SEGMENTATION_FAILURE|
| CAL |64|63|1|good179; source-border / SEGMENTATION_FAILURE|
| TEST |160|160|0|none|

Names above abbreviate `screw_good_NNN.png`. Original manifest order is unchanged.
All320 FIT/CAL pose records reproduce the framing checkpoint exactly. All253
freshly encoded usable FIT descriptors match its safe-frame units **bit-exactly**,
maximum error0. FIT/CAL failure identities and reasons were not reinterpreted.
Attempted observations and usable manifests are separate retained files.

## 5. Model and calibration statistics

Only existing `M2Trainer`, `M2Moments` and `M2Model` perform fitting/calibration.
For unit descriptors, FIT local mean/sample std use253 normals; raw discrepancy
is mean96 squared standardized residual. CAL uses63 separate normals:
`alpha=mean(raw)`, `beta=max(sampleStd(raw),1e-6)`, `Z=(raw-alpha)/beta`.
Image score remains the native196-cell maximum, never a resized-map maximum.

| Retained statistic | Minimum | Median | p95 | Maximum |
|---|---:|---:|---:|---:|
| Local sample/effective std |.000396720|.010932814|.109091194|.315329420|
| Alpha |.840647792|.998201300|1.115754925|1.194506274|
| Beta sample/effective std |.218835553|.431071147|.695831820|1.137731442|

No local or calibration std hits the floor in this run; the floor implementation
is unchanged and remains covered by existing M2 tests. Local mean, raw sample
std, effective std, alpha, raw beta sample std and effective beta are lossless
float64 NPY arrays. FIT/CAL normalized units, raw/Z arrays and native image-score
distributions are retained; no new training image or TEST observation enters them.
Independent NumPy2.2.5 reconstruction agrees within3.56e-15 across fitted means,
sample std, CAL raw scores, alpha and beta. CAL per-cell Z sample means differ
from0 by at most2.13e-15 and sample stds from1 by at most4.45e-16.

## 6. Bundle identity

Test-only `ScrewQualificationBundle`, format
`anomalib4j-screw-canonical-m2-qualification/v1`. It is intentionally category
specific; the production Metal Nut format/loader and legacy M2 are unchanged.
No strategy registry or generalized production persistence layer is introduced.

- Sorted25 UTF key/value contract fields, encoder SHA-256, payload SHA-256,
  payload length and big-endian float64 mean18816/std18816/alpha196/beta196.
- Numerical payload **304,192bytes**; complete archive **306,658bytes**.
  No images, masks, poses, training descriptors or CNN bytes inside the archive.
- Bundle SHA-256 / identity:
  `cdf64b691d790213b9608f42b0f5d4d4006ec7b93267e7a14384f3d39843a0d6`.
- Payload SHA-256:
  `36c633f9f26c3e4e7ee8e2dc6d1161db4b8af84737369abf813cb82d0f444e1b`.
- Strict loader rejects every contract-field mismatch, wrong encoder/format,
  duplicate fields, invalid size/hash/state, truncation and trailing bytes.
  Numerical reload is bit-exact. Both Metal Nut and Screw loaders reject the
  other's archive. Encoder bytes remain the existing classpath resource.

This is a qualification representation, not a new production Screw API. It follows
the existing canonical bundle principles without changing Metal Nut semantics.
State/archive sizes are not process memory or a latency measurement.

## 7. Frozen TEST evaluation protocol

Protocol SHA-256:
`5a1444a71bff34f8847c067771256c3dee51c5207ec2c893dc018414ca2c10f0`.
TEST manifest SHA-256:
`3cfc2f129079328aae6ac59d13d4fd967ebfecbdef06fd913c79d9e27ef478c2`.
Contract, protocol, manifests, model and CAL state were frozen before TEST access;
`train-freeze.json` records`2026-10-20T11:15:11.643933900Z` and `test_opened=false`.
Source/input/stage guards enforce ordering and unchanged identities.

The test-only map adapter composes **existing** functions: native14 Z ->
`LocalizationMaps.upsample`700 -> `CanonicalPilotBridge.backproject`700 with
forward A -> `LocalizationMaps.upsample`1024. All interpolation is the existing
half-pixel bilinear/replicated-border convention. The last stage accommodates
Screw's audited1024->700 view; no production localization API, sampler mathematics,
score normalization, smoothing or metric definition is changed. This composition
was fixed before TEST and tested with the old700 path and an independent
original-coordinate ramp. It is not claimed identical to direct unaligned14->1024.

GT is the existing unioned DatasetNinja bitmap at original1024 resolution.
`ComparisonEvaluator` and `LocalizationMetrics` are unchanged: all good background
pixels count, exact tied-score sweep,8-connected regions, normalized AUPRO@.30.
All320 TEST image/annotation hashes also match the original all-15 dataset inventory.
No TEST-derived threshold, parameter choice, exclusion or refit occurred.

## 8. TEST coverage and failures

**160 attempted /160 usable /0 failed:41 normal,119 anomalous.** All native Z and
original1024 evaluated maps, scores, poses, anisotropy, polarity, support bounds,
clearance and source transforms are retained per image. No preprocessing failure
identity exists to list; source-border, segmentation, weak-axis and weak-polarity
failure counts are all0. Framing violations are also0.

| Group | Attempted /usable /failed |
|---|---|
| good |41/41/0|
| manipulated_front |24/24/0|
| scratch_head |24/24/0|
| scratch_neck |25/25/0|
| thread_side |23/23/0|
| thread_top |23/23/0|

Pixel denominator is167,772,160:418,611 foreground and167,353,549 background,
with135 connected GT regions. These counts match the retained noncanonical case
exactly. Detailed coverage records keep attempted denominators separate from the
success-only prediction CSV; here the two TEST populations coincide.

## 9. Image AUROC

Canonical **0.6634556261529002**, retained M2 **0.564869850379176**;
absolute delta **+0.09858577577372418**. This is an improvement, but.663456 remains
weak image-level discrimination. No operating threshold or operational false-alarm
claim is inferred from AUROC. Better normal registration does not ensure that a
defect dominates the native maximum over every cell.

## 10. Pixel AUROC

Canonical **0.9619030577356351**, retained M2 **0.8639957642764617**;
absolute delta **+0.09790729345917337**. The complete original-coordinate GT
population and unchanged evaluator definitions are used. This is a substantial
category-level gain; it does not mean every defect group improves.

## 11. AUPRO

Canonical normalized AUPRO@.30 **0.8450292610702794**, retained M2
**0.6021072737787124**; absolute delta **+0.242921987291567**.
All135 regions retain equal region weight under the existing implementation.
The group metrics below are descriptive slices, not averaged to create this value.

## 12. Retained noncanonical Screw M2 comparison

| Metric | Noncanonical M2 | Canonical M2 | Absolute delta |
|---|---:|---:|---:|
| Image AUROC |.564869850|.663455626|+.098585776|
| Pixel AUROC |.863995764|.961903058|+.097907293|
| AUPRO@.30 |.602107274|.845029261|+.242921987|

Reference:`target/ablation/local-models-20261017/M2/screw`; its numerical artifacts
are read-only. The same160 TEST names and evaluator definitions are used. The
old model used256 FIT/64 CAL, while canonical training has253/63 usable normals.
Input filtering also differs: native bicubic224 versus logical-RGB700 plus B.
Map backprojection/size adaptation is necessary for the new frame. Thus this
comparison tests the whole frozen canonical contract, **not pose alone**.
No matched B_IDENTITY anomaly-model control is fitted in this task.

Historical M0 context only: Image/Pixel/AUPRO
.47017831522853043/.5688373719671034/.2335402339507815, same TEST population.
M0 was not rerun and is not the primary causal comparator.

## 13. Defect-group diagnostics

After primary metrics were frozen, existing `CanonicalPhase2Bridge defects`
evaluated each defect together with the same41 normal images, for both retained
baseline and canonical maps. Exact group GT counts match. No group result changed
the model or preprocessing. Each tuple is Image/Pixel/AUPRO.

| Defect (anomalies) | Retained M2 | Canonical M2 | Absolute deltas |
|---|---|---|---|
| manipulated_front(24) |.626016/.949402/.821327|.691057/.966199/.861803|+.065041/+.016797/+.040476|
| scratch_head(24) |.569106/.940366/.784869|.538618/.909816/.713909|-.030488/-.030550/-.070960|
| scratch_neck(25) |.590244/.875653/.631480|.698537/.976308/.921158|+.108293/+.100655/+.289678|
| thread_side(23) |.510074/.773628/.385998|.660657/.945429/.802041|+.150583/+.171800/+.416043|
| thread_top(23) |.523860/.820572/.471154|.729586/.984186/.938883|+.205726/+.163613/+.467729|

**Head scratches worsen on all three metrics**, including a.070960 AUPRO loss;
their image discrimination remains near chance. This is the important regression
hidden by category aggregates. Conversely thread_top AUPRO rises.471154->.938883,
and thread_side.385998->.802041. The large localization gain is concentrated in
the neck/thread groups, not a uniform improvement. Existing evidence cannot
attribute head degradation specifically to sampling, pose, photometry or changed
training coverage; do not silently choose one explanation.

## 14. Framing and pose behavior on TEST

All160 poses are OK. Minimum anisotropy.920891054 and minimum absolute polarity
.262026612 exceed unchanged.50/.10 floors. There is no measured ground-truth
orientation, so numerical confidence does not prove semantic pose accuracy.

No center or full-cell support clipping; no four-pixel margin violation.
Support clearance min/p05/median/p95 is
**5.799502/12.883931/30.632340/41.164623**pixels at700.
The reduced filled silhouette remains a proxy for actual object support. Native
source acquisition cannot restore content outside the original image. No geometry,
framing, source-border policy or confidence rule changed after seeing these records.
The earlier four training source failures remain failures, not fixed acquisitions.

## 15. Comparison with Metal Nut

Retained qualified Java B Metal Nut, without a new run:

| Category /pose | Noncanonical M2 Image/Pixel/AUPRO | Canonical Image/Pixel/AUPRO | Status |
|---|---|---|---|
| Metal Nut /fourfold H4 |.845552/.849318/.624208|.960899/.886602/.646812|qualified, production integrated|
| Screw /directed axis+polarity |.564870/.863996/.602107|.663456/.961903/.845029|mixed, experimental|

Metal Nut values come from the retained all-15 M2 and qualified Phase2 B artifacts,
not the earlier Python pilot or an inherited timing. Its absolute deltas are
+.115347/+.037284/+.022604. Screw has larger localization gains but weak image
discrimination and a head-scratch regression. Different categories/annotations
prevent ranking their absolute metrics as an architectural proof. No new runtime
measurement, Metal Nut fitting or other-category evaluation is performed.

## 16. Qualification decision

**MIXED_RESULT.** Coverage and frozen framing pass, all category metrics improve,
and four of five defect groups improve on all metrics. However the material
head-scratch regression and weak overall Image AUROC prevent calling this a
generally qualified second detector category. This is a qualitative application
of the predeclared mixed-result criteria, not a new score threshold or post-TEST
parameter gate. Aggregate gains are preserved; the negative subgroup result is
not hidden. Screw is not promoted into production or a generalized registry.
The earlier GO was permission to qualify safe framing, not a promised quality GO.

## 17. Architectural implication

The matched normal feature evidence remains identity cosine.469488 versus safe
canonical.784784; all253 fresh safe descriptors reproduce it exactly. That gain
corresponds to better aggregate anomaly metrics here, especially thread/neck
localization, but not uniformly better detection. Two geometrically distinct
families now have positive aggregate evidence; **two fully qualified categories
have not been established**. Metal Nut remains qualified, Screw remains mixed.
This supports further controlled investigation of semantic pose before strict
local M2, without proving pose-only causation, universal generality, photometric
robustness or a reason to generalize the production architecture yet.

## 18. Tests and provenance

Evidence root:`target/experiments/screw-canonical-m2-qualification-20261020`.
Source/input/output guards remain unchanged through all phases:169 source/resource
entries with full copies,338 initial input hashes,320 TEST image/annotation hashes,
and649 retained baseline-case file hashes. The original all-15 dataset inventory
SHA-256 is`fe8c4a0f11320dbfb63b08e9ff07bb631f4eec1a61c89e0ad5a425a82693f71b`;
all320 TEST entries match it exactly. FIT/CAL manifests retain hashes
`6a51242ae9f1760126c4ae7dfcab802f687a24f77926f9739c3987c4884b5160` and
`4108d760cc7d39fea55c4c0848a6bf24b89b42b1bd91cf1eb26eb533e1aac3a6`.
Stage hashes bind protocol/contract before training, model/CAL before TEST,
maps before metrics and primary metrics before defect interpretation.

- **51 focused tests pass**, including9 new qualification tests and existing
  generality/framing, M2/bundle/evaluator regressions.
- **215 relevant full Java tests pass**,0 failures/errors/skips. The standard
  command excludes existing expensive Bottle training/evaluation experiment
  classes; no unrelated campaign is launched.
- New tests cover exact/foreign bundle identity, every field mismatch, invalid
  encoder/hash/size/state, duplicate fields, truncation/trailing bytes, unchanged
  pose/B tensor equivalence, failure without fallback/stale tensor, map coordinate
  composition, complete failure coverage and phase ordering.
- FIT descriptor parity and bundle numerical reload have maximum error0.
  Independent NumPy checks pass as recorded in section5; no Python source changes.
- `git diff --check`, added-file whitespace, local links and frozen source/input/
  output guards pass. Existing production, benchmark implementation, configuration,
  metrics, sampler, encoder, M0/M2 and historical result files are unchanged.

Four new test-source classes: `ScrewQualificationInput`, `ScrewQualificationBundle`,
`ScrewM2Qualification` and `ScrewM2QualificationTest`. No public production API is
added. The qualification bundle, raw/fitted/CAL arrays, per-image states/maps, metrics,
defect summaries, comparison, exact phase commands/JDK/classpaths and source copies
are retained. Dataset and ignored target evidence require separate restoration in
a clone; exact ONNX checkpoint/export provenance remains unresolved.

Reproduce only into a new namespace with restored references; never `mvn clean`:

```powershell
mvn -B -Pbenchmark "-Dtest=ScrewM2QualificationTest,ScrewFramingTest,GeneralityAuditTest,M2ModelTest,CanonicalM2BundleTest,CanonicalPhase2BridgeTest,LocalizationMapsTest,LocalizationMetricsTest" test dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
$dataset = "C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja"
$framing = "target/experiments/screw-framing-feasibility-20261020"
$baseline = "target/ablation/local-models-20261017"
$out = "target/experiments/screw-canonical-m2-qualification-new"
java -Xmx8g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewM2Qualification prepare $dataset $framing $baseline $out
java -Xmx8g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewM2Qualification train $out
java -Xmx8g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewM2Qualification test $out
java -Xmx8g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewM2Qualification evaluate $out
java -Xmx8g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewM2Qualification diagnose $out
java -Xmx8g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.ScrewM2Qualification verify $out
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
git diff --check
```

Actual logs:`target/screw-m2-qualification-{focused,prepare,train,test,evaluate,
diagnose,java-tests,independent}.log`; retained copies accompany the run. No JMH
or latency benchmark was executed. No commit, reset, dataset change or overwrite
of preceding evidence occurred.

## 19. Exact next step

Separately preregister a **matched B_IDENTITY M2 control qualification** using the
same253/63 usable normals, fixed logical-RGB1024->700 view, unchanged B/encoder/M2,
the same160 TEST names and the same evaluation composition. Change only the
declared control transform to identity; retain the current safe-frame result
unchanged. This separates registration from input-filtering/training-coverage
differences and helps diagnose the head-scratch tradeoff. TEST remains final
evaluation/descriptive evidence, not a source of parameter tuning or independent
confirmation. Do not promote Screw or generalize production before resolving
that control question. This next experiment is not run or implemented here.
