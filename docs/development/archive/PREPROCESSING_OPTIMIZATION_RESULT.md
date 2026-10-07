# PREPROCESSING_OPTIMIZATION_RESULT

Validation of the already-implemented preprocessing optimization (direct render fast-path for
`TYPE_INT_RGB` / `TYPE_3BYTE_BGR` sources, original `getRGB -> TYPE_INT_RGB -> setRGB -> resize`
path retained as fallback). No code was modified, no optimization was added, no commit was made.
This report only records the values measured by the run.

## Command executed

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,PreprocessingOptimizationIT,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"
New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
$benchmarks = 'io.github.antctrlwin.anomalib4j.(onnx.PreprocessingMicroprofile.fullPreprocessing|onnx.EncoderMicroprofile.fullExtract|evaluation.PerformanceBaseline.detectionInference|evaluation.PerformanceBaseline.localizationInference)$'
java -cp $benchmarkCp org.openjdk.jmh.Main $benchmarks -foe true -rf json -rff target/benchmark/preprocessing-optimized-full.json -o target/benchmark/preprocessing-optimized-full.txt
```

- Build / equivalence phase: **BUILD SUCCESS** — 86 tests, 0 failures / 0 errors / 0 skipped.
  `PreprocessingOptimizationIT` passed ("Tests run: 1, Failures: 0, Errors: 0, Skipped: 0").
- JMH phase: exit 0.

## NEW measured results (us/op) — `preprocessing-optimized-full.json`

| benchmark | mean | p50 | p95 | p99 |
| --- | --- | --- | --- | --- |
| fullPreprocessing | 1402.5564771257823 | 1380.352 | 1456.128 | 1730.2732800000013 |
| fullExtract | 2707.0485806321208 | 2752.512 | 3387.3920000000003 | 4082.0736000000015 |
| detectionInference | 2744.9958960328295 | 2789.376 | 3489.792 | 4290.8876800000071 |
| localizationInference | 7208.47517934003 | 7462.912 | 8847.36 | 10141.696 |

## OLD vs NEW comparison

OLD sources (original JMH artifacts, as requested): `preprocessing-microprofile.json`
(fullPreprocessing), `encoder-microprofile.json` (fullExtract), `baseline.json`
(detectionInference, localizationInference). All units are us/op.

### fullPreprocessing

| statistic | OLD | NEW | speedup OLD/NEW | reduction |
| --- | --- | --- | --- | --- |
| mean | 12465.685062789977 | 1402.5564771257823 | 8.888 | 88.75% |
| p50 | 12271.616 | 1380.352 | 8.890 | 88.75% |
| p95 | 13362.790400000007 | 1456.128 | 9.177 | 89.10% |
| p99 | 16874.864639999985 | 1730.2732800000013 | 9.753 | 89.75% |

### fullExtract

| statistic | OLD | NEW | speedup OLD/NEW | reduction |
| --- | --- | --- | --- | --- |
| mean | 19048.864952290973 | 2707.0485806321208 | 7.037 | 85.79% |
| p50 | 19955.712 | 2752.512 | 7.250 | 86.21% |
| p95 | 23858.380799999995 | 3387.3920000000003 | 7.043 | 85.80% |
| p99 | 26688.225280000017 | 4082.0736000000015 | 6.538 | 84.70% |

### detectionInference

| statistic | OLD | NEW | speedup OLD/NEW | reduction |
| --- | --- | --- | --- | --- |
| mean | 19234.143113658069 | 2744.9958960328295 | 7.007 | 85.73% |
| p50 | 19824.64 | 2789.376 | 7.107 | 85.93% |
| p95 | 23035.904 | 3489.792 | 6.601 | 84.85% |
| p99 | 24739.84 | 4290.8876800000071 | 5.766 | 82.66% |

### localizationInference

| statistic | OLD | NEW | speedup OLD/NEW | reduction |
| --- | --- | --- | --- | --- |
| mean | 23525.305626092038 | 7208.47517934003 | 3.264 | 69.36% |
| p50 | 24444.928 | 7462.912 | 3.276 | 69.47% |
| p95 | 27885.568 | 8847.36 | 3.152 | 68.27% |
| p99 | 29544.939519999996 | 10141.696 | 2.913 | 65.67% |

## Separate readings requested

### Median improvement

- fullPreprocessing: 12271.616 -> 1380.352 us/op (8.890x).
- fullExtract: 19955.712 -> 2752.512 us/op (7.250x).
- detectionInference: 19824.64 -> 2789.376 us/op (7.107x).
- localizationInference: 24444.928 -> 7462.912 us/op (3.276x).

### Tail improvement (p95 / p99)

- p95 speedup: fullPreprocessing 9.177x, fullExtract 7.043x,
  detectionInference 6.601x, localizationInference 3.152x.
- p99 speedup: fullPreprocessing 9.753x, fullExtract 6.538x,
  detectionInference 5.766x, localizationInference 2.913x.
- The tails improve in all four benchmarks, and their relative reduction is comparable to
  (or larger than) the median reduction for fullPreprocessing/fullExtract/detection; for
  localizationInference the p99 reduction (65.67%) is smaller than the median reduction (69.47%).

### Anomalies

None detected. All four NEW benchmarks completed without `ERROR`/`FAILED`/`Exception`, and each
metric moves in the expected direction (NEW < OLD).

### Coherence with the smoke run

The smoke run (short configuration, not equivalent to the full run) reported
`fullPreprocessing` p50 1368.064 / p95 1456.3328 and `fullExtract` p50 2965.504 / p95 6576.5376.
The full run reports `fullPreprocessing` p50 1380.352 / p95 1456.128 and `fullExtract`
p50 2752.512 / p95 3387.392. The full-run medians are coherent with the smoke signal; the smoke
p95 for `fullExtract` is higher and its configuration differs, so smoke and full run are not
treated as equivalent. Detection and localization were not measured in the smoke run, so no
smoke-to-full comparison exists for them.

## FPS derived from p50

`1/p50` is a reciprocal latency indicator, not a sustained-throughput benchmark.

| benchmark | OLD 1/p50 (FPS) | NEW 1/p50 (FPS) |
| --- | --- | --- |
| detectionInference | 50.442 | 358.503 |
| localizationInference | 40.908 | 133.996 |

## Equivalence and source integrity

- The equivalence tests continue to pass within the run above: 86 tests, 0 failures/errors/skipped,
  including `PreprocessingOptimizationIT` (1 test passed), `ImageNetPreprocessorTest`,
  `PreprocessingContractTest` and `OnnxMobileNetV4EncoderTest`.
- No source file was modified by this validation. `git status --short` shows the same
  pre-existing modifications that existed before the run (`pom.xml`, `ImageNetPreprocessor.java`,
  `LocalizationMaps.java`) plus the pre-existing untracked reports/sources. `ImageNetPreprocessor.java`
  is the optimization under test and was already modified before this validation.

```
 M pom.xml
 M src/main/java/io/github/antctrlwin/anomalib4j/onnx/ImageNetPreprocessor.java
 M src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java
?? PREPROCESSING_OPTIMIZATION_RESULT.md
... (pre-existing untracked report .md files and test sources) ...
```

## Artifacts kept under target/benchmark/

- `preprocessing-optimized-full.json` (authoritative)
- `preprocessing-optimized-full.txt`
- pre-existing `preprocessing-microprofile.json/.txt`, `encoder-microprofile.json/.txt`,
  `baseline.json/.txt`, `preprocessing-optimization-smoke.json/.txt`, `smoke.json/.txt`

No further optimizations implemented. No commit made.
