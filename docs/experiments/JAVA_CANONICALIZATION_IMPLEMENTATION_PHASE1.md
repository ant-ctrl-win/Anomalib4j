# Java canonicalization implementation: Phase 1

Completed 2026-10-19. **J0 PASS; J1-lite PASS; J2 scalar infrastructure and
correctness PASS. Stop before scientific sampler qualification.**

Scope follows [the architecture review](JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md)
and the user's explicit Phase 1 checkpoint. No B/C/D model fitting, new anomaly
metrics, Metal Nut quality campaign, all-category scientific run, timing campaign,
production localization or commit was performed. M2 mathematics is unchanged.

## Implemented classes

New package: `src/main/java/io/github/antctrlwin/anomalib4j/canonical/`.

| Class | Responsibility |
|---|---|
| `CanonicalGeometry` | Immutable status/reason, centroid, orientation, area, harmonic strength and radius; failed poses cannot yield a transform |
| `RigidTransform` | Validated source-to-canonical rigid affine; analytic inverse; exact quarter-turn coefficients |
| `MetalNutGeometryEstimator` | Reusable primitive geometry workspace; pinned reduction, topology, algebraic harmonic4 |
| `AffineTensorSampler` | Geometry-independent B/C/D scalar sampling directly into a caller-owned tensor |
| `CanonicalPreprocessor` | One reusable RGB700 workspace, one bulk pixel read, estimation and conditional tensor generation |

The sampler accepts rigid geometry independently of Metal Nut's segmentation;
general shear/scale is intentionally not accepted under the frozen scale1 contract.
No category registry, plugin layer or canonical detector was introduced.

`OnnxMobileNetV4Encoder.extractPrepared(float[])` is the minimal new encoder seam.
It accepts exactly150,528 finite floats, implicitly shaped[1,3,224,224]. Both the
legacy image and prepared paths share the same input checks, ORT call, HWC output
copy/validation, synchronization and close handling. The existing
`ImageNetPreprocessor` is unchanged. Input floats are borrowed only during the
synchronized call; output arrays are independent. ORT input uses the existing
heap FloatBuffer creation and per-call tensor lifetime; no zero-copy optimization
or changed threading configuration is claimed.

## Geometry semantics and J0 evidence

Source700 RGB ->175 gray ->Otsu ->largest8-connected component ->4-connected
background hole filling ->centroid ->algebraic fourth harmonic ->pose/transform.

Exact pinned reduction:

```text
L = (19595*R + 38470*G + 7471*B + 32768) >> 16
horizontal = (sum of four L bytes + 2) >> 2
gray175 = (sum of four horizontal bytes + 2) >> 2
```

