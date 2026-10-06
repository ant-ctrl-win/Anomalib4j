# Anomalib4j Repository Cleanup Result

Status: **EXECUTED (working tree only, not committed)**.

This document is the execution record of the plan in
[REPOSITORY_CLEANUP_PLAN.md](development/archive/REPOSITORY_CLEANUP_PLAN.md). It reports exactly
what was done in the approved cleanup pass. Nothing was committed; the whole
change set lives in the working tree only.

Base revision at the time of the pass:

- HEAD: `83c4197f8771e5ec21db1bcd53508e0d00a4add6` (`test: compare spatial14 and spatial28 bottle baselines`)
- Branch: `main`
- Tags present: `sprint7-bottle-baseline`, `sprint8-bottle-localization`, `sprint9-spatial28-comparison`.
  Tags cover committed history only and **do not** protect untracked files.

No `git commit`, `git add`, `git reset` or `git tag` was run. The only Git
mutation commands used were `git mv` (for tracked files) and a single
`git rm PROJECT_STATE.md` (to remove the corrupt superseded root file, still
recoverable from history).

---

## 1. Safety state

Tracked, already modified before this pass (left as-is):

- `pom.xml`
- `src/main/java/org/vaimee/vsa/anomalib/onnx/ImageNetPreprocessor.java`
- `src/test/java/org/vaimee/vsa/anomalib/evaluation/BottleEvaluation.java`
- `src/test/java/org/vaimee/vsa/anomalib/evaluation/LocalizationMaps.java`
- `src/test/java/org/vaimee/vsa/anomalib/evaluation/Spatial28Evaluation.java` (Javadoc path only)

All four heatmap sources were verified to exist before copying, with expected
sizes, and `docs/PROJECT_STATE.md` was verified complete (25,306 bytes, valid
opening and closing prose) before the corrupt root `PROJECT_STATE.md` was
removed. No untracked file was deleted; local-only files were only ignored, not
touched.

---

## 2. Final repository tree (relevant part)

```
Anomalib4j/
|-- .gitignore
|-- AGENTS.md                      (repaired; kept in root)
|-- README.it.md                   (kept in root)
|-- pom.xml
|-- CHAT_HANDOFF.md                (local-only, gitignored)
|-- session-ses_f13e.md            (local-only, gitignored)
|-- docs/
|   |-- PROJECT_STATE.md           (canonical hub)
|   |-- DECISION_LOG.md            (canonical hub)
|   |-- REPOSITORY_CLEANUP_RESULT.md (this file)
|   |-- architecture/
|   |   |-- FUTURE_ARCHITECTURE_ROADMAP.md
|   |   `-- ONNX_MODEL_CONTRACT.md
|   |-- benchmark/
|   |   |-- BENCHMARK_CONTRACT.md
|   |   |-- BENCHMARK_CONTRACT_RECONCILIATION.md
|   |   |-- BENCHMARK_FINAL_RESULT.md
|   |   |-- BENCHMARK_PHASE1_AUDIT.md
|   |   |-- BENCHMARK_PHASE2_COMPLETION_AUDIT.md
|   |   |-- BENCHMARK_PHASE2_IMPLEMENTATION.md
|   |   |-- BENCHMARK_PHASE2_PREPARATION.md
|   |   |-- BENCHMARK_PRERUN_VERIFICATION.md
|   |   |-- HELDOUT_CALIBRATION_RESULT.md
|   |   |-- HELDOUT_CALIBRATION_VALIDATION.md
|   |   |-- HELDOUT_MULTI_SEED_RESULT.md
|   |   |-- LOCALIZATION_CONVENTIONS.md
|   |   |-- MVTEC_DATASET_CENSUS.md
|   |   |-- POSITIONAL_CALIBRATION_RESULT.md
|   |   |-- POSITIONAL_CALIBRATION_VALIDATION.md
|   |   `-- benchmark-prerun/        (evidence: json/txt/csv + map-format fixtures)
|   |-- diagnostics/
|   |   |-- BORDER_EXCLUSION_SENSITIVITY.md
|   |   |-- BOTTLE_SYMMETRIC_DIAGNOSTIC.md
|   |   |-- METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md
|   |   |-- METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md
|   |   |-- PATCH_GRID_OVERLAY_REVIEW.md
|   |   |-- SPATIAL_EDGE_ARTIFACT_AUDIT.md
|   |   `-- SPATIAL14_VS_SPATIAL28_PER_DEFECT.md
|   |-- development/
|   |   |-- PERFORMANCE_BASELINE_RESULT.md
|   |   |-- SPRINT7_RESULT.md
|   |   |-- SPRINT8_RESULT.md
|   |   `-- archive/
|   |       |-- ENCODER_MICROPROFILE_RESULT.md
|   |       |-- ENCODER_MICROPROFILE_VALIDATION.md
|   |       |-- PERFORMANCE_BENCHMARK_VALIDATION.md
|   |       |-- POST_BENCHMARK_STRATEGY.md
|   |       |-- PREPROCESSING_MICROPROFILE_RESULT.md
|   |       |-- PREPROCESSING_MICROPROFILE_VALIDATION.md
|   |       |-- PREPROCESSING_OPTIMIZATION_RESULT.md
|   |       |-- PREPROCESSING_OPTIMIZATION_VALIDATION.md
|   |       |-- REPOSITORY_CLEANUP_PLAN.md
|   |       |-- SPATIAL28_VALIDATION.md
|   |       |-- SPRINT6_RESULT.md
|   |       |-- SPRINT7_STATUS.md
|   |       |-- SPRINT9_RESULT.md
|   |       `-- TASK.md
|   `-- images/
|       |-- bottle-good.png
|       |-- bottle-broken-large.png
|       |-- bottle-broken-small.png
|       `-- metal-nut-good-activation.png
|-- src/ (main, test, jmh)
`-- tools/ (comparison, prerun)
```

