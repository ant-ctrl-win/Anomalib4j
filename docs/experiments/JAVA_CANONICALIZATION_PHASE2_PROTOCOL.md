# Java canonicalization Phase 2: frozen Metal Nut protocol

2026-10-19. Written before candidate extraction, fitting or TEST metrics.
This authorizes only the requested Metal Nut sampler qualification and early
matched preprocessing timing. Production sources and Phase 1 definitions remain
frozen. No all-category run, production integration, optimization or commit.

## Inputs and identities

- Retain exactly176 FIT normals,44 separate CAL normals and115 TEST images
  (22 normal/93 anomalous), in the pilot's manifest order.
- Reuse the88 saved source700 rotation PNGs from the pilot's `images/robust-a`:
 22 normals x{-10,-5,+5,+10}. They are exact challenge pixels, not freshly warped
 approximations. Known angles are used only for pairing/diagnostics, never pose.
- Authority: `target/experiments/m2-metal-nut-canonicalization-20261019` and
  `target/experiments/java-canonicalization-phase1-20261019`.
  Dataset/selected image and mask bytes must match the retained baseline hashes.
- Verify Phase 1 parity markers and input hashes; snapshot/hash current source,
  encoder assets, protocol, manifests, dataset inputs and retained references.
  Verify protected sources and inputs again after the run. Historical source
  guards are not relaxed to accept the prepared-input encoder change.

## Frozen candidates and computation

All candidates call the existing Java `CanonicalPreprocessor`,
`OnnxMobileNetV4Encoder.extractPrepared`, descriptor normalization and M2 trainer.
The geometry estimator sees only each decoded source RGB image, with unchanged
175 reduction, Otsu,8-connected largest foreground,4-connected background fill,
centroid, algebraic h4, modulo90 and fixed scale1/canvas700.

| ID | Existing Phase 1 kernel | Definition |
|---|---|---|
| B | BILINEAR | Four source taps; logical replicated edges |
| C | BICUBIC | 4x4 Keys/Catmull-Rom,a=-.5; sum-normalized, RGB clipped[0,255] |
| D | WIDENED_TENT | Rotated separable tent widened by3.125; normalized tap weights |

Every candidate uses integer pixel centers, inverse rigid mapping from
`q=(3.125*(u+.5)-.5,3.125*(v+.5)-.5)`, logical index clamping with unbounded
filter centers, direct float32 NCHW224 and the frozen ImageNet normalization.
No intermediate image/byte rounding or sampler change during evaluation.

Each candidate independently extracts FIT/CAL/TEST/rotation features, fits176
local means/sample stds and calibrates44 raw-score means/sample stds with the
existing M2 equations/floors. No cross-candidate state/calibration reuse. Retain
model state, diagnostics, raw/native Z, reload equality and source transforms.
Experimental archives remain in candidate directories with mandatory adjacent
preprocessing/source metadata; they are not production canonical model bundles.

## Quality, localization and diagnostics

Use unchanged `CanonicalPilotBridge`/`ComparisonEvaluator`/`LocalizationMetrics`:
native max for image score;14->canonical700 bilinear map, then original-frame
backprojection using each source-to-canonical matrix. Original masks are untouched.
This is existing test-source evaluation, not production localization integration.
Retain all115 images and verify normal/anomaly/region/pixel counts against reference.

Compare Image AUROC, global Pixel AUROC and AUPRO@.30 with retained unaligned A
and canonicalized two-stage R, loading exact values from their artifacts.
The current evaluator does not emit per-defect metrics. Supplement it using its
same `LocalizationMetrics`, image-AUROC, mask and map-validation implementations
on each defect plus all22 normal images, writing new diagnostic JSON files from
already produced maps. Report the restricted evaluation
population/counts; do not average per-defect metrics into the primary result.
Apply the same per-defect evaluation to R, without writing into R's directory.

