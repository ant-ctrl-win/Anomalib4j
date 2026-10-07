# Anomalib4j Repository Cleanup Plan

**Status: AUDIT ONLY — no file has been moved, copied, deleted or modified.**
This document is the proposal for a later, separately-approved migration pass.

Scope: prepare Anomalib4j for a clean public GitHub repository. The audit
covers the repository root and every Markdown file, and inspects `.gitignore`.

Repository baseline inspected: git `HEAD` = `83c4197`, branch working tree with
many uncommitted/untracked files (see §2). No install, build, test or commit was
run.

---

## 1. Classification model

| Category | Meaning |
|---|---|
| **KEEP_IN_ROOT** | Genuinely belongs at top level (README, language README, meta/agent config). |
| **MOVE_TO_DOCS** | Permanent technical documentation that stays public, grouped under `docs/`. |
| **ARCHIVE_OR_REMOVE** | Obsolete / superseded / corrupt / duplicated; useful content already covered by a canonical doc. |
| **LOCAL_ONLY_GITIGNORE** | Temporary notes, generated scratch, LLM transfer files, machine-specific or reproducible output. |

`docs/`, `tools/` and `src/jmh` are currently **untracked** (`git status` shows
`?? docs/`, `?? tools/`). Only 12 root `.md` files are tracked; 19 are not.

---

## 2. Current root inventory

### 2.1 Non-Markdown root files

| Path | Tracked | Notes |
|---|---|---|
| `.gitignore` | yes | see §12 |
| `pom.xml` | yes | build |
| `.git/`, `.idea/`, `src/`, `target/`, `tools/`, `docs/` | — | `.idea` untracked, `target/` ignored, `src` tracked, `docs`/`tools` untracked |

There is **no `README.md`** and **no `LICENSE`/`LICENSE.md`** in the repository.

### 2.2 Root Markdown files (31) with Git state

| # | File | Tracked | Bytes |
|---|---|---|---|
| 1 | `AGENTS.md` | **yes** | 2030 |
| 2 | `CHAT_HANDOFF.md` | no | 40358 |
| 3 | `ENCODER_MICROPROFILE_RESULT.md` | no | 5539 |
| 4 | `ENCODER_MICROPROFILE_VALIDATION.md` | no | 4947 |
| 5 | `HELDOUT_CALIBRATION_RESULT.md` | no | 4057 |
| 6 | `HELDOUT_CALIBRATION_VALIDATION.md` | no | 2831 |
| 7 | `HELDOUT_MULTI_SEED_RESULT.md` | no | 4735 |
| 8 | `LOCALIZATION_CONVENTIONS.md` | **yes** | 3398 |
| 9 | `MVTEC_DATASET_CENSUS.md` | **yes** | 12244 |
| 10 | `ONNX_MODEL_CONTRACT.md` | **yes** | 13370 |
| 11 | `PATCH_GRID_OVERLAY_REVIEW.md` | no | 7231 |
| 12 | `PERFORMANCE_BASELINE_RESULT.md` | no | 6982 |
| 13 | `PERFORMANCE_BENCHMARK_VALIDATION.md` | no | 5466 |
| 14 | `POSITIONAL_CALIBRATION_RESULT.md` | no | 3738 |
| 15 | `POSITIONAL_CALIBRATION_VALIDATION.md` | no | 2381 |
| 16 | `POST_BENCHMARK_STRATEGY.md` | no | 29801 |
| 17 | `PREPROCESSING_MICROPROFILE_RESULT.md` | no | 8478 |
| 18 | `PREPROCESSING_MICROPROFILE_VALIDATION.md` | no | 6450 |
| 19 | `PREPROCESSING_OPTIMIZATION_RESULT.md` | no | 6797 |
| 20 | `PREPROCESSING_OPTIMIZATION_VALIDATION.md` | no | 8184 |
| 21 | `PROJECT_STATE.md` | **yes** | 1006 |
| 22 | `README.it.md` | no | 17446 |
| 23 | `session-ses_f13e.md` | no | 334558 |
| 24 | `SPATIAL14_VS_SPATIAL28_PER_DEFECT.md` | no | 8531 |
| 25 | `SPATIAL28_VALIDATION.md` | **yes** | 1728 |
| 26 | `SPRINT6_RESULT.md` | **yes** | 6839 |
| 27 | `SPRINT7_RESULT.md` | **yes** | 7243 |
| 28 | `SPRINT7_STATUS.md` | **yes** | 12089 |
| 29 | `SPRINT8_RESULT.md` | **yes** | 11188 |
| 30 | `SPRINT9_RESULT.md` | **yes** | 2202 |
| 31 | `TASK.md` | **yes** | 1823 |

