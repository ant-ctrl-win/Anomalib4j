# Java canonicalization: architecture and performance review

Date: 2026-10-19. Reviewed repository HEAD: `d55b494`; worktree was clean at
entry. **Design only: no Java port, benchmark, model change or commit.**

## 1. Executive conclusion

Prefer a **scalar Java, output-driven, scale-aware affine sampler writing
directly to a reusable NCHW tensor**, preceded by the existing 175x175 Metal Nut
geometry estimator. Keep strict M2-P unchanged. Retain the native map and transform
for optional localization; do not construct a full-resolution map for detection.

The preferred first filtered sampler is a widened, rotated separable tent kernel
covering the downsampling footprint, specified below. It is a practical low-pass
candidate, not a proven quality-equivalent replacement. Compare it with a simple
fused bicubic control under separately refitted M2 models. Four-tap bilinear is
useful as a cheap diagnostic, but is not a defensible default for 3.125x reduction.
No evidence yet identifies the fastest sampler that preserves anomaly quality.

**Second-best fallback:** scalar Java source-resolution bilinear warp with logical
edge replication, followed by the existing Java bicubic preprocessing. It retains
the successful two-stage structure and is simpler to validate, at the cost of the
700x700 intermediate. Use it if fused sampling loses quality; do not assume its
Java cost from Python measurements.

The pilot strongly supports canonical coordinates for this Metal Nut split:
Image/Pixel/AUPRO improve from **0.845552/0.849318/0.624208** to
**0.964321/0.886982/0.648731**. FIT median std falls 26.91%, cosine mean rises
0.548333 to 0.871387, and average absolute level-median rotation delta falls
9.012 to 0.569 Z. Centering, rotation and resampling were changed together;
their individual contributions were not isolated. This is not proof across
categories, illumination conditions or unseen acquisitions.

The user's current instruction explicitly removes the old <=2 ms gate as a
product requirement. The [pilot report](../benchmark/M2_METAL_NUT_CANONICALIZATION_RESULT_20261019.md)
and [frozen protocol](M2_METAL_NUT_CANONICALIZATION_PLAN.md) retain their historical
NO-GO under that gate. This review supersedes their closing recommendation for
the proposed next engineering task, not their measurements. Product latency,
throughput, concurrency and failure-policy requirements remain unspecified.

Canonical-document lag: [PROJECT_STATE](../PROJECT_STATE.md),
[EXPERIMENT_INDEX](../EXPERIMENT_INDEX.md) and [D20](../DECISION_LOG.md)
still describe the earlier oracle follow-up and do not index the completed pilot.
Use the completed pilot report plus this explicitly requested review for this
task's scope. This review changes only this document, not historical evidence.

## 2. Evidence and current bottleneck

Read alongside the [Java M2 report](../benchmark/JAVA_M2_RESULT_20261018.md),
[controlled robustness](../benchmark/M2_ROBUSTNESS_RESULT_20261018.md),
[spatial correspondence](../benchmark/M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md)
and [oracle experiment](../benchmark/M2_ORACLE_CANONICALIZATION_RESULT_20261019.md).
N1/G are not the proposed correction. Oracle round trips at 224 with frozen
models are not equivalent to the pilot's source700 alignment and fresh fitting.

Implementation evidence:

- [geometry.py](../../tools/canonicalization/geometry.py): masks, harmonic, affine, padding and warp.
- [prepare.py](../../tools/canonicalization/prepare.py): all-role canonicalization and blind rotation challenges.
- [timing.py](../../tools/canonicalization/timing.py): precise measured stage boundaries.
- [run.py](../../tools/canonicalization/run.py) and [diagnostics.py](../../tools/canonicalization/diagnostics.py): fitting, evaluation, provenance and statistics.
- [ImageNetPreprocessor](../../src/main/java/io/github/antctrlwin/anomalib4j/onnx/ImageNetPreprocessor.java): Graphics2D bicubic, uint8 RGB, normalization.
- [OnnxMobileNetV4Encoder](../../src/main/java/io/github/antctrlwin/anomalib4j/onnx/OnnxMobileNetV4Encoder.java): synchronized image extraction, heap FloatBuffer input, output copying.
- [M2Detector](../../src/main/java/io/github/antctrlwin/anomalib4j/m2/M2Detector.java), [M2ModelStore](../../src/main/java/io/github/antctrlwin/anomalib4j/m2/M2ModelStore.java): native scoring and persisted identity.
- [CanonicalPilotBridge](../../src/test/java/io/github/antctrlwin/anomalib4j/evaluation/CanonicalPilotBridge.java) and [LocalizationMaps](../../src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java): original-frame evaluation contract.

Only three canonicalizer timing scopes were measured: geometry **2.113 ms**,
warp **9.995 ms**, total **12.308 ms**, each a pooled median of batch averages.
Three Python processes, eight predecoded FIT images, warmup and interleaved batches,
Ryzen AI 9 HX 370 / Windows. These are not per-call p50s. There is no profiler
breakdown inside these scopes, no Java canonicalizer timing and no measured
canonicalized complete-detector latency.

