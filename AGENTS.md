# Anomalib4j - Agent Contract

## Project idea

Anomalib4j explores:

CNN latent features -> VSA learning/memory -> compiled CNN-space scoring.

The VSA is the learning/memory space.
The CNN latent space is the observation/runtime space.

The historical M0 anomaly model learns positional VSA archetypes from normal CNN patches.
At inference, comparison against an archetype is compiled back into CNN feature space through the adjoint of the projection.

## Critical invariant

Explicit VSA scoring and compiled Adjoint scoring must remain numerically equivalent.

Current pipeline for patch x:

x_n = x / max(||x||, epsilon)
y   = (1/sqrt(D)) R x_n
z   = (y - mu) / sigma
score_explicit = A^T z

Compilation:

q = A / sigma
W = (1/sqrt(D)) R^T q
b = -mu^T q

score_compiled = W^T x_n + b

Do not change this mathematics unless the task explicitly requests it.

## M0 baseline implementation (preserved)

Language: Java
Build: Maven
Encoder: MobileNetV4 ONNX
Current baseline: SPATIAL_14 -> 14x14x96 HWC
VSA dimensions: 10000
Projection: dense Rademacher
Projection seed: 42
CNN normalization epsilon: 1e-6
Statistics std floor: 1e-8
Preprocessing: imagenet-rgb-resize224-bicubic-v1

## Working rules

- Inspect existing code before changing APIs.
- Prefer minimal changes.
- Do not redesign unrelated components.
- Do not silently change preprocessing, scoring, normalization, projection, bundling or model identity.
- Preserve existing tests.
- Separate unit tests from expensive real-data integration runs when practical.
- Do not modify the dataset.
- Do not commit or reset Git unless explicitly requested.
- Reports/results belong in Markdown files when requested.
- Anomalib is a benchmark/reference, not the architecture being reproduced.

## Portfolio maintenance

Anomalib4j is under a portfolio presentation freeze, not an open research phase.
The public/default system is MobileNetV4 Conv Small -> 14x14x96 -> M2.
No further model, encoder, scoring, dataset, VSA or ablation campaign is required
or authorized unless the user explicitly opens a separate future phase.
Preserve retained evidence and mathematical contracts.
README.md is the primary Italian landing page; README.en.md is its English
equivalent. Keep their structure, results, tables, links and technical claims
aligned. Research chronology belongs under docs/, not in either landing page.

## Canonical reading order

For a fresh Codex/OpenCode session:

1. [AGENTS.md](AGENTS.md): invariants and working rules.
2. [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md): current implementation and active phase.
3. [docs/EXPERIMENT_INDEX.md](docs/EXPERIMENT_INDEX.md): generations, retained evidence and reproducibility status.
4. The relevant benchmark or diagnostic report linked from the index.
5. [docs/TECHNICAL_OVERVIEW.md](docs/TECHNICAL_OVERVIEW.md) and implementation when needed.

[docs/DECISION_LOG.md](docs/DECISION_LOG.md) records dated decisions and supersession.
The encoder contract and dataset census are linked from the current state and index.
Read only the detailed documents needed for the task.

Repository code and evidence are authoritative. Chat history, ignored handoff files
and stale session notes are not authoritative and must not be required to resume.
Historical reports describe their recorded run, not the current workflow.
M0 is the preserved Java VSA/Adjoint baseline. M2 is the selected candidate after
the completed all-15 local-model ablation and now has a separate Java path.
M2 uses normalized CNN descriptors, FIT local mean/sample std (floor 1e-6),
mean squared standardized residuals, HELD-OUT positional Z (std floor 1e-6),
and native max image score. Descriptor norm below 1e-6 is rejected. Do not route
M2 through VSA/Adjoint or change either model's mathematics implicitly.

Generic M2 has exactly two declared `EncoderSpec` selections: SMALL_SPATIAL_14
(14x14x96, default) and LARGE_SPATIAL_14 (14x14x192). Only channel count/encoder
changes; preprocessing, formulas and evaluator do not. M0, SPATIAL_28, canonical
Metal Nut and historical diagnostics retain their Small contracts. See
[encoder integration](docs/architecture/M2_ENCODER_INTEGRATION.md). The completed
[all-15 S/L comparison](docs/benchmark/MOBILENETV4_SMALL_LARGE_FINAL_COMPARISON.md)
retains Small as the recommended default, with mixed quality gains and higher
latency for Large. Do not reuse Small statistics for Large.
