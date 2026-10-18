# Experiment index

Canonical evidence index, updated **2026-10-18**. Current implementation and
active work are defined in [PROJECT_STATE.md](PROJECT_STATE.md).
Values below summarize retained reports
and local CSVs; they do not certify a fresh execution.

**Metric order: Image AUROC / Pixel AUROC / AUPRO@0.30.** Values are rounded
to six decimals here; detailed reports/artifacts retain their recorded precision.
B = Bottle, N = Metal Nut. A fit/calibration split uses training-good images only.
All listed detector experiments use projection seed 42 where applicable.

## Detector generations

### Latest completed campaigns

| ID | Implementation / source | Coverage and split | Macro Image / Pixel / AUPRO | Evidence and status |
|---|---|---|---|---|
| `m0-all15-baseline-20261016` | Unchanged Java M0; workspace and source hashes in run | All 15; deterministic seed-42 held-out manifests, floor(0.8*N) fit with small-set clamp | 0.818690 / 0.807989 / 0.575592 | `target/comparison/m0-all15-baseline-20261016`; completed retained baseline |
| `local-models-20261017` | Python C1/exact V1/M2, shared Java features/evaluator; source hashes in run | All 15; same baseline manifests; separate held-out Z per candidate | C1 0.831351 / 0.866853 / 0.682340; V1 0.830704 / 0.866527 / 0.681510; M2 0.871803 / 0.931016 / 0.805887 | `target/ablation/local-models-20261017/{RESULT.md,comparison.csv,macro-summary.csv}`; M2 selected; [frozen plan](experiments/LOCAL_MODEL_ABLATION_PLAN.md) |
| `java-m2-20261018-validated` | Separate Java M2; source snapshot and input hashes in run | Bottle/Metal Nut/Hazelnut retained fit/calibration/test features; no new all-15 run | All three metrics reproduce retained Python M2 with zero reported deltas | `target/ablation/java-m2-20261018-validated`; [parity/performance report](benchmark/JAVA_M2_RESULT_20261018.md); implementation regression, not independent scientific evidence |

### Earlier evidence (historical status at its recorded date)

Descriptions of M2 as Python-only or not selected below describe the older
two-category generation, superseded by the campaigns above.

