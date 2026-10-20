# Current project state

Updated **2026-10-20**. Repository code and retained evidence are authoritative;
chat history and ignored handoffs are not needed to resume.

## Selected candidate: M2

The completed all-15 local-model ablation selected **M2, positional diagonal
Mahalanobis in normalized CNN space**. A separate Java M2 path now exists in
`src/main/java/io/github/antctrlwin/anomalib4j/m2/`. M0 remains the unchanged
Java VSA/Adjoint baseline, with its invariant preserved. Historical M1/M2/M3 names
remain unchanged.

Common encoder: frozen MobileNetV4 `mobilenetv4_conv_small.e2400_r224_in1k`,
SPATIAL_14, float32 NHWC 1x14x14x96, HWC flattening. Legacy input uses whole-image RGB
bicubic resize to 224x224, /255, ImageNet mean/std and float32 NCHW.
Preprocessing ID: `imagenet-rgb-resize224-bicubic-v1`. No crop or letterboxing.

M2: finite `u=x/||x||`, rejecting norm below 1e-6. FIT normals provide local
per-position/channel mean and sample std, floored at 1e-6.
`raw=mean_channels(((u-mu)/effectiveStd)^2)`. Separate HELD-OUT normals provide
per-position raw mean and sample std, floored at 1e-6.
`Z=(raw-alpha)/beta`; image score is max over 196 native cells. No clamp.
Interpolation and metrics use the existing [evaluation conventions](benchmark/LOCALIZATION_CONVENTIONS.md).
M2 has no VSA projection/statistics or Adjoint compilation.

Java M2 persists only encoder SHA-256, preprocessing/geometry, local means and
effective stds, calibration means and effective stds. See the
[Java M2 validation/performance report](benchmark/JAVA_M2_RESULT_20261018.md)
for architecture, parity, commands and benchmark status.
M0 architecture remains in [AGENTS.md](../AGENTS.md) and the
[technical overview](TECHNICAL_OVERVIEW.md).

## Current retained evidence

Both campaigns completed locally; artifacts are ignored and absent from a clone:

- `target/comparison/m0-all15-baseline-20261016`: unchanged M0, all 15 categories.
- `target/ablation/local-models-20261017`: C1/exact V1/M2, all 15, shared baseline
  manifests/features/evaluator; M0 metrics consumed from the preceding campaign.

| Macro metric | M0 | C1 | V1 | M2 |
|---|---:|---:|---:|---:|
| Image AUROC | 0.818690 | 0.831351 | 0.830704 | 0.871803 |
| Pixel AUROC | 0.807989 | 0.866853 | 0.866527 | 0.931016 |
| AUPRO@0.30 | 0.575592 | 0.682340 | 0.681510 | 0.805887 |

V1 did not outperform C1 on these macro metrics; this does not assert every
category has that ordering or statistical significance. Definitions remain in
[the frozen plan](experiments/LOCAL_MODEL_ABLATION_PLAN.md); older generations
are retained in [EXPERIMENT_INDEX.md](EXPERIMENT_INDEX.md).

Java M2 regression on retained Bottle, Metal Nut and Hazelnut descriptors
reproduced Python M2's three quality metrics with zero reported differences.
No new all-15 Java M2 anomaly-quality campaign has been launched; the separate
normal-image robustness measurement is described below.

## Reproducibility boundaries and limitations

- Historical `final-comparison-20261003` source is not byte-identical to current
  HEAD. Its measurements remain historical evidence, not today's timing.
- Newer runs retain workspace/source hashes, manifests and outputs. M0 and M2
  scoring are preserved. The shared encoder now also accepts prepared NCHW input;
  its legacy image path and prepared path have bit-identical output tests.
- Dataset path is recorded in campaign manifests. Dataset and ignored evidence
  require separate restoration. **Do not use `mvn clean`** as preparation.
- Exact ONNX checkpoint/export provenance remains unresolved. Byte identity and
  implemented preprocessing are established in the [encoder contract](architecture/ONNX_MODEL_CONTRACT.md).
- M0 and legacy unaligned M2 remain strictly positional: a displaced normal feature can encounter
  the wrong cell's reference. No spatial tolerance, registration, rotation/scale
  handling, illumination augmentation, full covariance or clustering was added
  to those baseline paths.
  The [controlled normal-image robustness run](benchmark/M2_ROBUSTNESS_RESULT_20261018.md)
  now measures translation, rotation, scale, brightness, contrast and gamma on
  467 normal tests across all 15 categories. Rotation/scale sensitivity is broad;
  Bottle has a severe photometric response concentrated in outer-grid scores.
  Reflections and local glare remain untested. A separate Metal Nut alignment
  pilot and Java preprocessing checkpoint are described below; default M2 remains
  unaligned and strictly positional.