### 2.3 `docs/` Markdown (16 files, all untracked)

`BENCHMARK_CONTRACT.md`, `BENCHMARK_CONTRACT_RECONCILIATION.md`,
`BENCHMARK_FINAL_RESULT.md`, `BENCHMARK_PHASE1_AUDIT.md`,
`BENCHMARK_PHASE2_COMPLETION_AUDIT.md`, `BENCHMARK_PHASE2_IMPLEMENTATION.md`,
`BENCHMARK_PHASE2_PREPARATION.md`, `BENCHMARK_PRERUN_VERIFICATION.md`,
`BORDER_EXCLUSION_SENSITIVITY.md`, `BOTTLE_SYMMETRIC_DIAGNOSTIC.md`,
`DECISION_LOG.md`, `FUTURE_ARCHITECTURE_ROADMAP.md`,
`METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md`, `METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md`,
`PROJECT_STATE.md`, `SPATIAL_EDGE_ARTIFACT_AUDIT.md`.
Plus non-Markdown evidence under `docs/benchmark-prerun/` (JSON manifests, split
`.txt`, memory `.csv`, map-format fixtures, an empty `java-runtime-stderr.txt`).

---

## 3. Classification table (root Markdown)

| File | Category | Proposed destination / action |
|---|---|---|
| `README.it.md` | **KEEP_IN_ROOT** | Root. Fix image links (§8). |
| `AGENTS.md` | **KEEP_IN_ROOT** | Root. **Must be repaired** (shell-wrapper artifact, §11 blocker). |
| `ONNX_MODEL_CONTRACT.md` | MOVE_TO_DOCS | `docs/architecture/ONNX_MODEL_CONTRACT.md` |
| `MVTEC_DATASET_CENSUS.md` | MOVE_TO_DOCS | `docs/benchmark/MVTEC_DATASET_CENSUS.md` |
| `LOCALIZATION_CONVENTIONS.md` | MOVE_TO_DOCS | `docs/benchmark/LOCALIZATION_CONVENTIONS.md` |
| `SPRINT6_RESULT.md` | MOVE_TO_DOCS | `docs/development/SPRINT6_RESULT.md` |
| `SPRINT7_RESULT.md` | MOVE_TO_DOCS | `docs/development/SPRINT7_RESULT.md` (canonical) |
| `SPRINT8_RESULT.md` | MOVE_TO_DOCS | `docs/development/SPRINT8_RESULT.md` |
| `SPRINT9_RESULT.md` | MOVE_TO_DOCS | `docs/development/SPRINT9_RESULT.md` |
| `SPATIAL28_VALIDATION.md` | MOVE_TO_DOCS | `docs/development/SPATIAL28_VALIDATION.md` |
| `SPATIAL14_VS_SPATIAL28_PER_DEFECT.md` | MOVE_TO_DOCS | `docs/diagnostics/SPATIAL14_VS_SPATIAL28_PER_DEFECT.md` |
| `PATCH_GRID_OVERLAY_REVIEW.md` | MOVE_TO_DOCS | `docs/diagnostics/PATCH_GRID_OVERLAY_REVIEW.md` |
| `POSITIONAL_CALIBRATION_RESULT.md` | MOVE_TO_DOCS | `docs/benchmark/POSITIONAL_CALIBRATION_RESULT.md` |
| `POSITIONAL_CALIBRATION_VALIDATION.md` | MOVE_TO_DOCS | `docs/benchmark/POSITIONAL_CALIBRATION_VALIDATION.md` |
| `HELDOUT_CALIBRATION_RESULT.md` | MOVE_TO_DOCS | `docs/benchmark/HELDOUT_CALIBRATION_RESULT.md` |
| `HELDOUT_CALIBRATION_VALIDATION.md` | MOVE_TO_DOCS | `docs/benchmark/HELDOUT_CALIBRATION_VALIDATION.md` |
| `HELDOUT_MULTI_SEED_RESULT.md` | MOVE_TO_DOCS | `docs/benchmark/HELDOUT_MULTI_SEED_RESULT.md` |
| `PERFORMANCE_BASELINE_RESULT.md` | MOVE_TO_DOCS | `docs/development/PERFORMANCE_BASELINE_RESULT.md` |
| `PERFORMANCE_BENCHMARK_VALIDATION.md` | MOVE_TO_DOCS | `docs/development/PERFORMANCE_BENCHMARK_VALIDATION.md` |
| `ENCODER_MICROPROFILE_RESULT.md` | MOVE_TO_DOCS | `docs/development/ENCODER_MICROPROFILE_RESULT.md` |
| `ENCODER_MICROPROFILE_VALIDATION.md` | MOVE_TO_DOCS | `docs/development/ENCODER_MICROPROFILE_VALIDATION.md` |
| `PREPROCESSING_MICROPROFILE_RESULT.md` | MOVE_TO_DOCS | `docs/development/PREPROCESSING_MICROPROFILE_RESULT.md` |
| `PREPROCESSING_MICROPROFILE_VALIDATION.md` | MOVE_TO_DOCS | `docs/development/PREPROCESSING_MICROPROFILE_VALIDATION.md` |
| `PREPROCESSING_OPTIMIZATION_RESULT.md` | MOVE_TO_DOCS | `docs/development/PREPROCESSING_OPTIMIZATION_RESULT.md` |
| `PREPROCESSING_OPTIMIZATION_VALIDATION.md` | MOVE_TO_DOCS | `docs/development/PREPROCESSING_OPTIMIZATION_VALIDATION.md` |
| `POST_BENCHMARK_STRATEGY.md` | MOVE_TO_DOCS | `docs/development/POST_BENCHMARK_STRATEGY.md` |
| `PROJECT_STATE.md` | **ARCHIVE_OR_REMOVE** | **Remove** (corrupt + superseded, §5) |
| `SPRINT7_STATUS.md` | **ARCHIVE_OR_REMOVE** | Archive → `docs/development/archive/SPRINT7_STATUS.md` |
| `TASK.md` | **ARCHIVE_OR_REMOVE** | Archive → `docs/development/archive/TASK.md` |
| `CHAT_HANDOFF.md` | **LOCAL_ONLY_GITIGNORE** | Add to `.gitignore`; never public |
| `session-ses_f13e.md` | **LOCAL_ONLY_GITIGNORE** | Already ignored by `session-*.md` |

