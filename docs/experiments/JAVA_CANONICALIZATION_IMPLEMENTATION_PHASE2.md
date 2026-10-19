# Java canonicalization Phase 2: Metal Nut qualification

Completed 2026-10-19. **All B/C/D pass the six frozen gates. Select B, fused
bilinear, for the next Metal Nut Java integration checkpoint.**

B preserves the demonstrated canonicalization benefit within the predeclared
margins and has the lowest matched preprocessing cost. C has a small Image AUROC
advantage; D has tighter FIT distributions and smaller signed median rotation
shifts, with higher cost and less favorable primary metrics/tail behavior.
No production detector, model bundle or localization integration was implemented.

## 1. Frozen protocol and provenance

[The Phase 2 protocol](JAVA_CANONICALIZATION_PHASE2_PROTOCOL.md) was written
before candidate extraction/fitting/TEST evaluation and copied/hashed into
`target/experiments/java-canonicalization-phase2-20261019/protocol.md`.
The [architecture review](JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md) and
[Phase 1 checkpoint](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md) establish
the definitions. This task explicitly allowed B to qualify alongside C/D.

Entry HEAD: `679e58f`, clean worktree. Evidence root:
`target/experiments/java-canonicalization-phase2-20261019`.
The retained [Python/two-stage pilot](../benchmark/M2_METAL_NUT_CANONICALIZATION_RESULT_20261019.md)
is reference R. Unaligned M2-P is A. Their exact primary values are loaded from
retained artifacts, not embedded in the runner.

Same source/encoder bytes, manifests, masks, M2 equations/floors, evaluator and
map transport. Every candidate independently extracts and fits its own state:
unit CNN descriptors ->FIT local mean/sample std ->mean squared standardized
residual ->own HELD-OUT positional Z ->native max image score. No VSA/Adjoint,
N1/G, new calibration, hyperparameter tuning or test fitting.

## 2. Frozen candidate definitions

| ID | Phase 1 Java kernel | Sampling |
|---|---|---|
| B | BILINEAR | Four taps, separable linear weights |
| C | BICUBIC | 4x4 Keys/Catmull-Rom,a=-.5; normalized weights; RGB overshoot clipped[0,255] |
| D | WIDENED_TENT | Rotated separable tent widened by3.125; normalized weights |

All use the unchanged Java Metal Nut geometry175 estimator, fixed scale1 and
canvas700. Tensor pixel(u,v) maps through the analytic inverse rigid transform
from`(3.125*(u+.5)-.5,3.125*(v+.5)-.5)`. Unbounded filter centers are retained;
source tap indices are clamped for logical replication. Float NCHW224 is produced
directly with the frozen ImageNet normalization. No intermediate image or new
byte rounding; no sampler/geometry/production source changed during or after
measurement. Candidate strings and metadata are retained with their outputs.

## 3. Coverage and computation

| Role, per candidate | Count | Use |
|---|---:|---|
| FIT normal | 176 | Independent local statistics |
| HELD-OUT CAL normal | 44 | Independent positional calibration |
| TEST normal/anomaly | 22 /93 |115 original-frame evaluations |
| Controlled rotations | 88 |22 normals x{-10,-5,+5,+10} |

All423 inputs per candidate passed source-byte and pose checks. Total fresh
encodings:1,269, in three candidate runs. No image drop, retry or fallback.
Each model's reload/native maps are bit-identical. Three archive hashes are
distinct; each archive is304,323 bytes. No candidate reused another's statistics
or calibration. Archives are experimental legacy-format payloads with adjacent
preprocessing/source metadata, not deployable canonical model bundles.

The existing test-source `CanonicalPilotBridge` transports native maps:
14->canonical700 with the existing bilinear interpolation, then backprojects
using the source-to-canonical matrix into the unchanged original masks.
Image score remains max(native Z). All candidates retain132 regions,
6,602,541 foreground pixels and49,747,459 background pixels.

## 4. Primary quality and absolute deltas

| Method | Image AUROC | Pixel AUROC | AUPRO@.30 |
|---|---:|---:|---:|
| A: unaligned M2-P, retained |0.845552|0.849318|0.624208|
| R: canonical two-stage, retained |0.964321|0.886982|0.648731|
| B: fused bilinear |**0.960899**|**0.886602**|**0.646812**|
| C: fused bicubic |0.962854|0.886632|0.646657|
| D: widened tent |0.959433|0.883459|0.646510|

