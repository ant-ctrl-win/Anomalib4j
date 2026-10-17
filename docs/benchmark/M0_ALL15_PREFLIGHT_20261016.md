# M0 all-15 campaign preflight - 2026-10-16

Status: **data/resource preflight passed; source release and campaign approval pending**.
No training, inference, prediction evaluation, PaDiM or PatchCore was executed.
No new scientific result is recorded. Existing M0 and metric code was not changed.

## Source and environment

HEAD: `b2cbe9e44b219850abd6657a3baa18d802d63f29`. Worktree is dirty.
HEAD alone does not identify the proposed detector/evaluation source tree.
Do not claim exact source reproducibility while this worktree is dirty.

Evidence namespace: `target/preflight/m0-all15-baseline-20261016/`.
It contains prospective manifests, census, complete dataset hashes, dry-run plan,
environment capture, helper sources, and a final workspace snapshot. These are
ignored local evidence; preserve them outside the checkout before cleaning.
The workspace snapshot includes a binary Git diff, all tracked/nonignored untracked
file bytes in `source-tree.zip`, per-file SHA-256, expanded dirty status and
dependency JAR hashes. It documents this dirty state; it is not a clean release.
No commit was made. Before launch, commit the reviewed infrastructure and canonical
documentation (including all new files), or explicitly designate and preserve an
immutable source archive. Rebuild from that chosen source and capture its identity
again at campaign initialization. Do not reuse an initialized run after editing source.

Java: Temurin 21.0.9+10; Maven 3.9.14; Python 3.13.5
(`C:/Users/Ion/anaconda3/python.exe`); ONNX Runtime Java 1.30.0.
Resolved comparison classpath also contains Jackson 2.21.7 (annotations 2.21),
JUnit 5.11.4 and JMH 1.37; exact JAR paths and hashes are retained.
M0 orchestration uses Python standard-library helpers. The retained Anomalib 2.6.2
configuration is historical reference; no competitor interpreter/model was invoked.

Complete modified/untracked inventory at final documentation preparation:
```text
M AGENTS.md
 M README.en.md
 M README.md
 M docs/DECISION_LOG.md
 M docs/PROJECT_STATE.md
 M docs/architecture/ONNX_MODEL_CONTRACT.md
 M src/jmh/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonBenchmark.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/BottleEvaluation.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonEvaluator.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonEvaluatorTest.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonModel.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonModelTest.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonRunner.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/EvaluationLabels.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/EvaluationLabelsTest.java
 M tools/comparison/common.py
 M tools/comparison/native.py
 M tools/comparison/results.py
 M tools/comparison/run.py
 M tools/comparison/timing.py
 M tools/comparison/worker.py
?? docs/EXPERIMENT_INDEX.md
?? docs/benchmark/FULL_DATASET_EXECUTION.md
?? docs/development/archive/PROJECT_STATE_20261007.md
?? src/test/java/io/github/antctrlwin/anomalib4j/evaluation/DatasetFixture.java
?? src/test/java/io/github/antctrlwin/anomalib4j/evaluation/DatasetInfrastructureTest.java
?? src/test/java/io/github/antctrlwin/anomalib4j/evaluation/DatasetManifests.java
?? src/test/java/io/github/antctrlwin/anomalib4j/evaluation/DatasetMetadata.java
?? src/test/java/io/github/antctrlwin/anomalib4j/evaluation/MvtecCategories.java
?? src/test/resources/mvtec-categories.txt
?? tools/comparison/preparation.py
?? tools/comparison/test_campaign.py
?? docs/benchmark/M0_ALL15_PREFLIGHT_20261016.md
```
Paths are repository-relative. The snapshot's `workspace/git-status-all.txt`
is the machine-readable inventory; no staged changes or deletions were requested.

## ONNX byte identity

- Active M0: `mobilenetv4_spatial_14x14.onnx`
  SHA-256 `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`.
