# Current project state

Reconciled: **2026-10-16**. Source inspected: `b2cbe9e44b219850abd6657a3baa18d802d63f29`.
This is the current snapshot, not an experiment log. Repository code and retained
artifacts establish implementation and evidence; chat history and ignored
handoff/session files are not authoritative. Infrastructure validation uses synthetic/unit tests;
no fresh real-data benchmark has been run.

## Current implementation: M0

**M0 is the current Java detector.** Java 21 / Maven / ONNX Runtime 1.30.0
implement CNN observation -> VSA learning/memory -> compiled CNN-space scoring.

- Frozen MobileNetV4, declared ID `mobilenetv4_conv_small.e2400_r224_in1k`.
  Default `SPATIAL_14`: float32 NHWC `1x14x14x96`, flattened HWC.
- Input: RGB whole-image bicubic resize to 224x224, /255 and ImageNet
  mean/std, float32 NCHW. Contract: `imagenet-rgb-resize224-bicubic-v1`.
  No crop, alignment or letterboxing.
- Fit normals, pass 1: CNN L2 normalization (epsilon 1e-6), dense Rademacher
  projection (D=10000, Java Random seed 42), pooled statistics across images
  and positions (Welford sample variance, standard-deviation floor 1e-8).
- Pass 2: standardize projected descriptors with those global statistics,
  sum separately at each position, L2-normalize each sum. One continuous
  archetype per cell; no explicit within-position dispersion model.
- Compile the fixed score into 96 weights plus bias per cell. Inference
  reuses the ONNX session, normalizes CNN descriptors and evaluates those filters.

The invariant in [AGENTS.md](../AGENTS.md) remains binding:

```text
u = x / max(||x||, 1e-6)
y = R u / sqrt(D)
z = (y - mu) / sigma
explicit = A[p]^T z
q[p] = A[p] / sigma
W[p] = R^T q[p] / sqrt(D)
b[p] = -mu^T q[p]
compiled = W[p]^T u + b[p]
raw[p] = 1 - compiled
```

The scientific benchmark path fits positional raw-score mean/sample std on
held-out normals: `Z[p]=(raw[p]-mean[p])/max(std[p],1e-6)`.
Image score is the maximum of the 196 native Z values. Localization upsamples
Z with half-pixel bilinear interpolation and replicated borders.
Raw/Z scores are unclamped and are not probabilities.

The Java core is in `src/main/java/io/github/antctrlwin/anomalib4j/`.
Positional Z, benchmark persistence/reload, dataset evaluation and full-resolution
map generation are in `src/test/java/.../evaluation/`; JMH is in `src/jmh/`.
Production `HeatmapCalibration` is a separate global visual mapping to [0,1],
not the scientific positional Z. See [technical overview](TECHNICAL_OVERVIEW.md)
and [localization conventions](benchmark/LOCALIZATION_CONVENTIONS.md).

## Retained experiments and coverage

**M2 is a retained Python local-memory experiment, not the current Java
implementation or a selected production v2.** It learns per-position CNN means
and per-channel dispersion on the same frozen features. It has the strongest
retained localization results on the two observed categories, without independent
generalization evidence or M0's Java latency measurements.

Verified comparative evaluation exists only for **Bottle and Metal Nut**:
`final-comparison-20261003`, M0 versus Anomalib 2.6.2 PaDiM/PatchCore.
M0 uses 167+42 fit/calibration normals for Bottle and 176+44 for Metal Nut;
test counts are 83 and 115. Competitors fit all 209/220 normals.
**No full 15-category MVTec AD run has been completed.**

The [experiment index](EXPERIMENT_INDEX.md) separates RAW, in-sample Z,
held-out, split-sensitivity, SPATIAL_28, frozen comparison, performance and
post-hoc evidence. Detailed results remain in the linked reports and retained
local artifacts. M0 benchmark latency is a whole-system comparison with different
backbones/runtime/budgets, not an isolated Adjoint speedup.

## Reproducibility boundaries