## Active phase

Java M2 parity, matched CPU-cost validation, controlled robustness, spatial
correspondence and oracle rotation/scale canonicalization experiments are complete.
**M2-P remains selected.** The user's 2026-10-19 decision supersedes the
[correspondence report](benchmark/M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md)'s
N1 recommendation; N1/G remain experiments, not production changes.

The [oracle result](benchmark/M2_ORACLE_CANONICALIZATION_RESULT_20261019.md)
is **B - PARTIALLY SUPPORTED**: 467 normals/all 15, 4,670 triples, exact identity
and retained-score parity. Shrink/restore scale recovers well; texture rotations
and enlargement round trips retain important errors. Its recommended follow-up
was superseded by the user's Metal Nut pilot and Java-port authorization.

The completed [Metal Nut pilot](benchmark/M2_METAL_NUT_CANONICALIZATION_RESULT_20261019.md)
improved Image/Pixel/AUPRO to0.964321/0.886982/0.648731 after canonicalized fitting
and inference. Its Python prototype measured12.308ms canonicalization and retained
a procedural NO-GO under its frozen2ms gate. That gate is not a current product
requirement; the subsequent [Java architecture review](experiments/JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md)
defines the port independently of it. Historical numerical evidence is unchanged.

[Java Phase 1](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md) is complete:
all423 geometry cases pass frozen Python parity; eight retained canonical
encodings and four native maps reproduce exactly. Scalar B/C/D direct tensor
samplers and a prepared encoder boundary pass correctness/regression tests.
The subsequent [Phase 2 qualification](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md)
independently fitted B/C/D on Metal Nut and evaluated the same115 tests/88 normal
rotation pairs. All six frozen gates pass for all three. **B, fused bilinear, is
selected for the next Metal Nut canonical integration**: Image/Pixel/AUPRO
0.960899/0.886602/0.646812, aggregate rotation delta0.472908Z and measured total
preprocessing median6.729813ms. C/D total medians are8.847606/20.321069ms;
these are early batch-average timings, not complete detector/JMH latency.
The [canonical M2 integration](experiments/JAVA_CANONICAL_M2_INTEGRATION.md) is now
complete for Metal Nut B: a separate versioned bundle binds the full preprocessing
contract and encoder/state hashes; the same Java path fits176/calibrates44 normals
and infers. All115 TEST native maps/scores and primary metrics reproduce Phase 2 B
exactly. `CanonicalM2Detector.detectScore` returns a fast native-max decision;
`inferWithMap` retains an owned native map, transform and identity for later use.
Geometry failure throws before encoding; sequential workspaces never score stale
input. Legacy M2 loading/default behavior and M0 remain unchanged.

Matched two-fork JMH directly measured the canonical fast decision at
10.108928ms p50,10.042803ms mean,12.315034ms p95. Geometry is the largest measured
preprocessing component (4.718592ms p50; tensor B1.404928ms). One setup-only PID
collision required replacing only the missing preprocessing fork; the report
preserves all measurements and source identities. CPU node execution is verified;
180 Java/44 Python tests passed for integration. The subsequent
[measurement-only geometry profile](experiments/JAVA_CANONICAL_GEOMETRY_PROFILE.md)
is complete: unchanged whole geometry p50/mean/p95 5.537792/7.061223/16.363520ms
on this campaign's host conditions. Bulk getRGB dominates (p50 4.243456ms), followed
by fused luminance/BOX reduction and component traversal; JFR corroborates generic
raster/color conversion cost. All423 geometry references and183 Java tests pass,
with production source unchanged at that checkpoint. The subsequent
[BGR acquisition optimization](experiments/JAVA_CANONICAL_BGR_FAST_PATH.md) is now
complete: validated built-in TYPE_3BYTE_BGR rasters use direct acquisition into the
same reusable packed buffer; every other representation uses generic getRGB.
Geometry, sampler B, encoder, M2 and bundle identity are unchanged. All423 inputs
have exact old/new pixel/geometry/tensor/descriptor/map/score parity; all115 TEST
maps/scores/primary metrics reproduce the retained bundle without refitting.
187 Java tests pass. All155 source/resource and1,617 retained input hashes remain
stable through validation/timing; node profiles verify CPU execution.

