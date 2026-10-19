# Metal Nut canonical B: Java M2 integration

Checkpoint started 2026-10-19; validation and measurement completed 2026-10-20.
Entry HEAD `18c4c38`, clean worktree. Scope: integrate the qualified **B/fused
bilinear** pipeline, with a distinct model bundle and sequential detector API.
The [Phase 2 selection](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md) is fixed.
C/D, geometry optimization, production localization and other categories are
outside this checkpoint. M0, legacy M2, encoder bytes and mathematical/metric
definitions are unchanged. No commit was made.

## 1. Final preprocessing contract

Identity:
`metal-nut-otsu175-h4-scale1-v1/canonical700-to224-bilinear-imagenet-v1`.
The existing Phase 1 production implementations are called unchanged. The full
mandatory contract is in `CanonicalM2Contract.fields()` and the run's
`contract.json`; the loader compares every key/value and rejects extra/missing
fields or unknown values.

- Source: exactly700x700 `BufferedImage`; one bulk `getRGB` converts to default
  sRGB packed RGB, discarding alpha. Other sizes are rejected, not resized.
- Geometry175: `L=(19595R+38470G+7471B+32768)>>16`, horizontal four-byte
  reduction `(sum+2)>>2`, then the same vertical reduction. Deterministic256-bin
  Otsu, double cumulative statistics, first maximum, foreground `gray>threshold`.
- First largest8-connected foreground component in row-major order;
  hole fill uses4-connected border-reachable background. Filled-mask centroid
  converts to source with `(coordinate+.5)*4-.5`.
- Fourth unit angular harmonic uses the frozen algebraic double accumulation,
  with an exact centroid pixel contributing(1,0). Clockwise angle is
  `atan2(im,re)/4`, modulo90 with representable[-45,45] endpoints. Reject area
  outside[.2,.8], filled foreground on the frame, or strength<.03.
- Scale1, fixed canonical canvas700, source-to-canonical rigid transform
  `A(p)=R(-angle)*(p-centroid)+(349.5,349.5)`.
- Tensor coordinates use integer pixel centers and analytic inverse A, starting
  from `q=3.125*(u+.5)-.5`; double row starts/steps. B takes four separable linear
  taps with normalized weights and logical edge replication: indices clamp,
  kernel centers do not. No intermediate image or byte quantization.
- RGB clips to[0,255], then `(channel/255.0-mean)/std` in double, cast to float32
  NCHW[1,3,224,224]. ImageNet means .485/.456/.406, stds .229/.224/.225.
- Same ONNX SPATIAL_14 output: float32 NHWC[1,14,14,96], flattened HWC.
  Unchanged M2 descriptor normalization, norm guard1e-6, FIT local sample std
  floor1e-6, mean squared standardized residual over96 channels, separate
  HELD-OUT positional Z/sample std floor1e-6, unclamped native196-cell maximum.

This is a distinct preprocessing contract from legacy whole-image bicubic M2.
The [Phase 1 report](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md) specifies
the already validated geometry/sampling mathematics and coordinate fixtures.

## 2. Bundle format and identity

`CanonicalM2BundleStore` writes **`anomalib4j-metal-nut-canonical-m2/v1`**, never
an `anomalib4j-m2/v1` archive. Binary fields use `DataOutputStream` encoding:

1. UTF format/version, int field count, sorted UTF key/value contract pairs.
2. UTF encoder SHA-256 and UTF SHA-256 of the numerical payload; int payload size.
3. Big-endian float64 local means18,816, effective local stds18,816,
   calibration alpha196 and effective beta196, in that order. No training
   descriptors, masks, transforms, images or VSA state are persisted.

Numerical payload: **304,192 bytes**. Validated bundle: **306,066 bytes**.
`bundle.identity()` is SHA-256 of the deterministic complete serialization,
binding format, full contract, encoder and state. Validated identity:
`8771287ad2777083c8a14f3e862142b9f36f4ef56dcbdec46cf156f30ebf9bb0`.
Encoder bytes remain a classpath resource, not embedded in the bundle.

Loading requires an expected encoder SHA-256, exact current contract, exact
payload length/hash, finite valid M2 state and no trailing data. Unknown versions,
contract mismatches, duplicate fields, corruption and truncation fail with
`IOException`. Oversized input is rejected before parsing. Writes use CREATE_NEW.
Detector construction additionally checks against the actual classpath encoder.
Legacy loading is unchanged; each loader rejects the other's format.
Source versions, split manifests and input hashes accompany the validated run;
they are provenance evidence, not extra learned inference state.

## 3. Training and loading flow

`CanonicalM2Trainer.fit(List<Path> fit, List<Path> calibration)` streams source
images through `CanonicalM2Input`, the exact shared input boundary used by the
detector. FIT and CAL paths must be distinct, duplicate-free real paths, with at
least two observations per role. Callers provide normal-only manifests; the
trainer does not infer training labels from filenames or use TEST.

