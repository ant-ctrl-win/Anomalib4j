# Performance baseline: SPATIAL_14

## Scope and configuration

Seven JMH 1.37 benchmarks use the existing implementations. Production source and API are unchanged.
The optional Maven `benchmark` profile adds `src/jmh/java` to test compilation and JMH test dependencies.
Configuration: SampleTime, microseconds/op, one JMH thread, one image/map per invocation,
batch size 1, five warmup iterations of 2 seconds, ten measurement iterations of 2 seconds,
two separate JVM forks per benchmark, 2 GiB maximum heap. Outputs are consumed with Blackhole.
See the [OpenJDK SampleTime example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_02_BenchmarkModes.java).

## Benchmarks

| Method | Included in timed region | Excluded |
| --- | --- | --- |
| preprocessing | Existing RGB conversion, bicubic resize to 224, ImageNet normalization and NCHW allocation | PNG read/decode, ONNX |
| encoderExtract | Actual synchronized extract: preprocessing, tensor creation, session.run, output copy/validation, resource close | Session creation, disk/decode |
| compiledAdjoint | Existing per-cell compiled scoring, raw=1-score, validation, 196-element allocation | Encoder, calibration |
| positionalZ | Existing positional calibration of 196 prepared raw scores, validation and allocation | Raw scoring, parameter estimation |
| bilinearUpsampling | Existing half-pixel bilinear interpolation from prepared 14x14 Z to 900x900, allocation and validation | Encoder, scoring, calibration |
| detectionInference | extract, compiled raw scoring, Z, existing summary/max, intermediate allocations | Model building, PNG I/O |
| localizationInference | Detection path plus existing full-resolution 900x900 Z upsampling | Overlay rendering, masks, metrics, PNG I/O |

All inputs are prepared at trial setup. The original `test/img/bottle_good_000.png` is decoded once
per trial, must be 900x900, and is repeatedly used in memory. The same encoder session is reused
until trial teardown. Model benchmarks build the existing seed-42 held-out Bottle model:
167 training images exclusively for VSA statistics/archetypes/Adjoint, 42 exclusively for positional
raw mu/sigma, floor 1e-6. Projection seed is 42, D=10000, normalization epsilon 1e-6,
VSA sigma floor 1e-8. All building and feature/raw/Z fixture preparation occur in `@Setup(Level.Trial)`.
Building is repeated for each model benchmark/fork and can dominate total wall-clock run duration;
it does not contribute to the inference samples. No model cache or serialization is introduced.

## Methodological limits

- **Single-thread limitation:** one JMH caller is enforced. The production encoder creates an
  ONNX session with default SessionOptions and exposes no thread configuration. Native ONNX
  execution may use multiple CPU threads. These results must be labeled single-caller CPU,
  **not strict single-native-thread latency**. No production change or reflective session access
  is made to circumvent this limitation. A strict ONNX thread limit remains unresolved.
- Preprocessing is package-private: a benchmark-only bridge in the same package calls it directly.
  ONNX session.run cannot be isolated through the existing public encoder API; encoderExtract
  includes preprocessing, tensor management, output copy and validation. Do not subtract timings
  and present the difference as a directly measured pure ONNX latency.
- Calibration and localization currently live in test evaluation helpers; benchmarks reuse these
  exact helpers. Detection uses their existing summarize method, including min/mean/argmax work.
- This is steady-state repeated-input batch-1 latency, not cold start, throughput under load,
  dataset-wide latency distribution, or an accuracy experiment. Allocations and resulting GC
  remain part of the measured implementation. No zero-allocation replacement is introduced.
- No GPU provider is registered by the current encoder. Record CPU, RAM, OS, Java version,
  power mode and background load alongside results. JMH output records JVM and run configuration.
- `CHAT_HANDOFF.md` was absent at inspection; AGENTS.md, PROJECT_STATE.md and actual code were read.

## Full execution for OpenCode (PowerShell)

Run from the repository root:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"
if ($LASTEXITCODE -ne 0) { throw 'Benchmark compilation/tests failed' }
New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.evaluation.PerformanceBaseline.*' -foe true -rf json -rff target/benchmark/baseline.json -o target/benchmark/baseline.txt
if ($LASTEXITCODE -ne 0) { throw 'JMH run failed' }
```

No accuracy integration tests are selected. The JMH command executes all seven benchmarks with
the annotations above. `baseline.txt` contains readable results and latency percentiles;
`baseline.json` contains machine-readable results. Existing benchmark output at those paths is
replaced on rerun. To verify discovery without executing setup, replace the JMH options with `-l`.
The long baseline has not been executed during implementation.