| Candidate | Image delta vs A /R | Pixel delta vs A /R | AUPRO delta vs A /R |
|---|---:|---:|---:|
| B |+0.115347 /-0.003421|+0.037284 /-0.000380|+0.022604 /-0.001919|
| C |+0.117302 /-0.001466|+0.037314 /-0.000349|+0.022449 /-0.002074|
| D |+0.113881 /-0.004888|+0.034141 /-0.003523|+0.022302 /-0.002221|

All gates use full precision from `metrics-unified.json`; tables round to six
decimals. Each candidate is above A in all three metrics and within.01 of R.
Independent pair-count Image AUROC checks reproduced the Java evaluator's values.

## 5. Per-defect results

The unchanged primary evaluator does not emit defect-specific metrics. The new
test-source bridge uses the same `LocalizationMetrics`, `BottleEvaluation.auroc`,
mask reader and map validation on each defect plus all22 normal images. R's
diagnostics were computed read-only from its retained predictions/maps; no R
encoding, fitting or whole-campaign rerun. Results reside in new JSON files.

| Defect (anomalies /regions) | Method | Image | Pixel | AUPRO |
|---|---|---:|---:|---:|
| bent (25 /35) | R |1.000000|0.986107|0.896603|
| | B |1.000000|0.985723|0.891900|
| | C |1.000000|0.985613|0.887425|
| | D |0.998182|0.986204|0.904589|
| color (22 /30) | R |0.900826|0.965079|0.881457|
| | B |0.900826|0.964847|0.883347|
| | C |0.902893|0.964863|0.884624|
| | D |0.900826|0.963660|0.883216|
| flip (23 /23) | R |1.000000|0.778088|0.290931|
| | B |1.000000|0.776856|0.287480|
| | C |1.000000|0.776663|0.286249|
| | D |1.000000|0.772689|0.277465|
| scratch (23 /44) | R |0.950593|0.945319|0.850201|
| | B |0.936759|0.945176|0.849341|
| | C |0.942688|0.945787|0.851242|
| | D |0.932806|0.942576|0.842469|

These are restricted populations, not a decomposition whose metric averages
equal the primary global metrics. Normals are repeated across diagnostic subsets.
B loses0.013834 scratch Image AUROC versus R; C recovers0.005929 of that versus B.
D gains bent AUPRO but loses0.013467 flip AUPRO and0.017787 scratch Image AUROC
versus R. These differences remain explicit even though the frozen gates concern
the three complete-test metrics, not an additional per-defect threshold.

## 6. Statistical coherence

FIT sample std over all18,816 coordinates, before floor:

| Method | Mean | Median | p25 | p75 | p90 | p95 |
|---|---:|---:|---:|---:|---:|---:|
| R |0.022049|0.008036|0.004042|0.028993|0.064723|0.086921|
| B |0.022123|0.008075|0.004057|0.029030|0.065080|0.087220|
| C |0.022207|0.008117|0.004083|0.029154|0.065412|0.087739|
| D |0.021722|0.007877|0.003943|0.028645|0.064007|0.085659|

Median std ratios to R: B1.004819,C1.010076,D0.980180, all<=1.10.
Every method has zero coordinates<=1e-6/1e-5/1e-4. Counts<=.001:
R109,B108,C101,D121. Minimum stds are R0.000528,B0.000525,C0.000535,D0.000457;
there are no local floor hits. Maximums and complete quantiles are retained.

| Method | Mean /median per-position variance | Min /max per-position variance |
|---|---:|---:|
| R |0.001340 /0.001290|0.000156 /0.003894|
| B |0.001349 /0.001312|0.000156 /0.003973|
| C |0.001357 /0.001322|0.000162 /0.004027|
| D |0.001306 /0.001265|0.000145 /0.003609|

| Method | Pairwise cosine mean /median | Dispersion mean /median | CAL raw std mean /median /p95 |
|---|---:|---:|---:|
| R |0.871387 /0.876198|0.127883 /0.123098|0.419435 /0.392380 /0.684472|
| B |0.870472 /0.874022|0.128792 /0.125262|0.417548 /0.388619 /0.698371|
| C |0.869700 /0.873073|0.129559 /0.126206|0.415713 /0.387051 /0.669129|
| D |0.874618 /0.878517|0.124669 /0.120792|0.428053 /0.399234 /0.692970|