FIT diagnostics use sample std(ddof1), all18,816 coordinates; quantiles
mean/median/p25/p75/p90/p95/min/max, low-std counts <=1e-6/1e-5/1e-4/1e-3,
per-position mean variance, mean unordered-pair cosine
`(N*||mean_unit||^2-1)/(N-1)`, dispersion`1-||mean_unit||^2`.
CAL raw spread is per-position sample std of raw M2 residuals. Verify recomputed
diagnostics against Java persisted statistics/calibration. Compare to R's retained
diagnostics. Low variance alone cannot justify adoption.

For each of88 rotation pairs, delta=max(Z_rotated)-max(Z_original), using the
candidate's own refitted/calibrated model for both. Report median/mean/p90/p95/max
by angle, absolute-delta summaries and average of the four absolute signed
level medians. Compare with A/R on the identical retained source challenge.
These candidate-specific Z units are not physical distances or operational FPR.

Require identical successful geometry/statuses and absolute angle/centroid/matrix
errors<=1e-6 versus retained poses/transforms for all423 inputs. Compare poses
across B/C/D, retain all records, zero drops/fallbacks. Record the six expected
modulo90 crossings and require no unexplained new crossings. bent_011's old
post-warp framing diagnostic remains context; it was not a source failure and
is not grounds for removal. No post-TEST geometry adjustment.

## Acceptance and selection

Every condition is mandatory, applied at full retained precision:

1. Each primary metric >=R metric-.01.
2. Each primary metric strictly>A metric.
3. Median FIT sample std <=1.10*R median.
4. Mean same-position pairwise cosine >=R mean-.02.
5. Average absolute rotation level-median delta <=1.0Z.
6. Zero new source/geometry failures, drops, fallbacks or unexplained branch changes.

The user's six gates govern qualification. Report rotation tails explicitly;
a >1Z p95 increase versus R triggers investigation/reporting, not an invented
seventh automatic gate. Never adjust kernels or tolerances to pass.

First exclude nonqualifiers regardless of speed. Among qualifiers compare all
three metrics, robustness, coherence, total preprocessing cost and simplicity,
without an aggregate score. For practical selection, differences<=.005 in each
quality metric,<=.2Z aggregate rotation delta,<=10% median std and<=.02 cosine
are treated as near-equivalent engineering evidence, not statistical equivalence.
In that case prefer lower matched total median; if medians differ<=5%, prefer
the simpler implementation(B,C,D order). Expose material quality/cost tradeoffs
numerically and justify one selection if evidence supports it. B may win.
If none qualifies, STOP with the sole next fallback being a source700 Java
two-stage reference; do not invent a fourth sampler.

## Early matched timing

Three fresh sequential JVMs on this host, batch1 work batched for timing;
first8 FIT images predecoded as the same fixtures for all candidates. Preload
packed pixels/poses for tensor-only stages, reuse Phase 1 workspaces/tensor buffers.
Three seconds mixed warmup/JVM,9 interleaved rounds,16 operations/stage/round;
rotate seven-stage order by round and fork. Consume outputs.

- Geometry: bulk RGB read + existing estimator, no sampling.
- Tensor B/C/D: preloaded packed RGB + precomputed pose ->normalized NCHW tensor.
- Total B/C/D: existing preprocessor from decoded image, including bulk RGB read,
  fresh geometry and direct sampling, measured directly. Never add stage medians.

Exclude decode/I/O/CNN/M2/maps. Record per-batch ns/image and optional current
thread allocated bytes/image if JVM-supported. Summarize per-fork and pooled
median/mean/p95 of batch averages, ranges and fixture/runtime/hardware identity.
The p95 is dispersion across batches, not per-image tail latency. Report no final
JMH, complete detector latency, Python/Java speedup or2ms gate. No production
optimizations may be introduced in response to timings.

Artifacts: `target/experiments/java-canonicalization-phase2-20261019`.
Reject existing output roots; preserve all older evidence and dataset bytes.
Result: [Phase 2 report](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md), written
after measurement, including all gates, selection/fallback, tests and next step.