Root now contains only: `.gitignore`, `AGENTS.md`, `pom.xml`, `README.it.md`
plus the two local-only, gitignored scratch files.

---

## 3. Files moved

### 3.1 Tracked files moved with `git mv`

| From (root) | To |
|---|---|
| `ONNX_MODEL_CONTRACT.md` | `docs/architecture/ONNX_MODEL_CONTRACT.md` |
| `LOCALIZATION_CONVENTIONS.md` | `docs/benchmark/LOCALIZATION_CONVENTIONS.md` |
| `MVTEC_DATASET_CENSUS.md` | `docs/benchmark/MVTEC_DATASET_CENSUS.md` |
| `SPRINT7_RESULT.md` | `docs/development/SPRINT7_RESULT.md` |
| `SPRINT8_RESULT.md` | `docs/development/SPRINT8_RESULT.md` |
| `SPRINT6_RESULT.md` | `docs/development/archive/SPRINT6_RESULT.md` |
| `SPRINT9_RESULT.md` | `docs/development/archive/SPRINT9_RESULT.md` |
| `SPATIAL28_VALIDATION.md` | `docs/development/archive/SPATIAL28_VALIDATION.md` |
| `SPRINT7_STATUS.md` | `docs/development/archive/SPRINT7_STATUS.md` |
| `TASK.md` | `docs/development/archive/TASK.md` |

### 3.2 Untracked files moved with filesystem moves

| From (root) | To |
|---|---|
| `ENCODER_MICROPROFILE_RESULT.md` | `docs/development/archive/` |
| `ENCODER_MICROPROFILE_VALIDATION.md` | `docs/development/archive/` |
| `PERFORMANCE_BENCHMARK_VALIDATION.md` | `docs/development/archive/` |
| `POST_BENCHMARK_STRATEGY.md` | `docs/development/archive/` |
| `PREPROCESSING_MICROPROFILE_RESULT.md` | `docs/development/archive/` |
| `PREPROCESSING_MICROPROFILE_VALIDATION.md` | `docs/development/archive/` |
| `PREPROCESSING_OPTIMIZATION_RESULT.md` | `docs/development/archive/` |
| `PREPROCESSING_OPTIMIZATION_VALIDATION.md` | `docs/development/archive/` |
| `PERFORMANCE_BASELINE_RESULT.md` | `docs/development/` |
| `HELDOUT_CALIBRATION_RESULT.md` | `docs/benchmark/` |
| `HELDOUT_CALIBRATION_VALIDATION.md` | `docs/benchmark/` |
| `HELDOUT_MULTI_SEED_RESULT.md` | `docs/benchmark/` |
| `POSITIONAL_CALIBRATION_RESULT.md` | `docs/benchmark/` |
| `POSITIONAL_CALIBRATION_VALIDATION.md` | `docs/benchmark/` |
| `PATCH_GRID_OVERLAY_REVIEW.md` | `docs/diagnostics/` |
| `SPATIAL14_VS_SPATIAL28_PER_DEFECT.md` | `docs/diagnostics/` |

