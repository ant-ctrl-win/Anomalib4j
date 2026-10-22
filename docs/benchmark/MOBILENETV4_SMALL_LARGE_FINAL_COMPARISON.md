# MobileNetV4 Small / Large: final generic M2 comparison

## 1. Purpose

Completed **2026-10-22**: fresh, matched Java M2-P evaluation on all 15 MVTec AD
categories, followed by matched CPU JMH measurements. Large improves all three
macro metrics, with mixed category outcomes and higher latency. **Small remains
the recommended portfolio default.**

Only the frozen encoder changes. Both use the same whole-image RGB bicubic224 /
ImageNet preprocessing, unit descriptors, strict positional FIT mean/sample std,
1e-6 floors, mean squared standardized residual, held-out positional alpha/beta,
native maximum image score and unchanged localization evaluator. There is no CNN
fine-tuning, semantic canonicalization, TEST tuning or additional encoder.

Retained evidence root: [`m2-small-large-final-20261022-validated`](../../target/comparison/m2-small-large-final-20261022-validated).
Its [protocol](../../target/comparison/m2-small-large-final-20261022-validated/protocol.json)
was frozen at 13:32:05 UTC before this campaign's TEST evaluation. Source hashes,
workspace snapshots, manifests, features, parity arrays, predictions, metrics,
model archives, adaptation timings and JMH/profile outputs remain there. These
are ignored local artifacts and require separate preservation outside a clone.
The [summary](../../target/comparison/m2-small-large-final-20261022-validated/summary.json),
[quality CSV](../../target/comparison/m2-small-large-final-20261022-validated/quality.csv),
[adaptation CSV](../../target/comparison/m2-small-large-final-20261022-validated/adaptation.csv)
and [latency CSV](../../target/comparison/m2-small-large-final-20261022-validated/latency.csv)
retain precision beyond the rounded tables below. `summarize.py` in that root
validates and derives these summaries without rerunning inference.

Both runs contain exactly 15 categories, byte-identical FIT/CAL/TEST manifests,
matching TEST image hashes/labels/original geometry, all metrics and fresh features.
Small also passes the existing retained-array and quality regression guards;
both encoders pass Java/Python mathematical parity and persistence reload checks.
Detector, preprocessing, evaluator and benchmark sources were frozen throughout.

Two orchestration failures remain disclosed: the initial unsuffixed output root
stopped on duplicate exclusive creation of an input-hash file before evaluation;
the validated root completed Small before a duplicate status-file write stopped
the controller. Separate normal/final hash files and append-only events fixed
these issues. Small was retained, then Large and both JMH runs completed. The
explicit `orchestration-amendment-1792676848469589400.json` records the sole
Small/Large source-snapshot difference: controller logging/resume handling in
`final_encoder_campaign.py`. Scientific and benchmark code did not change.

Final regression checks: **262 Java unit/smoke tests and 21 Python tests passed**,
with two existing Typer/Click deprecation warnings. The final Java selection was
`mvn -B -Pbenchmark '-Dtest=*Test,!BottleRealEvaluationTest,!BottleTrainingTest' test`;
Python used `python -m pytest tools/ablation -q`. Earlier broad Maven selections
were stopped during historical retraining; they are not claimed as completed
checks. The final selection excludes historical real-data retraining and `IT`
classes. No completed S/L measurement was repeated for these checks.

## 2. Hardware/runtime

| Field | Matched S/L measurement environment |
|---|---|
| CPU | AMD Ryzen AI 9 HX 370 with Radeon 890M; 12 physical / 24 logical cores |
| OS | Windows 11 Pro, build 26200 |
| Reported physical RAM | 67,772,403,712 bytes |
| Power plan | Balanced; GUID `381b4222-f694-41f0-9685-ff5bb260df2e` |
| Java / VM | Eclipse Adoptium JDK 21.0.9 / `21.0.9+10-LTS` |
| Java ONNX Runtime | 1.30.0 CPU package; default session/internal threading |
| Executed provider | `CPUExecutionProvider` in all 12 retained execution profiles |
| JMH | 1.37; SampleTime; 1 caller; batch 1; 2 forks per scope |
| Warmup / measurement | 5 x 2 seconds / 10 x 2 seconds per fork |
| Heap / affinity mask | `-Xmx2g` / `16777215`, all 24 logical CPUs allowed |

