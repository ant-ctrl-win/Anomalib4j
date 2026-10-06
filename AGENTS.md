# Anomalib4j - Agent Contract

## Project idea

Anomalib4j explores:

CNN latent features -> VSA learning/memory -> compiled CNN-space scoring.

The VSA is the learning/memory space.
The CNN latent space is the observation/runtime space.

The current anomaly model learns positional VSA archetypes from normal CNN patches.
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

## Current implementation

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

## Canonical supporting documents

- docs/PROJECT_STATE.md
- docs/architecture/ONNX_MODEL_CONTRACT.md
- docs/benchmark/MVTEC_DATASET_CENSUS.md
- docs/development/SPRINT7_RESULT.md

Read only the documents needed for the current task.