### 3.3 Existing `docs/` reorganization

| From (`docs/`) | To |
|---|---|
| `FUTURE_ARCHITECTURE_ROADMAP.md` | `docs/architecture/` |
| `BENCHMARK_CONTRACT.md` | `docs/benchmark/` |
| `BENCHMARK_CONTRACT_RECONCILIATION.md` | `docs/benchmark/` |
| `BENCHMARK_FINAL_RESULT.md` | `docs/benchmark/` |
| `BENCHMARK_PHASE1_AUDIT.md` | `docs/benchmark/` |
| `BENCHMARK_PHASE2_COMPLETION_AUDIT.md` | `docs/benchmark/` |
| `BENCHMARK_PHASE2_IMPLEMENTATION.md` | `docs/benchmark/` |
| `BENCHMARK_PHASE2_PREPARATION.md` | `docs/benchmark/` |
| `BENCHMARK_PRERUN_VERIFICATION.md` | `docs/benchmark/` |
| `BORDER_EXCLUSION_SENSITIVITY.md` | `docs/diagnostics/` |
| `BOTTLE_SYMMETRIC_DIAGNOSTIC.md` | `docs/diagnostics/` |
| `METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md` | `docs/diagnostics/` |
| `METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md` | `docs/diagnostics/` |
| `SPATIAL_EDGE_ARTIFACT_AUDIT.md` | `docs/diagnostics/` |
| `benchmark-prerun/` | `docs/benchmark/benchmark-prerun/` |

Hubs left in place: `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`.

### 3.4 Root files kept

`README.it.md`, `AGENTS.md`, `pom.xml`, `.gitignore`.

### 3.5 README heatmaps

Created `docs/images/` and **copied** (source files untouched):

| Source (under gitignored `target/`) | Destination | Bytes |
|---|---|---|
| `target/bottle-spatial14-heldout-calibration/bottle_good_000.png` | `docs/images/bottle-good.png` | 1,975,894 |
| `target/bottle-spatial14-heldout-calibration/bottle_broken_large_000.png` | `docs/images/bottle-broken-large.png` | 2,170,397 |
| `target/bottle-spatial14-heldout-calibration/bottle_broken_small_000.png` | `docs/images/bottle-broken-small.png` | 2,017,729 |
| `target/comparison/final-comparison-20261003/diagnostics/metal_nut_good_visuals/high_01_metal_nut_good_009.png` | `docs/images/metal-nut-good-activation.png` | 1,090,712 |

All four destinations exist and are non-empty.

### 3.6 Corrupt root documentation

- `PROJECT_STATE.md` (root): corrupt PowerShell-wrapped fragment, superseded by
  `docs/PROJECT_STATE.md`. Removed with `git rm` after confirming the canonical
  copy was present and complete. Recoverable from history if ever needed.
- `AGENTS.md`: repaired in place (kept in root). See section 6.

---

## 4. Files archived

Moved under `docs/development/archive/` as historical material that is not
canonical or current, while preserving it (no deletions):

- Sprint snapshots: `SPRINT6_RESULT.md`, `SPRINT9_RESULT.md`, `SPRINT7_STATUS.md`, `TASK.md`, `SPATIAL28_VALIDATION.md`.
- Benchmark/performance validation and microprofile/optimization reports: `ENCODER_MICROPROFILE_RESULT.md`, `ENCODER_MICROPROFILE_VALIDATION.md`, `PREPROCESSING_MICROPROFILE_RESULT.md`, `PREPROCESSING_MICROPROFILE_VALIDATION.md`, `PREPROCESSING_OPTIMIZATION_RESULT.md`, `PREPROCESSING_OPTIMIZATION_VALIDATION.md`, `PERFORMANCE_BENCHMARK_VALIDATION.md`.
- Strategy: `POST_BENCHMARK_STRATEGY.md`.

