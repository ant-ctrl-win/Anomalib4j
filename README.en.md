# Anomalib4j

**Industrial anomaly detection in Java: ONNX inference on CPU, local visual memory and compiled VSA scoring.**

[Versione italiana](README.md)

Anomalib4j is a computer-vision project built with **Java 21 and ONNX Runtime**, using **MobileNetV4**, to detect image anomalies and locate them through heatmaps.

The project addresses two main aspects: reducing the cost of comparison against a visual memory and better describing variation among normal examples. The Java baseline compiles VSA scoring into equivalent CNN-feature filters; the subsequent M2 model improves localization by learning local dispersion. Code, tests and measurements document both results.

## Key results

- **Java pipeline on CPU:** preprocessing, ONNX feature extraction, local scoring and heatmap generation.
- **M2, the current best detector:** for localization, it achieves AUPRO **0.916 on Bottle** and **0.624 on Metal Nut**, compared with M0's **0.877** and **0.380**.
- **Verified Adjoint equivalence:** maximum error **1.31 × 10⁻¹²** between explicit and compiled VSA scores, over **16,268 real patches**.
- **M0 performance:** about **12 µs for 196 positions** in the JMH kernel measurement; on Bottle, **3.723 ms p50** for detection and **9.486 ms p50** for full localization.
- **Same-hardware benchmark:** v1 compared with PaDiM and PatchCore, quality evaluated using common conventions and execution costs documented.

**M2 metrics and M0 latencies belong to different detector versions.**

## The project at a glance

The input is an RGB image. Pretrained, frozen MobileNetV4 extracts a **14×14×96** grid: 196 local descriptors with 96 components each. The memory learns nominal behaviour from normal images.

At each position the detector computes a deviation; calibration uses normal images separate from fitting. The maximum of the calibrated map provides the image score; interpolation produces the heatmap at the original resolution.

```text
RGB image
    |
Preprocessing + MobileNetV4 / ONNX
    |
14×14×96 features --> normalized descriptors
    |
Local memory + positional scoring
    |
Positional calibration
    +--> image score
    `--> 14×14 map --> full-resolution heatmap