| ID / classification | Implementation and source status | Coverage / split / calibration | Principal metrics | Report and retained artifact location | Reproducibility status |
|---|---|---|---|---|---|
| Early RAW M0, Sprints 6-8 / historical | Java SPATIAL_14; historical references `5caca96` / `f4132a1` recorded in D01/D03; exact source-to-artifact match not revalidated | B: fit 209, test 83; no positional Z | 0.985714 / 0.956950 / 0.865347; explicit/compiled max error 1.31e-12 on 16,268 test cells | [Sprint 7](development/SPRINT7_RESULT.md), [Sprint 8](development/SPRINT8_RESULT.md); `target/bottle-raw-heatmaps.csv`, `target/bottle-localization/` | Historical reports and local outputs retained; dataset required for refit; original source identity not newly verified |
| In-sample positional Z / historical | Java M0 SPATIAL_14 + evaluation helper; original experiment commit not established | B: same 209 normals for fit and Z calibration; test 83 | Z: 1.000000 / 0.961102 / 0.876670 | [Result](benchmark/POSITIONAL_CALIBRATION_RESULT.md); `target/bottle-spatial14-positional-calibration/` | Retained evidence; not held-out calibration and not an independent generalization estimate |
| Held-out M0 baseline / current baseline protocol, retained evidence | Java M0 SPATIAL_14; original standalone run commit not established; later reproduced in frozen comparison | B: fit 167, calibration 42, test 83; filename sort + Java shuffle seed 42 | RAW: 0.985714 / 0.956927 / 0.865098; Z: 1.000000 / 0.961664 / 0.877447 | [Result](benchmark/HELDOUT_CALIBRATION_RESULT.md), [validation](benchmark/HELDOUT_CALIBRATION_VALIDATION.md); `target/bottle-spatial14-heldout-calibration/` | Retained; canonical paired RAW/Z comparison. Refit requires external dataset |
| Split sensitivity / historical | Same Java M0 and positional Z; original run commit not established | B: 167+42, test 83; split seeds 1-5, projection seed fixed at 42 | Mean RAW: 0.985714 / 0.956977 / 0.865513; mean Z: 1.000000 / 0.960423 / 0.876453 | [Multi-seed report](benchmark/HELDOUT_MULTI_SEED_RESULT.md); `target/bottle-spatial14-heldout-multiseed/` | Retained; overlapping splits on one test set, not five independent datasets |
| SPATIAL_28 / exploratory historical | Java M0, shallower 28x28x64 encoder; `83c4197` / `sprint9-spatial28-comparison` recorded in D04 | B: fit 209, test 83; RAW | 1.000000 / 0.901402 / 0.705556 | [Sprint 9](development/archive/SPRINT9_RESULT.md), [per-defect comparison](diagnostics/SPATIAL14_VS_SPATIAL28_PER_DEFECT.md); `target/bottle-spatial28/` | Retained; changes stage and channels as well as grid. Does not replace SPATIAL_14 |
| `final-comparison-20261003` / current M0 reference, retained frozen evidence | Java M0 versus Anomalib 2.6.2 PaDiM/PatchCore. Frozen workspace/source hashes retained; not byte-identical to current HEAD | B: M0 167+42, competitors 209, test 83. N: M0 176+44, competitors 220, test 115. M0 positional Z; competitors native scores | M0 B: 1.000000 / 0.961664 / 0.877447; N: 0.757576 / 0.692630 / 0.379794. Competitor values in report | [Final result](benchmark/BENCHMARK_FINAL_RESULT.md), [contract](benchmark/BENCHMARK_CONTRACT.md); `target/comparison/final-comparison-20261003/` | Six complete retained cases with predictions/maps/state/environment. Dataset absent at configured path at audit; no current-HEAD rerun |
| M1/M2/M3 local-memory ablation / exploratory post-hoc evidence | Python `tools/prerun/local_memory_ablation.py`, Java feature dump; M0 reconstruction control, M1 CNN centroid, M2 CNN dispersion, M3 VSA dispersion. Exact original script commit not pinned by this index | B/N: same frozen 167+42 / 176+44 splits and 83/115 test images; per-model RAW and held-out Z | Z M1 B: 0.999206 / 0.963259 / 0.890405; N: 0.800098 / 0.656094 / 0.372387. Z M3 B: 0.999206 / 0.964170 / 0.891606; N: 0.849951 / 0.744457 / 0.504555. M2 below | [Full ablation](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md); frozen run `diagnostics/local-memory-ablation/` | Retained CSV/JSON/features; depends on frozen learned state/maps and external dataset. Already-observed categories; not independent validation |
| M2 local-memory result / exploratory evidence, strongest retained localization | **Python only**, local CNN mean + per-position/per-channel sample std, mean squared standardized residual; same ablation/source status as preceding row | Same B/N split and test sets; own held-out positional Z | Z B: 1.000000 / 0.975697 / 0.915738; N: 0.845552 / 0.849318 / 0.624208 | [M2 definitions/results](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md); `diagnostics/local-memory-ablation/{bottle,metal_nut}_metrics.csv` within frozen run | This row highlights M2 within the same ablation, **not an additional run**. Not Java M0, not selected v2, no inherited M0 latency |

Historical commit references above are references recorded by the
[decision log](DECISION_LOG.md), not newly proven complete reproducibility packages.

## Performance and diagnostic evidence