Kept directly under `docs/development/` because they are either referenced by
`README.it.md` or are canonical evidence needed to understand a public result:

- `SPRINT7_RESULT.md` - canonical real-image Adjoint equivalence evidence.
- `SPRINT8_RESULT.md` - SPATIAL_14 localization baseline.
- `PERFORMANCE_BASELINE_RESULT.md` - measured performance baseline.

No historical report was deleted.

---

## 5. Files local-only

| File | Handling |
|---|---|
| `CHAT_HANDOFF.md` | Kept in root, added to `.gitignore`, removed from public Markdown links. |
| `session-ses_f13e.md` | Kept in root, already matched by `session-*.md`. |

Public Markdown links to `CHAT_HANDOFF.md` that would have 404'd were replaced
with prose ("handoff locale (non pubblicato)" / local unpublished handoff) in
`docs/PROJECT_STATE.md` (2 links) and `docs/DECISION_LOG.md` (1 link).

---

## 6. AGENTS.md repair and publication concerns

Repair performed (wrapper only):

- Removed accidental first line `@'`.
- Removed accidental final line `'@ | Set-Content -Encoding utf8 AGENTS.md`.
- The file now starts at `# Anomalib4j - Agent Contract` and ends at
  `Read only the documents needed for the current task.`

Canonical supporting documents list updated to the post-migration paths:

- `docs/PROJECT_STATE.md`
- `docs/development/archive/TASK.md`
- `docs/architecture/ONNX_MODEL_CONTRACT.md`
- `docs/benchmark/MVTEC_DATASET_CENSUS.md`
- `docs/development/SPRINT7_RESULT.md`

Publication audit (requested separately):

- Local absolute paths: **none**.
- Private workflow notes: **none**.
- ChatGPT / Codex / OpenCode transfer instructions: **none**.
- `AGENTS.md` is safe to publish as-is from a privacy standpoint.

Remaining semantic concerns (flagged for owner decision, not silently changed):

- `TASK.md` is listed as a canonical supporting document, but it is now archived
  and is described as obsolete by `docs/PROJECT_STATE.md` and
  `docs/BENCHMARK_CONTRACT_RECONCILIATION.md`. Its path was updated but its
  semantic status was not altered.
- `docs/PROJECT_STATE.md` explicitly supersedes the former root
  `PROJECT_STATE.md`; the canonical reference now correctly points at
  `docs/PROJECT_STATE.md`.

Publication concerns **outside** `AGENTS.md`:

- `docs/PROJECT_STATE.md` line 3 contained a machine-specific absolute checkout
  path; it has been generalized to a repository-relative description.
- `docs/development/archive/REPOSITORY_CLEANUP_PLAN.md` intentionally retains
  pre-migration paths and references a local temp directory; it is an internal
  audit record.
- No `README.md` (English) and no `LICENSE` exist yet; both were deliberately
  not created in this pass.

---

## 7. Link and reference fixes

Broken-link scan result: **145 links checked, 0 broken** in the final state
(script `check_links.py`). Remaining stale strings (`docs/benchmark-prerun`,
`docs/BENCHMARK_*`) exist **only** in `docs/development/archive/REPOSITORY_CLEANUP_PLAN.md`, which
deliberately documents the pre-migration layout.

Fix activity:

- `relink.py` rewrote 65 Markdown links across the reorganized docs.
- `replace_paths.py` rewrote 42 qualified `docs/...` references across docs,
  tools and Java sources (notably `tools/comparison/common.py` and
  `tools/prerun/{border_exclusion_sensitivity,bottle_symmetric_diagnostic,map_io,offline_env,resize_equivalence,workspace_identity}.py`).