`CanonicalM2Input` owns the existing B `CanonicalPreprocessor`, its RGB/geometry
workspaces, a reusable150,528-float tensor and one prepared-input encoder session.
FIT accumulates normalized descriptors with existing `M2Moments`; CAL accumulates
the fitted model's raw residuals. Existing `M2Trainer` constructs local/calibrated
models. No Python training tensor or changed statistics implementation is used.

The test-source `CanonicalM2Integration` bridge reuses the frozen176 FIT/44 CAL
ordering, writes the bundle, reloads it, compares all state arrays, then runs both
APIs on115 TEST images. It checks source bytes, retained source identities and
manifests before fitting and verifies input/source hashes again after evaluation.
There is no need to rerun Phase 2 B/C/D qualification.

Typical loading:

```java
var bundle = CanonicalM2BundleStore.read(path, M2ModelStore.encoderHash());
try (var detector = new CanonicalM2Detector(bundle)) {
    double score = detector.detectScore(source700);
    CanonicalInference retained = detector.inferWithMap(source700);
}
```

These are alternative inference APIs. Calling both, as parity checks do, runs
the CNN twice; a caller needing later localization should use only `inferWithMap`.

## 4. APIs, failure, ownership and lifetime

- `detectScore(BufferedImage)`: source ->B preprocessing ->prepared encoder
  ->unchanged M2 ->native max. It performs no upsampling, backprojection,
  overlay/rendering or other localization work. Native scoring allocations remain
  those of the unchanged M2 implementation; scalar return does not retain them.
- `inferWithMap(BufferedImage)`: same inference, returning `CanonicalInference`
  with score, owned row-major196-double calibrated Z, source-to-canonical rigid
  transform and model identity. Fixed source/canonical dimensions700 and native
  dimensions14 are exposed, along with preprocessing ID. No tensor, feature map,
  source image/RGB copy or canonical700 image is retained in the result.
- The result defensively copies the map on construction and access. It remains
  valid across workspace reuse and detector close. Its native payload is1,568
  bytes; object/accessor-copy costs are separate. The caller associates the result
  with its source frame and retains that image only if a later overlay needs it.
- Geometry failure throws `CanonicalGeometryException`, carrying the existing
  typed failed geometry. It is checked **before** `extractPrepared`, so an old
  tensor can never produce a new score. No unaligned fallback, retry or image
  omission is performed. Shape/nonfinite/lifetime errors remain explicit errors.
- One detector is sequential. Concurrent callers require separate detector
  instances/workspaces or external serialization of inference **and close**.
  Encoder synchronization/session ownership remain unchanged; no caller-level
  parallelism or new ORT threading configuration is introduced. Close is
  idempotent; subsequent calls through either API fail before preprocessing.

Production original-frame localization is deliberately absent. The retained map
and A suffice for a later checkpoint without rerunning CNN. This task uses only
the existing test evaluator's native14->canonical700 interpolation followed by
forward-A original-frame sampling to establish metric parity.

## 5. Integrated quality parity

Evidence root: `target/experiments/java-canonical-m2-integration-20261019-validated`.
Authority: `target/experiments/java-canonicalization-phase2-20261019/B`.
No rounded report value is used as an acceptance assertion.

| Check | Result |
|---|---:|
| Fresh source-image FIT/CAL |176 /44|
| All local mean/effective std/alpha/beta entries |bit-exact, maximum error0|
| Reloaded integrated TEST native maps |115/115 bit-exact, maximum error0|
| Integrated fast and retained-map image scores vs B |115/115 exact, maximum error0|
| Image AUROC |0.9608993157380255, delta0|
| Pixel AUROC |0.8866021047395616, delta0|
| AUPRO@.30 |0.6468117811251299, delta0|
| TEST normal /anomaly /regions |22 /93 /132|
| Foreground /background pixels |6,602,541 /49,747,459|

All TEST transforms meet the existing1e-6 retained affine tolerance, including
bent_011. No image drops or geometry failures. The pilot's post-warp framing
limitation is preserved as context, not reclassified as a source failure.
No new rotation campaign or all-15 experiment is claimed.

The run retains144 source/resource hashes and1,286 input/evidence entries.
Every previously captured Phase 2 `src/` entry is unchanged. The new input hashes
cover the retained B evidence, source/annotation identities and manifests; no
older numerical artifact was altered. An initial preflight stopped because the
new guard incorrectly treated equal basenames in train/test as duplicates. The
guard now uses split-qualified identity and still rejects FIT/CAL overlap. Its
failed output namespace is retained; no state/quality measurement existed there.
A preceding compile error used more than ten `Map.of` entries and was corrected
in reporting code before extraction. Neither issue changed scientific behavior.