**Counts:** KEEP 2 · MOVE 24 · ARCHIVE/REMOVE 3 · LOCAL_ONLY 2 = **31**.

RESULT ↔ VALIDATION pairs are complementary, not duplicates, and are moved
together (same folder) so their cross-links remain short.

---

## 4. Proposed destination for every moved document

| Destination folder | Files |
|---|---|
| `docs/architecture/` | `ONNX_MODEL_CONTRACT.md` |
| `docs/benchmark/` | `MVTEC_DATASET_CENSUS.md`, `LOCALIZATION_CONVENTIONS.md`, `POSITIONAL_CALIBRATION_RESULT.md`, `POSITIONAL_CALIBRATION_VALIDATION.md`, `HELDOUT_CALIBRATION_RESULT.md`, `HELDOUT_CALIBRATION_VALIDATION.md`, `HELDOUT_MULTI_SEED_RESULT.md` |
| `docs/diagnostics/` | `SPATIAL14_VS_SPATIAL28_PER_DEFECT.md`, `PATCH_GRID_OVERLAY_REVIEW.md` |
| `docs/development/` | `SPRINT6_RESULT.md`, `SPRINT7_RESULT.md`, `SPRINT8_RESULT.md`, `SPRINT9_RESULT.md`, `SPATIAL28_VALIDATION.md`, `PERFORMANCE_BASELINE_RESULT.md`, `PERFORMANCE_BENCHMARK_VALIDATION.md`, `ENCODER_MICROPROFILE_RESULT.md`, `ENCODER_MICROPROFILE_VALIDATION.md`, `PREPROCESSING_MICROPROFILE_RESULT.md`, `PREPROCESSING_MICROPROFILE_VALIDATION.md`, `PREPROCESSING_OPTIMIZATION_RESULT.md`, `PREPROCESSING_OPTIMIZATION_VALIDATION.md`, `POST_BENCHMARK_STRATEGY.md` |
| `docs/development/archive/` | `SPRINT7_STATUS.md`, `TASK.md` |
| `docs/images/` | copied heatmaps (§8) — folder does not exist yet |

`docs/` itself is reorganized by grouping the 16 existing untracked docs
(previously flat) under the same folders — see §10 tree. Suggested mapping:
- `docs/architecture/`: `FUTURE_ARCHITECTURE_ROADMAP.md`
- `docs/benchmark/`: `BENCHMARK_CONTRACT.md`, `BENCHMARK_CONTRACT_RECONCILIATION.md`, `BENCHMARK_PRERUN_VERIFICATION.md`, `BENCHMARK_PHASE1_AUDIT.md`, `BENCHMARK_PHASE2_PREPARATION.md`, `BENCHMARK_PHASE2_IMPLEMENTATION.md`, `BENCHMARK_PHASE2_COMPLETION_AUDIT.md`, `BENCHMARK_FINAL_RESULT.md`
- `docs/diagnostics/`: `SPATIAL_EDGE_ARTIFACT_AUDIT.md`, `BOTTLE_SYMMETRIC_DIAGNOSTIC.md`, `METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md`, `METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md`, `BORDER_EXCLUSION_SENSITIVITY.md`
- `docs/` (hub, kept at top of docs): `PROJECT_STATE.md`, `DECISION_LOG.md`, `REPOSITORY_CLEANUP_PLAN.md` (this file)