- `fix_project_state.py` corrected 32 links in `docs/PROJECT_STATE.md`.
- Manual fixes:
  - 3 `CHAT_HANDOFF.md` links replaced with prose (see section 5).
  - `docs/architecture/FUTURE_ARCHITECTURE_ROADMAP.md` non-`.md` link repointed
    to `../benchmark/benchmark-prerun/configurations.json`.
  - `docs/benchmark/BENCHMARK_PRERUN_VERIFICATION.md`: `](../tools/prerun/` ->
    `](../../tools/prerun/`.
  - `docs/development/archive/POST_BENCHMARK_STRATEGY.md`: `](src/main/` ->
    `](../../../src/main/`.
  - `docs/diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md`: `](../target/` ->
    `](../../target/`.
  - `tools/comparison/common.py` and
    `tools/prerun/{splits.jsh,Verify-MemoryTool.ps1,verify_competitors.py}`:
    `docs/benchmark-prerun` -> `docs/benchmark/benchmark-prerun`.
  - `ResizeEquivalenceTest.java` and `NpyMapTest.java`: `Path.of` segments
    updated to `docs/benchmark/benchmark-prerun/...`.
  - 4 Java Javadoc comments qualified to
    `docs/benchmark/LOCALIZATION_CONVENTIONS.md`.

Left unchanged on purpose:

- `ComparisonEvaluator.java:52` data string containing
  `LOCALIZATION_CONVENTIONS.md` (part of emitted artifact metadata; changing it
  would alter frozen outputs).
- Bare prose filenames (e.g. `PROJECT_STATE.md`) in historical references.
- `docs/development/archive/REPOSITORY_CLEANUP_PLAN.md` keeps the old paths as
  the pre-migration audit record.

`tools/prerun/workspace_identity.py` `CRITICAL_FILES` finalized to:

`AGENTS.md`; `docs/architecture/ONNX_MODEL_CONTRACT.md`;
`docs/benchmark/BENCHMARK_CONTRACT.md`; `docs/PROJECT_STATE.md`;
`docs/benchmark/benchmark-prerun/{source,configurations,weights,data-sha256,resize-equivalence}.json`;
`docs/benchmark/benchmark-prerun/map-format/fixtures.json`; `pom.xml`;
`src/main/.../ImageNetPreprocessor.java`; both ONNX models;
`EvaluationLabels.java`; `LocalizationMaps.java`; `LocalizationMetrics.java`.

Verification: running the script printed `critical_missing: []`.

---

## 8. `.gitignore`

Final content:

```
target/
.idea/
*.iml
*.log
Thumbs.db
.DS_Store
__pycache__/
*.pyc
session-*.md
CHAT_HANDOFF.md
```

Changes versus before: removed the duplicated `session-*.md` line; added
`__pycache__/` and `*.pyc` (the tools tree contained untracked compiled Python);
added `CHAT_HANDOFF.md`. `docs/`, `tools/` and all source/test files remain
tracked-eligible (not ignored).

---

## 9. Test results

Targeted suite (as requested):

```
mvn -B "-Dtest=AdjointEquivalenceTest,DenseRademacherProjectionTest,ProjectionStatisticsAccumulatorTest,PositionalMemoryBuilderTest" test
```

Result: **Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**.

Additional check on the code paths that were edited for the reorganized
evidence directory:

```
mvn -B "-Dtest=ResizeEquivalenceTest,NpyMapTest" test
```

Result: **Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**
(resize equivalence `max_abs = 7.105e-15` against tolerance `1e-9`).

No test was modified to accommodate the cleanup.

---

## 10. Remaining blockers before public release

1. **No `README.md` (English) and no `LICENSE`.** Only `README.it.md` exists;
   it links images into the gitignored `target/` and should be repointed to
   `docs/images/` before publication.
2. **`README.it.md` image links.** They currently reference
   `target/...` artifacts; the four public copies now exist under
   `docs/images/` but `README.it.md` has not been repointed yet.
3. **Machine-specific absolute path** in `docs/PROJECT_STATE.md` line 3
   (a local checkout path) should be generalized.
4. **`AGENTS.md` semantic drift**: `TASK.md` is still listed as canonical while
   it is archived/obsolete; owner decision needed.
5. **Large untracked breadth.** `docs/`, `tools/`, `src/jmh/` and ~18
   `src/test` Java files remain untracked; a future explicit staging/commit pass
   is required (not part of this pass).
6. **`docs/development/archive/REPOSITORY_CLEANUP_PLAN.md`** references
   pre-migration paths and a local temp directory by design; decide whether to
   keep, sanitize or remove it before publication.

No file was committed. Commit only on explicit request.