This matches Pillow11.3.0's RGB-to-L conversion and separately quantized BOX
passes. A randomized700 RGB probe also had zero reduced-byte mismatches.
Pinned source references: [Pillow conversion](https://github.com/python-pillow/Pillow/blob/11.3.0/src/libImaging/Convert.c)
and [resampling](https://github.com/python-pillow/Pillow/blob/11.3.0/src/libImaging/Resample.c).

Otsu uses double cumulative statistics, first maximum and strict gray>threshold.
Components are enumerated row-major with a primitive queue; the first largest
component wins ties. Hole filling floods border-connected background using4
neighbors, preserving the distinction from8-connected foreground labeling.
All mask and topology buffers are reused.

Area outside[.2,.8] or any filled foreground on the frame returns AREA_OR_BORDER.
Otherwise the centroid uses double sums, converted to source coordinates by
`(coordinate+.5)*4-.5`. For each foreground pixel, with a=dx^2,b=dy^2,r2=a+b:

```text
re += (a*a - 6*a*b + b*b) / (r2*r2)
im += 4*dx*dy*(a-b) / (r2*r2)
```

An exact centroid pixel contributes(1,0), matching the Python origin convention.
One final atan2 of the means gives clockwise degrees/4; strength is hypot of
the means. Strength<.03 returns WEAK_FOURFOLD_HARMONIC. No per-pixel trig,
complex allocations, new scale adjustment, segmentation tuning or reduced grid.
The phase is modulo90, including the representable +/-45 endpoint; no new
intensity branch selector or forced endpoint folding was added.

Read-only Python exports first reproduced every retained pose exactly using the
pinned Pillow11.3.0/SciPy1.16.0 reference. Java then compared both the selected
component before filling and filled mask, not merely centroid statistics.

| J0 check | Result |
|---|---:|
| Original FIT / CAL / TEST | 176 /44 /115 =335 |
| Retained source700 rotated inputs | 88 |
| Status and reason | All423 identical; all successful |
| Reduced175 gray / selected component / filled mask | All423 byte/element exact |
| Maximum source centroid error | 0 pixels |
| Maximum absolute angle error | 1.0480505352461478e-13 degrees |
| Maximum harmonic-strength error | 1.7763568394002505e-15 |
| Maximum affine coefficient error against stored six-coefficient matrices | 7.389644451905042e-13 |
| Branch behavior | Absolute and modulo90 differences both<=1e-6 degrees |

Frozen centroid/angle tolerance is1e-6 source pixel/degree, unchanged from the
architecture plan. Synthetic tests additionally cover exact centroid/symmetry,
origin contribution, diagonal background gap,8-connected foreground, equal-size
component tie, uniform/empty/border/weak-harmonic failure, branch endpoints and
quarter turns. A branch fixture initially used a diamond with expected45 degrees;
independent Python confirmed its phase is zero. A filled square supplies the
intended45-degree fixture. The assertion and tolerance were preserved; no geometry
implementation change was made to satisfy a mistaken fixture.

## J1-lite retained canonical-reference bridge

Used eight existing pilot canonical700 PNGs: first/last FIT, first/last CAL,
and four TEST cases including a normal, anomalies and bent_011's known post-warp
framing diagnostic. Source PNG hashes were checked before Java decoding.

All eight normalized encoder descriptors match retained `inputs/*.npy` **exactly**,
maximum difference0. The four TEST native Z maps, scored with the retained
canonicalized M2 archive, also match **exactly**, maximum difference0. No model
was fitted and no quality evaluator was called. This bridge establishes retained
input usability and regression on representative images, not exhaustive
335-image re-encoding or a new anomaly-quality result.

The final bridge was rerun after the prepared-input seam compiled. Separate
encoder tests feed the exact legacy-prepared float tensor into the new seam and
require bit-identical HWC features, input preservation, independent outputs and
shape/nonfinite/closed-session rejection. No full Java700->700 warp was needed.
The final parity runner also checks every affine directly against the retained
`geometry.json` / `rotation-geometry.json` matrix, with manifest-order assertions.

## Coordinate and exact B/C/D definitions

Integer pixel centers, x right/y down. A is the source-to-canonical transform:

```text
A(p) = R(-theta)*(p-c) + (349.5,349.5)
q(u,v) = (3.125*(u+.5)-.5, 3.125*(v+.5)-.5)
source(u,v) = R^T * (q(u,v)-translation(A))
```

The inverse is analytic; no generic matrix inversion. Each224-pixel row begins
with freshly computed double coordinates. The inner loop advances by fixed
double increments(3.125*cos(A),-3.125*sin(A)). Tensor center is **(111.5,111.5)**,
mapping to canonical center349.5,349.5. Columns111/112 map to347.9375/351.0625
before inverse pose transformation. No Pillow half-pixel adjustment is reapplied.

| Sampler | Exact scalar reference definition | Preprocessing suffix |
|---|---|---|
| B | Four taps from floor(source coordinate), separable linear weights | `canonical700-to224-bilinear-imagenet-v1` |
| C | 4x4 Keys/Catmull-Rom cubic, a=-.5; support[-2,2], sum-normalized weights | `canonical700-to224-keys-a-0.5-clip-imagenet-v1` |
| D | Rotated scale-aware separable tent defined by the architecture plan; all integer source taps in its inverse support; sum-normalized positive weights | `canonical700-to224-rotated-scaled-tent-imagenet-v1` |

For C and t=abs(distance), k(t)=1.5t^3-2.5t^2+1 for t<=1;
k(t)=-.5t^3+2.5t^2-4t+2 for1<t<2; zero otherwise. C can overshoot: after
RGB accumulation/weight normalization, each channel is clipped to[0,255].
No RGB byte rounding occurs before ImageNet normalization. This defines a new
sampler contract, not Pillow/Graphics2D byte parity.

D uses k(t)=max(0,1-abs(t)) and
`w=k((A(i,j).x-qx)/3.125)*k((A(i,j).y-qy)/3.125)`.
The implementation evaluates the equivalent rotated displacement from the inverse
sample center, avoiding translation cancellation. The source-axis bounding radius
is3.125*(abs(cos)+abs(sin)); zero-weight taps are harmless. This is a discrete
widened tent low-pass approximation, not exact area integration or ideal
bandlimiting. It remains an unqualified scientific candidate.

## Boundaries, tensor and workspace contracts

All samplers use logical edge replication. Tap indices are clamped independently;
the unbounded kernel center and virtual tap locations remain unchanged. No padded
image or intermediate canonical700/224 BufferedImage is created. The scalar
reference deliberately retains simple per-tap clamps; an interior fast path has
not been optimized. Nonfinite/nonrigid transforms, wrong buffer lengths and
coordinates outside the safe integer tap range are rejected explicitly.

One RGB weighted sum is shared across channels; values are normalized with the
existing double expression `(channel/255.0-ImageNetMean)/ImageNetStd`, then cast
to float32. Layout is contiguous NCHW, R at0, G at50,176, B at100,352, with no
clamp or change to downstream M2 Z/native max. Source arrays are not mutated.

`CanonicalPreprocessor` uses a single bulk BufferedImage.getRGB into its reusable
490,000-int buffer; Java's default RGB conversion discards alpha as in the legacy
conversion policy. It supports700x700 only and never silently resizes other
geometry. Tests cover RGB and offset BGR subimages through the same bulk path.
No raw raster specialization was introduced. This does not establish new
Pillow/Java equivalence for every color profile or grayscale encoding.

Estimator workspace:30,625-byte gray, two30,625-int arrays(labels/queue),
two30,625-boolean masks,256-int histogram. Sampler scratch is three doubles.
The caller supplies/reuses the150,528-float output. Small immutable pose/transform
records are allocated per successful preparation; there are no per-pixel objects
or per-image image-sized geometry/sampling arrays. Diagnostic mask/gray getters
return defensive snapshots only when explicitly called by parity tooling.

These workspaces are sequential and not thread-safe. Separate concurrent callers
need separate preprocessors/output buffers or external serialization. The encoder
retains its per-instance synchronized execution/close. No pooling, threading,
Vector API, direct-buffer reuse or allocation/performance claim was added.

On geometry failure, prepare returns an explicit failed pose and leaves the
destination untouched. **The caller must check status before consuming that
buffer**; its previous contents are not a valid new inference. The failed pose
cannot provide a transform. This phase has no detector integration that could
silently score it.

## Tests and retained validation evidence

- Java full dataset-free regression: **167 passed**,0 failures/errors/skips;
  includes29 new tests and all36 geometry/sampler/preprocessing/encoder-focused
  cases. Initial focused run had the single incorrect diamond fixture described
  above; the corrected cases pass in the full suite.
- Python final suite: **40 passed**, two upstream Typer/Click deprecation warnings.
  New exporter tests preserve source pixels, enforce exact reference agreement
  and reject reference drift/existing fixture files.
- Prepared/legacy encoder bit parity, existing M0/VSA/Adjoint/M2, metrics,
  persistence and preprocessing regression tests pass.
- Sampler tests cover identity, constants, X/Y ramps, impulse, integer/half-pixel
  translations, exact +/-90, off-center rotation, tensor center, every border and
  outside it, independent fixed-window filter sums, determinism, finite output,
  NCHW ordering and normalization. Constant RGB is exact across all output cells.
- Python rule checks pass for all three changed Python files. Diff whitespace,
  new-file whitespace and local Markdown links pass; source modules are below250
  code lines and retain one responsibility each. No type suppression was added.
  basedpyright/ruff are unavailable on PATH, so those checks were not run.

The initial broader Python run found38 passes and one failure: the dry-run test
used the real frozen20261016 baseline, whose source guard correctly rejected
the new canonical package. It would also reject the changed encoder source.
The test now constructs an actual tiny synthetic all-category dataset/manifests
for planning, instead of assuming historical source identity. A separate test
first accepts the matching synthetic source snapshot and then rejects an altered
encoder hash. Production `baseline_contract` and its guards were not changed.
No frozen source hashes or test assertions were relaxed.

Evidence root: `target/experiments/java-canonicalization-phase1-20261019/`.
Contains423 gray/component/filled reference triples, source paths/hashes/poses,
eight bridge descriptor rows and four available TEST-map references, Java pose
records, `j0-result.json`, `j1-result.json`, and1,094 input hashes. All recorded
input hashes match, including every retained pilot file. The bridge map array
has explicit NaN placeholders for the four FIT/CAL rows, which have no retained
test-map reference and are never compared or fed into a sampler/scorer.
Only actual finite TEST-map rows participate in J1 scoring checks.

Among28 original production source entries recorded by the pilot, only
`OnnxMobileNetV4Encoder.java` changed: the requested shared prepared-input seam.
M0/M2 math, existing preprocessing, encoder bytes/output geometry, interpolation,
evaluation and retained experiment artifacts are unchanged. Source identity is
therefore intentionally different; old campaign runners still reject current
HEAD under their strict frozen-source contract. Parity is numerical evidence,
not a replacement source-hash certificate or a new timing measurement.

Commands (new namespaces required for export/reproduction):

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/canonicalization/phase1_reference.py --output target/experiments/java-canonicalization-phase1-new
$phase1cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
java -cp $phase1cp io.github.antctrlwin.anomalib4j.evaluation.CanonicalPhase1Parity target/experiments/java-canonicalization-phase1-new target/experiments/m2-metal-nut-canonicalization-20261019
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python -m pytest tools -q --basetemp target/test-tmp/canonical-phase1-new
git diff --check
```

Logs: `target/canonical-phase1-compile.log`, `canonical-phase1-reference.log`,
`canonical-phase1-parity-final.log`, `canonical-phase1-parity-matrices.log`,
`canonical-phase1-java-regression.log`,
`canonical-phase1-python-final.log`, with initial focused/failed-suite logs retained
separately. Target evidence and external dataset are ignored/local. Never run
`mvn clean` to prepare reproduction.

## Discrepancies, limits and next checkpoint

No unresolved geometry or coordinate discrepancy remains in the requested
fixtures. J1-lite covers eight representative encodings, not a full re-encoding.
The pilot's bent_011 post-warp diagnostic failure remains historical evidence;
no image was dropped or transform corrected here.

The phase is ready for a **separate scientific C/D sampler qualification task**:
same Java preprocessing on FIT/CAL/TEST, own refitted M2 statistics/Z and frozen
quality/robustness criteria. B is diagnostic only; none is the selected winner.
Filtering may remove defects or alter low-variance feature distributions despite
correct coordinates. No latency or quality benefit is claimed for the Java port.

Canonical model bundles and validated preprocessing metadata, production fast
detector integration, deferred native-map result ownership and localization
integration remain later work. New string preprocessing IDs distinguish samplers
but are not a persistence-validation mechanism. Never pair a changed sampler
with a legacy-looking archive merely because the encoder hash matches.
ONNX checkpoint/export provenance remains unresolved. Product cost requirements
remain open; the historical2ms gate is not reinstated.