Let S=490,000 source pixels, G=30,625 geometry pixels, O=50,176 tensor pixels,
F=filled foreground pixels (roughly half G), and P=padded source pixels.

| Operation | Scaling and likely work | Allocation / performance implication |
|---|---|---|
| NumPy RGB -> Pillow image | O(S) representation handling | May copy; do not attribute all time to Python loops |
| Luminance conversion | O(S), channel loads and weighted arithmetic | Full luminance image materialized |
| BOX 700 -> 175 | O(S), local reduction | Another image and NumPy gray array |
| Histogram/Otsu | O(G)+O(256) | Histogram plus cumulative double arrays; tiny arithmetic stage |
| 8-connected labeling/largest component | O(G), irregular neighbor accesses | Label array, sizes, comparisons and masks |
| Hole filling | O(G), background flood | Extra masks/workspace; topology matters |
| Centroid | O(F) | nonzero produces coordinate arrays |
| Fourth harmonic | O(F), transcendental/complex operations | atan2 array, complex exp array and reductions |
| Affine construction/inversion | O(1) | Tiny matrices and generic linear algebra dispatch |
| Padding | O(P), memory copy/fill | P can greatly exceed S near diagonal rotations |
| Bilinear warp700 | O(S), four RGB samples/output | Native Pillow loop; address/weight arithmetic and cache traffic |
| RGB materialization | O(S) | Pillow destination then NumPy output copy |
| Later Java bicubic224 | O(O) output with implementation-dependent filter work | New RGB224 image, graphics object, then float tensor |

Memory traffic/copying is a plausible major contributor to padding/materialization;
harmonic computation is arithmetic-heavy; components are branch/cache-sensitive;
sampling combines arithmetic and memory access. These are algorithmic diagnoses,
not measured hardware bottleneck classifications. Most NumPy/SciPy/Pillow bulk
work is already native. Replacing Python orchestration alone does not promise a
large gain. `prepare.py`'s repeated estimates, symmetry audits and PNG writes are
campaign work, not all included in timed inference.

The pilot report's 0.200 ms difference between aggregate medians cannot identify
affine-construction overhead. Medians are not additive; even the constant-sized
matrix operations need direct measurement before attributing that residual.
Later Java resize/normalization is outside the 12.308 ms timing entirely.

Historical Bottle JMH p50s: encoder excluding preprocessing 0.624640 ms,
raw M2 17.696 us, full detection/localization 2.760704/7.356416 ms. These are
900x900 Bottle, not matched Metal Nut results. Do not add them to prototype
times or derive a new speedup from their ratios.

## 3. Preferred architecture and coordinate contract

```text
decoded opaque sRGB700
  -> validated source pixel view
  -> geometry175 -> pose + source-to-canonical affine A
  -> footprint-filtered source sampling directly into float32 NCHW224
  -> same ONNX encoder -> same normalized-descriptor M2-P -> native max
                                                       |
                                     optional owned native Z + A + dimensions
                                                       |
                                     localization on demand in original frame
```

Retain scale=1, fixed canvas700, same center translation, no content crop or
letterbox. A fused input is a **new preprocessing identity**, not an invisible
optimization of `imagenet-rgb-resize224-bicubic-v1`.

Use integer pixel-center coordinates throughout custom Java code, x right and
y down. For source center c and target center t=(349.5,349.5):

```text
A(p) = R(-theta) (p-c) + t             source -> canonical700
D(u,v) = (s*(u+0.5)-0.5, s*(v+0.5)-0.5), s=700/224=3.125
B(u,v) = A^-1(D(u,v))                 tensor224 -> source700
```

Positive theta is clockwise; the input warp removes theta. Construct B once
using the transpose of the rigid rotation, not a generic matrix inversion.
The future general affine sampler can accept six coefficients, but the estimator
must not silently introduce scale. D is the explicit new sampler's half-pixel
contract; it is not a claim of byte parity with every Graphics2D implementation.