CPU and Azure providers are available, but Azure is inactive in the executed
node traces. Hardware snapshots immediately before both JMH runs agree on CPU,
OS, RAM and power plan; background CPU was 3% at each snapshot. Runtime records
agree on Java, ORT, heap, affinity mask and fixture SHA. This is a desktop host,
without dedicated-core isolation or a sustained camera throughput measurement.

All timing scopes use the same predecoded **900x900** Bottle normal
`bottle_good_000.png`, SHA-256
`9e7bfe658efe9b6db2607a96405b4ac6880936eaf93b89c85bcd311122ceb57b`.
JMH excludes image decode, disk I/O, model loading and session initialization.
SampleTime values in microseconds are converted to milliseconds below; means and
percentiles are read directly from the aggregate JMH JSON, not summed stages.

## 3. Encoder contracts

Both ONNX artifacts accept float32 NCHW `[1,3,224,224]` and emit float32 NHWC,
flattened HWC. Preprocessing is `imagenet-rgb-resize224-bicubic-v1`, without
crop, alignment or letterboxing. Large's upstream `r256` tag does not change the
export's fixed 224 input used here.

| Field | Small | Large |
|---|---|---|
| Encoder spec | `SMALL_SPATIAL_14` | `LARGE_SPATIAL_14` |
| Upstream identity | `mobilenetv4_conv_small.e2400_r224_in1k` | `mobilenetv4_conv_large.e500_r256_in1k` |
| Actual resource | `mobilenetv4_spatial_14x14.onnx` | `mobilenetv4_large_spatial_14x14.onnx` |
| Input / output names | `input_image` / `spatial_features` | `input` / `features` |
| Output | `[1,14,14,96]` | `[1,14,14,192]` |

Small SHA-256: `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`.
Large SHA-256: `ac83d397ee76768ba7ea5b2ec054337e07a454286403e27e72c832100021d6c3`.

Actual files were inspected once before the campaign using ONNX 1.19.0; both
passed `onnx.checker`. The [encoder inspection](../../target/comparison/m2-small-large-final-20261022-validated/encoder-artifacts.json)
records hashes, bytes, graph counts and tensor contracts. Large's
[externally supplied Colab export verification](../architecture/MOBILENETV4_LARGE_EXPORT_VERIFICATION.md)
is distinct from the local Java/Python M2 checks. PyTorch-to-ONNX export parity
was not rerun locally. Small's exact checkpoint-to-export provenance remains
unresolved; this comparison identifies the actual bytes used.

## 4. Resource footprint

| Measured quantity | Small | Large |
|---|---:|---:|
| Truncated ONNX file, bytes | 1,269,313 | 13,578,076 |
| Initializer values in actual graph | 314,272 | 3,335,376 |
| Graph nodes / initializers | 46 / 48 | 93 / 108 |
| Serialized M2 v2 archive per category, bytes | 304,381 | 605,436 |
| Numerical M2 payload, bytes | 304,192 | 605,248 |

All 15 archives for each encoder have the reported size. M2 stores float64 local
mean and effective std (`196*C` each), alpha and beta (`196` each), plus version,
encoder identity, hash, preprocessing identity and HWC geometry. It does not store
training descriptors. ONNX is separate from this archive. These are file/payload
sizes, not process RSS; no matched process-memory measurement is claimed.
Large costs **10.697x ONNX bytes** and **1.989x serialized M2 bytes**.

## 5. All-15 quality results

All metrics lie in [0,1]; AUPRO is normalized AUPRO@0.30. Delta means Large minus
Small in absolute metric units. Counts are shared and listed in section 8.