D is tightest in FIT, but its primary quality is lower and CAL spread is not
uniformly smaller. Tightness alone does not establish successful filtering.
All recomputed means/stds/alpha/beta passed comparisons with Java persisted state.
Per-position rows, variance maps, low-std counts and full calibration quantiles
are retained in each candidate's `position-statistics.csv`, `variance-map.npy`
and `coherence.json`; equivalent R diagnostics were written into the new R folder.

## 7. Source-rotation robustness

Use the exact saved source700 challenge PNGs. Geometry is estimated blindly
before the retained pose is used for validation; neither expected angle nor known
perturbation enters `CanonicalPreprocessor.prepare(image,tensor)`.
Delta is each candidate's transformed native max minus its own original native
max. All rows below contain22 paired normals. These separately calibrated Z
units are not physical distances, significance tests or operational false alarms.

| Method | Angle | Median | Mean | p90 | p95 | Max |
|---|---:|---:|---:|---:|---:|---:|
| A | -10 |10.246|12.045|18.751|21.623|27.155|
| A | -5 |5.921|5.196|8.167|9.823|10.677|
| A | +5 |7.945|8.211|14.046|16.373|22.520|
| A | +10 |11.937|11.132|18.248|18.758|20.562|
| R | -10 |0.691|0.900|2.607|2.814|3.421|
| R | -5 |0.217|0.509|2.100|2.506|5.028|
| R | +5 |0.552|0.370|1.416|1.511|2.225|
| R | +10 |0.816|0.606|1.883|2.294|2.573|
| B | -10 |0.687|1.088|2.727|3.046|4.198|
| B | -5 |0.404|0.642|1.754|2.291|2.808|
| B | +5 |0.363|0.242|1.173|2.129|3.299|
| B | +10 |0.438|0.493|1.875|2.517|2.863|
| C | -10 |0.606|1.039|2.610|3.625|3.778|
| C | -5 |0.451|0.720|2.044|2.498|3.297|
| C | +5 |0.364|0.289|1.217|2.359|3.038|
| C | +10 |0.569|0.509|2.304|2.449|2.734|
| D | -10 |0.362|0.675|2.294|3.162|3.574|
| D | -5 |0.094|0.179|1.301|1.396|2.105|
| D | +5 |0.007|-0.008|1.681|1.936|2.741|
| D | +10 |-0.034|0.359|2.162|3.692|4.280|

Average of absolute signed level medians: **A9.012412,R0.568973,B0.472908,
C0.497402,D0.124302 Z**. All candidates pass<=1.0Z.

The small signed median for D does not mean uniformly smaller individual errors.
At+10, median absolute per-image delta is0.995952 for D versus0.528278 for B
and0.920612 for R. D's p95 signed delta3.692239 exceeds R2.294494 by1.397745Z,
triggering the frozen reporting/investigation rule. The two largest D rises are
good_000:+4.280067 and good_017:+3.770409Z; neither crosses a modulo90 branch.
Their R rises are0.885004/2.573490 and B rises1.810504/0.647166Z. Source pose
parity is unchanged. These are retained response outliers, not geometry failures;
filtering, refitted statistics and calibration contributions were not isolated.
No correction, image removal or seventh automatic gate was introduced.

## 8. Geometry reliability

All423 records per candidate have success/OK, zero centroid error and maximum
angle difference1.04805e-13 degrees versus retained Python. All six affine
coefficients meet1e-6. B/C/D geometry records are identical. Each retains exactly
six expected equivalent branch crossings, with zero unexplained changes, source
failures, drops or fallbacks. No geometry was tuned after TEST metrics.

The historical bent_011 post-warp border check remains a framing limitation in
the two-stage pilot; its source pose succeeded and it remains in all115 TEST
evaluations here. This task does not create a new full-resolution canonical
image or reinterpret its failed residual as zero-angle success.

## 9. Matched early preprocessing timing

Three fresh JVMs, first8 frozen FIT images decoded before timing,3s mixed warmup,
9 interleaved rounds/stage/fork,16 operations/batch, rotated seven-stage order.
Output is consumed; workspaces and tensor buffers are reused. Saved189 distinct
batches cover geometry, tensor B/C/D and directly measured total B/C/D.

