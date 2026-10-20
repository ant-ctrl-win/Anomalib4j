# Canonical Metal Nut: validated BGR acquisition

2026-10-20. Entry HEAD `c36afe9`, clean worktree. One optimization only:
contract-preserving source pixel acquisition for compatible TYPE_3BYTE_BGR.
Geometry mathematics, B sampling, encoder, M2, bundle format/identity, detector
semantics and downstream ownership/allocations are unchanged. No localization,
all-category campaign, refit or commit is performed.

## Bottleneck and design choice

The [geometry profile](JAVA_CANONICAL_GEOMETRY_PROFILE.md) directly measured bulk
getRGB p50 4.243456ms, packed estimator1.214464ms and whole geometry5.537792ms.
JFR corroborated generic raster/color conversion in81.43% of main-thread samples.
The [integrated baseline](JAVA_CANONICAL_M2_INTEGRATION.md) retained complete
decision p50/mean/p95 10.108928/10.042803/12.315034ms.

`CanonicalRgbAcquisition.read(image, destination)` replaces only the bulk read in
`CanonicalPreprocessor.prepare`. It writes exact default-sRGB **ARGB words**,
including opaque0xff alpha for BGR, into the existing reusable490,000-int buffer.
That single acquisition serves both the unchanged geometry estimator and sampler
B. There is no repeated full source conversion.

A shared BGR view directly consumed by reduction and B would require new source
access interfaces and changes in both already qualified implementations. This
checkpoint deliberately chooses the permitted minimal packed-acquisition route.
The1.96MB source capacity remains reused; no source-sized per-inference allocation
or intermediate image is introduced. No luminance/BOX fusion or sampler change
is smuggled into this optimization.

## Validated raster/color contract

The direct path requires an ordinary BufferedImage with TYPE_3BYTE_BGR, the
runtime's built-in BGR raster class, exact PixelInterleavedSampleModel and exact
ComponentColorModel. It also requires the standard sRGB ColorSpace object,
three8-bit color components, no alpha/premultiplication, byte transfer and one
DataBufferByte bank. Exact sample-model class guarantees interleaved bank0.
Subclasses/custom color/raster implementations are not silently reinterpreted.

Pixel and scanline strides and each RGB band offset come from the sample model.
Strides/offsets are validated, band offsets must be distinct and inside a pixel,
and complete source access bounds are checked in long arithmetic before writing
the destination. Rows may contain padding; physical storage need not start at
the first visible pixel. Sample-model translations account for parent/subimage
origins. BufferedImage-visible coordinates start at0,0; a translated child can
be rebased there and retain a nonzero sample-model translation.

**Important JDK offset distinction:** the validated JDK21 ByteInterleavedRaster
uses its interleaved band offsets and region/sample-model translation directly
in `getDataElements`; it does **not** add DataBuffer.getOffset to those offsets.
Other raster accessors have different addressing formulas. Since bulk getRGB
calls `getDataElements`, matching that actual layout is the logical contract.
For this validated class, a source band index is:

```text
(y-sampleModelTranslateY)*scanlineStride
+(x-sampleModelTranslateX)*pixelStride+bandOffset[channel]
```

This remains correct for the tested nonzero DataBuffer offset: adding it again
would disagree with Java's actual getRGB words. The installed JDK source
`lib/src.zip:java.desktop/sun/awt/image/ByteInterleavedRaster.java` establishes
the constructor/dataOffsets and getDataElements behavior. No internal-JDK class
import, reflective production access, module export, JNI or unsafe API is used;
the built-in raster class token comes from a normal1x1 BGR image.

The first focused run exposed exactly this mistake: adding the DataBuffer offset
shifted a padded fixture and its canonical centroid. The implementation was
corrected against the JDK source **before real-data validation or timing**. The
failing tests/assertions were retained unchanged; no tolerance was relaxed.

## Fallback and model identity

If any fast-path predicate fails, the destination is filled by the exact previous
bulk `image.getRGB(...)` call. This covers INT_RGB, alpha images, gray/indexed
images, nonstandard color spaces, subclasses and unsupported sample/raster layouts.
Canonical dimension and tensor-size checks still occur before acquisition, with
the same700x700 rejection behavior. Source bytes are not modified.