| Category | S Image | L Image | Delta | S Pixel | L Pixel | Delta | S AUPRO | L AUPRO | Delta |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| bottle | 1.000000 | 0.999206 | -0.000794 | 0.975697 | 0.966159 | -0.009538 | 0.915738 | 0.882232 | -0.033506 |
| cable | 0.818216 | 0.820090 | +0.001874 | 0.858937 | 0.892665 | +0.033728 | 0.679613 | 0.735449 | +0.055836 |
| capsule | 0.847228 | 0.873953 | +0.026725 | 0.968015 | 0.970362 | +0.002347 | 0.814402 | 0.832240 | +0.017838 |
| carpet | 0.946228 | 0.982745 | +0.036517 | 0.985635 | 0.988517 | +0.002882 | 0.918112 | 0.945380 | +0.027268 |
| grid | 0.961571 | 0.970760 | +0.009190 | 0.945783 | 0.966174 | +0.020391 | 0.858965 | 0.905062 | +0.046097 |
| hazelnut | 0.617500 | 0.462857 | -0.154643 | 0.895854 | 0.935446 | +0.039592 | 0.781311 | 0.784088 | +0.002777 |
| leather | 0.998302 | 1.000000 | +0.001698 | 0.991365 | 0.990715 | -0.000650 | 0.961311 | 0.972093 | +0.010782 |
| metal_nut | 0.845552 | 0.898338 | +0.052786 | 0.849318 | 0.915602 | +0.066284 | 0.624208 | 0.732671 | +0.108463 |
| pill | 0.812875 | 0.808511 | -0.004364 | 0.887255 | 0.911121 | +0.023866 | 0.822227 | 0.875711 | +0.053484 |
| screw | 0.564870 | 0.642550 | +0.077680 | 0.863996 | 0.871782 | +0.007787 | 0.602107 | 0.652300 | +0.050192 |
| tile | 0.980519 | 0.999278 | +0.018759 | 0.940407 | 0.941268 | +0.000861 | 0.849164 | 0.852362 | +0.003198 |
| toothbrush | 0.902778 | 0.863889 | -0.038889 | 0.959277 | 0.975422 | +0.016145 | 0.690294 | 0.752928 | +0.062633 |
| transistor | 0.906667 | 0.932500 | +0.025833 | 0.952651 | 0.965002 | +0.012351 | 0.848442 | 0.842390 | -0.006053 |
| wood | 0.914912 | 0.992982 | +0.078070 | 0.930064 | 0.939539 | +0.009475 | 0.840309 | 0.896755 | +0.056446 |
| zipper | 0.959821 | 0.922532 | -0.037290 | 0.960991 | 0.967632 | +0.006641 | 0.882107 | 0.899092 | +0.016985 |

The same evaluator consumes native unclamped positional Z, the same original
image/mask geometry and the existing interpolation/metric definitions; see
[localization conventions](LOCALIZATION_CONVENTIONS.md). No category is omitted
because of weak results, and no model was changed after viewing these values.

## 6. Macro quality comparison

Each of the 15 categories has equal weight; these are not pooled image/pixel
metrics or repeated-run confidence estimates.

| Metric | Small | Large | Large minus Small | Percentage-point delta |
|---|---:|---:|---:|---:|
| Image AUROC | 0.871803 | 0.878013 | +0.006210 | +0.6210 |
| Pixel AUROC | 0.931016 | 0.946494 | +0.015477 | +1.5477 |
| AUPRO@0.30 | 0.805887 | 0.837383 | +0.031496 | +3.1496 |

## 7. Category deltas

Wins use the strict sign of the full-precision delta, as frozen before TEST.
There are no exact ties. No significance threshold is introduced.

| Metric | Large improves | Small improves |
|---|---|---|
| Image, 10 / 5 | cable, capsule, carpet, grid, leather, metal_nut, screw, tile, transistor, wood | bottle, hazelnut, pill, toothbrush, zipper |
| Pixel, 13 / 2 | cable, capsule, carpet, grid, hazelnut, metal_nut, pill, screw, tile, toothbrush, transistor, wood, zipper | bottle, leather |
| AUPRO, 13 / 2 | cable, capsule, carpet, grid, hazelnut, leather, metal_nut, pill, screw, tile, toothbrush, wood, zipper | bottle, transistor |

Largest Image gain: **wood +0.078070**; largest loss: **hazelnut -0.154643**.
Largest Pixel gain: **metal_nut +0.066284**; largest loss: **bottle -0.009538**.
Largest AUPRO gain: **metal_nut +0.108463**; largest loss: **bottle -0.033506**.
Hazelnut illustrates that stronger pixel ranking does not guarantee a better
native-maximum image ranking. Bottle regresses on all three metrics.

## 8. Fitting/calibration cost

The normal-only timer directly measures the current adaptation workflow from
normal input processing to serialized/reloaded ready model state. It includes
integrity hashes, image loading/decode/preprocessing/CNN, fresh JVM/session startup
for each split, descriptor/NPY I/O, normalization, statistics, alpha/beta,
serialization/reload and normal diagnostics. TEST extraction, TEST inference and
evaluation occur afterwards and are excluded. A later inference session startup
is not included. No training epochs or backpropagation occur.