Pillow's public coordinate convention places pixel centers at half-integers.
The prototype compensates with `margin+.5-inverseLinear*[.5,.5]` when passing its
center-based matrix to Pillow. Do not carry this correction into Java a second
time. See [Pillow coordinate documentation](https://pillow.readthedocs.io/en/stable/handbook/concepts.html#coordinate-system).

Mandatory coordinate fixtures: identity, constant RGB, x/y ramps, impulse/marker,
integer and half-pixel translation, exact +/-90 degrees, off-center rotation,
corners and samples outside all four edges. Check source center maps to tensor
center111.5, not112. Use exact coefficients for exact quarter turns.

### Affine traversal

For B=(a,b,c;d,e,f), initialize each row at `(b*y+c,e*y+f)`, advance by `(a,d)`.
Compute row starts in double; restart every row so rounding does not accumulate
over the image. Over224 samples double drift is small but floor decisions near
integer boundaries still need marker tests. Float coordinates save little
relative to sampling and can alter boundary cells; keep double initially.
Precomputing224 row starts is optional, not necessary. Avoid explicit FMA changes
in parity mode. Row stepping removes repeated multiplications but does not
remove filter loads, weight calculations or channel unpacking; prioritize those.

## 4. Geometry: preserve semantics, remove avoidable work

Keep175x175 for the first port. The observed normal-rotation p95 is0.0944 degrees
and maximum0.1029 degrees; even the current result is not uniformly <0.1 degree.
There is no evidence that a smaller raster preserves it. Reduced resolution is
a later estimator experiment, not a free performance optimization.

1. **Fuse RGB access, luminance, BOX reduction and histogram accumulation.**
   Source dimensions are exactly4x geometry dimensions. Reproduce Pillow's
   RGB-to-L quantization and BOX reduction rounding/order, including any staged
   rounding, before claiming parity. Averaging RGB first and converting once
   need not equal averaging quantized L. Use small row/block scratch arrays;
   no full700 gray image is necessary. Build the256-bin histogram as each reduced
   byte is emitted. Verify against the pinned Pillow11.3.0 reference, not a guessed
   coefficient/rounding formula. Do not change to linear-light luminance.
2. **Otsu:** preserve all256 bins, double cumulative arithmetic, zero-denominator
   handling and first maximum tie-break; foreground is strictly `gray > threshold`.
   Integer weighted sums fit comfortably here; between-class expressions should
   be double. Otsu is not a credible optimization target ahead of resampling.
3. **Largest component:** full integer labeling is not mandatory. A row-major
   scan with a reusable primitive queue and visited/label arrays can enumerate
   every8-connected component and retain the largest, preserving first-component
   tie-break. Do not seed only at the center, assume one component, or discard
   small components before knowing which is largest. Anomalous color/shape can
   change segmentation. Keep worst-case O(G), with each cell enqueued once/pass.
4. **Hole fill:** retain it. It suppresses internal hole/shading effects on
   centroid and harmonic weighting. Flood the complement from the border with
   **4-connectivity**, matching SciPy's default fill structure, despite the
   foreground labeling being8-connected. A diagonal-gap fixture must test this
   distinction. [SciPy fill documentation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.ndimage.binary_fill_holes.html)
   describes background invasion and default connectivity. Do not substitute
   an8-connected background fill or a contour-only estimator.
5. **Moments:** combine area, x/y sums and border checks with the final filled-mask
   scan. After obtaining the centroid, make a second foreground scan for h4.
   Centroid and exact angular moments cannot generally be accumulated in one
   streaming pass because the angular denominator depends on the final centroid.
   Two passes over30,625 cells are inexpensive and easy to verify.

### Algebraic harmonic4

For dx=x-cx, dy=y-cy, set a=dx*dx, b=dy*dy, r2=a+b. For r2>0:

```text
cos4 = (a*a - 6*a*b + b*b) / (r2*r2)
sin4 = 4*dx*dy*(a-b) / (r2*r2)
hRe = mean(cos4); hIm = mean(sin4)
theta = atan2(hIm,hRe)/4
strength = hypot(hRe,hIm)
```

One reciprocal per foreground pixel can serve both expressions; no sqrt or
trigonometric function per pixel. Use double sums and a deterministic traversal.
An exact centroid pixel needs an explicit convention: the Python `atan2(0,0)`
path contributes `(1,0)` in the usual positive-zero case, not `(0,0)` and not
an omitted sample. Pin this with a symmetric synthetic mask. Do not add an
epsilon radius that changes the weights. The bounded175 grid is safe for double
fourth powers; test near-centroid fractional offsets and near-zero h4 explicitly.

This formula is mathematically equivalent away from the origin, not necessarily
bit-identical to complex exponentials/reduction order. Require numerical parity
and the same success/branch behavior before changing the reference implementation.
Using unnormalized fourth central moments would weight outer pixels by r^4 and
is a different estimator. Contour-only moments also change the objective and
could overweight anomalous protrusions; neither is a routine optimization.

Precompute dx and dx^2 for175 columns, dy and dy^2 for175 rows per image if useful.
The centroid varies, so a fixed angle LUT on integer grid coordinates is not
equivalent. Fractional-centroid LUTs, fixed point, reciprocal approximations and
SIMD reductions add error and complexity; defer them unless profiling justifies it.

Preserve area[.2,.8], border rejection and strength>=.03, status/reason and
modulo90 convention. Normalize branch endpoints explicitly after testing signed
zero and +/-45-degree ties; the Python expression can reach a branch endpoint.
Geometry estimation receives pixels only, never labels/masks or known perturbation
parameters. Source failure returns a typed failure, not an unaligned M2 score.

## 5. Sampling and filtering analysis

Reducing700 to224 maps one output pixel to a rotated footprint approximately
3.125 source pixels wide, area9.765625 source pixels. Interpolation estimates a
point value; minification also needs low-pass filtering over a footprint.
Neither the word 'bicubic' nor a rendering-quality hint establishes a sufficient
anti-alias filter. Java specifies bicubic interpolation but not the complete
numerical preprocessing identity: see [RenderingHints](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/RenderingHints.html).
The current path must be characterized empirically; do not call it an ideal
anti-aliased reference.

| Strategy | Work and memory | Aliasing / feature risk | Complexity and vector potential | MobileNetV4 assessment |
|---|---|---|---|---|
| A: bilinear700 then existing bicubic224 | 490k warp outputs plus50k resize outputs; full RGB700 intermediate, optional padding | Extra smoothing/quantization; successful observed distribution; no ideal filtering guarantee | Simple scalar warp + existing Java resize; sequential output, irregular source reads | Only demonstrated quality reference |
| B: fused four-tap bilinear | About201k source-pixel taps,50k tensor outputs; no full intermediate | High minification alias risk; missing two-stage blur/rounding changes descriptors | Lowest cost, simple weights; channel unpack/gather can limit SIMD | Diagnostic floor for cost, not default |
| C: fused ordinary4x4 bicubic | About803k taps plus cubic weights; no full intermediate | Better reconstruction than B but still not scale-aware anti-aliasing; ringing/overshoot possible | Moderate scalar complexity; weights reusable for RGB, irregular gathers | Serious control; quality must be measured after refit |
| D: footprint-aware widened tent (preferred) | Roughly39 nonzero taps/output at this scale, about2m overall; rotated bounding-box checks add work; no full RGB intermediate | Practical low-pass attenuation; may erase small defects; not an ideal bandlimited filter | Positive polynomial weights, straightforward double reference, constant geometry per image | Best first balance of simplicity, stability and controlled reduction; unvalidated quality |

Tap counts are arithmetic estimates, not latency forecasts. D can perform more
sampling arithmetic than A and could be slower in Java despite fewer allocations.
Many neighboring output footprints reuse source cache lines. Rotation prevents
the usual simple horizontal/vertical two-pass separability on the source raster.

### Specify D rather than using an ambiguous 'anti-aliased bicubic'

Let q=D(u,v), A(p)=R*p+t0 and k(t)=max(0,1-abs(t)). For integer source-lattice
points p in the inverse-mapped support of `[qx-s,qx+s] x [qy-s,qy+s]`:

```text
wx = k((A(p).x-qx)/s)
wy = k((A(p).y-qy)/s)
w = wx*wy
RGB = sum(w * sourceExtended(p)) / sum(w)
```

Use the same weights for all channels; accumulate in double. Clamp only source
fetch indices for virtual pixels, not their positions in weight calculation.
Normalize weights to reproduce constants, including boundaries. No intermediate
uint8 quantization; emit normalized float32 directly. Freeze this choice as its
own preprocessing version. Keep operations in encoded sRGB byte-value space to
avoid introducing an additional color-transform experiment.

The tent is separable in canonical coordinates, widened with the scale, with
finite support and nonnegative weights. It attenuates aliasing rather than
eliminating it. A rotated square support has area4*s*s; the axis-aligned bounding
box grows near45 degrees. An axis-aligned source kernel with identical widths
would be a different, angle-dependent filter. Document the actual implementation.
Do not claim exact area integration of source pixels for this discrete weighted sum.

A widened cubic could retain more detail but needs roughly four times the tent's
support area and introduces negative lobes/clipping choices. Exact polygon area
integration is more complicated; fixed supersampling only approximates the
footprint and can alias. A source mip/downsample stage followed by affine sampling
may eventually outperform a wide kernel, but adds another image, filter and
coordinate contract. It is not necessarily worse because it has two stages,
nor justified before measuring this small fixed-scale problem. These alternatives
are deferred, not a menu of additional experiments to run automatically.

The successful pilot may depend on its precise blur, quantization and boundary
behavior. Sharper or visually closer pixels do not guarantee better M2: small
feature shifts can be magnified by low local standard deviations. Refit and
recalibrate each serious sampler; do not reuse the pilot archive for a different
input distribution or judge success by PSNR alone.

## 6. Boundary, memory and tensor design

**Logical replication replaces materialized padding.** For bilinear, clamping
continuous coordinates then neighbors implements edge extension. For cubic or
widened filters, keep the unbounded sample center/weights and clamp each tap's
integer source index; clamping the center first changes the kernel near edges.
Provide an interior loop when the entire footprint lies inside source bounds,
and an edge loop otherwise. Per-row interior intervals are possible under affine
mapping; start with one footprint-bounds check per output pixel and optimize
only if measured. Border logic must cover all taps, not only the center.

Avoid per-pixel `getRGB`, `Color`, `Point`, streams, boxed queues, tiny matrices
and `BufferedImage` construction. Begin with a reusable packed `int[]` filled by
one bulk `getRGB` call for a simple correct source contract. Then add validated
opaque-sRGB TYPE_INT_RGB and TYPE_3BYTE_BGR raster fast paths. Handle sample-model
offsets, scanline/pixel stride, band offsets, data-buffer offsets and subimages;
`getData()[y*width+x]` is unsafe for an arbitrary raster. Do not read premultiplied
ARGB, indexed, grayscale or non-sRGB data as raw RGB. Retain an explicit bulk
conversion fallback matching the existing color policy. [BufferedImage API](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/image/BufferedImage.html)
documents `getRGB` conversion to default RGB; format specialization must preserve it.

Packed INT makes one load deliver RGB and is a good initial internal format.
Direct byte BGR avoids a source copy but adds unpacking/stride handling; benchmark
the real decoded input formats before declaring either faster. Both geometry and
sampling share the validated source view without separately converting the image.

Per sequential inference workspace, reuse gray[G], mask/visited[G], queue[G],
optional labels[G], histogram[256], row scratch, affine coefficients and tensor.
Approximate raw payloads: packed source1.96MB, RGB700 bytes1.47MB,
NCHW224 float602,112 bytes, one int queue122,500 bytes, gray30,625 bytes.
These are array payload estimates, not process RSS or measured peak memory.

Generate each RGB sample once and write three plane offsets p, plane+p,2*plane+p.
Three sequential write streams are cache-friendly enough to try first. Do not
resample three times just to get planar writes. Normalize once per channel after
filtering: `(value/255.0-mean)/std`, then cast to float. For weights summing to1,
normalization is affine and can be algebraically fused into multiply/add, but
reassociation changes rounding. Preserve the expression initially. Do not
normalize each source tap, clamp Z, or alter M2 double arithmetic.

Current ORT input is `FloatBuffer.wrap(float[])`, which is heap-backed. A future
native-order direct FloatBuffer can avoid ORT's input copy; its shape remains
[1,3,224,224]. Buffer ownership, position/limit and tensor lifetime must be explicit.
ORT documents direct-buffer input and reuse via backing buffers in
[OnnxTensor](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OnnxTensor.html).
Test direct writes against heap-array generation plus one bulk transfer: Java
buffer writes may offset the copy saving. Reuse a tensor only after verifying
the pinned ORT1.30.0 lifecycle and mutation behavior; close it with the session.
Do not change output extraction/feature validation in this port.

The existing encoder serializes `extract` and `close`. Workspace reuse needs a
lock around preprocessing plus run, not only session execution. Prefer one
workspace per detector instance and sequential calls; separate instances for
explicitly requested concurrency. Returned native maps must own their storage.
Immutable models may be shared; mutable buffers and input tensors may not.

## 7. Fast decision and optional localization

Existing `M2Detector.detect` already takes max of its native map and does not
upsample. Preserve that separation in the canonical detector.

Retain for deferred localization: owned196-double calibrated native Z (1,568 bytes),
the six double coefficients of A, original/canonical dimensions, preprocessing
identity and a request/image identifier. No CNN features, input tensor or700 RGB
canonical image is necessary. The original image is needed only for an overlay;
the caller can retain it separately. A scalar-only call may discard the native
map, but then cannot later localize without another inference. Make this explicit.

For initial quality validation, exactly retain the pilot's two-stage map transport:
native14 -> canonical700 using existing half-pixel bilinear interpolation, then
for each original pixel p sample that map at **A(p)** with replicated boundaries.
Although called backprojection, this pull operation uses the forward matrix,
not A^-1. Image score remains max(native Z), never max(backprojected map).

A direct native14 -> original affine interpolation would remove another large
temporary map, but is not generally identical to two bilinear interpolations
across cell boundaries. Defer that optimization until its own map/metric parity
test; it should not confound the tensor sampler experiment. Rendering color
overlays, scaling for display and image I/O remain outside scientific maps.

## 8. Minimal Java API sketch and model contract

These are proposed names/signatures, not implemented APIs:

| Component | Ownership / responsibility |
|---|---|
| `CanonicalGeometry` immutable record | Success pose, A coefficients, source/canonical dimensions, symmetry and diagnostics; distinct typed failure result |
| `MetalNutGeometryEstimator` | RGB ->175 mask -> centroid/h4; no CNN, resampling or labels |
| `AffineTensorSampler` | Pixel view + A + fixed sampler spec -> caller-owned NCHW buffer; no Metal Nut mathematics |
| `CanonicalPreprocessor` | Owns/reuses geometry/sampling workspace, enforces color and preprocessing spec |
| `CanonicalM2Detector` | Owns preprocessor, encoder and unchanged M2 model; `detect`, `inferWithMap`, close |
| `CanonicalInference` immutable result | Score plus owned native map/geometry for deferred localization |
| `LocalizationBackprojector` | Optional original-frame scientific map; separate visualization caller |

No generic estimator registry, plugin factory hierarchy or duplicate canonicalizer
facade is needed. A small estimator interface may be extracted only when a second
real strategy exists. The affine sampler must already accept geometry independent
of the Metal Nut estimator.

Add a narrowly scoped prepared-input entry point at the encoder boundary during
the later implementation, retaining the existing BufferedImage method and M0
behavior. Both paths share tensor-shape checks, ORT execution, output extraction
and close handling. Do not force tensor data through a fake BufferedImage or
decode/encode PNG to reuse the old entry point.

**Persisted identity is currently insufficient.** `M2ModelStore` hardcodes the
old preprocessing ID, and the pilot canonicalization provenance lives in adjacent
config/source hashes rather than a self-validating canonical model format. A
standalone pilot `model.bin` can therefore look like an ordinary unaligned model.

For the port, introduce a separate versioned canonical model bundle containing
the unchanged M2 numerical state plus a mandatory validated preprocessing spec.
Legacy archives remain readable only by the legacy path; reject the canonical
bundle in that loader. Do not publish a bare legacy-looking canonical archive.
The wrapper must bind the payload hash to: encoder hash; input color policy;
geometry175/reduction/threshold/connectivity/fill/rounding versions; source size
contract; centroid/angle sign/branch; scale1/canvas; sampler kernel, footprint,
boundary/quantization/precision; ImageNet normalization/layout; localization
contract. Record implementation/source version and split hashes for reproducibility.
Unknown or mismatched identities fail loading. No change to local statistics,
calibration equations, std floors or score semantics is required.

FIT, CAL and inference must call the **same Java preprocessing implementation and
specification**. Re-extract176 FIT and44 CAL, fit/calibrate with the existing
trainer, then score115 tests. Python may orchestrate and summarize but must not
produce a different training tensor. Different samplers get separate bundles and
new output namespaces; stored transforms are per image, not model statistics.

## 9. Staged implementation plan

| Stage | Work | Exit evidence |
|---|---|---|
| J0: geometry parity | Port175 reduction/mask and algebraic h4; retain geometry rules | Synthetic topology/ties/failure tests; all335 source and88 perturbed poses/statuses compared to Python; no test-label tuning |
| J1-lite: reference fixture bridge | Consume retained canonical700 PNGs through existing Java preprocessing/encoder and retained manifests | Pilot tensor/features/model/metrics reproduced; no new Java700 warp needed initially |
| J2: direct sampler | Double scalar B/C/D in a test harness, freeze spec; qualify serious C/D via own extraction/refit/calibration | Coordinate/constant/edge tests, full Metal Nut quality/coherence/rotation checks below; numerical failure is explicit |
| J3: integration | Select qualified sampler, direct tensor boundary, bundle identity and sequential workspace reuse | End-to-end map/score vs harness; legacy M0/M2 regression; matched warm-JVM stages and fast decision |
| J4: optional localization | Retain native map and A; expose existing two-stage backprojection on demand | Original-frame masks/metrics identical to qualified harness; no CNN rerun; separate timing |

**Do not fully implement J1 just for symmetry.** Retained source poses, canonical
PNGs, tensors obtainable through the existing preprocessor, normalized features,
maps and metrics provide an independent reference. J0 plus J1-lite validates the
geometry and evidence bridge, while J2 is allowed a new sampling contract.
Implement the full Java700 warp only if J2 fails quality or a discrepancy cannot
be isolated using fixtures. Then it earns its cost as the fallback/reference;
establish rounding and coordinate parity before claiming quality parity.

All stages are recommendations for the later authorized task, not work executed
by this review. Preserve retained outputs and do not run all15 categories or
revisit N1/G to make a candidate appear successful.

## 10. Benchmark plan

Use JMH for final component and complete-path measurements; a purpose-built
matched, interleaved harness is sufficient for early C/D screening and per-image
diagnostics. Do not compare their different summary statistics as if identical.

Final matched fixture set: decoded700x700 Metal Nut FIT images with representative
angles, offsets and boundary footprints, same inputs for every candidate and
baseline. Keep an anomalous-shape fixture as a disclosed stress case; never select
only the cheapest angle. Rotate fixtures deterministically; separate resident
small-set latency from a larger streaming set if cache behavior matters.

| Timed scope | Included / excluded |
|---|---|
| Geometry | RGB access, reduction, histogram, components, fill and moments; no decode |
| Tensor generation | Precomputed pose, all source sampling and normalization to NCHW; no geometry |
| Canonical preprocessing total | Directly measured geometry + tensor generation; not sum of medians |
| Encoder | Prepared tensor, binding/run/output extraction under documented reuse policy; no preprocessing |
| M2 | Raw scorer on normalized descriptors; separately full normalize/raw/Z/native-max scope |
| Complete fast decision | Predecoded source -> geometry -> tensor -> encoder -> M2 score, including normal allocations/ownership |
| Optional localization | Retained native map/A -> original scientific map; no CNN; rendering separately |
| Complete localization | Directly measured source -> final original map when requested |

Match host, JDK21 build, ORT1.30.0, CPU execution provider, thread options, batch1,
power plan, heap and concurrent load. Prove active provider in a separate profile,
then disable profiling for timing. Use at least the retained JMH protocol:
2 forks,5x2s warmup,10x2s measurement,1 caller, SampleTime; add AverageTime or
throughput only as separately labeled runs. Consume outputs, create sessions and
load models outside timed calls, report p50/p95/mean/per-fork dispersion and
allocation bytes/op with GC diagnostics. Include source-format conversion in the
complete path. Keep a source snapshot and exact commands with every result.

Use a profiler only after a real hotspot remains; stage traces should distinguish
mask topology, harmonic arithmetic, tap loads/weights, buffer writes, ORT copy,
allocation and GC. Do not assume auto-vectorization, zero-copy or bandwidth
saturation without evidence. Report model-state payload, scratch capacity and
measured RSS separately. Do not turn Java heap capacity into observed memory use.

| Rate context | Frame interval |
|---|---:|
| 30 FPS | 33.333 ms |
| 60 FPS | 16.667 ms |
| 100 FPS | 10.000 ms |
| 200 FPS | 5.000 ms |

These are arithmetic service intervals, not claims of achievable throughput.
Camera/decode, tail latency, queues, optional localization and CPU contention need
headroom. Sequential service stability depends on mean service time as well as
tails; parallel ORT sessions can oversubscribe CPU. Product limits must be supplied
later. There is **no2 ms acceptance gate** in this review.

## 11. Quality-preservation and correctness plan

Freeze candidate definitions and acceptance rules before extracting new test
metrics. Use identical176/44/115 manifests, same encoder bytes, unchanged M2 and
evaluator, original masks, all22 normals and88 source700 rotation pairs. Preserve
the exact saved perturbed RGB challenge where available so interpolation changes
do not silently change the perturbation. Re-estimate geometry blindly for each.

J0 target: reduced gray/mask exact parity, identical status/component choice;
centroid error<=1e-6 source pixel and modulo90 angle difference<=1e-6 degree for
algebraic-vs-trigonometric moments on identical masks. These are proposed port
tolerances, distinct from physical pose accuracy. Investigate threshold/branch
crossings explicitly rather than loosening them to pass. Compare all anomalies,
not just normal outlines; exercise exact-centroid and diagonal-gap synthetic cases.

For C/D, do **not** require pixel/feature parity with the two-stage reference.
Proposed engineering noninferiority criteria, to freeze before the later run:

- No primary Image/Pixel/AUPRO loss >0.01 absolute relative to canonicalized
  reference B, and each remains above unaligned A. Publish all three differences,
  per-defect breakdown and per-image rankings; do not trade losses through a
  combined average. Reference B is0.964321/0.886982/0.648731.
- FIT median local std <=1.10 times B's0.008036; pairwise cosine mean >=B's0.871387
  minus0.02. Also report mean/quantiles, low-std counts, per-position variance,
  directional dispersion and CAL raw spread. Tightness alone is not accuracy:
  excessive blur may satisfy coherence while hiding defects.
- Average absolute level-median rotation delta <=1.0 Z (B0.569, A9.012), with
  each angle's signed median/mean/p95/max and absolute-delta distribution retained.
  Investigate any signed p95 increase >1 Z relative to its B level; an unresolved
  tail regression blocks promotion. Subtract each refitted model's own normal
  score. Z scales differ, so also inspect CAL spread and original normal scores.
- No new source-estimation failures or unexplained branch changes. Retain every
  test image; no fallback/drop. Preserve the known post-warp framing diagnostic
  for bent_011 as a limitation, not a source-estimation failure or a measured
  zero-angle residual. Check analogous framing in the new tensor footprint.

These margins define an engineering decision on an already observed split, not
statistical equivalence or a product false-alarm guarantee. If uncertainty crosses
a margin, report inconclusive; do not tune filters on the115 tests until passing.
Paired bootstrap by image (stratified normal/anomaly for Image AUROC, whole-image
clusters for pixel/region metrics and rotation groups) can describe uncertainty;
never bootstrap individual pixels as independent observations. Repeated use of
Metal Nut for selection limits generalization even with confidence intervals.

Classify a candidate as functionally preserved only if all guards pass. Call it
better only with a material quality or matched-cost advantage and no unresolved
regression; otherwise prefer the simpler qualified path. Failure of a primary
guard is unacceptable for promotion. A faster but unqualified path is not success.

Run existing Java/Python regression suites in the later implementation; retain
legacy tensor equivalence, descriptor/M2 parity, persistence, native-score and
metric tests. Add bundle-mismatch, concurrency/reuse, close/lifetime, subimage,
color fallback and deferred-localization ownership tests. This documentation-only
review runs no scientific tests and asserts no new benchmark results.

## 12. Ranked optimizations

Benefit is expected scope removed, not an unsupported speedup estimate.

| Priority | Optimization | Expected benefit | Cost | Correctness risk |
|---|---|---|---|---|
| 1 | Fused output-driven sampling, logical replication, direct tensor | Removes padded/full warped RGB and downstream image materialization | Medium | High: filtering changes CNN distribution; refit required |
| 2 | Keep localization wholly demand-driven | Avoids full-resolution map work for decisions | Low | Low if native max and retained ownership preserved |
| 3 | Algebraic h4 + primitive reusable geometry workspace | Removes transcendental/complex arrays and repeated allocation | Low-medium | Low-medium: origin, topology and tie semantics |
| 4 | Fuse luminance/BOX/histogram; one validated pixel-access path | Removes full gray passes and duplicated conversions | Medium | Medium: quantization, raster and color semantics |
| 5 | Reuse NCHW/ORT input with explicit ownership | Reduces allocation and possible ORT input copies | Medium | Medium: buffer lifetime, concurrent calls |
| 6 | Double row stepping, shared RGB weights, interior loop | Reduces per-output arithmetic/boundary work | Low | Low with coordinate tests |
| 7 | Profile-guided Vector API or alternate filter implementation | Unknown until profiling | High | Medium-high; portability and numerical drift |

Plain optimized scalar Java comes first. HotSpot may vectorize simple primitive
loops, but gathers, mask floods and dependent reductions may inhibit it. Vector
API on the current Java21 target adds incubator/module and maintenance concerns;
defer until a scalar bottleneck and measured opportunity exist. Do not assume
SIMD across rotated source accesses is cheap. Channel unpacking/normalization or
regular reductions may be easier targets than the full sampler.

Do not split224 output rows into tasks initially. Fork/join, scheduling and
synchronization can consume the saved work and compete with ORT. Virtual threads
do not accelerate CPU loops. If throughput later demands concurrency, benchmark
ordinary bounded image-level worker pools with separate workspaces and controlled
ORT threads; avoid nested row pools and unbounded detector instances.

## 13. Main risks and unresolved assumptions

- **Main correctness risk:** filtering changes the normal/defect feature
  distributions. Geometry accuracy alone cannot prove the gain survives fusion.
- Fourfold outline symmetry is not surface/reflection symmetry. Six expected
  branch crossings occurred; numeric modulo90 accuracy does not prove identical
  canonical appearance. No intensity-based branch selection is authorized.
- Threshold geometry responds to defects: color_009/010 and bent_000 are retained
  outliers, and bent_011 reaches the post-warp border. Do not optimize by assuming
  a perfect normal outline or rescale away deformation.
- The pilot's success is centering/rotation only, not demonstrated scale invariance.
  Source resolution700, opaque sRGB and fixed framing are initial supported inputs.
  Other resolutions, alpha/color profiles and acquisition failure handling need an
  explicit contract; do not silently resize into the estimator to accept anything.
- Boundary extension cannot recover clipped content. The oracle study establishes
  that exact inverse geometry does not undo sampling/cropping losses.
- Illumination/reflections are not solved by alignment. Bottle's photometric
  weakness and untested local glare remain outside this Metal Nut port.
- **Main performance risk:** wide filtering, direct-buffer stores or irregular
  memory access may erase allocation savings. Python bulk work is already native;
  Java is not automatically faster. Actual product cost tolerance remains open.
- A native-map retention API is small, but storing original images for unlimited
  deferred overlays can dominate memory. Caller ownership/retention is essential.
- Exact ONNX checkpoint/export provenance remains unresolved; retain established
  encoder byte identity without claiming to have resolved its provenance.

## 14. Explicitly rejected or deferred alternatives

- Reject transparent replacement of legacy preprocessing under the old model ID.
- Reject point bilinear/bicubic as proof of anti-aliasing or quality preservation.
- Reject normal-only or center-seeded geometry shortcuts, contour moments, radial
  weighting, omitted hole filling, new scale normalization and smaller grids in J0.
- Reject training in Python with different inference preprocessing in Java.
- Reject full-resolution localization on the decision path and scoring a warped
  map instead of the native maximum.
- Reject N1/G, a new detector, covariance changes, illumination correction or an
  all-category campaign as ways to rescue this port.
- Defer full J1 until it has diagnostic/fallback value; defer mip pyramids, EWA,
  exact area clipping, LUT centroids, fixed point, JNI/OpenCV/GPU and SIMD until
  the minimal scalar implementation has measured limitations.
- Defer category frameworks. Keep estimator and sampler separate: rotationally
  symmetric objects may lack an observable angle; rectangles may permit an axis;
  textures or orientation-free objects may need no canonical rotation at all.
  A weak/undefined pose must not be forced through Metal Nut harmonic4.

## 15. Final implementation recommendation for GPT-6.1 Sol

Implement J0 and J1-lite first, then the specified footprint-aware direct tensor
sampler with a fused bicubic comparison, using the same Java path for FIT, CAL
and inference. Keep M2 mathematics and the legacy encoder entry path intact.
Qualify quality before choosing on measured speed; integrate only the qualified
candidate with a validated canonical model bundle and optional localization.

If fusion cannot preserve the pilot's quality, implement the source700 Java
bilinear reference plus existing bicubic as the single fallback. Measure that
path before considering more elaborate optimizations. Do not impose the retired
2 ms gate, promise a Java speedup, or claim product readiness without product
requirements and measured complete-path evidence.

This plan is the only artifact produced by the review. Historical reports,
production/test sources, numerical artifacts and canonical state files are
unchanged. No benchmark campaign, Java/Python test suite or commit was run.