Geometry includes bulk getRGB and estimator. Tensor-only uses preloaded packed
pixels/precomputed poses and includes sampling/normalization. Total calls the
unchanged preprocessor with fresh geometry and bulk RGB reading. No decode,
disk I/O, CNN, M2, evaluator or localization is included. Totals are not sums.

Host: Ryzen AI9 HX370,12 cores/24 logical, Windows11 Pro/build26200, Balanced,
background-CPU snapshot5%. No affinity/load isolation. The post-run Java
environment capture records Temurin21.0.9+10-LTS/amd64,2GiB heap in commands.
This is a simple matched harness, not JMH or final production latency.

Milliseconds/image below are **statistics of batch averages**,27 batches/stage;
p95 is batch dispersion, not per-image p95.

| Stage | Median | Mean | p95 | Min /max | Mean allocated bytes/op |
|---|---:|---:|---:|---:|---:|
| Geometry, shared |5.356713|5.873622|7.549951|4.793444 /11.111475|96|
| B tensor |2.151081|2.190505|2.635726|1.861331 /2.680894|0|
| C tensor |3.791950|4.201814|7.423446|3.124638 /9.133700|0|
| D tensor |14.535100|15.478039|19.270368|13.071063 /25.405900|0|
| **B total** |**6.729813**|**7.438475**|**10.481804**|5.956338 /12.259806|144|
| C total |8.847606|9.188520|12.027226|7.921844 /13.460913|144|
| D total |20.321069|20.402398|22.499540|17.619431 /23.819744|144|

Per-fork medians(ms/image):

| Fork | Geometry | B tensor /total | C tensor /total | D tensor /total |
|---|---:|---:|---:|---:|
|0|5.629763|1.969275 /6.729813|3.752506 /8.171975|14.387825 /20.060725|
|1|5.119813|2.209256 /6.401863|3.689125 /8.847606|14.603731 /19.496906|
|2|5.089806|2.334650 /6.790175|3.863225 /9.383306|14.400038 /21.564244|

Per-fork mean/p95/ranges are retained in `timing-summary.csv`. B has the lowest
total median in every fork. Allocations are current-thread Java bytes measured
with ThreadMXBean; they exclude setup buffers and are not process RSS/native
memory. The96/144-byte scopes retain small pose/transform allocations.
Geometry and total timings have noticeable dispersion; no component contribution
is inferred by subtracting or adding medians. No Python/Java speedup or complete
detector latency is inferred from the old12.308ms prototype or Bottle JMH.
There is no2ms gate.

## 10. Quality/cost selection

| Candidate | Quality vs R (Image /Pixel /AUPRO) | Rotation aggregate | Total median(ms) |
|---|---|---:|---:|
| **B** |-0.003421 /-0.000380 /-0.001919|0.472908|**6.729813**|
| C |-0.001466 /-0.000349 /-0.002074|0.497402|8.847606|
| D |-0.004888 /-0.003523 /-0.002221|0.124302|20.321069|

B/C meet the predeclared practical near-equivalence margins across primary
quality, aggregate rotation and coherence. C improves Image AUROC by0.001955
and Pixel AUROC by0.000030 over B, including a0.005929 scratch Image advantage.
B has slightly higher AUPRO, lower aggregate rotation delta, slightly tighter
FIT statistics/higher cosine, lower cost and the simpler kernel. Choose B.

D improves coherence and the signed-median rotation statistic, but gives no
primary-quality gain over B and costs20.321ms versus6.730ms; its+10 tail is also
worse. This is an explicit tradeoff, not a claim that D has worse robustness in
every statistic. B is selected after scientific qualification, not merely because
it was the fastest diagnostic floor. There is no need to invoke the fallback.

## 11. Acceptance gates

| Full-precision rule | B | C | D |
|---|---|---|---|
| All primary metrics>=R-.01 | PASS | PASS | PASS |
| All primary metrics>A | PASS | PASS | PASS |
| Median std<=0.008839694282280207 |0.008074812 PASS|0.008117060 PASS|0.007876812 PASS|
| Mean cosine>=0.8513865373339473 |0.870472122 PASS|0.869700326 PASS|0.874618490 PASS|
| Aggregate rotation<=1.0Z |0.472908 PASS|0.497402 PASS|0.124302 PASS|
| Zero new failures/unexplained branches | PASS | PASS | PASS |
| **Qualifies** | **YES** | **YES** | **YES** |

