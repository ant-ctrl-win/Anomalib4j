# ImageNet preprocessing micro-profile

## Scope

Production code/API and the existing baseline benchmarks are unchanged. Only benchmark/test
sources are added under src/jmh/java using the existing Maven benchmark profile.
The control directly calls ImageNetPreprocessor.preprocess. Isolated stages reproduce its
current statements in benchmark-only helpers; no reflection or production method extraction.
JMH: SampleTime, us/op, threads 1, batch 1, warmup 5 x 2 seconds, measurement 10 x 2 seconds,
forks 2, maximum heap 2 GiB. Every output is consumed with Blackhole.

The baseline ImageState loads the same original 900x900 bottle_good_000.png once per trial.
Stage fixtures are prepared once, outside timing. No PNG I/O/decode, ONNX sessions, VSA or
model building occurs in measured regions. The setup checks exact float-array equality of
the composed stages against production on the real fixture. A fast test additionally checks
24 combinations of shapes and image types, including alpha, premultiplied alpha, grayscale,
indexed and BGR images, repeated output allocation and preservation of the source image.

## Timed boundaries

| Benchmark | Includes | Excludes |
| --- | --- | --- |
| sourceRgbExtraction | Original-image getRGB, color conversion to sRGB where required, new int[] | RGB image creation, resize |
| rgbImageMaterialization | New original-size TYPE_INT_RGB image and setRGB from prepared pixels; alpha discarded as in production | Source getRGB, resize |
| bicubicResize | New 224x224 TYPE_INT_RGB destination, createGraphics, original interpolation/render-quality hints, drawImage and dispose | Original-image RGB conversion and output getRGB |
| resizedRgbExtraction | getRGB on prepared 224x224 image and int[] allocation | Resize and numeric conversion |
| scalingNormalizationNchw | Packed RGB extraction, division by 255.0, mean/std arithmetic, final float cast, float[] allocation and planar NCHW stores in the original fused loop | All image/graphics operations |
| fullPreprocessing | Unchanged production method, including all its allocations | PNG I/O and decoding |

Scaling, ImageNet normalization and layout writes are fused in production. There is no standalone
float [0,1] buffer: arithmetic uses doubles and casts to float after normalization. Separating those
operations into extra loops/buffers would measure a different implementation, so no such benchmark
is introduced. The resize benchmark includes destination allocation and graphics lifecycle rather
than claiming to isolate the interpolation kernel alone.

## JMH full execution for OpenCode

PowerShell, from the repository root:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"
if ($LASTEXITCODE -ne 0) { throw 'Compilation/tests failed' }
New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.onnx.PreprocessingMicroprofile.*' -foe true -rf json -rff target/benchmark/preprocessing-microprofile.json -o target/benchmark/preprocessing-microprofile.txt
if ($LASTEXITCODE -ne 0) { throw 'JMH failed' }
```

## JFR recording for OpenCode

After the compilation/classpath preparation above, run separately:

```powershell
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
New-Item -ItemType Directory -Force target/benchmark/preprocessing-jfr | Out-Null
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.onnx.PreprocessingMicroprofile.fullPreprocessing' -foe true -prof 'jfr:dir=target/benchmark/preprocessing-jfr;configName=profile;stackDepth=128' -rf json -rff target/benchmark/preprocessing-jfr.json -o target/benchmark/preprocessing-jfr.txt
if ($LASTEXITCODE -ne 0) { throw 'JFR run failed' }
Get-ChildItem target/benchmark/preprocessing-jfr -Recurse -Filter *.jfr | ForEach-Object { jfr summary $_.FullName }
```

The installed JMH 1.37 JavaFlightRecorderProfiler manages recordings in the **forked workload JVMs**,
with profile settings and 128-frame stacks. Its output lists the actual .jfr paths. Inspect them in
JDK Mission Control: allocation samples, garbage collection, execution samples/hot methods and
java.awt/sun.java2d conversion/rendering stacks. No external profiler dependency is introduced.
The recording command retains the baseline warmup/measurement/fork configuration and selects
only full production preprocessing. JFR output and text/JSON are separate from unprofiled results.

## Limits and interpretation

- Isolated stage inputs are reused and cache-warm; allocations, GC, JIT compilation and object
  lifetimes differ from the complete method. Do not sum medians or infer missing stages by subtraction.
- The helpers replicate current code and must be checked if production changes. Exact output
  equivalence is tested; equal output does not establish equal timing of a separately compiled method.
- The image type/color model and Java2D backend affect conversion costs. The fixture is not converted
  in setup before the original-image benchmark; it preserves ImageIO's decoded representation.
- JFR profile settings provide sampled allocation and execution information, not a census of every
  allocation. Short runs may contain few events. GC events depend on whether GC occurs. Native graphics
  work may not be fully resolved in Java stacks. Recording adds overhead; use the unprofiled JMH run
  for baseline latency. File writes needed by JFR are diagnostic overhead, not application input I/O.
- One JMH caller and repeated input do not characterize concurrent load or all Bottle images.
- Prior reports are context, not new measurements: preprocessing p50 is 12615.68 us in the performance
  baseline and 12632.064 us in the encoder micro-profile. The encoder report's phrase 'includes session
  close' for sessionRunAndClose is inaccurate: the code closes each Result; the session closes at teardown.
  Those existing reports are left unchanged.

The long JMH and JFR runs are intentionally deferred to OpenCode. Discovery can use `-l`;
smoke checks override with `-wi 0 -i 1 -r 100ms -f 1` and use separate output paths.
