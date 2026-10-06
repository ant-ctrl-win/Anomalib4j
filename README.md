# Anomalib4j

**Learning a visual memory in VSA space and compiling its scoring directly into CNN features.**

An experimental project in **Java 21 and ONNX Runtime** that studies Adjoint compilation in a CNN + Vector Symbolic Architecture pipeline. Industrial anomaly detection is the proving ground: images, heatmaps and reproducible measurements make the result visible.

- **Verified equivalence:** explicit VSA scoring and the compiled filters agree to within errors on the order of `10^-12` on the real test data.
- **Cheap inference:** the compiled kernel evaluates the 196 positions in about **12 µs**, with no 10,000-dimensional projection at inference time.
- **A concrete applied result:** on Bottle, Image AUROC **1.000**; the detector runs at about **3.72 ms p50**, while the full pipeline produces the **900×900** heatmap in **9.49 ms p50** on CPU — over 100 FPS.

[Idea](#the-central-idea-compiling-the-score) · [Results](#benchmark-on-mvtec-ad) · [Heatmaps](#qualitative-examples) · [Reproducibility](#reproducibility) · [Documentation](#documentation)

## Why this project exists

Anomalib4j grew out of earlier neuro-symbolic visual experiments: a CNN extracts features, a projection maps them into VSA space, and a memory of archetypes enables comparison against previously acquired examples.

```text
image -> CNN -> features -> VSA projection -> comparison with memory
```

The CNN → VSA projection accounted for a significant part of the cost. The original question was therefore very precise: **can we turn the learned VSA model into an equivalent scorer that operates directly on normalized CNN features?**

This repository collects the derivation, the implementation and the verification of that step. Anomaly detection was chosen to evaluate it on a measurable visual task. The comparison with the Anomalib 2.6.2 implementations of PaDiM and PatchCore came later, to place the prototype against existing systems.

## The central idea: compiling the score

During learning, statistics and archetypes are built in VSA space. The Adjoint of the projection then transfers the comparison against each archetype into a filter in CNN space.

```text
MEMORY CONSTRUCTION

normalized CNN features
    -> random projection to 10,000 dimensions
    -> VSA statistics and standardization
    -> accumulation of examples into nominal archetypes
    -> Adjoint compilation
    -> filters in CNN space

INFERENCE

image -> CNN -> normalized features -> compiled filters -> score
```

The essential relation is:

```text
Aᵀ ((R x_norm / √D − μ) / σ) = Wᵀ x_norm + b
```

The filter carries the projection, the frozen statistics and the archetype. Feature normalization stays in the computation; **the VSA projection is no longer executed at inference**.

The equivalence concerns the **score**, i.e. the linear functional applied to the representation. It requires neither a bijection between spaces nor an inverse reconstruction of the CNN feature.

Verification on the 83 Bottle images of Sprint 7 compared **16,268 cells**, with a maximum absolute error of **1.31 × 10^-12**. In the same experiment, the in-process comparison after the encoder measured about **651 ms** for explicit VSA scoring and **13 µs** for the compiled path; the later JMH measurement of the kernel is about **12 µs**. These are scoring costs, distinct from full-pipeline latency and from the comparison with other methods.

Details: [equivalence and internal benchmark](docs/development/SPRINT7_RESULT.md), [JMH measurements](docs/development/PERFORMANCE_BASELINE_RESULT.md), [mathematical conditions of the compilation](docs/architecture/FUTURE_ARCHITECTURE_ROADMAP.md#151-mathematical-clarification-of-cnnvsa-compiled-equivalence).

## Architecture: the "eyes" and the memory

The CNN is the **eyes** of the system: it turns the image into features. The memory and the detector work on what these eyes make available.

To keep the experiment focused on Adjoint, the backbone is a **pretrained, frozen MobileNetV4**, with no fine-tuning on MVTec or on the industrial categories. Its features are therefore not optimized for this specific domain.

The detector is a deliberately simple experimental baseline:

```text
MobileNetV4 frozen
    -> 14×14×96 feature map
    -> 196 local descriptors
    -> one nominal archetype per position
    -> compiled Adjoint scoring
    -> raw = 1 − score
    -> held-out positional Z calibration
    -> heatmap + maximum as the image score
```

The calibration estimates the mean and standard deviation of the raw scores on normal images separate from those used to learn the archetypes. The scientific heatmap keeps the Z values without clamping; bilinear upsampling brings it to the original resolution.

| Component | Current choice |
|---|---|
| Runtime | Java 21, ONNX Runtime 1.30.0, CPU |
| CNN input | RGB 224×224, bicubic resize, ImageNet normalization |
| Feature | SPATIAL_14, HWC layout, 14×14×96 |
| Memory | Continuous VSA archetypes, no binarization |
| Projection at learning time | Rademacher, D = 10,000, seed 42 |
| Scoring | Adjoint filters on normalized features |
| Evaluation | Image AUROC, Pixel AUROC, AUPRO@0.30 |

## Incremental memory and archetypes

**A new example updates the memory, not the eyes.** Separating backbone and memory makes it natural to fold new examples into the archetypes **without CNN backpropagation**, then recompile the filters. This is an important architectural motivation of the CNN + VSA line, compatible with on-the-fly archetypes, few examples and incremental updates.

With encoder, projection and statistics fixed, the memory can accumulate new observations in the same space. Doing so correctly requires the accumulators and the counts; changes to the statistics require consistent handling of memory and calibration.

The frozen backbone avoids overwriting encoder weights during incremental memory updates — one classical mechanism behind catastrophic forgetting. **This benchmark does not, however, demonstrate a general solution to continual learning:** memory interference, few-shot quality and long-run behaviour were not measured here.

## Benchmark on MVTec AD

CPU comparison on the same hardware between Anomalib4j and the **Anomalib 2.6.2** implementations of PaDiM and PatchCore. Final run: `final-comparison-20261003`.

### Quality

Higher is better. Image AUROC measures the separation between healthy and defective images; Pixel AUROC evaluates pixels; AUPRO@0.30 evaluates the coverage of anomalous regions up to a false-positive rate of 0.30.

| Category | Method | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---|---:|---:|---:|
| Bottle | **Anomalib4j** | **1.000** | **0.962** | **0.877** |
| Bottle | PaDiM | 0.998 | 0.978 | 0.922 |
| Bottle | PatchCore | 1.000 | 0.985 | 0.944 |
| Metal Nut | **Anomalib4j** | **0.758** | **0.693** | **0.380** |
| Metal Nut | PaDiM | 0.952 | 0.941 | 0.851 |
| Metal Nut | PatchCore | 0.998 | 0.987 | 0.940 |

### CPU latency: detector and full-resolution localization

Median p50, batch 1, image already decoded in RAM. The Anomalib4j detection path alone is about **3.72 ms** on Bottle and **3.70 ms** on Metal Nut. The table below instead uses the end-to-end comparison window: preprocessing, encoder, detector and production of the map at the original resolution; it excludes file loading, model construction and the evaluator. On Bottle, **9.49 ms** for a 900×900 heatmap correspond to over 100 inferences per second.

| Category | Final resolution | Anomalib4j | PaDiM | PatchCore |
|---|---|---:|---:|---:|
| Bottle | 900×900 | **9.49 ms** | 57.60 ms | 247.42 ms |
| Metal Nut | 700×700 | **5.78 ms** | 44.46 ms | 222.93 ms |

Hardware: **AMD Ryzen AI 9 HX 370**, Windows 11. JMH for Java and a Python harness for the competitors, with warm-up and repeated measurements across two processes/forks.

The comparison involves native systems: MobileNetV4 for Anomalib4j, ResNet18 for PaDiM and Wide ResNet50-2 for PatchCore, each with its own preprocessing. Anomalib4j uses 167 images for learning + 42 for calibration on Bottle, 176 + 44 on Metal Nut; the competitors use all 209/220 normal images for fitting. The test sets contain 83 and 115 images. Metrics are computed with a common evaluator.

The result is a **measured quality/latency trade-off**, not a general superiority. The end-to-end advantage also includes differences in backbone and runtime; it is not attributable to Adjoint alone. Protocol, p95/p99, fitting and memory are in the [full report](docs/benchmark/BENCHMARK_FINAL_RESULT.md).

## Qualitative examples

The images show the behaviour of the prototype and serve as a qualitative visualization, not as statistical evidence.

- **Bottle**: **RAW discrepancy** maps (`1 - compiledScore`), the canonical qualitative localization outputs of the SPATIAL_14 Sprint-8 baseline; the middle panel shows the ground truth in red.
- **Metal Nut**: **positional Z** map (the scientific heatmap keeps the Z values without clamping); the middle panel is the Z map presented on a visualization scale.

All panels use the same **blue → red** presentation colormap. The display ranges are **category-specific and not numerically comparable** between Bottle and Metal Nut. The colors are not probabilities, and the scaling is for display only: it does not change any reported metric.

<p align="center">
  <img src="docs/images/bottle-broken-large.png" alt="Bottle broken large: original, ground truth and RAW discrepancy overlay" width="880">
</p>

**Bottle · broken_large_000** — RAW discrepancy map (`1 - compiledScore`). The response follows part of the extended fracture on the right and lower edge; the color serves display only.

<p align="center">
  <img src="docs/images/bottle-broken-small.png" alt="Bottle broken small: original, ground truth and RAW discrepancy overlay" width="880">
</p>

**Bottle · broken_small_000** — RAW discrepancy map (`1 - compiledScore`). A small chip shows the level of detail of the case and the local response on the coarse grid; the color serves display only.

<p align="center">
  <img src="docs/images/bottle-good.png" alt="Bottle good: original, ground truth and RAW discrepancy overlay" width="880">
</p>

**Bottle · good_000** — RAW discrepancy map (`1 - compiledScore`). A healthy example with a contained response on the shared scale of the Bottle examples; the color serves display only.

<p align="center">
  <img src="docs/images/metal-nut-good-activation.png" alt="Metal Nut good: original, positional Z map and overlay (blue -> red)" width="880">
</p>

**Metal Nut · good_009** — Positional Z map. A healthy part produces a marked internal activation: a concrete example of the limitation of the current configuration. The blue → red colormap is Metal-Nut-specific and serves display only.

The published Bottle panels are the RAW localization outputs (SPATIAL_14 Sprint-8 baseline), copied from the local artifacts in `target/bottle-localization/` into `docs/images/`; the colormap serves display only and `target/` stays excluded from the repository.

## What worked, and where the limits show

**Adjoint met its original goal:** the derivation became Java code, and the compiled score was verified against the explicit path on synthetic features and real images. The VSA projection left the hot path.

**Bottle shows the potential of the prototype.** The separation is already strong before calibration: RAW Image AUROC about 0.986. Z brings it to 1.000, with Pixel AUROC about 0.962. A simple detector can therefore make effective use of pretrained features without task-specific fine-tuning.

**Metal Nut shows the limit of the current combination of features and local memory.** RAW is already weak; Z improves the aggregate but does not close the gap. Pose and rotation do not appear to be the dominant cause in the analyses performed, and border/background effects do not explain the overall result. Shape defects such as `bent` are consistent with a limitation of the current local readout and motivate testing an explicit regional or global representation.

This does not imply that MobileNetV4 produces weak features in general: here they were not optimized for the domain. The capacity of the single archetype and the aggregation strategy may also contribute to the result. The diagnostics are collected in the [Bottle](docs/diagnostics/BOTTLE_SYMMETRIC_DIAGNOSTIC.md), [Metal Nut RAW/Z](docs/diagnostics/METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md) and [border](docs/diagnostics/BORDER_EXCLUSION_SENSITIVITY.md) reports.

## What we learned

In the current case — linear projection, superposition of examples, frozen statistics and linear readout — much of the pipeline can be collapsed into CNN feature space. **The 10,000-dimensional expansion is not, by itself, the demonstrated value.**

This opens a possibility that emerged during the project: in the future, building at least part of the memory directly in CNN feature space. Doing so could also remove the offline cost of the high-dimensional projection and simplify the pipeline further. It would, however, be a different learning process: the possible benefit to quality has yet to be tested. The current Adjoint, by contrast, preserves exactly the learned score and reduces its inference cost.

The useful distinction is between four levels:

| Level | Role |
|---|---|
| **Representation** | What information the CNN makes accessible |
| **Memory** | How examples and archetypes describe the subject |
| **Compiled inference** | How to apply the same score with less work |
| **VSA operations** | How to compose, associate and query representations |

Bundling, archetypes and incremental updates remain interesting. Binding, multimodal memories and symbolic or relational operations offer further motivations for VSA, beyond mere dimensional expansion. Their specific advantage requires dedicated comparisons; it does not follow automatically from the results of this benchmark.

## Future work

The directions outlined for a possible v2 mainly concern feature quality and the memory model:

- choose layers, subspaces or adapters depending on the task and the nominal variations;
- evaluate complementary local and regional representations with a single CNN;
- compare different detectors on the same multiscale features;
- study few-shot/incremental updates and compositional memory operations.

These are perspectives that follow the Adjoint validation, not objectives already achieved. The [technical roadmap](docs/architecture/FUTURE_ARCHITECTURE_ROADMAP.md) distinguishes mathematical properties, hypotheses and possible experiments, without modifying the frozen benchmark.

## Reproducibility

The core uses **Java 21, Maven and ONNX Runtime**; tests are in JUnit 5. JMH measures the Java paths. The external comparison uses a separate Python environment with Anomalib 2.6.2 and weights identified by hash.

To run only a targeted group of algebraic and memory tests, without training on real images:

```shell
mvn -B "-Dtest=AdjointEquivalenceTest,DenseRademacherProjectionTest,ProjectionStatisticsAccumulatorTest,PositionalMemoryBuilderTest" test
```

The normal `mvn test` also includes tests on real data and should not be treated as a quick smoke test. The MVTec AD / DatasetNinja dataset is external to the repository. The comparison runners were validated in the documented Windows workspace; the benchmark protocol, environment and execution commands are in the [execution instructions](docs/benchmark/BENCHMARK_PHASE2_IMPLEMENTATION.md) and the [environment preparation](docs/benchmark/BENCHMARK_PRERUN_VERIFICATION.md).

The original local final run retained its manifest, splits, hashes, model state, lossless maps, predictions and latency samples under `target/comparison/final-comparison-20261003/`. That directory is a local evidence store, not part of the public repository, and `mvn clean` would delete it. The Markdown reports carry the results and the references to the artifacts.

## A line of CNN + VSA projects

Anomalib4j belongs to a line of experiments that includes the Java VSA project on PlantVillage, the Android app / VisionSector, VisionSectorHub and other CNN + VSA work. Here the specific theme is the **Adjoint compilation of the memory into a scorer on CNN features**.

The individual projects and their results will be documented separately. Related repositories will be linked here as they are published.

## Documentation

| To go deeper | Document |
|---|---|
| State and architecture | [PROJECT_STATE](docs/PROJECT_STATE.md) |
| Equivalence on real images | [Sprint 7](docs/development/SPRINT7_RESULT.md) |
| Comparative protocol | [BENCHMARK_CONTRACT](docs/benchmark/BENCHMARK_CONTRACT.md) |
| Results, latency, fitting and memory | [BENCHMARK_FINAL_RESULT](docs/benchmark/BENCHMARK_FINAL_RESULT.md) |
| Localization conventions | [LOCALIZATION_CONVENTIONS](docs/benchmark/LOCALIZATION_CONVENTIONS.md) |
| Held-out calibration | [HELDOUT_CALIBRATION_RESULT](docs/benchmark/HELDOUT_CALIBRATION_RESULT.md) |
| Coordinates and spatial activations | [SPATIAL_EDGE_ARTIFACT_AUDIT](docs/diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md) |
| Future directions and limits of the compilation | [FUTURE_ARCHITECTURE_ROADMAP](docs/architecture/FUTURE_ARCHITECTURE_ROADMAP.md) |