The two benchmark "hub" docs `PROJECT_STATE.md` and `DECISION_LOG.md` are kept at
`docs/` top level because nearly every other document links to them.

---

## 5. Files safe to archive/remove, and why

1. **`PROJECT_STATE.md` (root) — REMOVE.** It is not a valid Markdown document:
   line 1 is `@'` and the final line is
   `'@ | Set-Content -Encoding utf8 PROJECT_STATE.md`, i.e. a stray PowerShell
   here-string wrapper. Content is the obsolete Sprint-7/VSA checkpoint.
   `docs/PROJECT_STATE.md` explicitly states it *"Sostituisce come fotografia
   corrente il vecchio `PROJECT_STATE.md` nella radice"*, and
   `docs/BENCHMARK_CONTRACT_RECONCILIATION.md` calls the root `PROJECT_STATE`/
   `TASK` obsolete. No unique content is lost.
2. **`SPRINT7_STATUS.md` — ARCHIVE.** A pre-execution status/recovery note that
   itself reports `SPRINT7_RESULT.md` as **assente** ("missing"). `SPRINT7_RESULT.md`
   was subsequently produced and supersedes the "not executed" state. Kept as
   historical process evidence under `docs/development/archive/` (not deleted),
   per the instruction not to discard historical evidence.
3. **`TASK.md` — ARCHIVE.** A Sprint-9 `SPATIAL_28` task instruction, completed
   by `SPRINT9_RESULT.md`; `docs/BENCHMARK_CONTRACT_RECONCILIATION.md` lists the
   root `TASK` as obsolete. It is also listed as "canonical" in `AGENTS.md`, so
   archiving it requires updating that list (§11 blocker).

Everything else is retained because it is either canonical or unique
per-run/per-stage evidence (see §9).

---

## 6. Files that should become local-only

| File | Reason | Action |
|---|---|---|
| `CHAT_HANDOFF.md` | Self-declared *"temporary transfer packet for a new ChatGPT chat and for Codex/Astra"* and *"not canonical project truth"*. It is an LLM/client handoff artifact, not public documentation. | Add `CHAT_HANDOFF.md` to `.gitignore`; keep locally. |
| `session-ses_f13e.md` | 334 KB raw session transcript. | Already ignored via `session-*.md`; no change needed. |

Recommendation: any future chat/session dumps must match `session-*.md` (already
covered). Do **not** gitignore `docs/` or `tools/` — those are the public
documentation and reproducibility tooling.

---

## 7. Link / reference impact

### 7.1 Referrers of each moved root document

Derived from an automated basename scan across all `.md` files plus a scan of
non-Markdown sources.

