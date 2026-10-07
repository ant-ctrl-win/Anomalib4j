# ENCODER_MICROPROFILE_RESULT

JMH microprofiling of the already-implemented encoder. This report only records the values
measured by the run. No code was modified, nothing was optimized.

## Command executed

```
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"

New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.onnx.EncoderMicroprofile.*' -foe true -rf json -rff target/benchmark/encoder-microprofile.json -o target/benchmark/encoder-microprofile.txt
```

Build phase result: **BUILD SUCCESS** — 84 tests, 0 failures/errors/skipped.
JMH phase exit 0, `# Run complete. Total time: 00:05:13`.

Benchmark class: `io.github.antctrlwin.anomalib4j.onnx.EncoderMicroprofile`
(5 `@Benchmark` methods: `preprocessing`, `tensorCreateAndClose`, `sessionRunAndClose`,
`outputExtractionCopy`, `fullExtract`).

## Per-benchmark results (units: us/op)

| benchmark | mean | p50 | p95 | p99 | units |
| --- | --- | --- | --- | --- | --- |
| preprocessing | 14829.134 | 12632.064 | 23522.509 | 51677.102 | us/op |
| tensorCreateAndClose | 131.595 | 83.200 | 118.656 | 193.792 | us/op |
| sessionRunAndClose | 560.693 | 549.888 | 681.984 | 782.336 | us/op |
| outputExtractionCopy | 15.196 | 10.288 | 32.576 | 56.960 | us/op |
| fullExtract | 19048.865 | 19955.712 | 23858.381 | 26688.225 | us/op |

Note on means: `tensorCreateAndClose` (and to a lesser degree `preprocessing`) have heavy
right tails; the JMH `Score` mean is pulled upward by rare outliers (e.g. tensor p1.00 =
233046.016 us/op, p0.9999 = 81568.727 us/op). Percentiles above are therefore the more
representative values.

## Comparison with the previous encoder baseline (`encoderExtract`)

| quantity | previous encoderExtract | current fullExtract | difference |
| --- | --- | --- | --- |
| p50 (us/op) | 20054.016 | 19955.712 | -98.304 (-0.49%) |
| p95 (us/op) | 23714.2016 | 23858.381 | +144.179 (+0.61%) |

The values are **coherent**: p50 and p95 differ by well under 1%. No anomaly.

## Diagnostic table

| stage | p50 (us/op) | p95 (us/op) | note |
| --- | --- | --- | --- |
| preprocessing | 12632.064 | 23522.509 | standalone ImageNet preprocessing; heavy tail (p99 = 51677.102) |
| tensorCreateAndClose | 83.200 | 118.656 | includes tensor resource close; tail outliers inflate the mean |
| sessionRunAndClose | 549.888 | 681.984 | single ONNX session run, includes session close; equivalent but separate instance |
| outputExtractionCopy | 10.288 | 32.576 | copy of an already-produced output; not a full inference |
| fullExtract | 19955.712 | 23858.381 | encoder extract end-to-end on the measured image(s) |

Stage timings are **not** summed, and no "pure CNN time" is derived by subtraction because
the benchmark does not measure the CNN forward pass in isolation.

## Weight of individual stages relative to fullExtract p50 (19955.712 us/op)

| stage | p50 (us/op) | % of fullExtract p50 | % of fullExtract p95 (23858.381) |
| --- | --- | --- | --- |
| preprocessing | 12632.064 | 63.30% | 98.59% |
| sessionRunAndClose | 549.888 | 2.755% | 2.858% |
| tensorCreateAndClose | 83.200 | 0.417% | 0.497% |
| outputExtractionCopy | 10.288 | 0.0516% | 0.1365% |

- Preprocessing is the dominant measured stage (~63% of fullExtract p50).
- `sessionRunAndClose` is small in this microprofile (~2.8%).
- Tensor creation and output copy are negligible at p50; tensor creation only becomes
  visible through rare tail outliers.

## Known methodological limits

- Resource close is included inside `tensorCreateAndClose` and `sessionRunAndClose`.
- The session is equivalent but a separate instance, not the one used by `fullExtract`.
- `outputExtractionCopy` copies an already-ready output, not a full inference.
- Native ONNX Runtime threading is not guaranteed by the current API.
- The stage times are not directly summable, and no isolated CNN forward time is measured.

## Environment and effective configuration

- Total benchmark duration: `# Run complete. Total time: 00:05:13`.
- Java/JVM: JDK 21.0.9 Temurin (`21.0.9+10-LTS`, OpenJDK 64-Bit Server VM);
  VM invoker `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe`;
  VM options `-Xmx2g`.
- CPU: AMD Ryzen AI 9 HX 370 w/ Radeon 890M, 12 cores / 24 logical processors.
- OS: Windows 11 Pro 10.0.26200 64-bit.
- ONNX Runtime: 1.30.0.
- JMH: 1.37.
- Effective JMH configuration: `@BenchmarkMode` SampleTime, `OutputTimeUnit` microseconds,
  `@Threads(1)`, Warmup 5 iterations x 2 s, Measurement 10 iterations x 2 s,
  Timeout 10 min per iteration, `@Fork(2)` with `-Xmx2g`.
- Dataset parameter: `../Anomalib4j_md/mvtec-ad-DatasetNinja`.

## Warnings / errors

None. No `ERROR`, `FAILED`, or `Exception` entries in the JMH output.

## Artifacts

Retained in `target/benchmark/`:

- `encoder-microprofile.json` (authoritative, 2057474 bytes)
- `encoder-microprofile.txt` (56197 bytes)
- pre-existing `baseline.json`, `baseline.txt`, `smoke.json`, `smoke.txt`,
  `encoder-microprofile-smoke.json`, `encoder-microprofile-smoke.txt`

## Source changes

None. The validation did not modify any source file. `pom.xml` and
`src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java` were already
modified before this task.