## 6. Matched integrated timing

JMH1.37 SampleTime, two successful forks/stage, five2s warmup and ten2s
measurement iterations/fork, one Java caller, batch1, -Xmx2g, GC profiler. Same
nine predecoded700x700 TYPE_3BYTE_BGR images cycle deterministically in every
stage: the first eight frozen FIT images (good_120/011/039/208/027/042/164/112)
plus the predeclared bent_011 anomalous-shape stress case. Angles span
-41.751 to27.990 degrees; no cheapest-angle selection. Setup precomputes tensors,
poses and features only for component measurements. The fast path computes fresh
geometry/tensor/features/scores per operation. No disk, decode, fitting, loading,
evaluator or localization cost is timed.

Host: Ryzen AI9 HX370,12 physical/24 logical cores, Windows11 Pro26200,
Temurin21.0.9+10-LTS, ORT1.30.0, Balanced power scheme, unrestricted affinity
mask16777215,2GiB heap. Pre-run background CPU snapshot2%; no load isolation or
affinity/power tuning. Resident nine-image latency is not a streaming/cold test.

All values below are milliseconds/operation from measured samples. The complete
fast decision is measured directly through `detectScore`; no component timings
are added or subtracted to estimate it.

| Scope | p50 | Mean | p95 | GC profiler bytes/op |
|---|---:|---:|---:|---:|
| Geometry, bulk getRGB +estimate |4.718592|4.746238|4.849664|218.54|
| B tensor, packed RGB/precomputed A ->NCHW |1.404928|1.545113|2.265088|71.47|
| Complete preprocessing, directly measured |5.857280|5.921215|6.070272|314.68|
| Prepared encoder, validation/binding/run/output copy |0.741376|0.792861|0.899072|152,096.61|
| Descriptor normalization |0.021984|0.023569|0.023392|150,546.52|
| M2 raw +Z +native max, normalized descriptors supplied |0.017984|0.018219|0.018400|3,169.41|
| **Complete fast decision, predecoded source ->score** |**10.108928**|**10.042803**|**12.315034**|**306,388.72**|

Per-fork **p50 /mean /p95**, same units:

| Scope | Fork1 | Fork2 | Allocated bytes/op, fork1 /fork2 |
|---|---|---|---:|
| Geometry |4.734976 /4.760064 /4.857856|4.694016 /4.732502 /4.833280|205.34 /231.74|
| B tensor |1.376256 /1.487337 /2.095309|1.447936 /1.607463 /2.801664|63.99 /78.95|
| Preprocessing |5.873664 /5.965481 /6.234112|5.849088 /5.877559 /5.971968|317.18 /312.18|
| Encoder |0.743424 /0.796800 /0.902144|0.738304 /0.788956 /0.896000|152,098.09 /152,095.13|
| Normalization |0.021888 /0.023175 /0.023296|0.021984 /0.023972 /0.023392|150,546.46 /150,546.58|
| Raw/Z/max |0.017984 /0.018257 /0.018400|0.017984 /0.018182 /0.018496|3,169.40 /3,169.42|
| **Fast decision** |**10.092544 /9.981125 /12.147098**|**10.141696 /10.105225 /12.474778**|306,379.47 /306,397.98|

The saved histograms contain4,006 complete-decision samples (2,015/1,991).
Per-fork sample statistics are reconstructed from saved JMH histogram counts;
the pooled p50/p95 and weighted means match JMH's recorded values. Preprocessing
pools its original successful fork and the single replacement fork described
below. Allocation values are GC-profiler normalized measurements, including
harness/runtime allocation; they are not exact object ledgers or native/process
memory. In particular, tensor-only observed allocation is not reported as zero
merely because its image-sized workspace is reused. No RSS measurement was made.
Component scopes have different JIT/cache/allocation conditions from the composed
path. Their medians are not additive, and unmeasured overhead is not assigned by
subtraction. No speedup against the Python pilot or unaligned Bottle is inferred.

**CPU-provider evidence:** available providers are[CPU,AZURE]. A separate untimed
profile uses the same encoder bytes, canonical prepared tensor and default
session options, with no additional EP. All375 provider-tagged node events across
15 retained profiles use **CPUExecutionProvider**; Azure is available but inactive.
Fourteen successful fork runtime records identify the same bundle/fixtures/JDK/
ORT. The extra profile belongs to the failed setup attempt below. Timed production
sessions do not enable profiling. One Java caller does not assert one ORT native
worker or one hardware thread.

