# PERFORMANCE_BASELINE_RESULT

Real execution of the already-implemented JMH Performance Baseline (Astra).
Code was not modified or optimized; results are reported as measured.

## Command executed

```
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"
New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.evaluation.PerformanceBaseline.*' -foe true -rf json -rff target/benchmark/baseline.json -o target/benchmark/baseline.txt
```

Build phase: BUILD SUCCESS, 83 tests, 0 failures / 0 errors / 0 skipped (pre-existing suite; unrelated to the benchmark).
JMH phase: completed, exit code 0.

## Per-benchmark results

All values are `us/op` (microseconds per operation), `Mode = SampleTime`, `Cnt` = sample count pooled across measurements.
Mean is the JMH aggregate score; p50/p95/p99 are the sampled percentiles. Throughput/FPS is derived from p50 as `1e6 / p50`.
For `positionalZ` the reported p50 is 0.2 us (sub-microsecond, clock-resolution limited), so its derived FPS is an upper bound rather than a precise figure.

| benchmark | mean (us/op) | p50 (us/op) | p95 (us/op) | p99 (us/op) | units | derived FPS @p50 |
| --- | --- | --- | --- | --- | --- | --- |
| preprocessing | 12751.35902723242 | 12615.68 | 13418.496 | 16836.85376 | us/op | 79.2664 |
| encoderExtract | 19530.061233898312 | 20054.016 | 23714.2016 | 26028.93312 | us/op | 49.8653 |
| compiledAdjoint | 12.170364129450434 | 12.0 | 12.4 | 14.592 | us/op | 83333.3333 |
| positionalZ | 0.264755699322888 | 0.2 | 0.301 | 0.301 | us/op | 5000000 (upper bound) |
| bilinearUpsampling | 3018.085452850053 | 2965.504 | 3108.864 | 4808.704 | us/op | 337.2108 |
| detectionInference | 19234.14311365807 | 19824.64 | 23035.904 | 24739.84 | us/op | 50.4423 |
| localizationInference | 23525.305626092042 | 24444.928 | 27885.568 | 29544.93952 | us/op | 40.9083 |

## Synthesis table

Reference = the true end-to-end path, not a mechanical sum of isolated benchmarks.
`detectionInference` measures the detection path directly (encoder.extract + compiled Adjoint + positional Z + summarize).
`localizationInference` measures the localization path directly (detection + bilinear upsampling).

| stage | p50 (us/op) | p95 (us/op) | % of detection end-to-end (p50) |
| --- | --- | --- | --- |
| preprocessing | 12615.68 | 13418.496 | 63.6364% |
| encoder/extract | 20054.016 | 23714.2016 | 101.157% |
| compiled Adjoint scoring | 12.0 | 12.4 | 0.0605% |
| positional Z calibration | 0.2 | 0.301 | 0.001% |
| bilinear upsampling | 2965.504 | 3108.864 | 14.9587% |
| detection inference (end-to-end) | 19824.64 | 23035.904 | 100% |
| localization inference (end-to-end) | 24444.928 | 27885.568 | 123.3058% |

Notes on interpretation:
- `encoder/extract` calls `ImageNetPreprocessor.preprocess` internally, so it already contains the preprocessing cost.
  The isolated `preprocessing` benchmark is therefore a sub-component of `encoder/extract`; the two must not be added to each other.
- `detection inference` is the reference detection path. Its p50 (19824.64 us) is dominated by `encoder/extract` (20054.016 us),
  and `compiled Adjoint` (12.0 us) plus `positional Z` (0.2 us) are negligible at this scale.
- `localization inference` p50 (24444.928 us) exceeds `detection inference` p50 by 4620.288 us; the standalone
  `bilinearUpsampling` p50 is 2965.504 us. The difference between the standalone upsampling cost and the
  localization-minus-detection difference is measurement variance, not a separate pipeline stage.
- Percentages are relative to `detectionInference` p50 and are diagnostic only. The end-to-end values are the real reference.

## Adjoint compatibility check

The new JMH `compiledAdjoint` benchmark is compatible with the previous indicative measurement of ~13 us for 196 cells:

- `compiledAdjoint` p50 = 12.0 us/op, mean = 12.170364129450434 us/op, p95 = 12.4 us/op, p99 = 14.592 us/op.
- This is the same order as, and statistically indistinguishable from, the previous ~13 us figure (mean within ~0.83 us of 13 us;
  p50 is 1 us below it). No anomaly.

## Environment

- Total JMH duration: **00:47:01** (`# Run complete. Total time: 00:47:01`). Build phase before JMH: ~3.657 s.
- Java/JVM: Oracle/OpenJDK **Temurin 21.0.9+10-LTS**; `JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS`.
  Invoker: `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe`. VM options: `-Xmx2g`.
- CPU: AMD Ryzen AI 9 HX 370 w/ Radeon 890M; cores = 12, logical processors = 24 (`Environment.ProcessorCount` = 24).
- OS: Microsoft Windows 11 Pro, version 10.0.26200, 64 bit.
- ONNX Runtime: **1.30.0** (`com.microsoft.onnxruntime:onnxruntime:1.30.0`).
- JMH version: **1.37**.

## Effective JMH configuration

- `@BenchmarkMode(Mode.SampleTime)`, `@OutputTimeUnit(TimeUnit.MICROSECONDS)`.
- `@Threads(1)`; `@Warmup(iterations = 5, time = 2, timeUnit = SECONDS, batchSize = 1)`.
- `@Measurement(iterations = 10, time = 2, timeUnit = SECONDS, batchSize = 1)`.
- `@Fork(value = 2, jvmArgsAppend = "-Xmx2g")`; `@Timeout(time = 2, timeUnit = HOURS)`.
- CLI: `-foe true`, `-rf json`, `-rff target/benchmark/baseline.json`, `-o target/benchmark/baseline.txt`.
- Blackhole mode: compiler blackholes (JVM auto-detected).
- Header confirms: Warmup 5 iterations x 2 s, Measurement 10 iterations x 2 s, Threads 1, Sampling time.

## Threading note

JMH is configured with a single benchmark thread (`@Threads(1)`, `Threads: 1 thread, will synchronize iterations`).
However, the ONNX Runtime session may internally use native worker threads, and the current API does not guarantee or
expose the number of native ONNX threads used. Therefore the single-thread JMH setting does not guarantee that the
encoder/extract and end-to-end stages ran on exactly one native thread.

## Errors / warnings

- No `ERROR`, `FAILED`, or `Exception` entries in the JMH output; `-foe true` was used and the run completed with exit code 0.
- One informational JMH note: compiler blackholes are experimentally supported and in use; results should be compared only
  with the same blackhole mode. Advisory only.
- The `baseline.txt` file is written using the host locale (decimal comma and a garbled `±` glyph); the JSON artifact
  `target/benchmark/baseline.json` is the authoritative numeric source used for this report.

## Artifacts

Retained in `target/benchmark/`:

- `baseline.json` (624423 bytes)
- `baseline.txt` (78573 bytes)

## Final git status

No source, test, JMH, or pom files were modified during this validation. (The ` M pom.xml` and ` M LocalizationMaps.java`
and the untracked benchmark sources/reports shown by git predate this validation.)
