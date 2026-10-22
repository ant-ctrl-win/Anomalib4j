# Current project state

Updated **2026-10-22** against the approved
[research synthesis](ANOMALIB4J_RESEARCH_SYNTHESIS.md), the high-level scientific and
engineering interpretation. This file is the current snapshot; the
[experiment index](EXPERIMENT_INDEX.md) navigates retained evidence and the
[decision log](DECISION_LOG.md#d32---adopt-the-research-synthesis-and-prepare-a-bounded-v1)
records chronology and supersession. Repository code/evidence are authoritative;
chat history and ignored handoffs are not required.

## Current phase and publication status

**Portfolio presentation freeze. Scientific development is closed for this phase;
no additional experiment is needed or authorized.** The public/default system is
MobileNetV4 Conv Small -> 14x14x96 -> M2. Detailed scientific history remains
retained evidence, not the public landing-page narrative.

[README.md](../README.md) is the primary Italian landing page;
[README.en.md](../README.en.md) is its aligned English equivalent. Both foreground
selected category examples, immediately disclose the all-15 macro results in
Results, and present measured CPU scopes and independent same-hardware references.

The 2026-10-22 freeze audit verified compilation, 262 targeted Java tests and
two pipeline smoke cases in a clean copy of the proposed publication files,
without ignored scientific artifacts. The clone URL works, but published HEAD
`2cb0ef8fb08654d05f405ed0d7adf4fb9866df40` does not yet contain the new
encoder-contract source/resources and pipeline smoke test. **The validated
working tree is ready for portfolio review; publication is pending versioning
and publication of the intentional uncommitted/untracked files.** No commit or
push was authorized or performed.

The final generic Java M2 Small/Large all-15 campaign and matched CPU JMH are
complete; the [technical comparison](benchmark/MOBILENETV4_SMALL_LARGE_FINAL_COMPARISON.md)
retains their evidence. They are not featured in either README. The separate
qualified Metal Nut extension retains its existing scope.

Covariance-aware local statistics remain an UNTESTED, unimplemented historical
hypothesis outside this freeze. No scientific follow-up is scheduled.
Historical M3 denotes VSA dispersion and is not renamed.

## Selected generic detector: strict local M2

The selected Java architecture is **M2-P**, in
`src/main/java/io/github/antctrlwin/anomalib4j/m2/`:

- Frozen MobileNetV4 `mobilenetv4_conv_small.e2400_r224_in1k`, SPATIAL_14:
  float32 NHWC[1,14,14,96], flattened HWC.
- Explicit `EncoderSpec` also supports frozen
  `mobilenetv4_conv_large.e500_r256_in1k`, NHWC[1,14,14,192], through the same
  generic M2 pipeline. Small remains default. The historical metrics below
  describe Small; the completed final comparison separately records Large's
  all-15 quality and matched complete CPU latency.
- Unit local descriptors `u=x/||x||`, rejecting nonfinite values/norm below 1e-6.
  FIT normals supply per-position/channel mean and sample std, floored at 1e-6.
- Strict positional reference, with
  `raw=mean_channels(((u-mu)/effectiveStd)^2)`. Separate HELD-OUT normals supply
  positional raw mean alpha and sample std beta, floored at 1e-6.
- `Z=(raw-alpha)/beta`, unclamped; image score is the maximum of 196 native
  cells. Localization and metrics retain the
  [evaluation conventions](benchmark/LOCALIZATION_CONVENTIONS.md).

**Selected M2 uses no VSA projection/statistics or Adjoint scoring.** The generic
default remains unaligned whole-image RGB bicubic224, /255, ImageNet mean/std,
float32 NCHW (`imagenet-rgb-resize224-bicubic-v1`), without crop/letterboxing.
The encoder also has a prepared-tensor boundary for explicit canonical sampling.
New generic M2 archives are v2 with explicit encoder identity/hash/geometry;
old v1 archives still load as Small only. Canonical Metal Nut remains Small-only.
See the [Java M2 parity/performance report](benchmark/JAVA_M2_RESULT_20261018.md)
for inference state, APIs, checks and measured scopes.

**M0 remains a historically valid exact compiled VSA/Adjoint implementation and
retained all-15 baseline: legacy/reference, not the selected default
architecture.** Its mathematics, implementation and equivalence invariant remain
preserved; it is neither deleted nor invalidated. See [AGENTS](../AGENTS.md) and
the [technical overview](TECHNICAL_OVERVIEW.md) for that architecture.

## Retained all-15 scientific quality

Equal-category macro Image AUROC / Pixel AUROC / normalized AUPRO@0.30:

| Metric | M0 reference | C1 control | V1 control | Selected M2 |
|---|---:|---:|---:|---:|
| Image AUROC | 0.818690 | 0.831351 | 0.830704 | 0.871803 |
| Pixel AUROC | 0.807989 | 0.866853 | 0.866527 | 0.931016 |
| AUPRO@0.30 | 0.575592 | 0.682340 | 0.681510 | 0.805887 |

Evidence: `target/comparison/m0-all15-baseline-20261016` (Java M0) and
`target/ablation/local-models-20261017` (Python C1/exact V1/M2 with shared Java
features/evaluator, consuming retained M0 metrics). These are ignored local
artifacts, not guaranteed clone contents. Definitions are in the
[frozen local-model plan](experiments/LOCAL_MODEL_ABLATION_PLAN.md).

Java M2 first reproduced all three Python metrics on retained Bottle, Metal Nut
and Hazelnut with zero reported deltas. The final 2026-10-22 fresh all-15 Java
campaign now confirms Small macros 0.871803/0.931016/0.805887 and measures Large
macros 0.878013/0.946494/0.837383 (Image/Pixel/AUPRO). Large wins 10/15 Image and
13/15 Pixel/AUPRO categories; Small remains the recommended default for compact
CPU inference. See the final comparison for category regressions, artifact/source
identities, adaptation timing limitations and direct JMH scopes. Robustness and
correspondence studies retain their separate scopes. Macro ordering is not a
significance claim or a claim of uniform category performance.

## Correspondence and robustness

Strict P remains selected. N1/G are experimental robustness/quality tradeoffs,
not promoted paths. D20 supersedes the
[correspondence report](benchmark/M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md)'s
historical N1 recommendation: retain useful strict spatial identity and address
geometric variance upstream when semantic pose is observable and validated.

The [controlled robustness study](benchmark/M2_ROBUSTNESS_RESULT_20261018.md)
covers 467 normals/all 15 categories: broad rotation/scale sensitivity and
category-specific photometric failures, especially Bottle. A displaced normal
feature can meet the wrong positional reference. Alignment does not establish
illumination/reflection immunity; local glare/reflections remain untested.
The [oracle diagnostic](benchmark/M2_ORACLE_CANONICALIZATION_RESULT_20261019.md)
is partly supported, not proof that inverse coordinates restore lost content.

## Canonical production capability: Metal Nut only

Metal Nut is the **sole qualified integrated canonical category**, under
`src/main/java/.../canonical/`: H4 modulo90, fixed scale1/canvas700, qualified
direct sampler **B/fused bilinear**, explicit preprocessing identity and a distinct
validated bundle binding the complete contract plus encoder/state hashes.
Geometry fails closed before CNN, with no unaligned fallback or stale scoring.
`detectScore` returns native max; `inferWithMap` owns native Z/transform/identity
for deferred use. **Production original-frame localization/backprojection is
still deferred**; qualification map transport is not a production API.
See [qualification](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md)
and [integration](experiments/JAVA_CANONICAL_M2_INTEGRATION.md).

Latest matched complete score-only JMH p50/mean/p95:
**4.423680/4.363740/5.816320 ms**, versus generic acquisition comparator
10.272768/10.173606/12.992512 ms. The
[BGR report](experiments/JAVA_CANONICAL_BGR_FAST_PATH.md) records exact parity,
guarded raster acquisition/fallback and executed CPUExecutionProvider nodes.
These are predecoded desktop CPU measurements, excluding decode/I/O/loading and
localization, **not a camera/system throughput benchmark**. Earlier timings keep
their own scopes. The Python pilot's historical 2 ms NO-GO remains evidence;
D21 retired that gate, and D22-D25 establish the subsequent qualified Java path.

## Experimental and rejected scope

Screw is **MIXED / experimental only** in `src/test/java/.../evaluation/`.
Directed axis+polarity and safe fixed scale1 framing are solved within the frozen
support contract. The
[matched identity control](experiments/SCREW_B_IDENTITY_M2_CONTROL.md) supports a
strong pose effect and materially better aggregate quality, but scratch_head
regresses on all three metrics. `POSE_EFFECT_SUPPORTED_BUT_MIXED` is not production
promotion. Historical FIT/CAL256/64 differs from matched usable253/63; three FIT
and one CAL source-border failures remain accounted for. TEST160/160 succeeds.

The [head analysis](experiments/SCREW_HEAD_RESPONSE_ANALYSIS.md) supports nuisance
competition despite usually stronger local scratch response. The
[CAL-only photometric control](experiments/SCREW_CAL_PHOTOMETRIC_NUISANCE.md) is
`PHOTOMETRIC_SENSITIVITY_PARTIAL`, not a complete causal explanation. Its proposed
clipping-isolation factorial remains unexecuted and deferred by D32.

The [generality audit](experiments/CANONICALIZATION_GENERALITY_AUDIT.md) covers
**six categories, not all 15**. Latest status: Metal Nut qualified; Screw mixed;
Bottle low-value center-only; tested Hazelnut/Cable rigid strategies rejected;
Grid has no whole-object canonical pose. No universal pose framework/production
registry is justified. Other nine categories have no pose conclusion from this
audit. VSA multimodal fusion remains an unrelated UNTESTED future direction.

## Reproducibility and qualification boundaries

- SPATIAL_14 ONNX byte SHA-256 is known:
  `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`.
  Exact **checkpoint-to-export provenance remains unresolved**. Implemented
  preprocessing/geometry are established in the
  [encoder contract](architecture/ONNX_MODEL_CONTRACT.md); they are not an
  unresolved implementation blocker.
- Historical measured source identities differ from current HEAD. Frozen
  reports describe their recorded run; numerical parity does not replace source
  identity. Newer manifests/source snapshots/hashes retain their own provenance.
- External dataset and ignored evidence require separate restoration. Dataset
  locations are recorded in campaign manifests; **do not use `mvn clean`** on
  an evidence-bearing workspace. The [index](EXPERIMENT_INDEX.md) distinguishes
  repository reports from local artifacts.
- Metal Nut qualification and Screw evidence use known MVTec splits, not
  independent real acquisition validation. Semantic pose, photometric nuisance,
  operational thresholds and sustained system throughput remain bounded/unproven.