The preprocessing ID, source RGB contract and canonical bundle remain unchanged:
the contract specifies logical values equivalent to default-sRGB getRGB, not a
requirement to pay for that implementation on every input. The retained bundle
is loaded unchanged; no FIT/CAL statistics, calibration or TEST selection changes.
Non-BGR inference continues through the established fallback.

## Parity before benchmarking

Evidence root: `target/experiments/java-canonical-bgr-fast-20261020`.
`BgrFastPathParity` consumes the exact frozen176 FIT/44 CAL/115 TEST/88 rotation
manifest and retained source bytes. It compares old generic acquisition with the
actual optimized preprocessing, then compares against retained Phase 2 B state.
Private histogram/Otsu state is inspected only in test code; no production
geometry API is changed to expose diagnostics.

- **423/423** exact logical ARGB, reduced gray, actual histogram, Otsu threshold,
  selected component and filled mask.
- Exact Java geometry records/centroid and rigid transforms; B NCHW tensors and
  encoder descriptors match exactly. All normalized descriptors also reproduce
  the retained B descriptor arrays in their original role order.
- Exact native M2 maps/image scores on all423 inputs under the unchanged bundle.
- Both integrated APIs reproduce all115 TEST native maps and scores, with no
  stale buffer, image removal, fallback-to-unaligned scoring or refit.
- Existing original-frame evaluation reproduces Image0.9608993157380255,
  Pixel0.8866021047395616 and AUPRO.6468117811251299, all deltas0. Counts remain
  22 normal/93 anomaly/132 regions,6,602,541 foreground/49,747,459 background pixels.
- The existing423-case frozen reference runner is rerun in a new copied reference
  namespace, preserving old artifacts. Same status/reason, exact gray/component/
  filled data, centroid error0, angle error1.0480505352461478e-13 degrees and
  affine error7.389644451905042e-13, within unchanged1e-6 tolerances. Its eight
  retained encodings/four native maps also match with maximum error0.

Focused tests exercise ordinary BGR, nonzero DataBuffer offset plus padded rows,
subimages, rebased translated children, random/full-channel patterns and extremes,
source-byte preservation, another image type, alpha fallback, custom linear color,
BufferedImage subclasses and invalid dimensions. Equivalent RGB/offset BGR/alpha
images produce the same canonical geometry and B tensor.

## Matched measurements

All timings below are **milliseconds per operation**, measured directly. The old
path is the test-only generic reference; the new path uses the production
acquisition/preprocessor/detector. Complete geometry includes acquisition and the
unchanged estimator. Preprocessing includes acquisition, geometry and B tensor
sampling. Complete detectScore includes preprocessing, actual ONNX extraction,
M2 native scoring and max. Decode, model/session construction, diagnostics and
high-resolution localization are excluded.

| Direct measured scope | Old p50 | Old mean | Old p95 | New p50 | New mean | New p95 | Old B/op | New B/op |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Source acquisition | 4.141056 | 4.162412 | 4.284416 | 0.343040 | 0.347704 | 0.364032 | 190.186 | 18.590 |
| Complete geometry | 5.013504 | 5.990930 | 7.921664 | 1.146880 | 1.156237 | 1.193984 | 290.246 | 116.131 |
| Canonical preprocessing | 9.175040 | 9.481104 | 11.108352 | 2.359296 | 2.507496 | 2.777088 | 347.546 | 221.938 |
| Complete detectScore | 10.272768 | 10.173606 | 12.992512 | 4.423680 | 4.363740 | 5.816320 | 306410.063 | 306178.818 |

The pooled p50/mean/p95 reductions are acquisition **91.72/91.65/91.50%**,
geometry **77.12/80.70/84.93%**, preprocessing **74.29/73.55/75.00%**, and
complete decision **56.94/57.11/55.23%**. These are comparisons of directly
measured old/new scopes, not component subtraction or a reconstructed detector
latency.