The retained deterministic seed-42 manifests use floor(0.8*N) FIT, remaining
training-good images CAL; the established small-set clamp reserves valid sample
statistics. No clamp is needed for these populations. FIT/CAL identities are
disjoint, shared between encoders and separate from TEST.

| Category | FIT | CAL | TEST | Small total, s | Large total, s |
|---|---:|---:|---:|---:|---:|
| bottle | 167 | 42 | 83 | 8.591546 | 7.880032 |
| cable | 179 | 45 | 150 | 14.014748 | 11.907151 |
| capsule | 175 | 44 | 132 | 13.262193 | 11.444316 |
| carpet | 224 | 56 | 117 | 17.304653 | 14.820387 |
| grid | 211 | 53 | 78 | 13.471125 | 11.783343 |
| hazelnut | 312 | 79 | 110 | 21.213684 | 18.601740 |
| leather | 196 | 49 | 124 | 13.766031 | 11.468623 |
| metal_nut | 176 | 44 | 115 | 9.309295 | 7.350027 |
| pill | 213 | 54 | 167 | 12.251718 | 10.511938 |
| screw | 256 | 64 | 160 | 16.006980 | 13.993355 |
| tile | 184 | 46 | 117 | 12.543492 | 10.592554 |
| toothbrush | 48 | 12 | 42 | 5.870986 | 4.699807 |
| transistor | 170 | 43 | 100 | 12.298991 | 11.717516 |
| wood | 197 | 50 | 79 | 14.344330 | 13.925656 |
| zipper | 192 | 48 | 151 | 11.458343 | 10.750830 |
| Total counts | 2,900 | 729 | 1,725 | | |

| Distribution of directly measured totals | Small, s | Large, s |
|---|---:|---:|
| Median category | 13.262193 | 11.468623 |
| Minimum, toothbrush | 5.870986 | 4.699807 |
| Maximum, hazelnut | 21.213684 | 18.601740 |
| Sum of 15 measured category totals | 195.708114 | 171.447275 |

Separately observed stage medians follow; per-category stage values are retained
in `adaptation.csv` and each `M2/<category>/{adaptation,fit-timing}.json`.

| Stage median across 15 categories | Small, s | Large, s |
|---|---:|---:|
| FIT input/extraction/normalization | 9.211377 | 8.445266 |
| CAL input/extraction/normalization | 2.730612 | 2.538788 |
| Prepared-descriptor FIT statistics | 0.061108 | 0.087187 |
| Prepared-descriptor alpha/beta calibration | 0.012204 | 0.016864 |
| Serialization/reload | 0.045868 | 0.057487 |

Java statistics/calibration phases include descriptor reads and validation;
these are workflow stage timings, not isolated arithmetic kernels. Stage medians
must not be added to reconstruct a median total. Other orchestration/diagnostic
overheads are included in the directly measured total.

**The lower observed Large adaptation totals do not establish faster intrinsic
fitting.** This is one observation per category, in fixed Small-then-Large order,
with uncontrolled filesystem caches/host state and a controller pause between
campaigns. Startup and I/O materially affect the measurements. Large's directly
measured encoder latency and prepared-descriptor statistics cost are higher.

## 9. Encoder latency

Direct preprocessed float32 tensor to spatial feature output, including ONNX
tensor/session execution, output copy, validation and result cleanup; excludes
preprocessing and M2.

| Encoder | p50, ms | Mean, ms | p95, ms |
|---|---:|---:|---:|
| Small | 0.641024 | 0.671423 | 0.819200 |
| Large | 3.096576 | 3.148364 | 3.715072 |

Large p50 is **4.831x**, or **+2.455552 ms**. Evidence:
[Small JMH](../../target/comparison/m2-small-large-final-20261022-validated/SMALL_SPATIAL_14/benchmark/final-generic/jmh.json),
[Large JMH](../../target/comparison/m2-small-large-final-20261022-validated/LARGE_SPATIAL_14/benchmark/final-generic/jmh.json).
Those files also contain the two complete paths below; each has two completed
forks and ten measurement iterations per fork.

## 10. Complete detection latency

Direct predecoded image -> preprocessing -> encoder -> descriptor normalization
-> M2 -> native maximum image score, with ready model/session.