| ID / classification | Variant, source and coverage | Principal retained finding | Report / artifact location | Reproduction boundary |
|---|---|---|---|---|
| Internal scoring/performance / historical | M0 Java, Bottle; full-209 Sprint 7 then held-out JMH fixture; original execution sources not newly matched | Sprint 7 explicit ~651 ms/map versus compiled ~13 us; later JMH compiled p50 12 us/196 cells; earlier detection p50 19.824640 ms | [Sprint 7](development/SPRINT7_RESULT.md), [performance baseline](development/PERFORMANCE_BASELINE_RESULT.md); `target/benchmark/` | Distinct timing protocols and stages; not competitor speedups |
| Preprocessing optimization / historical implementation evidence | M0 held-out Bottle fixture; guarded RGB fast path; original run commit not established | 47,867,904 floats compared with zero differences for production; detection p50 2.789376 ms, localization 7.462912 ms in that run | [Optimization](development/archive/PREPROCESSING_OPTIMIZATION_RESULT.md), [validation](development/archive/PREPROCESSING_OPTIMIZATION_VALIDATION.md); `target/benchmark/preprocessing-optimized-full.json`, `preprocessing-equivalence.csv` | Empirical equivalence on recorded JVM/input cases; separate from final comparative timing |
| Frozen system timing / retained reference | Six frozen comparison cases; source identity as above, AMD Ryzen AI 9 HX 370 / Windows 11 | M0 detection/localization p50: B 3.723264/9.486336 ms; N 3.694592/5.775360 ms | [Final benchmark](benchmark/BENCHMARK_FINAL_RESULT.md); per-case `jmh.json`, latency and memory records under frozen run | Predecoded repeated input, batch 1; excludes decode/I/O/model construction. Different backbones/budgets and Java/Python timing protocols |
| Pose and RAW/Z / diagnostic | Frozen M0 N, 176 fit / 44 calibration / 115 test; pose associations on 22 test-good images; scripts in `tools/prerun/`, original script revisions not pinned here | Pose-as-primary-cause hypothesis not supported within measured natural poses; RAW N 0.704790 / 0.625562 / 0.345386 versus frozen Z above | [Pose](diagnostics/METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md), [RAW/Z](diagnostics/METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md); frozen run `diagnostics/` | Not a controlled translation/rotation or illumination experiment; later edge audit qualifies connectivity and interpretation |
| Edge/grid/background / diagnostic | Frozen M0 N plus synthetic markers, uniform inputs and local intensity perturbations; diagnostic code partly local under target | Correct tested coordinate semantics; padding/background effects present; no demonstrated dominant explanation of total error | [Edge audit](diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md), [grid review](diagnostics/PATCH_GRID_OVERLAY_REVIEW.md); frozen run `diagnostics/spatial-edge-audit/`, `target/patch-grid-overlays/` | Diagnostic local classes/scripts are not all tracked; limited images/perturbations, not a robustness campaign |
| Bottle symmetry and border exclusion / post-hoc diagnostic | Frozen M0 B/N splits/maps; scripts in `tools/prerun/`, original revisions not pinned here | B signal dominates similar technical effects; removing outer cells does not materially resolve N errors | [Bottle](diagnostics/BOTTLE_SYMMETRIC_DIAGNOSTIC.md), [border sensitivity](diagnostics/BORDER_EXCLUSION_SENSITIVITY.md); frozen run `diagnostics/` and `diagnostics/border-exclusion/` | Exclusion changes evaluated support; these are sensitivity results, not replacement official metrics |
| Positional/global variance / diagnostic | M0 frozen B/N fit sets and Java-extracted features; Python reconstruction, original script commit not pinned here | Between-position structure stronger in B; M0 models directional offset, not within-position dispersion | [Variance diagnostic](diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md); frozen run `diagnostics/` (exact outputs listed in report) | Descriptive associations, not proof of causal superiority or independent generalization |

## Evidence and source-status rules

- `target/` paths are repository-relative **ignored local evidence**, not
  guaranteed contents of a fresh clone. Reports and checked-in ONNX assets
  survive a clone; dataset, learned states, maps, feature dumps and environments
  require separate restoration. Do not delete evidence with `mvn clean`.
- Frozen run identity is recorded in its `workspace/`, `source-hashes.json`,
  `manifest.json` and environment records. The 2026-10-16 literal source-hash
  audit found 12 unchanged entries, 9 changed and 86 absent original paths.
  Relocations explain many missing paths; this count does not prove algorithmic
  changes or complete equivalence to current HEAD.
- [Namespace refactor](development/PACKAGE_NAMESPACE_REFACTOR.md) and
  [repository cleanup](REPOSITORY_CLEANUP_RESULT.md) explain later moves.
  Their historical workflow labels do not establish current Git status.
- ONNX byte identity is recorded in the [contract](architecture/ONNX_MODEL_CONTRACT.md);
  exact checkpoint/export provenance is still unresolved.
- Full 15-category M0 and local-model runs now exist as listed above. No controlled
  robustness campaign has been completed. The [census](benchmark/MVTEC_DATASET_CENSUS.md)
  inventories categories, not results.
- Reproduction commands and prerequisites belong to each linked report and the
  [technical overview](TECHNICAL_OVERVIEW.md); unverified source identity,
  unavailable inputs or partial local tooling must remain explicit.