- Frozen benchmark source identity is **not byte-identical to current HEAD**.
  The run preserves `workspace/`, `source-hashes.json` and environment records.
  Later package/document relocation and tool changes prevent treating today's
  checkout as the exact frozen source. The [namespace report](development/PACKAGE_NAMESPACE_REFACTOR.md)
  documents the mechanical Java rename; it is not a fresh scientific rerun.
- Models, predictions, maps and derived results are retained under ignored
  `target/comparison/final-comparison-20261003/`; a fresh clone does not contain
  that archive. Do not run `mvn clean` as a preparation shortcut.
- The earlier audit recorded the expected dataset as absent. The resumed
  2026-10-16 infrastructure check found
  `../Anomalib4j_md/mvtec-ad-DatasetNinja` present, with metadata, annotation
  directories and train/test PNGs for all 15 categories. The subsequent
  [campaign preflight](benchmark/M0_ALL15_PREFLIGHT_20261016.md) validated all
  input metadata/geometry and test masks, generated prospective splits, and
  hashed every dataset file. No inference was performed. The prepared competitor
  Python environment has not been revalidated.
- Both checked-in ONNX hashes match the [encoder contract](architecture/ONNX_MODEL_CONTRACT.md).
  **Exact checkpoint/export provenance remains unresolved.** Implemented
  bicubic/ImageNet preprocessing is established; it is not blocked by that
  separate provenance question.
- Historical passing tests and numerical equivalence reports remain evidence
  of their recorded executions, not certification of a new current-HEAD run.
  Default `mvn test` includes expensive real-data tests; `*IT` experiments are
  opt-in. Quick-suite instructions are in the technical overview/reports.

## Robustness limitations

The memory and score calibration are strictly positional: a displaced normal
feature is compared with the destination cell's reference. There is no
neighbor-reference search, registration or training augmentation in M0.
M2 is also positional. CNN receptive fields overlap; displayed cells are not
independent cropped patches.

The [pose diagnostic](diagnostics/METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md)
did not support pose as the principal explanation of the observed Metal Nut
failures. It does not establish robustness under controlled translations or
rotations. The [edge audit](diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md) records
padding/background effects and correct indexing. Illumination/reflection
causality and systematic robustness remain unmeasured; ImageNet normalization
and CNN L2 normalization do not establish invariance.

## Active phase and next task

**Repository/infrastructure preparation for a fresh full-dataset M0 baseline
and controlled spatial/illumination robustness evaluation.**
[D16](DECISION_LOG.md#d16---repository-first-preparation-for-the-new-experimental-phase)
supersedes the previous release's closure as the active workflow, while retaining
its frozen evidence.

The evaluation infrastructure now accepts all 15 categories through one shared
category resource, DatasetNinja metadata validation, actual image geometry and
generated deterministic fit/calibration manifests. The loader accepts structurally
valid calibration sets above 44. Serial fail-fast orchestration, configurable dataset
paths and M0-only execution are available. See the
[execution contract](benchmark/FULL_DATASET_EXECUTION.md) for commands and boundaries.

The [2026-10-16 preflight](benchmark/M0_ALL15_PREFLIGHT_20261016.md) passed dataset,
split-regression, dry-run and estimated resource checks. Proposed campaign:
`m0-all15-baseline-20261016`. The worktree is still dirty: a source snapshot is
retained, but a stable release/archive must be designated before launch. This is
preflight-complete with source freeze pending, not unqualified campaign-ready.

Next task: freeze the reviewed source identity, recheck resources and obtain
approval to execute the unchanged M0 baseline across all 15 categories. No full
campaign or robustness intervention has been executed. Preserve preprocessing,
scoring, compilation equivalence, evaluation conventions and CPU measurement boundaries.

Historical workflow details are preserved in the
[archived pre-reconciliation snapshot](development/archive/PROJECT_STATE_20261007.md);
decisions remain in [DECISION_LOG.md](DECISION_LOG.md). Neither archive nor
roadmap overrides this current snapshot.
