# Final Post-Hoc Reconciliation — Anomalib4j

Recording and portfolio reconciliation date: **2026-10-07**.
Status: **documentation consolidated; scientific phase closed**.
The previous 2026-10-04 recording date was corrected on the author's explicit
instruction. The frozen benchmark date and run ID remain 2026-10-03 /
`final-comparison-20261003`; genuine historical dates are preserved.

## 1. Three distinct results

### Central contribution

Learn a VSA memory, compile its fixed score algebraically, and evaluate the
equivalent scorer directly on CNN features. Explicit and compiled scoring agree
within the documented floating-point tolerance: maximum absolute error
1.31e-12 over 16,268 real patch comparisons in Sprint 7.

### Baseline v1 / M0

The Java/ONNX pipeline uses a deliberately simple positional directional readout.
The archetype is a standardized positional mean offset relative to the pooled
global population. It does not explicitly model within-position nominal
dispersion. This is a modeling choice, not a failure of Adjoint compilation.

### Subsequent M2 result

The accepted internal post-hoc ablation uses the same frozen features and existing
splits. M2 learns local CNN mean and per-position/per-coordinate dispersion.
It has the best localization results among the evaluated models on Bottle and
Metal Nut. It does not dominate every detection metric.

After positional Z calibration, M1 already controls for isotropic distance from
the non-unit mean under the stated conditions. M1-Z versus M2-Z supports the
complete anisotropic weighting scheme, not optimality of individual weights.

M2 was developed after the external benchmark. Its quality results do not inherit
M0's Java runtime measurements. It is not the model used in the frozen external
PaDiM/PatchCore run, nor a production-ready v2. The test categories had already
been observed, so this is not independent generalization evidence.

## 2. Public documentation hierarchy

- [README.md](../../README.md): Italian portfolio landing page.
- [README.en.md](../../README.en.md): complete English translation with the same
  structure, numbers and qualifications.
- [TECHNICAL_OVERVIEW.md](../TECHNICAL_OVERVIEW.md): coherent explanation from
  input image to score, with notation, model distinctions and reproduction.
- Benchmark, diagnostics and development reports: detailed evidence and history.

The landing page emphasizes Java/ONNX, measured quality, compiled scoring and
the engineering progression from a weak result to a targeted improvement.
It retains the original M0/PaDiM/PatchCore tables and a labelled M0 image.

## 3. Canonical supporting evidence

- [Frozen benchmark](../benchmark/BENCHMARK_FINAL_RESULT.md).
- [Positional/global diagnostic](../diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md).
- [Local-memory ablation](../diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).
- [Equivalence on real images](SPRINT7_RESULT.md).
- [Current state](../PROJECT_STATE.md) and [decision D15](../DECISION_LOG.md#d15---accettare-le-diagnosi-post-hoc-e-chiudere-il-ramo-per-questa-release).

No further scientific experiment is required for this phase.

## 4. Documentation corrections

- The reconciliation/recording date is 2026-10-07 in current-state notes, D15,
  this report and diagnostic revision notes. The roadmap's original design date
  remains historical.
- PROJECT_STATE marks earlier Git/workspace descriptions as historical and
  records that benchmark persistence and reload superseded the older absence.
- M1/M2 are direct CNN baselines; M3 estimates dispersion after VSA projection.
- The ablation no longer states a universal M1/M3/M2 ordering or denies that M0
  wins some metrics. Accepted metric tables are preserved.
- H and offset magnitudes are descriptive, not proof of detector quality or
  statistical support. The positional diagnostic module header uses the same
  interpretation; calculations are unchanged.
- M0/M3 Python timings describe one shared explicit-projection loop, not separate
  model latencies or production M0 inference.
- Diagnostic scripts read production/frozen inputs and write derived outputs;
  they are not literally side-effect-free.

## 5. Reproduction and publication boundary

The two post-hoc scripts require the external DatasetNinja dataset, frozen run
manifests and learned state under ignored `target/`; the ablation also reads
predictions and frozen maps. A fresh clone does not contain that evidence archive.

[bootstrap.ps1](../../tools/comparison/bootstrap.ps1) prepares Java classes and
`target/comparison-classpath.txt`; it does not recreate frozen results.
The technical overview lists Python dependencies, the relative dataset layout
and the prepared local interpreter used by the reports.

Published material consists of source, Markdown, existing model assets and
selected images. Generated feature dumps, models learned by the benchmark,
CSV/JSON outputs, environments and maps under `target/` remain excluded.
Session files, handoffs and IDE files also remain local.

## 6. Portfolio pass scope

New files created by the portfolio restructuring:

- `README.en.md`
- `docs/TECHNICAL_OVERVIEW.md`

Existing documents edited:

- `README.md`
- `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`
- `docs/architecture/FUTURE_ARCHITECTURE_ROADMAP.md`
- this reconciliation report and the two diagnostic reports

Only the module description in
`tools/prerun/positional_global_variance_diagnostic.py` changes.
The existing `local_memory_ablation.py` and Java feature-dump harness remain
part of the publication set without implementation changes in this pass.

No production source, accepted metric or frozen benchmark is intentionally
changed. No scientific experiment, benchmark, commit or push is part of this pass.
The final handoff records the static checks of links, bilingual content, file
scope and preserved source/metric fingerprints.

## 7. Closure

Scientific work remains closed. The current activity packages and explains the
accepted results for a professional portfolio. Any later scientific work is a
separate scope, not a prerequisite for publishing these documented results.
