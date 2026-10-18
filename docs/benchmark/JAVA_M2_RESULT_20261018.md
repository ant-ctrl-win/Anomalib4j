# Java M2 validation and CPU performance

Date: 2026-10-18. No all-15 Java M2 scientific campaign was launched.
Evidence: `target/ablation/java-m2-20261018-validated/`. Historical M0 and
Python ablation artifacts were read without modification.

## Selection and implementation

The completed `target/ablation/local-models-20261017` ablation selected M2.
Macro Image/Pixel/AUPRO: M2 0.871803/0.931016/0.805887;
C1 0.831351/0.866853/0.682340; V1 0.830704/0.866527/0.681510.
V1 did not outperform C1 in these macro results; this is not a significance claim.

Production Java classes in `src/main/java/io/github/antctrlwin/anomalib4j/m2/`:

- `M2Moments`: Welford accumulation, sample std (N-1), minimum two observations.
- `M2Trainer`: explicit FIT and separate HELD-OUT calibration operations.
- `M2Model`: immutable local and calibration state, descriptor normalization,
  raw diagonal scorer, positional Z and native max.
- `M2ModelStore`: versioned binary persistence and strict identity/state validation.
- `M2Detector`: reusable production encoder session plus native map/detection API.

Frozen math: `u=x/||x||`, `mu=mean_fit(u)`, `s=max(sampleStd_fit(u),1e-6)`,
`raw=mean_channels(((u-mu)/s)^2)`, `alpha=mean_cal(raw)`,
`beta=max(sampleStd_cal(raw),1e-6)`, `Z=(raw-alpha)/beta`, `image=max_native(Z)`.
Float32 encoder features are normalized and scored in float64. Norm below 1e-6
and nonfinite values fail explicitly. No VSA, concentration weighting, neighborhood
search, alignment, covariance, clustering or illumination changes.

The first implementation retains direct division by effective std. No speculative
inverse-scale or vectorization optimization was introduced. Java reduction order
and Welford statistics differ from NumPy's reduction order, within measured tolerances.
Localization uses unchanged `LocalizationMaps`; quality uses unchanged
`ComparisonEvaluator`/`LocalizationMetrics`/DatasetNinja masks.

## Persisted state

`anomalib4j-m2/v1`: encoder SHA-256, preprocessing ID, geometry 14/14/96,
18,816 local means, 18,816 effective sample stds, 196 calibration means and
196 effective calibration stds, all float64. No training observations, VSA
memory, raw calibration observations or redundant inverse arrays are persisted.
Arrays are defensively copied. Reload rejects wrong identity, geometry,
preprocessing, nonfinite state, scales below floor, truncation and trailing bytes.

Numeric payload: **304,192 bytes**. Actual archive: **304,323 bytes** per category.
This is model-state size, not process RSS or Java object-graph size; encoder and
runtime memory are excluded.
Process RSS/peak memory was not measured in this task. The recorded 2 GiB JVM
heap maximum is a configuration limit, not observed memory consumption.

## Parity and quality regression

All retained FIT/calibration/test descriptors from Bottle (83 tests), Metal Nut
(115 tests) and Hazelnut (110 tests) were reused in manifest order. No full dataset
feature extraction was rerun. Input hashes, source snapshot, model, diagnostics
and output maps are retained. Synthetic independent data also covers Java fit,
calibration, scoring and reload against Python.

Tolerance fixed before evaluation: `atol=1e-8`, `rtol=1e-10`, for every element.
References use the retained Python M2 formula and the original saved raw/Z arrays.
The table reports maximum absolute error / mean absolute error for each category:

| Quantity | Bottle | Metal Nut | Hazelnut |
|---|---:|---:|---:|
| Local mean | 9.99e-16 / 1.92e-17 | 6.66e-16 / 1.58e-17 | 1.33e-15 / 2.43e-17 |
| Sample/effective std | 6.25e-17 / 2.16e-18 | 2.22e-16 / 7.76e-18 | 2.22e-16 / 9.15e-18 |
| Calibration alpha | 1.11e-15 / 2.52e-16 | 8.88e-16 / 2.39e-16 | 1.22e-15 / 2.97e-16 |
| Calibration beta | 3.77e-15 / 2.46e-16 | 5.55e-16 / 1.09e-16 | 5.55e-16 / 1.22e-16 |
| Raw patch score | 1.82e-12 / 1.40e-15 | 8.88e-15 / 3.72e-16 | 4.44e-15 / 3.48e-16 |
| Native 14x14 Z | 1.36e-12 / 3.45e-15 | 2.49e-14 / 1.17e-15 | 1.60e-14 / 9.89e-16 |
| Image max | 1.36e-12 / 6.33e-14 | 2.49e-14 / 4.61e-15 | 1.42e-14 / 3.42e-15 |