This is qualification on one already-observed Metal Nut split using engineering
margins. It is not a statistical equivalence proof, independent generalization
test, all-category validation or operational false-alarm guarantee.

## 12. Validation and retained recovery

**171 Java tests pass**, including all existing M0/VSA/Adjoint/M2, encoder and
sampler tests plus four new bridge tests. The per-defect diagnostic equals the
unchanged unified evaluator on a synthetic identical-population fixture.
**44 Python tests pass**, with two upstream Typer/Click deprecation warnings.
Gate-boundary tests use full precision, pairing tests reject duplicates/drift,
source guards reject changed bytes, and the CSV regression preserves batch16.
Python rule checks pass; basedpyright remains unavailable as previously recorded.
Final diff/new-file whitespace and documentation links are checked separately.

All scientific work and three timing forks completed once. The initial summary
step failed because Pydantic Literal[16] rejected CSV text"16". A failing test
reproduced it; an integer field constrained to[16,16] fixes parsing and still
rejects17. No scientific or timing body changed or reran. Saved189 timing batches
were summarized by the explicit recovery script.

Original `source-hashes.json` remains unchanged:153/155 captured source entries
still match current files. Two post-measurement changes are the parser field/import
and its regression test. Their exact measured versions are preserved under
`source-at-measurement/`; `postprocessing-source-status.json` records old/current
hashes and recovery-script identity. Every production, extraction, fitting,
scoring, geometry, sampler, evaluator and timing source remains hash-identical
to measurement. All761 run inputs and1,094 retained Phase 1/pilot inputs match.
Do not mistake the documented parser/test hash differences for changed runtime math.

`complete.json` records recovered completion without rerun. Scientific-phase
duration142.1751895s is retained from the original error trace's measured local
variable; exact initial whole-campaign wall duration is unavailable. No duration
is reconstructed by adding medians or silently including repair time.

Evidence includes frozen protocol/source/input hashes, dataset/manifests, all
candidate descriptors/poses/transforms/models/raw/Z/reload maps, original-frame
maps and predictions, primary/per-defect metrics, full coherence and paired
rotation records,189 timing batches, summary tables, source reconciliation,
completion and `selection.json`. Target evidence/dataset are local/ignored.
ONNX byte identity is verified; checkpoint/export provenance remains unresolved.

## 13. Changed files and reproduction

New files:

- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/CanonicalPhase2Bridge.java`
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/CanonicalPhase2BridgeTest.java`
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/CanonicalPhase2Timing.java`
- `tools/canonicalization/phase2.py`, `phase2_inputs.py`, `phase2_models.py`,
  `phase2_statistics.py`, `phase2_results.py`, `phase2_finalize.py`, `test_phase2.py`
- this report and `JAVA_CANONICALIZATION_PHASE2_PROTOCOL.md`

Updated only for the new checkpoint: `docs/PROJECT_STATE.md`,
`docs/EXPERIMENT_INDEX.md`, `docs/DECISION_LOG.md`.
No production Java, Phase 1 source, old report, README, configuration or retained
historical numerical artifact was modified. No commit was made.

Fresh reproduction, requiring retained evidence and external dataset:

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/canonicalization/phase2.py --output target/experiments/java-canonicalization-phase2-new
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools -q --basetemp target/test-tmp/canonical-phase2-new
git diff --check
```

The corrected normal runner now parses its own timing CSV. The one-time
`phase2_finalize.py` recovery was used only for the completed20261019 run and
refuses to overwrite its completion marker. Never rerun that campaign or use
`mvn clean` to prepare reproduction. Logs are `target/canonical-phase2-campaign.log`,
`canonical-phase2-finalize.log`, `canonical-phase2-java-tests.log`,
`canonical-phase2-python-final.log` and the retained red/green CSV-test logs.

## 14. Exact next implementation checkpoint

Implement a **Metal-Nut-only B canonical model bundle and fast detector API**:
validate/persist the full canonical preprocessing identity with M2 state; use
the same Java B path for FIT/CAL/inference; source->canonical tensor->encoder->M2
native max. Retain an owned native map and transform only when requested for
later localization. Preserve legacy loaders/default M2 behavior. No production
localization, optimizations or additional categories are included in this report.
The earlier architecture preference for D was a sampling hypothesis; this
measured qualification selects B for that next checkpoint.