Against the separate retained integration baseline
10.108928/10.042803/12.315034ms, the newly measured decision
4.423680/4.363740/5.816320ms is **56.24/56.55/52.77% lower**. That is a
cross-campaign comparison, not a matched causal estimate. The new matched generic
decision is10.272768/10.173606/12.992512ms; retain both contexts rather than
overwriting historical results.

### Protocol, host and variation

JMH1.37 SampleTime, two independent forks per scope, one caller, five2s warmups
and ten2s measurements per fork, -Xmx2g, GC profiler. All eight scopes completed;
no measurement/recovery rerun was required. Inputs are the same predecoded nine
700x700 TYPE_3BYTE_BGR fixtures: FIT good120,011,039,208,027,042,164,112 and TEST
bent011, visited cyclically. The retained bundle identity is
`8771287ad2777083c8a14f3e862142b9f36f4ef56dcbdec46cf156f30ebf9bb0`.

Host: Windows11 Pro build26200, Ryzen AI9 HX370,12 physical/24 logical cores,
67,772,403,712 RAM bytes, balanced power plan,2% background CPU at capture.
JDK21.0.9+10-LTS, ORT1.30.0,2,147,483,648-byte maximum heap and affinity mask
16777215; no individual core pinning. CPU/Azure are exposed, but all **400**
provider-tagged executed-node events across16 separate untimed profiles use
**CPUExecutionProvider**. Profile sessions are outside timed bodies.

Trials run sequentially in JMH method order, not randomized/interleaved; this
host is not an isolated fixed-frequency laboratory. Per-fork variation is
material: generic geometry fork medians4.932/7.258ms and new complete decision
medians3.957/4.514ms. Generic preprocessing also differs from the older
integration's5.857280ms p50; its9.175040ms here is preserved, not normalized or
silently replaced. These measurements show a large acquisition/whole-path
reduction on this host, not a guaranteed percentage on every deployment.
Separate scope quantiles cannot be added or subtracted.

### Per-fork measurements

| Scope / path | Fork | p50 ms | Mean ms | p95 ms | B/op |
|---|---:|---:|---:|---:|---:|
| Source acquisition / old | 1 | 4.108288 | 4.124685 | 4.243456 | 186.222 |
| Source acquisition / old | 2 | 4.165632 | 4.200770 | 4.300800 | 194.150 |
| Source acquisition / new | 1 | 0.342016 | 0.346696 | 0.362496 | 19.540 |
| Source acquisition / new | 2 | 0.344576 | 0.348716 | 0.365056 | 17.639 |
| Complete geometry / old | 1 | 4.931584 | 4.964174 | 5.095424 | 226.278 |
| Complete geometry / old | 2 | 7.258112 | 7.555602 | 9.504358 | 354.215 |
| Complete geometry / new | 1 | 1.153024 | 1.162450 | 1.206272 | 111.525 |
| Complete geometry / new | 2 | 1.140736 | 1.150092 | 1.181696 | 120.737 |
| Canonical preprocessing / old | 1 | 9.207808 | 9.461629 | 10.792141 | 327.256 |
| Canonical preprocessing / old | 2 | 9.142272 | 9.500644 | 11.622810 | 367.836 |
| Canonical preprocessing / new | 1 | 2.359296 | 2.659299 | 4.061594 | 234.309 |
| Canonical preprocessing / new | 2 | 2.359296 | 2.371970 | 2.420736 | 209.567 |
| Complete detectScore / old | 1 | 10.272768 | 10.132403 | 13.156352 | 306417.166 |
| Complete detectScore / old | 2 | 10.272768 | 10.215166 | 12.795904 | 306402.960 |
| Complete detectScore / new | 1 | 3.956736 | 4.341754 | 5.896192 | 306170.593 |
| Complete detectScore / new | 2 | 4.513792 | 4.385981 | 5.701632 | 306187.044 |

Pooled statistics come from the raw JMH sample histograms, not averages of fork
medians. Per-fork quantiles use JMH's N+1 interpolation; per-fork means weight
samples by their recorded histogram counts. Normalized allocation values are
GC-profiler iteration averages. `timing-summary.json` retains full precision,
sample counts, fork summaries and percentage comparisons; they were checked
against the raw pooled JMH primary metrics.

### Allocation boundary