- Retained alternative, not selected: `mobilenetv4_spatial_28x28.onnx`
  SHA-256 `23ee797ff10396f5eab8b4f00bec3520a573246448262f3c07c0a88f44e9aaf3`.

Both assets are under `src/main/resources/models/`.
Exact upstream checkpoint/export provenance remains unresolved. Hashing identifies
the available bytes and does not resolve that upstream provenance gap.

## Dataset integrity and census

Dataset: `C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja`.
This export is flat: `train/img`, `train/ann`, `test/img`, `test/ann`, and
`meta.json`; physical per-category directories are not part of its contract.
All 15 logical categories are present.

The existing Java DatasetMetadata/DatasetManifests path validated every input's
filename, category/good tags, defect class identity, annotation dimensions and
image header dimensions. Every test mask was decoded with DatasetNinjaMasks;
anomalies had nonempty foreground and normals had empty masks. No duplicate split
inputs were accepted. This is not a full RGB-pixel decode or an inference test.

| Category | Train normal | Fit | Calibration | Test good | Test anomaly | Original geometry | Anomaly defect types (counts) |
|---|---:|---:|---:|---:|---:|---|---|
| bottle | 209 | 167 | 42 | 20 | 63 | 900x900 | broken_large (20), broken_small (22), contamination (21) |
| cable | 224 | 179 | 45 | 58 | 92 | 1024x1024 | bent_wire (13), cable_swap (12), combined (11), cut_inner_insulation (14), cut_outer_insulation (10), missing_cable (12), missing_wire (10), poke_insulation (10) |
| capsule | 219 | 175 | 44 | 23 | 109 | 1000x1000 | crack (23), faulty_imprint (22), poke (21), scratch (23), squeeze (20) |
| carpet | 280 | 224 | 56 | 28 | 89 | 1024x1024 | color (19), cut (17), hole (17), metal_contamination (17), thread (19) |
| grid | 264 | 211 | 53 | 21 | 57 | 1024x1024 | bent (12), broken (12), glue (11), metal_contamination (11), thread (11) |
| hazelnut | 391 | 312 | 79 | 40 | 70 | 1024x1024 | crack (18), cut (17), hole (18), print (17) |
| leather | 245 | 196 | 49 | 32 | 92 | 1024x1024 | color (19), cut (19), fold (17), glue (19), poke (18) |
| metal_nut | 220 | 176 | 44 | 22 | 93 | 700x700 | bent (25), color (22), flip (23), scratch (23) |
| pill | 267 | 213 | 54 | 26 | 141 | 800x800 | color (25), combined (17), contamination (21), crack (26), faulty_imprint (19), pill_type (9), scratch (24) |
| screw | 320 | 256 | 64 | 41 | 119 | 1024x1024 | manipulated_front (24), scratch_head (24), scratch_neck (25), thread_side (23), thread_top (23) |
| tile | 230 | 184 | 46 | 33 | 84 | 840x840 | crack (17), glue_strip (18), gray_stroke (16), oil (18), rough (15) |
| toothbrush | 60 | 48 | 12 | 12 | 30 | 1024x1024 | defective (30) |
| transistor | 213 | 170 | 43 | 60 | 40 | 1024x1024 | bent_lead (10), cut_lead (10), damaged_case (10), misplaced (10) |
| wood | 247 | 197 | 50 | 19 | 60 | 1024x1024 | color (8), combined (11), hole (10), liquid (10), scratch (21) |
| zipper | 240 | 192 | 48 | 32 | 119 | 1024x1024 | broken_teeth (19), combined (16), fabric_border (17), fabric_interior (16), rough (17), split_teeth (18), squeezed_teeth (16) |

Totals: 3629 training normals, 2900 fit,
729 calibration, 467 test good,
1258 test anomalous.
All normals, anomalies and masks remain untouched.

## Dataset identity and prospective splits