| Encoder | p50, ms | Mean, ms | p95, ms |
|---|---:|---:|---:|
| Small | 2.904064 | 2.910571 | 3.637248 |
| Large | 5.341184 | 5.385078 | 6.299648 |

Large p50 is **1.839x**, or **+2.437120 ms**. These are directly measured complete
paths; no encoder/scorer/preprocessing sum is substituted.

## 11. Bottle full-resolution localization latency

Direct predecoded image -> preprocessing -> encoder -> M2 native Z/image score
-> existing full-resolution **900x900** anomaly-map interpolation. Excludes
rendering, file output and semantic canonicalization.

| Encoder | p50, ms | Mean, ms | p95, ms |
|---|---:|---:|---:|
| Small | 7.421952 | 7.280697 | 8.812954 |
| Large | 9.846784 | 9.719325 | 11.321344 |

Large p50 is **1.327x**, or **+2.424832 ms**. Means and medians are distinct JMH
sample statistics; their ordering is retained as measured.

## 12. PaDiM/PatchCore same-hardware references

These **retained Anomalib 2.6.2** independent reference systems were not rerun.
The primary practical comparison is the matched predecoded Bottle900 complete
localization scope. Values come from `regions.full_resolution.aggregate` in
`target/comparison/final-comparison-20261003/{padim,patchcore}/bottle/latency-summary.json`;
the recorded microseconds are converted to milliseconds.

| Bottle 900x900 system | p50, ms | Mean, ms | p95, ms |
|---|---:|---:|---:|
| Java M2 Small, current matched run | 7.421952 | 7.280697 | 8.812954 |
| Java M2 Large, current matched run | 9.846784 | 9.719325 | 11.321344 |
| Anomalib PaDiM, retained | 57.602450 | 57.826711 | 61.188560 |
| Anomalib PatchCore, retained | 247.424450 | 249.549471 | 272.782625 |

Retained p50 divided by current M2 p50 is **7.76x / 5.85x** for PaDiM versus
Small/Large, and **33.34x / 25.13x** for PatchCore versus Small/Large. These are
observed system-latency ratios, not isolated scorer or equal-accuracy claims.

Retained Metal Nut700 p50 is **44.4633 ms PaDiM / 222.9253 ms PatchCore**,
from the corresponding `metal_nut/latency-summary.json` files. No current matched
S/L Metal Nut700 latency was measured here, so no Metal Nut speed ratio is made.

The references use Python 3.11.15, PyTorch 2.13.0+cpu, one caller, 12 intra-op /
12 inter-op threads, CPU and affinity mask 16777215. They use an independent
`perf_counter_ns` sampler, different backbones/fitting budgets and a different
date, rather than this campaign's JMH. Same hardware and practical output scope
permit a useful engineering comparison, not causal attribution to one component.
See the retained [detailed comparison report](BENCHMARK_FINAL_RESULT.md).

## 13. Engineering conclusion

**Large improves aggregate quality:** +0.006210 Image AUROC, +0.015477 Pixel
AUROC and +0.031496 AUPRO, or +0.6210/+1.5477/+3.1496 percentage points. The
localization improvement is useful in this dataset, with positive Pixel/AUPRO
deltas in 13/15 categories. It is mixed across categories: Image improves in
10/15, Hazelnut Image drops 0.154643, and Bottle loses all three metrics.

The cost is 4.831x encoder p50, 1.839x complete detection p50 and 1.327x complete
Bottle localization p50, with 10.697x ONNX bytes and 1.989x M2 archive bytes.
**Small is the better default portfolio configuration** for the project's compact
CPU inference emphasis: the aggregate image gain is modest, category regressions
remain, and Small has lower measured complete latency and storage. Large is a
validated alternative when its measured localization tradeoff is appropriate;
it is not a universal category upgrade. This is an engineering recommendation,
not a statistical significance verdict or a detector/default-code change.

The experiment demonstrates that the same spatial M2 mathematics, calibration,
persistence validation and evaluator work with both declared frozen descriptor
encoders and channel counts. Backbone-agnostic means the detector consumes a
validated spatial descriptor contract; it does not imply arbitrary-network
loading, reusable Small statistics for Large, or invariant accuracy/robustness.
The unchanged positional assumption and known geometric/photometric limitations
are not solved by this encoder comparison. This closes the final authorized
portfolio campaign without expanding the architecture or research scope.