Direct two-fork JMH fast decision p50/mean/p95 is now
**4.423680/4.363740/5.816320ms**, versus the matched generic comparator
10.272768/10.173606/12.992512ms (56.94% lower p50). Acquisition p50 is0.343040ms
versus4.141056ms; geometry1.146880ms and preprocessing2.359296ms are directly
measured, not summed. Per-fork variation and historical comparison are retained
in the new report; approximately306KB/op downstream allocation remains unchanged
in ownership and deferred. The remaining B tensor measurement recommendation is
deferred while the scientific generality checkpoint below defines the next work.
Production original-frame localization and other-category canonicalization remain
later work. Use the BGR report for current canonical timing, the earlier reports
for their historical contexts and the Java M2 report for legacy unaligned timing;
neither oracle nor Python pilot timing measures this integrated path.
M2 does not inherit historical M0 latency. Public README claims await a separate
update. [DECISION_LOG.md](DECISION_LOG.md) records supersession and history.

The [six-category generality audit](experiments/CANONICALIZATION_GENERALITY_AUDIT.md)
is complete: unchanged Metal Nut H4 is GO/already qualified; Bottle centering and
Screw axis/polarity are MAYBE; tested Hazelnut/Cable registration and whole-object
Grid pose are NO-GO. All 176 Metal Nut FIT descriptors reproduce retained B exactly.
Screw has strong geometry and a material paired cosine gain (.469488->.782811),
but every valid FIT mask clips under the fixed scale1 mass-centered canvas.
Hazelnut centering/axis worsen coherence; its print can become the selected region.
No new M2 fitting, calibration, scoring, anomaly metrics, timing or production
category support was added. Experimental grayscale conversion was corrected before
CNN/TEST screening; both source/evidence versions remain explicitly separated.
197 Java tests pass; the final run retains 162 source/resource and 2,347 input hashes.

The subsequent [Screw framing checkpoint](experiments/SCREW_CANONICAL_FRAMING_FEASIBILITY.md)
is complete: **GO_FOR_M2_QUALIFICATION**, experimental only. One fixed scale1 anchor
`(395.50859765961616,349.2832413193618)` preserves all253 valid FIT/63 valid CAL
silhouette supports, with minimum clearance8.712/7.310 pixels. The3 FIT/1 CAL
source-border failures retain explicit rejection/no-score and coverage accounting.
Matched safe-frame cosine is.784784 versus.782811 current and.469488 identity;
206 Java tests pass. No M2 fitting/scoring or new TEST evaluation occurred.
The [full Screw M2 qualification](experiments/SCREW_CANONICAL_M2_QUALIFICATION.md)
is now complete: **MIXED_RESULT**. Coverage is256/253/3 FIT,64/63/1 CAL and160/160/0
TEST (attempted/usable/failed), with no TEST framing-margin violations. Canonical
Image/Pixel/AUPRO is.663456/.961903/.845029 versus retained.564870/.863996/.602107.
All category metrics improve, but scratch_head worsens on all three, including
AUPRO.784869->.713909. A distinct test-only Screw bundle binds the frozen contract;
169 source/resource hashes remain stable and215 Java tests pass. No production
promotion, TEST tuning or new timing occurred. The subsequent
[matched B_IDENTITY control](experiments/SCREW_B_IDENTITY_M2_CONTROL.md) is complete:
**POSE_EFFECT_SUPPORTED_BUT_MIXED**. Same253/63 usable normals and160 TEST names,
fresh identity statistics/Z, exact identity tensor/retained FIT parity. Identity
Image/Pixel/AUPRO is.564870/.864740/.607138; canonical adds
.098586/.097163/.237891 over that matched control. Pipeline-only net gains are
small, while pose amplifies scratch_head losses (Pixel-.026605, AUPRO-.059242).
All172 source/resource and897 canonical-evidence hashes remain stable;225 Java
tests pass. The unsuffixed control root is a pre-fit JSON-representation diagnostic;
the `-validated` root supplies final evidence. Next recommended work is a separately
authorized FIT/CAL-first head-response failure analysis using frozen models/maps,
without TEST tuning or production promotion.
Metal Nut remains the only integrated canonical Java detector; default unaligned
M2, M0 and historical numerical results are unchanged.