Reload results are bit-identical to Java before persistence; their Python differences
equal the native-Z row. All three quality metrics reproduce retained Python M2
with **zero reported delta for each of the three categories**. This is implementation
regression on existing data, not independent model-selection evidence.

An additional live check re-encoded one good and one anomalous image from each
of the three categories through `M2Detector`, including Java normalization.
Largest native-map error was 8.53e-14; largest per-image mean absolute error was
1.53e-15. Detailed six-image results are in `live-parity.csv`. This closes the
gap between descriptor-level tests and the timed complete detector path.

## CPU provider and measurement protocol

Dependency remains `com.microsoft.onnxruntime:onnxruntime:1.30.0` (CPU package),
not a GPU package. Available providers observed: `[CPU, AZURE]`. Azure is available
but not registered in default session options. An untimed profiling session requires
the executed-node provider set to equal `{CPUExecutionProvider}`; node traces and
runtime records are retained for each fork. Measured sessions disable profiling
and use default SessionOptions, as does M0. No GPU dependency or encoder change.
See the official [Java package guide](https://onnxruntime.ai/docs/get-started/with-java.html)
and [SessionOptions contract](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.SessionOptions.html).

Matched JMH 1.37: SampleTime, microseconds, one caller thread, batch one,
two forks, five 2-second warmups and ten 2-second measurement iterations,
2 GiB heap. Predecoded Bottle test-good fixture, original 900x900 geometry.
Hardware: AMD Ryzen AI 9 HX 370, 12 physical/24 logical cores, Windows 11 Pro,
64 GiB installed RAM, Balanced power plan. Default ORT threading, no affinity pin.
Detailed process affinity, Java version, hardware and command are in the artifacts.

Nine measurements separate preprocessing, CNN execution/output extraction,
raw M2 scorer, positional calibration, interpolation, M2 detection/localization,
and matched M0 detection/localization loaded from retained M0 state.
Raw scoring includes input validation and output allocation, excludes descriptor
normalization. Complete paths include normalization. CNN-only uses preprocessed
input and includes tensor creation, session execution, output copy and close.
Complete paths exclude decode, disk I/O, model loading and session creation.
Stage medians are measured separately and must not be summed as an end-to-end result.

Completed matched measurements (microseconds per operation):

| Stage | Mean | p50 | p95 |
|---|---:|---:|---:|
| Preprocessing | 1389.989 | 1374.208 | 1415.168 |
| CNN extraction, excluding preprocessing | 653.568 | 624.640 | 795.648 |
| M2 raw scorer, pre-normalized descriptors | 17.935 | 17.696 | 18.080 |
| Positional calibration | 0.179 | 0.200 | 0.200 |
| Native-to-900x900 interpolation | 2999.438 | 2940.928 | 3035.136 |
| M2 complete detection | 2699.667 | 2760.704 | 3444.736 |
| M2 complete localization | 6953.870 | 7356.416 | 8781.824 |
| M0 complete detection, retained model remeasured | 3138.954 | 3178.496 | 3768.320 |
| M0 complete localization, retained model remeasured | 7979.762 | 8216.576 | 9355.264 |

Thus M2 detection/localization p50 is **2.761/7.356 ms**, versus matched M0
**3.178/8.217 ms**. This is one fixture on one machine, not a claim of universal
speedup or an isolated scorer comparison. Existing M0 end-to-end helper behavior,
including summary construction, is retained. CPU frequency/background load remain
uncontrolled; sampling tails and per-fork data are in `benchmark/cpu-verified/jmh.json`.
Calibration measurements approach timer resolution; do not interpret 0.200 us
as nanosecond precision. The full run completed in 9m52s.

The older 2026-10-03 M0 Bottle p50 was 3.723/9.486 ms on the same hardware family,
Java/ORT versions and JMH protocol. It is historical context, not the matched
comparison denominator: power/load/session context differ across dates.
No Python timing is compared with Java timing.

## Commands and progress

Build (never clean retained artifacts):

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
```

Three-category retained-feature parity/quality regression (use a new output):

```powershell
python tools/ablation/java_m2.py --output target/ablation/java-m2-regression-new
```

Matched performance, only after successful regression:

```powershell
python tools/ablation/benchmark_m2.py --run target/ablation/java-m2-20261018-validated
```

Future all-15 Java M2 run with fresh extraction, **not executed in this task**:

```powershell
python tools/ablation/java_m2.py --output target/ablation/java-m2-all15-next --all-categories --reextract
```

Omit `--reextract` to validate retained features; `--category bottle` selects one
category. This runner intentionally binds to the frozen local-model campaign and
its baseline manifests/dataset. It verifies old encoder/M0 source identity,
manifests and dataset bytes before evaluation. It stops on parity or metric drift,
never silently updates old results, and refuses existing output namespaces.
The old ablation source validator permits the separate additive M2 package while
still checking every original M0 source/asset. No scientific definitions changed.

Progress example: `[01/15] bottle - FIT - complete - 00:00:08`.
INPUTS, EXTRACT by split (when requested), FIT, CALIBRATE, PERSIST/RELOAD, INFER,
PARITY and EVALUATE are exposed;
category position and elapsed phase time go to stdout and `progress.log`, with
Java details and exact commands also in per-category logs.

## Validation status and limitations

Java suite: 112 tests passed, including six new M2 tests. Python suite: 15 passed,
including synthetic cross-language parity and progress failure reporting; two
upstream Typer/Click deprecation warnings. Commands:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools/ablation -q --basetemp "target/test-tmp/$([guid]::NewGuid())"
```

Initial failed integration attempt `java-m2-20261018` is retained: evaluator
required config.json, now supplied. Initial benchmark guard rejected available
Azure; corrected to distinguish availability from active node execution and
prove CPU execution using a trace. A hardware-capture call initially passed a
directory instead of a filename and was corrected before measurement.
No failed attempt is performance or quality evidence.

The MCP call waiting on the long JMH command timed out, but the benchmark process
continued and completed normally. Its complete JSON and final completion log,
not the tool's timeout, establish the measurement result.

### Final reconciliation after interruption

The resumed review preserved the completed measurements; no JMH or all-category
scientific campaign was rerun. All five production M2 source hashes still match
the pre-benchmark parity snapshot. The benchmark and CPU-evidence sources were
last modified at 10:10:25, before the recorded 10:11:22 benchmark launch and
10:21:15 result completion (Europe/Rome, 2026-10-18). The benchmark-source audit
uses this chronology and the recorded edits; the earlier parity snapshot predates
the CPU-verification changes and is not a hash certificate for those two files.
Post-measurement work affected validation, tests, progress and documentation,
not the timed M2/M0 paths or benchmark bodies.

Rechecked all nine saved JMH records (two forks and ten measurement iterations
each), all three zero-delta quality records, model archive sizes, six-image live
parity and all 18 provider traces. Every recorded executed-node provider was
`CPUExecutionProvider`. The timing table above matches the saved JSON.

Final resumed checks: **112 Java tests passed; 15 Python tests passed** (two
Typer/Click deprecation warnings). Logs: `target/m2-resume-java-tests.log` and
`target/m2-resume-python-tests.log`. Python compilation, the Python rule checker,
new-file whitespace checks and `git diff --check` passed. No source changes
were necessary during this final reconciliation.

## Changed files

- Core: `m2/M2Model.java`, `M2Moments.java`, `M2Trainer.java`, `M2ModelStore.java`,
  `M2Detector.java` under `src/main/java/io/github/antctrlwin/anomalib4j/`.
- Java validation: `src/test/java/.../m2/M2ModelTest.java`,
  `src/test/java/.../evaluation/M2Runner.java`, `M2LiveParity.java`.
- JMH: `src/jmh/java/.../evaluation/M2Benchmark.java`, `M2CpuEvidence.java`.
- Python: new `tools/ablation/java_m2.py`, `benchmark_m2.py`, `progress.py`,
  `test_java_m2.py`; small changes to `inputs.py` and `models.py`. The latter
  fixes exception traceback mutability; model arithmetic is unchanged.
- Documentation: this report, `AGENTS.md`, `docs/PROJECT_STATE.md`,
  `docs/EXPERIMENT_INDEX.md`, `docs/DECISION_LOG.md`,
  `docs/experiments/LOCAL_MODEL_ABLATION_PLAN.md`.

Each new source module has one responsibility and is below the 250-code-line
ceiling. Model boundaries validate finite shapes/scales/identity; CLI selections
are validated against canonical categories. No type suppression or algorithmic
fallback was added. Source review found no changes to production M0, preprocessing,
encoder geometry, interpolation, metric definitions, historical artifacts or README.

Exact ONNX checkpoint/export provenance remains unresolved. Runtime byte identity
is verified. No robustness claim, whole-dataset latency claim, or Python-versus-Java
timing comparison is made. README and historical numerical results remain unchanged.