| Moved file | Markdown referrers | Code referrers |
|---|---|---|
| `ONNX_MODEL_CONTRACT.md` | `AGENTS.md`, `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `docs/BENCHMARK_CONTRACT.md`, `docs/BENCHMARK_PHASE2_PREPARATION.md`, `POST_BENCHMARK_STRATEGY.md`, `CHAT_HANDOFF.md` (local) | **`tools/prerun/workspace_identity.py`** (`CRITICAL_FILES`) |
| `MVTEC_DATASET_CENSUS.md` | `AGENTS.md`, `docs/PROJECT_STATE.md`, `docs/BENCHMARK_CONTRACT.md`, `POST_BENCHMARK_STRATEGY.md`, `CHAT_HANDOFF.md` | — |
| `LOCALIZATION_CONVENTIONS.md` | `README.it.md`, `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `docs/BENCHMARK_CONTRACT.md`, `docs/BENCHMARK_FINAL_RESULT.md`, `docs/BENCHMARK_PRERUN_VERIFICATION.md`, `SPRINT8_RESULT.md`, `SPATIAL28_VALIDATION.md`, `TASK.md`, `POSITIONAL_CALIBRATION_VALIDATION.md`, `HELDOUT_CALIBRATION_VALIDATION.md`, `SPATIAL14_VS_SPATIAL28_PER_DEFECT.md`, `POST_BENCHMARK_STRATEGY.md`, `HELDOUT_CALIBRATION_RESULT.md`, `CHAT_HANDOFF.md` | `BottlePositionalCalibrationIT.java`, `ComparisonEvaluator.java`, `HeldOutBottleCalibration.java`, `HeldOutMultiSeedSummary.java`, `Spatial28Evaluation.java` |
| `SPRINT7_RESULT.md` | `README.it.md`, `AGENTS.md`, `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `SPRINT8_RESULT.md`, `SPRINT7_STATUS.md`, `POST_BENCHMARK_STRATEGY.md`, `CHAT_HANDOFF.md` | `BottleRealEvaluationTest.java` |
| `SPRINT6_RESULT.md` | `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `SPRINT7_STATUS.md`, `POST_BENCHMARK_STRATEGY.md`, `CHAT_HANDOFF.md` | `BottleTrainingTest.java` |
| All `PERFORMANCE_*`, `ENCODER_*`, `PREPROCESSING_*`, `HELDOUT_*`, `POSITIONAL_*`, `SPATIAL14_VS_*`, `PATCH_GRID_*`, `POST_BENCHMARK_STRATEGY`, `SPATIAL28_VALIDATION`, `SPRINT9_RESULT` | `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `docs/BENCHMARK_*`, `CHAT_HANDOFF.md` | `Spatial28Evaluation.java` (SPRINT9) |

### 7.2 Required rewrites

- **`README.it.md`** (becomes the public entry point): its links are relative to
  root, so every moved target changes prefix, e.g.
  `SPRINT7_RESULT.md` → `docs/development/SPRINT7_RESULT.md`,
  `PERFORMANCE_BASELINE_RESULT.md` → `docs/development/PERFORMANCE_BASELINE_RESULT.md`,
  `HELDOUT_CALIBRATION_RESULT.md` → `docs/benchmark/HELDOUT_CALIBRATION_RESULT.md`,
  `LOCALIZATION_CONVENTIONS.md` → `docs/benchmark/LOCALIZATION_CONVENTIONS.md`,
  and the existing `docs/...` links gain their new subfolder. Image links change
  to `docs/images/...` (§8).
- **`AGENTS.md`** canonical list: replace
  `PROJECT_STATE.md`, `TASK.md`, `ONNX_MODEL_CONTRACT.md`,
  `MVTEC_DATASET_CENSUS.md`, `SPRINT7_RESULT.md` with their new paths / removal
  decisions.
- **`docs/*` hub documents** (`PROJECT_STATE.md`, `DECISION_LOG.md`,
  `BENCHMARK_CONTRACT.md`, `FUTURE_ARCHITECTURE_ROADMAP.md`, and each
  `docs/BENCHMARK_*`): once both root and docs files are regrouped, their
  relative links (currently `../ONNX_MODEL_CONTRACT.md`,
  `../LOCALIZATION_CONVENTIONS.md`, sibling `BENCHMARK_*.md`, …) must be
  recomputed. This is the largest mechanical edit.
- **`tools/prerun/workspace_identity.py` `CRITICAL_FILES`**: change
  `"ONNX_MODEL_CONTRACT.md"` → `"docs/architecture/ONNX_MODEL_CONTRACT.md"`.
  Without this the workspace-identity capture file hashes/assertions break.
- **Java Javadoc comments** referencing `LOCALIZATION_CONVENTIONS`,
  `SPRINT6_RESULT`, `SPRINT7_RESULT`: update comment paths (compilation is
  unaffected, but the references should not rot).
- **`CHAT_HANDOFF.md` links inside public docs**: `docs/PROJECT_STATE.md` links
  `../CHAT_HANDOFF.md` and `docs/DECISION_LOG.md` links `CHAT_HANDOFF.md`. Once
  `CHAT_HANDOFF.md` is local-only, those public links will 404 on GitHub. Replace
  them with prose ("the local chat handoff is not published") rather than a link.

---

## 8. README heatmaps — source verification and copy proposal

All four proposed sources **exist** and are currently inside the gitignored
`target/`, so the README images are broken on GitHub until copied to a durable
path. `docs/images/` does **not** exist yet.

| Verified source | Exists | Proposed destination |
|---|---|---|
| `target/bottle-spatial14-heldout-calibration/bottle_good_000.png` | yes | `docs/images/bottle-good.png` |
| `target/bottle-spatial14-heldout-calibration/bottle_broken_large_000.png` | yes | `docs/images/bottle-broken-large.png` |
| `target/bottle-spatial14-heldout-calibration/bottle_broken_small_000.png` | yes | `docs/images/bottle-broken-small.png` |
| `target/comparison/final-comparison-20261003/diagnostics/metal_nut_good_visuals/high_01_metal_nut_good_009.png` | yes | `docs/images/metal-nut-good-activation.png` |

Notes:
- `README.it.md` already carries this exact proposal (lines 156–161) and states
  explicitly that no copy was made. This audit confirms the mapping; **no copy is
  performed in this pass.**
- The four README image links (lines 135–147) must be updated from the `target/`
  paths to the `docs/images/` paths in the same migration pass.
- The proposal includes only four figures; `README.it.md` records that no
  ready-made original+overlay tables were found for Metal Nut `scratch`/`color`
  and `bent`. That gap is acknowledged, not closed here.

---

## 9. Canonical documents that must not be lost

| Document | Why canonical |
|---|---|
| `docs/PROJECT_STATE.md` | Explicit canonical current state; supersedes root `PROJECT_STATE.md`. |
| `docs/DECISION_LOG.md` | Reconciled decisions D01–D14 with evidence and rejected alternatives. |
| `docs/BENCHMARK_CONTRACT.md` | Frozen benchmark protocol all benchmark docs defer to. |
| `docs/BENCHMARK_FINAL_RESULT.md` | Authoritative results for `final-comparison-20261003`. |
| `docs/FUTURE_ARCHITECTURE_ROADMAP.md` | v2 roadmap; `PROPOSED / FUTURE_WORK`. |
| `docs/SPATIAL_EDGE_ARTIFACT_AUDIT.md` + the four post-hoc diagnostics | Frozen run evidence base. |
| `AGENTS.md` | Agent contract / working rules (repair, do not discard). |
| `ONNX_MODEL_CONTRACT.md`, `MVTEC_DATASET_CENSUS.md`, `LOCALIZATION_CONVENTIONS.md` | Normative references (listed by `AGENTS.md`). |
| `SPRINT7_RESULT.md` | Canonical real-image Adjoint equivalence result. |

---

## 10. Proposed final repository tree

```
Anomalib4j/
├── .gitignore                      (see §12)
├── README.it.md                    (KEEP; links -> docs/images + new doc paths)
├── AGENTS.md                       (KEEP; repair wrapper + update canonical list)
├── pom.xml
├── docs/
│   ├── REPOSITORY_CLEANUP_PLAN.md
│   ├── PROJECT_STATE.md            (canonical hub)
│   ├── DECISION_LOG.md             (canonical hub)
│   ├── architecture/
│   │   ├── ONNX_MODEL_CONTRACT.md
│   │   └── FUTURE_ARCHITECTURE_ROADMAP.md
│   ├── benchmark/
│   │   ├── BENCHMARK_CONTRACT.md
│   │   ├── BENCHMARK_CONTRACT_RECONCILIATION.md
│   │   ├── BENCHMARK_PRERUN_VERIFICATION.md
│   │   ├── BENCHMARK_PHASE1_AUDIT.md
│   │   ├── BENCHMARK_PHASE2_PREPARATION.md
│   │   ├── BENCHMARK_PHASE2_IMPLEMENTATION.md
│   │   ├── BENCHMARK_PHASE2_COMPLETION_AUDIT.md
│   │   ├── BENCHMARK_FINAL_RESULT.md
│   │   ├── MVTEC_DATASET_CENSUS.md
│   │   ├── LOCALIZATION_CONVENTIONS.md
│   │   ├── POSITIONAL_CALIBRATION_RESULT.md
│   │   ├── POSITIONAL_CALIBRATION_VALIDATION.md
│   │   ├── HELDOUT_CALIBRATION_RESULT.md
│   │   ├── HELDOUT_CALIBRATION_VALIDATION.md
│   │   ├── HELDOUT_MULTI_SEED_RESULT.md
│   │   └── benchmark-prerun/       (JSON/CSV/txt evidence)
│   ├── diagnostics/
│   │   ├── SPATIAL_EDGE_ARTIFACT_AUDIT.md
│   │   ├── BOTTLE_SYMMETRIC_DIAGNOSTIC.md
│   │   ├── METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md
│   │   ├── METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md
│   │   ├── BORDER_EXCLUSION_SENSITIVITY.md
│   │   ├── SPATIAL14_VS_SPATIAL28_PER_DEFECT.md
│   │   └── PATCH_GRID_OVERLAY_REVIEW.md
│   ├── development/
│   │   ├── SPRINT6_RESULT.md
│   │   ├── SPRINT7_RESULT.md
│   │   ├── SPRINT8_RESULT.md
│   │   ├── SPRINT9_RESULT.md
│   │   ├── SPATIAL28_VALIDATION.md
│   │   ├── PERFORMANCE_BASELINE_RESULT.md
│   │   ├── PERFORMANCE_BENCHMARK_VALIDATION.md
│   │   ├── ENCODER_MICROPROFILE_RESULT.md
│   │   ├── ENCODER_MICROPROFILE_VALIDATION.md
│   │   ├── PREPROCESSING_MICROPROFILE_RESULT.md
│   │   ├── PREPROCESSING_MICROPROFILE_VALIDATION.md
│   │   ├── PREPROCESSING_OPTIMIZATION_RESULT.md
│   │   ├── PREPROCESSING_OPTIMIZATION_VALIDATION.md
│   │   ├── POST_BENCHMARK_STRATEGY.md
│   │   └── archive/
│   │       ├── SPRINT7_STATUS.md
│   │       └── TASK.md
│   └── images/
│       ├── bottle-good.png
│       ├── bottle-broken-large.png
│       ├── bottle-broken-small.png
│       └── metal-nut-good-activation.png
├── src/...                          (main + test; jmh)
└── tools/...                        (prerun + comparison)
```

Local-only (present on disk, not published): `CHAT_HANDOFF.md`,
`session-*.md`, `target/`.

---

## 11. Blockers and ambiguous cases

1. **`AGENTS.md` is corrupt (BLOCKER for a public repo).** Line 1 is `@'` and
   line 72 is `'@ | Set-Content -Encoding utf8 AGENTS.md`; the real content is
   lines 2–71. It is tracked, so this garbage is already committed. Repair =
   delete line 1 and the trailing wrapper line. Must be done before publishing.
2. **No `README.md`.** Only `README.it.md` exists (Italian, untracked). GitHub
   will show no default README. Decide whether to (a) add an English `README.md`,
   or (b) rename `README.it.md` to `README.md`. Not resolved in this pass.
3. **`AGENTS.md` canonical list vs. reality.** `AGENTS.md` names `PROJECT_STATE.md`,
   `TASK.md`, `ONNX_MODEL_CONTRACT.md`, `MVTEC_DATASET_CENSUS.md`,
   `SPRINT7_RESULT.md` as canonical, but `docs/PROJECT_STATE.md` and
   `docs/BENCHMARK_CONTRACT_RECONCILIATION.md` declare the root `PROJECT_STATE`/
   `TASK` obsolete. Requires a content decision (recommended: point the list at
   the new `docs/` paths and drop the archived items).
4. **`POST_BENCHMARK_STRATEGY.md` and `PATCH_GRID_OVERLAY_REVIEW.md` /
   `SPATIAL14_VS_SPATIAL28_PER_DEFECT.md`** carry historical/strategic content
   that is partly superseded by `docs/PROJECT_STATE.md` but contains unique
   reasoning and per-defect analysis. Classified MOVE (not archive) to preserve
   evidence; if the owner prefers a lean repo they could be archived instead.
5. **`docs/benchmark-prerun/` is machine-specific evidence** (hardware.json,
   environment freeze, memory CSVs, splits, an empty `java-runtime-stderr.txt`).
   Treated as public benchmark evidence. If the repo must be minimal, the empty
   file and machine-specific environment captures could become local-only — needs
   owner confirmation.
6. **No `LICENSE`.** A public repo normally needs one; out of scope of this audit
   but flagged.
7. **Untracked breadth.** `docs/`, `tools/`, `src/jmh`, and ~18 `src/test` Java
   files are untracked. The migration must decide what to `git add`; the plan
   assumes `docs/`, `tools/` and the new `docs/images/` are intended to be public.

---

## 12. Proposed `.gitignore` changes

Current file:

```gitignore
target/
.idea/
*.iml
*.log
Thumbs.db
.DS_Store
session-*.md
session-*.md        # duplicated line
```

Proposed:

```gitignore
# Build output
target/

# IDE
.idea/
*.iml

# OS cruft
Thumbs.db
.DS_Store

# Logs
*.log

# Python scratch (tools/prerun currently has a __pycache__/ dir)
__pycache__/
*.pyc

# Local LLM/session scratch — never public
session-*.md
CHAT_HANDOFF.md
```

Changes:
- **Add** `__pycache__/` and `*.pyc` — `tools/prerun/__pycache__/*.pyc` exists and
  is currently unignored, so it would be committed.
- **Add** `CHAT_HANDOFF.md` — per §6.
- **Remove** the duplicated `session-*.md` line.
- **Keep** `target/`, `.idea/`, `*.iml`, `*.log`, `Thumbs.db`, `.DS_Store`.
- **Do not add** `docs/` or `tools/` to `.gitignore`: they are public content, and
  ignoring them would be used as a substitute for proper organization.

---

## 13. Exact ordered migration plan (for the later, approved pass)

All commands are shown for Windows PowerShell from the repository root. Do not
run until approved. Steps 0–2 must precede any `git add` of the reorganization.

0. **Integrity snapshot.** `git rev-parse HEAD`; confirm no intended work is
   uncommitted; tag the pre-migration state (e.g. `git tag pre-repo-cleanup`).
1. **Repair `AGENTS.md`** (tracked): remove line 1 `@'` and the trailing
   `'@ | Set-Content -Encoding utf8 AGENTS.md`; review `git diff`; keep it in root.
2. **Create `docs/images/` and copy the four heatmaps** (§8); update the four
   `README.it.md` image links to `docs/images/...`. No `target/` file is moved or
   deleted.
3. **Create `docs/architecture`, `docs/benchmark`, `docs/diagnostics`,
   `docs/development`, `docs/development/archive`.**
4. **Move tracked root docs with `git mv`** (preserves history):
   `git mv ONNX_MODEL_CONTRACT.md docs/architecture/`;
   `git mv MVTEC_DATASET_CENSUS.md LOCALIZATION_CONVENTIONS.md
   POSITIONAL_CALIBRATION_RESULT.md POSITIONAL_CALIBRATION_VALIDATION.md
   HELDOUT_CALIBRATION_RESULT.md HELDOUT_CALIBRATION_VALIDATION.md
   HELDOUT_MULTI_SEED_RESULT.md docs/benchmark/`;
   `git mv SPRINT6_RESULT.md SPRINT7_RESULT.md SPRINT8_RESULT.md SPRINT9_RESULT.md
   SPATIAL28_VALIDATION.md PERFORMANCE_BASELINE_RESULT.md
   PERFORMANCE_BENCHMARK_VALIDATION.md ENCODER_MICROPROFILE_RESULT.md
   ENCODER_MICROPROFILE_VALIDATION.md PREPROCESSING_MICROPROFILE_RESULT.md
   PREPROCESSING_MICROPROFILE_VALIDATION.md PREPROCESSING_OPTIMIZATION_RESULT.md
   PREPROCESSING_OPTIMIZATION_VALIDATION.md POST_BENCHMARK_STRATEGY.md
   docs/development/`.
   (Some listed pairs are untracked — add them in step 6 instead.)
5. **Move untracked root docs with `Move-Item`** into the same folders
   (the 16 untracked MOVE files from §3).
6. **Archive/remove:**
   `git rm PROJECT_STATE.md` (root, corrupt + superseded);
   `git mv SPRINT7_STATUS.md TASK.md docs/development/archive/`.
7. **Regroup the existing `docs/*.md`** into the subfolders per §4 (currently
   untracked, plain `Move-Item`).
8. **Rewrite links** in `README.it.md`, `AGENTS.md`, `docs/PROJECT_STATE.md`,
   `docs/DECISION_LOG.md`, `docs/BENCHMARK_CONTRACT.md`,
   `docs/FUTURE_ARCHITECTURE_ROADMAP.md`, the `docs/BENCHMARK_*` and diagnostics,
   for both moved root targets and moved `docs/` siblings; remove the two public
   links to `CHAT_HANDOFF.md` (§7.2).
9. **Update code references:**
   `tools/prerun/workspace_identity.py` `CRITICAL_FILES`
   `"ONNX_MODEL_CONTRACT.md"` → `"docs/architecture/ONNX_MODEL_CONTRACT.md"`;
   update Javadoc path references in the listed Java test files.
10. **Apply `.gitignore` changes** (§12); verify `git check-ignore CHAT_HANDOFF.md`
    returns the file.
11. **Stage and verify:** `git add docs tools src`; confirm `git status` shows no
    stray `__pycache__`, no `session-*`, no `CHAT_HANDOFF.md`; run the focused
    test command `mvn -B "-Dtest=AdjointEquivalenceTest,DenseRademacherProjectionTest,ProjectionStatisticsAccumulatorTest,PositionalMemoryBuilderTest" test`
    and the workspace-identity capture to prove `CRITICAL_FILES` paths resolve.
12. **Commit only when explicitly requested** (working rule: no commit in this
    audit).

---

## 14. Deliverable summary

- Root files to keep: **2** (`README.it.md`, `AGENTS.md`).
- To move: **24**. To archive/remove: **3**. To make local-only/ignore: **2**.
- Four README heatmap sources verified and mapped to `docs/images/`; **not copied**.
- Canonical docs identified in §9; supersession evidence in §5.
- Blockers: corrupt `AGENTS.md`; missing `README.md`; `AGENTS.md` canonical list
  out of sync; untracked `docs/`/`tools/` breadth; no LICENSE.
- No repository file was modified in this pass.