One second preprocessing fork failed **before measurement**: Windows reused a
PID and the CREATE_NEW `runtime-PID.json` collided with a previous stage's record.
Six other stages retained both forks. The original JSON/log were preserved; only
the missing preprocessing fork was run in `preprocessing-recovery/`, using the
same unmodified measured source, bundle, fixture bytes and JMH protocol. No valid
fast-decision or other stage measurement was repeated. Original preprocessing
fork1 and recovery fork form the reported two-fork statistics; the original JSON
alone correctly contains only one successful preprocessing fork.

After all measurement, only untimed evidence setup changed: each trial now gets
an atomically unique directory instead of a PID-only filename. A regression
creates two evidence directories in the same process and verifies both records
survive. Timed methods, fixtures, production source and benchmark settings did not
change. The exact original benchmark source is retained in
`source-at-measurement/CanonicalM2Benchmark.java`. Of144 captured source/resource
entries,143 remain unchanged; this single reporting-source change and one added
regression test are recorded separately in `timing-reconciliation.json`. A fresh
reproduction captures the corrected setup in its own quality snapshot; the old
snapshot is not silently rewritten. No further timing rerun is warranted.

Frame interval context only:30FPS33.333ms;60FPS16.667ms;100FPS10ms;200FPS5ms.
These arithmetic intervals do not establish camera/system throughput, queue
stability or a latency acceptance gate. Capture/decode, scheduling, contention,
localization and tails require separate system-level evidence.

## 7. Tests and review

Final Java suite: **180 passed**,0 failures/errors/skips. Python: **44 passed**,
two existing upstream Typer/Click deprecation warnings. The eight focused
integration tests passed before real-data measurement; the final suite adds the
PID-reuse reporting regression. Tests cover bundle round trip/ownership, every
contract-field mismatch, encoder mismatch, unknown/corrupt/truncated/trailing
bundles, legacy isolation, identical FIT/CAL/inference preprocessing, raw/native
score parity, result-map ownership across reuse/close, successful->failed->valid
geometry without stale scoring, split overlap/count rejection and close behavior.
Existing geometry, B/C/D sampler, encoder, M0/VSA/Adjoint/M2 and metric tests pass.
No test was weakened or skipped to obtain a pass. No new Python source was needed.
`git diff --check`, added-file whitespace and local report links are validated.
Java LSP is unavailable; Maven/JUnit provide compile and regression diagnostics.

## 8. Reproduction and evidence

Keep existing evidence intact; do not use `mvn clean`. Dataset and target artifacts
are local/ignored and require separate restoration in a fresh clone. The bridge
requires retained B evidence and writes a new output namespace.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.CanonicalM2Integration target/experiments/java-canonicalization-phase2-20261019 C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja target/experiments/java-canonical-m2-integration-new
java -Xmx2g -cp $cp org.openjdk.jmh.Main '.*CanonicalM2Benchmark.*' -p run=target/experiments/java-canonical-m2-integration-new -p phase2=target/experiments/java-canonicalization-phase2-20261019 -p dataset=C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja -f 2 -wi 5 -i 10 -w 2s -r 2s -t 1 -bm sample -prof gc -rf json -rff target/experiments/java-canonical-m2-integration-new/jmh.json
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools -q --basetemp target/test-tmp/canonical-integration-new
git diff --check
```

Artifacts: contract/bundle, source/input hashes, original-order TEST manifest,
owned maps/transforms/scores, evaluation predictions/maps, unified metrics,
`quality-complete.json`, hardware snapshot, JMH JSON and per-fork runtime/provider
profiles, single-fork recovery JSON, measured-source backup and timing/source
reconciliation. Logs: `target/canonical-integration-parity-validated.log`,
`canonical-integration-jmh.log`, `canonical-integration-preprocessing-recovery.log`,
`canonical-integration-java-final.log` and `canonical-integration-python-tests.log`.
Timing-source hashes are validated before each JMH trial against the successful
quality snapshot. Measured code must not be changed without reconciling timing.

## 9. Limits and next target

Qualification/integration remains Metal-Nut-only on the already observed split,
not independent generalization, all-category canonicalization, operational FPR
or camera throughput. Fourfold geometry does not guarantee photometric symmetry;
scale, illumination and reflections remain unresolved. Border extension cannot
recover clipped content. Other resolutions/acquisitions need separate evidence.
The exact ONNX checkpoint/export provenance is still unresolved, despite verified
encoder byte identity. Native/ORT process RSS is not inferred from Java allocation
rates, state size or reserved heap. No latency gate is imposed.

**Exactly one next optimization target: Metal Nut geometry estimation, including
its bulk RGB/reduction boundary.** It is the largest directly measured
preprocessing component (p50 4.719ms versus tensor1.405ms). Profile its internal
costs before changing implementation while preserving the frozen geometry
contract. This is a measured scope choice, not a claimed internal hotspot or a
prediction of complete-path speedup. No geometry optimization is implemented here.