The complete decision remains **306,178.818B/op**, versus306,410.063B/op for the
matched generic comparator (historical integration306,388.724B/op). This is
approximately306KB decimal/299KiB, not process memory or persisted model size.
No encoder output, descriptor, map or ORT buffer ownership is changed. The small
stage allocation values include profiler/runtime effects; they do not establish
zero allocation. The existing1.96MB reusable source array remains per instance.

## Final regression and evidence checks

- Focused raster/preprocessor suite: **7 tests,0 failures/errors/skips**, after
  diagnosing the initial offset bug without weakening its assertions.
- Complete relevant Java regression suite: **187 tests,0 failures/errors/skips**.
  The command excludes the two pre-existing expensive Bottle experiment classes;
  the requested retained real-data geometry/quality validations run separately
  and pass as described above.
- Source/resource hashes: **155/155 unchanged** from pre-parity snapshot through
  all timing trials and final regression. Retained input hashes:
  **1,617/1,617 unchanged**. No source change after timing.
- Same nine fixture identities and source snapshot in all16 runtime records,
 16/16 CPU proof files; no fork exception. Raw JMH SHA-256:
  `07da699ce45ed7685c19a94882fb276c1f7309f8bc349577f9ccad344b832c67`.
- `git diff --check`, new-file whitespace and local Markdown link validation
  pass. M0, M2 math, estimator, sampler, encoder, persistence, configuration,
  historical reports and retained artifact bytes are unchanged.

## Reproduction and provenance

`BgrGenericReference` is test-only: the pre-optimization bulk acquisition,
preprocessor and complete prepared-encoder/M2 path. It preserves the old
input/feature/map ownership and math and is not a production alternative.
`BgrFastPathBenchmark` directly measures old/new acquisition, geometry,
preprocessing and complete decisions. The new decision invokes the actual
`CanonicalM2Detector.detectScore` API. Old/new stage comparisons share the same
nine decoded BGR fixtures, frozen bundle and JVM protocol; source/fixture/bundle
identity is validated outside timing. CPU proof runs in a separate untimed
profile; measured sessions retain unchanged default options.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
$out = "target/experiments/java-canonical-bgr-fast-new"
$phase2 = "target/experiments/java-canonicalization-phase2-20261019"
$phase1 = "target/experiments/java-canonicalization-phase1-20261019"
$baseline = "target/experiments/java-canonical-m2-integration-20261019-validated"
$dataset = "C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja"
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.BgrFastPathParity $phase2 $phase1 $baseline $dataset $out
java -Xmx2g -cp $cp org.openjdk.jmh.Main '.*BgrFastPathBenchmark.*' -p run=$out -p phase2=$phase2 -p dataset=$dataset -f 2 -wi 5 -i 10 -w 2s -r 2s -t 1 -bm sample -prof gc -foe true -rf json -rff "$out/jmh.json"
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
git diff --check
```

Use a new output namespace and retain the dataset/artifacts separately; never use
`mvn clean`. Source hashes and retained input hashes are preserved, with parity,
evaluated maps/metrics, raw JMH histograms, per-fork runtime/provider traces and
hardware snapshot. Logs are `target/bgr-fast-{focused,focused-fixed,compile,parity,
jmh,java-tests}.log`. Python source is not modified. No historical numerical result
is overwritten, no historical source guard is relaxed, and no commit is made.

## Limitations and next target

Fast layout support is intentionally conservative; unsupported representations
use the generic path. Address behavior is verified on the recorded JDK21 runtime;
other JDK/raster implementations require the same regression validation. Concurrent
source mutation/inference still requires caller synchronization, as before.
Retained Metal Nut parity is an implementation regression, not independent
generalization or all-category canonicalization evidence. Exact ONNX export/
checkpoint provenance remains unresolved. Production localization stays deferred.

One recommended next target is measurement of the remaining B tensor sampling
inside optimized preprocessing, before deciding whether any further optimization
is justified. Its current standalone cost has not been remeasured here, so no
residual latency is inferred by subtraction. The approximately306KB/op downstream
allocation boundary is also retained for a separately authorized investigation;
no second optimization is implemented in this checkpoint.