Full SHA-256 hashing was practical: 10712 files,
5275017778 bytes (about 5.28 GB), including images, annotations,
metadata and dataset documentation. Capture plus hashing took about
97 seconds.
`dataset-sha256.json` records every relative path, size and hash.
Its SHA-256 is `90519b7dcba4b611d9478dca640b4b03d7fb79abee41c8b704d93f0eb166927f`.
`manifest-sha256.json` identifies generated manifests and per-image census data.
Keep these inventories and verify inputs against them at campaign launch; the
existing campaign initializer separately hashes all selected images/annotations
and meta.json and checks those hashes before each phase.

Split policy: lexical filename order, Java Collections.shuffle with Random(42),
fit `floor(4*N/5)` clamped to `[1,N-2]`; calibration is the remainder.
Projection seed stays 42. Prospective manifests are in
`target/preflight/m0-all15-baseline-20261016/data/<category>/`.
They were generated by the existing Java implementation, not a Python replica.
Bottle and Metal Nut fit, calibration, competitor-fit and test ordered entries
match all eight historical manifests exactly. Raw file hashes are separately
retained in `manifest-regression.json`; line-ending differences are not silently
treated as byte identity. Historical manifests were not overwritten.

The prospective manifests are not an initialized comparison run. The eventual
`run` command initializes a fresh namespace and regenerates deterministic
manifests; compare them with this preflight evidence. Avoid calling `init`
on the final namespace and then `run`: `run` requires an absent directory.

## Dry-run and launch

The actual all-category `--dry-run` completed successfully: 15 M0 cases exactly
once, 75 serial phases (fit, infer, evaluate, latency, memory), no competitors.
Order: bottle, cable, capsule, carpet, grid, hazelnut, leather, metal_nut, pill, screw, tile, toothbrush, transistor, wood, zipper.
The final output directory did not exist and was not created.

Proposed run ID: **`m0-all15-baseline-20261016`**.
Artifacts will be under `target/comparison/m0-all15-baseline-20261016/`:
run manifest, workspace and source/data hashes, `data/<category>/`,
`anomalib4j/<category>/`, `memory-sessions/`, campaign status log and summaries.
The frozen `final-comparison-20261003` is a different namespace.

After source freezing and campaign approval, from the repository root:
```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$env:ANOMALIB_EVALUATION_HEAP = "4g"
python tools/comparison/run.py run --run-id m0-all15-baseline-20261016 --output-root target/comparison --dataset "C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja" --all-categories --method anomalib4j --seed 42
```
Append `--dry-run` to the last command to repeat the nonexecuting plan check.
This report does not authorize or record execution of the launch command.

## Resource preflight

Windows 11 Pro 10.0.26200. At capture: 248.94 GiB disk free;
63.12 GiB physical RAM visible,
36.19 GiB available.
No evaluator-heap environment override was set; default is 4g.
Use the explicit setting above for the campaign.

Largest metric case is Screw: 167,772,160 pixels; the existing double-score and
integer-region arrays require 1.875 GiB, plus per-image buffers, component
bookkeeping and JVM overhead. A 4 GiB evaluator heap has plausible headroom;
this is a static estimate, not a successful metric execution.
All full-resolution float64 maps total about 12.00 GiB. Allow at least 20 GiB
for maps, two learning-state copies per category, native maps, logs and evidence.
Current capacity is sufficient by this estimate; recheck immediately before launch.
Detector/JMH heap remains 2g where already configured. Neither heap limit nor this
offline estimate is a measured M0 inference-memory claim.

## Validation, blockers and next action

Maven benchmark-profile compilation/classpath preparation passed. Existing Java
metadata, manifest and mask helpers successfully checked the real dataset.
No implementation changed in this task, so the preceding 106 Java and nine Python
test results were not rerun. `git diff --check` is the final documentation check.

Remaining gate: choose a stable source release/archive and retain evidence, then
obtain campaign approval. Preserve the unresolved upstream ONNX provenance
limitation in the resulting report. No dataset/schema/resource blocker was found.
The active state remains preflight-complete with source freeze pending, not an
unqualified campaign-ready or experimentally validated state.