```

The M0 baseline core is Java. Comparison tools and the M2 ablation include Python; M2 operates on the same features extracted by the Java encoder.

![Bottle: image, annotation and RAW heatmap](docs/images/bottle-broken-large.png)

*M0/v1 baseline output, RAW localization variant: image, defect annotation and overlay. This is not an M2 output. Colours display a score on a visualization scale, not probabilities.*

## Current best detector

**M2 is the project's current best detector for localization on the two evaluated categories.**

At each position it learns the mean feature behaviour and the normal variability of each component. A deviation in a stable component therefore matters more than the same deviation in a naturally variable component. The score combines squared standardized residuals.

Results after positional Z calibration; higher is better:

| Category | Model | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---|---:|---:|---:|
| Bottle | M0 — v1 baseline | 1.000000 | 0.961664 | 0.877447 |
| Bottle | **M2 — local dispersion** | **1.000000** | **0.975697** | **0.915738** |
| Metal Nut | M0 — v1 baseline | 0.757576 | 0.692630 | 0.379794 |
| Metal Nut | **M2 — local dispersion** | **0.845552** | **0.849318** | **0.624208** |

Image AUROC measures separation between normal and anomalous images; Pixel AUROC measures separation at the pixel level; AUPRO evaluates coverage of defective regions, considering false-positive rates up to 0.30.

**M2 was developed after the frozen v1 benchmark**, keeping the CNN features and splits. The external PaDiM/PatchCore comparison therefore concerns M0.

## Compiling VSA scoring

M0 builds a memory in VSA space, a **10,000-dimensional** representation, accumulating statistics and nominal examples into positional archetypes.

Once the memory is fixed, projection, standardization and comparison against the archetype can be incorporated into a filter on the **96 CNN features**. This transformation is Adjoint compilation.

The high-dimensional projection disappears from inference while preserving the score. Feature normalization remains in the path. Verification on the 83 Bottle images compared **16,268 cells**, with maximum absolute error **1.31 × 10⁻¹²**.

The later JMH kernel measurement is about **12 µs per map**. This is the scoring cost: CNN extraction and heatmap construction belong to full-pipeline latency.

## Frozen Java v1 baseline benchmark

**Before the introduction of M2.** The tables preserve the systems actually executed in the original run, without replacing them with later results.

Comparison with the **Anomalib 2.6.2** implementations of PaDiM and PatchCore, on **AMD Ryzen AI 9 HX 370**, Windows 11:

| Category | Method | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---|---:|---:|---:|
| Bottle | **Anomalib4j M0** | **1.000** | **0.962** | **0.877** |
| Bottle | PaDiM | 0.998 | 0.978 | 0.922 |
| Bottle | PatchCore | 1.000 | 0.985 | 0.944 |
| Metal Nut | **Anomalib4j M0** | **0.758** | **0.693** | **0.380** |
| Metal Nut | PaDiM | 0.952 | 0.941 | 0.851 |
| Metal Nut | PatchCore | 0.998 | 0.987 | 0.940 |

Median latency for localization at the original resolution:

| Category | Resolution | M0 | PaDiM | PatchCore |
|---|---|---:|---:|---:|
| Bottle | 900×900 | **9.49 ms** | 57.60 ms | 247.42 ms |
| Metal Nut | 700×700 | **5.78 ms** | 44.46 ms | 222.93 ms |

M0 detection alone measures **3.723 ms** on Bottle and **3.695 ms** on Metal Nut. Measurements include preprocessing and inference, batch 1, with the image already decoded; file loading and model construction are excluded.

Backbones and fitting budgets differ across systems. Latencies use JMH for Java and a Python harness for competitors. The result measures a quality/cost trade-off of the entire pipeline; it does not attribute the advantage to Adjoint alone.

## From a limitation to an improvement

M0's weak localization on Metal Nut prompted inspection of the memory and its statistics. The archetype describes a positional direction relative to the global population, without explicitly modelling local nominal dispersion.

M2 introduces that dispersion while keeping the features frozen. The comparison verifies improvement on both categories, particularly Metal Nut: **measurement of the limitation, diagnosis, targeted change and verification**.

## What this project demonstrates

- **AI integration in Java:** ONNX Runtime, preprocessing and descriptor handling.
- **Design and optimization:** positional memory and scoring compilation.
- **Verification and benchmarking:** numerical tests, JMH and system comparison.
- **Measurable diagnosis:** a change to the memory model, comparison on the same features and documented results.

## Getting started and further reading

Core prerequisites: **JDK 21 and Maven**. Tests use JUnit 5. To compile and run a targeted group of algebra and memory tests, without training on real images:

```shell
mvn -B "-Dtest=AdjointEquivalenceTest,DenseRademacherProjectionTest,ProjectionStatisticsAccumulatorTest,PositionalMemoryBuilderTest" test
```

The normal `mvn test` also includes real-data tests. The dataset and benchmark artifacts are external to the public copy; full reproduction prerequisites are in the technical overview.

- [Technical overview](docs/TECHNICAL_OVERVIEW.md): data flow, models, formulas and reproducibility.
- [Equivalence on real images](docs/development/SPRINT7_RESULT.md).
- [Full benchmark](docs/benchmark/BENCHMARK_FINAL_RESULT.md).
- [Local-memory ablation](docs/diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).
- [Positional/global diagnostic](docs/diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md).

## Status and limitations

The project is a prototype, not a production-ready product. Results concern two MVTec AD categories with a frozen backbone; the internal ablation uses already-observed categories and does not establish independent generalization. M2 is not included in the external PaDiM/PatchCore run, and the results do not establish general superiority.
