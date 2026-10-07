# SPATIAL_14 encoder micro-profile

## Configuration and fixture

JMH SampleTime, microseconds/op, batch 1, one caller thread, warmup 5 x 2 seconds,
measurement 10 x 2 seconds, two forks, maximum heap 2 GiB: same configuration as the baseline.
Reuse baseline ImageState/EncoderState and its original 900x900 `bottle_good_000.png`.
Image reading/PNG decoding occur once at trial setup. No Bottle model building or VSA training occurs.
Production files and public APIs are unchanged. The existing `benchmark` Maven profile is sufficient.

## Timed boundaries

| Benchmark | Timed work | Prepared outside timing |
| --- | --- | --- |
| preprocessing | Existing ImageNetPreprocessor, including resize, normalization and NCHW allocation | BufferedImage |
| tensorCreateAndClose | FloatBuffer.wrap, OnnxTensor.createTensor and tensor.close, including any native preparation/copy | Normalized float NCHW, OrtEnvironment |
| sessionRunAndClose | OrtSession.run on a reused input map/tensor, result creation and result.close | Session, normalized NCHW, input tensor and map |
| outputExtractionCopy | Named output lookup, tensor type/shape check, getFloatBuffer, float[] allocation/copy and finite checks | Session result from a single setup run, kept open until teardown |
| fullExtract | Actual production extract: preprocessing, tensor preparation, run, copy/validation, cleanup and synchronization | Image and production encoder/session |

All outputs are consumed by Blackhole. Trial teardown closes the retained result before its input
tensor and session. The JVM-wide OrtEnvironment follows production ownership and is not closed.
All three isolated ONNX benchmarks share one state whose single trial setup prepares the session,
input and retained output in dependency order. Tensor creation does not use the prepared session
or output during timing; these remain resident, matching an initialized inference environment.

## Methodological limits

- Production session access is private. The isolated benchmarks create their own session from the
  **same classpath model bytes**, using default SessionOptions and the same tensor names/shapes,
  heap FloatBuffer input and FLOAT HWC output path. No reflection or production API change is used.
  This is a benchmark replica of those boundaries, not instrumentation inside extract. The targeted
  test checks its output against production extract on a deterministic synthetic image (1e-6 absolute
  tolerance), repeated output copies, and input tensor reuse. Future encoder changes must be reflected
  in this fixture; the test alone cannot prove performance equivalence.
- `sessionRunAndClose` is ONNX-only but includes Java/JNI dispatch, native execution, output allocation
  and result destruction. It is not a timer for just native operators. Cleanup remains inside timing
  to avoid retaining an unbounded number of native outputs. Tensor creation similarly includes close.
- Output-copy timing repeatedly accesses one already produced output; its cache state differs from
  freshly produced outputs in fullExtract. getFloatBuffer may itself copy native data; that cost is
  deliberately included, as in production. Output validation is also included.
- One JMH thread does not restrict native ONNX CPU worker threads. Default session threading is
  preserved; report single-caller CPU, not strict single-native-thread execution.
- Isolated stages are not additive: allocation/GC, cache state, synchronization, input map creation
  and JNI interactions differ. Use fullExtract as the measured control, not the sum of medians.
- Repeated-input steady-state measurements do not characterize cold start or dataset variability.

## OpenCode full execution (PowerShell)

From the repository root:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"
if ($LASTEXITCODE -ne 0) { throw 'Compilation/tests failed' }
New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.onnx.EncoderMicroprofile.*' -foe true -rf json -rff target/benchmark/encoder-microprofile.json -o target/benchmark/encoder-microprofile.txt
if ($LASTEXITCODE -ne 0) { throw 'JMH failed' }
```

The command selects only the five encoder benchmarks, not the model-building baseline benchmarks.
Outputs: readable text and JSON under target/benchmark. Existing same-named outputs are replaced.
For discovery only use `-l`; for a harness smoke check override with `-wi 0 -i 1 -r 100ms -f 1`
and separate smoke output filenames. Smoke timings are not baseline results.
The long micro-profile has not been executed during implementation.
