# Metal Nut canonical geometry: measurement profile

2026-10-20. Entry HEAD `dffc76e`, clean worktree. **Measurement only: no production
optimization, scientific behavior change, persistence/API change, localization,
all-category campaign or commit.** The purpose is to choose one first optimization
target from measured geometry costs, before changing implementation.

## Contract and authority

The [integrated canonical B detector](JAVA_CANONICAL_M2_INTEGRATION.md) remains
authoritative. Its historical geometry p50 is4.718592ms, B tensor1.404928ms,
preprocessing5.857280ms, encoder0.741376ms and complete fast decision10.108928ms.
Those values are preserved; this campaign independently measures geometry only.

Geometry is unchanged: default-sRGB bulk RGB700 acquisition; Pillow-compatible
luminance `(19595R+38470G+7471B+32768)>>>16`; horizontal four-byte BOX rounding
`(sum+2)>>>2` before vertical rounding; gray175;256-bin double Otsu with first
maximum/strict gray>threshold; first largest row-major8-connected foreground;
4-connected exterior flood/hole fill; filled centroid/area/border;
algebraic unit harmonic4 with origin contribution(1,0); clockwise atan2/4,
modulo90 with representable endpoints; unchanged area[.2,.8], border and
strength.03 failure rules; source centroid `(reduced+.5)*4-.5`; scale1 and
fixed target349.5,349.5 with the existing rigid transform. No rounding, traversal,
connectivity, angle folding or failure-policy adjustment is authorized here.

See [Phase 1](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md),
[Phase 2](JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md) and the
[architecture review](JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md) for retained
scientific definitions. Production `MetalNutGeometryEstimator`, `RigidTransform`,
canonical preprocessing, sampler B, encoder, M2 and detector code are untouched.

## Measurement helpers and stage boundaries

New code exists only under `src/jmh/java/.../canonical/`:

- `GeometryProfileStages`: measurement-only mechanical partition of the existing
  estimator. Same primitive arrays, loops, predicates, formula/order and output
  records. Extra method boundaries/state fields enable isolated timing; this is
  not a replacement production implementation.
- `GeometryProfileFixtures`: loads/verifies the matched nine source images,
  precomputes real stage inputs outside timing and records types/hashes/poses.
- `CanonicalGeometryBenchmark`: JMH production controls and isolated stages.
- `GeometryProfileEvidence`: provenance preparation, mirror parity and optional
  JDK Flight Recorder corroboration of the unchanged production path.
- `GeometryProfileStagesTest`: focused numerical/mask/failure/reuse tests.

| JMH scope | Actual work included |
|---|---|
| `wholeGeometry` | **Unchanged control:** decoded image bulk getRGB into reused packed buffer, then actual production `estimate`; returns geometry, no affine transform |
| `estimatorPacked` | Actual production `estimate` on prepacked source; directly measured, not control minus getRGB |
| `stagedWholeControl` | Bulk getRGB plus complete staged mirror; exposes method/field/JIT partition effects |
| `sourceGetRGB` | Exact `image.getRGB(0,0,700,700,packed,0,700)` call on the same decoded image types |
| `reduction` | Packed reads, luminance coefficients/rounding, staged horizontal/vertical BOX and gray writes, in their original fused loop |
| `histogram` | Clear256 bins and count all30,625 real gray bytes |
| `otsuBatch64` | Original256-bin threshold selection,64 independent operations per timed batch |
| `thresholdAndComponents` | Label clear, full gray scan, **fused threshold comparisons**,8-connected BFS, first-largest selection; includes real queue operations |
| `selectedMask` | Existing label-to-selected-foreground boolean mask materialization |
| `holeFill` | Exterior clear, border seeding,4-connected queue flood and final filled mask |
| `centroidAreaBorder` | Filled-mask scan, area/X/Y sums, border test, fraction/failure check, reduced centroid division |
| `harmonic4` | Filled-mask algebraic harmonic accumulation, exact-origin convention and mean division |
| `orientationTransformBatch64` | Final hypot/atan2/degrees/radius, validated `CanonicalGeometry`, and its existing `transform()`,64 operations/batch |

There is no separate foreground threshold-mask pass in production: thresholding
occurs while scanning/traversing components. Inventing a preliminary threshold
array would change runtime behavior, so it is not benchmarked. Selected-component
mask generation is genuinely separate and is measured separately.

Reduction's packed loads, per-pixel integer luminance, BOX arithmetic and gray
writes are interleaved. Their grouped operational scope is measured; independent
per-instruction costs are not invented by replacing the loop or adding extra
intermediates. Histogram is a separate existing pass and can be isolated.

Centroid and harmonic are consecutive passes in the same production method.
The mirror partitions them without changing the arithmetic. Final transform
construction is **outside** the historical/control geometry scope; its small
batched measurement is labelled accordingly rather than added to that control.

## Fixtures, hardware and benchmark method

Same nine images as integrated timing: frozen FIT good_120,011,039,208,027,042,
164,112 plus TEST bent_011. Their source poses span-41.751 to27.990 degrees,
with varying centers and a retained anomalous-shape framing stress case.
Every decoded image is **BufferedImage.TYPE_3BYTE_BGR (5),700x700**. Input bytes
are checked against the retained Phase 2 source manifest; no easy-angle selection,
synthetic-only timing or new perturbation generation occurs.

Host: Ryzen AI9 HX370,12 physical/24 logical cores, Windows11 Pro26200,
Temurin21.0.9+10-LTS, Balanced power scheme,2GiB heap. Pre-run background CPU
snapshot1%; no CPU/load isolation or affinity/power tuning. No ORT session is
created by this geometry benchmark; the established CPU-only encoder configuration
and bytes are untouched. No model loading, decode, disk, encoder, M2 or localization
is inside any timed scope.

JMH1.37 SampleTime, one caller, two forks, five2s warmup and ten2s measurement
iterations/fork, GC profiler, outputs consumed. Fixtures cycle deterministically.
Image-sized buffers are reused. Normal stage results are individual sampled-call
latencies. Otsu and final orientation/transform use64 operations/invocation; JMH
normalizes units per operation, but their p50/p95 describe **batch-average** cost,
not individual-call tails. All primary timings use SampleTime; no incompatible
AverageTime percentiles are substituted.

For isolated stages, nine real precomputed stage workspaces preserve their exact
input arrays/thresholds/centroids. Hole filling mutates its mask, so its original
selected mask is copied back in `@Setup(Level.Invocation)`, **outside timing**;
the real flood/queues still run every time. This reset warms that mask and adds
untimed harness activity. Other isolated inputs are precomputed once/trial.
Whole controls execute the complete pipeline with one reused estimator and
fresh source acquisition, without those per-invocation resets.

Method partitioning, field stores, precomputed inputs, cache warmth, batched
consumption and compiler profiles can change isolated stage cost. The staged
whole control exposes some of that effect. Component medians are **never summed**
to reconstruct geometry, and no difference of medians is called residual overhead.
Argument guards, dispatch, clear/reset passes and record validation are included
in their actual scopes. No independent material residual routine exists to time.

## Whole control, timings and per-fork evidence

**Unchanged whole geometry: p50 5.537792ms, mean 7.061223ms, p95
16.363520ms**, directly measured over5,694 sampled calls (2,814/2,880 per fork).
All13 scopes have two successful forks; no failed fork or measurement rerun.

| Scope | p50(us) | Mean(us) | p95(us) | GC bytes/op |
|---|---:|---:|---:|---:|
| Production whole geometry (control) |5537.792|7061.223|16363.520|452.98|
| Production estimator, packed input |1214.464|1239.662|1349.632|102.91|
| Staged whole control |5468.160|7012.115|17235.968|449.54|
| Bulk getRGB |4243.456|5672.326|14057.472|394.37|
| Luminance +BOX reduction |428.032|607.177|2004.992|51.61|
| Histogram |13.488|18.620|57.856|2.33|
| Otsu† |0.409|0.587|1.930|0.06|
| Threshold +components |249.600|350.762|1157.120|32.62|
| Selected mask |7.896|11.854|33.472|1.57|
| Hole fill |54.656|80.530|297.472|10.92|
| Centroid /area /border |20.384|24.409|37.184|2.77|
| Harmonic4 |49.856|47.932|63.680|4.02|
| Orientation +transform† |0.032|0.051|0.156|120.00|

†64-operation normalized batch averages. Other rows are individual-call samples.
These are microseconds, not milliseconds. Full precision and sample counts are
in `timing-summary.json` and the unmodified JMH raw histograms. Reconstructed
pooled p50/p95 and weighted means match all13 JMH primary results.

| Scope | Fork1 p50 /mean /p95(us) | Fork2 p50 /mean /p95(us) | GC bytes/op fork1 /fork2 |
|---|---|---|---:|
| Production whole geometry (control) |5586.944 /7137.872 /16990.208|5509.120 /6986.331 /15509.094|457.67 /448.29|
| Production estimator, packed input |1212.416 /1239.782 /1372.160|1216.512 /1239.542 /1335.296|103.02 /102.80|
| Staged whole control |5439.488 /6992.252 /17235.968|5480.448 /7032.103 /17235.968|448.16 /450.91|
| Bulk getRGB |4251.648 /5693.408 /14057.472|4227.072 /5651.380 /14062.387|396.11 /392.63|
| Luminance +BOX reduction |428.032 /595.443 /1966.080|428.544 /619.367 /2023.424|50.61 /52.60|
| Histogram |13.600 /18.800 /58.368|13.488 /18.444 /57.344|2.33 /2.32|
| Otsu† |0.410 /0.592 /1.942|0.407 /0.583 /1.922|0.06 /0.06|
| Threshold +components |249.856 /352.266 /1167.360|249.088 /349.268 /1142.784|32.69 /32.55|
| Selected mask |7.896 /11.584 /28.896|8.000 /12.133 /37.056|1.51 /1.64|
| Hole fill |54.784 /79.636 /286.720|54.656 /81.445 /302.080|10.69 /11.15|
| Centroid /area /border |20.384 /29.018 /78.080|20.384 /21.028 /22.592|3.64 /1.91|
| Harmonic4 |49.984 /51.584 /56.896|32.768 /42.903 /101.843|3.01 /5.02|
| Orientation +transform† |0.032 /0.052 /0.159|0.032 /0.050 /0.150|120.00 /120.00|

The two production whole-control p50s are5.586944/5.509120ms. Staged whole p50
is5.468160ms; its mean is99.3% of the production mean as a scale comparison,
not an equivalence test. Partitioning did not create an order-of-magnitude whole
cost difference, but individual scopes retain cache/JIT effects. In particular,
harmonic4 fork p50s49.984/32.768us and several wide tails limit fine comparisons.
The current whole-control tail is much broader than the integrated campaign's
historical result. Identical production source is verified; neither timing
difference is interpreted as a code regression or improvement. Host load,
scheduling, frequency and compiler contributions were not independently isolated.
All percentages below use **this campaign's whole mean7061.223us**, not the old
p50. No geometry total or residual is reconstructed by summing/subtracting stages.

### Bulk RGB acquisition

The authoritative standalone call costs **4.243456ms p50,5.672326ms mean,
14.057472ms p95**, with p50s4.251648/4.227072ms across forks. This is the
largest directly measured individual scope, on real TYPE_3BYTE_BGR inputs with
a reused output buffer. The installed JDK21 source in
`lib/src.zip:java.desktop/java/awt/image/BufferedImage.java` shows a pixel loop
calling `raster.getDataElements` then `colorModel.getRGB` for every pixel.
A band-count scratch array is created once per bulk call; the destination array
is allocated only when null, which these measurements never pass.

The generic conversion/access path is a substantial operational cost; the current
benchmark is not a direct-raster optimization. **No diagnostic direct-raster
ceiling was implemented or measured**, and no alternative pixel-conversion
equivalence or speed is claimed.

### Reduction, topology and moments

Reduction is second: p50 **428.032us**, mean607.177us. Histogram is a separate,
much smaller pass (p50 13.488us). The current measurement does not isolate packed
loads versus luminance arithmetic versus quantized BOX versus output writes
inside the fused reduction loop. It cannot establish which of those instructions
dominates without a separate experiment; no manufactured per-instruction
precision is presented.

Threshold/components are third (p50 **249.600us**, mean350.762us). Hole fill
p50 is54.656us; selected-mask materialization7.896us. Thresholding remains fused
with component traversal. Otsu selection is tiny (normalized batch p50 .409us).
Both topological operations use their real30,625-cell arrays and primitive queues,
not synthetic algorithm-only inputs.

Centroid/area/border p50 is20.384us; harmonic4 is49.856us pooled, with the fork
dispersion noted above. Final orientation/geometry/affine construction is tiny:
batch-average p50 .032us, mean .050699us. Its combined small-record allocation
is measured below. No moment approximation, omitted hole fill, reduced grid or
changed transform is implied by these measurements.

## Allocation interpretation

Production reuses packed source490,000 ints, gray30,625 bytes, labels/queue each
30,625 ints, selected/exterior masks each30,625 booleans and256 histogram ints.
Raw estimator array payload is337,899 bytes; packed source is1,960,000 bytes.
These are capacities, not measured heap/RSS, and setup-only allocation is excluded.
Diagnostic snapshot getters clone arrays only during validation, never timing.

The numeric/topology passes themselves allocate no image-sized per-call arrays.
`finish` constructs the existing small geometry record; requesting an affine
constructs the existing rigid-transform record. Bulk getRGB can allocate internal
conversion scratch despite a reused destination. GC-profiler normalized bytes/op
include JMH/runtime activity and must not be read as exact object-size ledgers.
Small nonzero numbers on otherwise allocation-free passes are retained as noise,
not hidden or used as an excuse to optimize allocations.

Final orientation plus geometry/transform records measure **120.004B/op** in the
batched GC profile, stable at120.004B/op in both forks. The production packed
estimator measures102.911B/op, bulk getRGB394.366B/op and whole control452.981B/op;
their normalized counts include runtime/harness allocation and cannot be
subtracted into an exact object ledger. For example, component traversal32.620B/op
and reduction51.608B/op do not imply those loops allocate new arrays: inspection
shows reused arrays only. The small records remain unchanged; allocation
optimization is not the recommendation. The installed JDK bulk implementation's
band-count scratch is a small per-call allocation in source, not a new source-sized
RGB array for these non-null-destination calls.

## JFR corroboration

After JMH, a separate production-only recording used the same nine decoded images,
one estimator/reused packed buffer,5s warmup and30s JDK21 `profile` recording with
10ms execution sampling. It completed4,732 operations, consuming returned poses.
No staged mirror, encoder or model was executed in the recording. This is
corroborating evidence, not another primary latency measurement.

The retained JFR and full-depth JSON contain1,752 execution samples:1,750 on the
main thread and2 elsewhere. **1,425/1,750 (81.43%)** main-thread stacks lie inside
bulk getRGB. Their leaf frames are:

| Leaf frame within getRGB | Samples |
|---|---:|
| `ByteInterleavedRaster.getDataElements` |658|
| `ComponentColorModel.extractComponent` |421|
| `ComponentColorModel.getRGBComponent` |346|

Another163 main-thread samples (9.31%) map to the existing fused reduction loop.
These stack observations corroborate bulk acquisition/conversion dominance; they
are **not wall-time percentages** or replacements for direct stage measurements.
Fine source-line labels are less reliable:78 samples map to the selected-mask
statement despite that isolated pass's small direct cost. Inlining/poll/source-line
mapping may affect that attribution; it does not override the direct ranking.

Only two object-allocation sample events were captured, both in ASM/runtime
work rather than geometry. Their weighted allocation samples are not a per-call
ledger and cannot quantify the small geometry records. Allocation conclusions
therefore use source inspection and the JMH GC results, not a division of JFR
sample weights by operation count. No new profiling software was installed.

## Reproduction, validation and provenance

Evidence root: `target/experiments/java-canonical-geometry-profile-20261020`.
Source/input hashes, nine fixture identities/types, hardware, per-trial runtime
records and raw JMH JSON are retained. Trial directories are atomically unique,
avoiding the integrated campaign's historical PID-reuse collision. Fail-on-error
is enabled. Target evidence and the external dataset require separate restoration.
Never run `mvn clean` as preparation and never overwrite an existing output root.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
$cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
$out = "target/experiments/java-canonical-geometry-profile-new"
$phase2 = "target/experiments/java-canonicalization-phase2-20261019"
$phase1 = "target/experiments/java-canonicalization-phase1-20261019"
$dataset = "C:/Users/Ion/IdeaProjects/Anomalib4j_md/mvtec-ad-DatasetNinja"
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.canonical.GeometryProfileEvidence prepare $out $phase2 $dataset $phase1
java -Xmx2g -cp $cp org.openjdk.jmh.Main '.*CanonicalGeometryBenchmark.*' -p run=$out -p phase2=$phase2 -p dataset=$dataset -f 2 -wi 5 -i 10 -w 2s -r 2s -t 1 -bm sample -prof gc -foe true -rf json -rff "$out/jmh.json"
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.canonical.GeometryProfileEvidence profile $out $phase2 $dataset
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.canonical.GeometryProfileEvidence parity $out $phase1
java -Xmx2g -cp $cp io.github.antctrlwin.anomalib4j.evaluation.CanonicalPhase1Parity "$out/reference-parity" target/experiments/m2-metal-nut-canonicalization-20261019
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
git diff --check
```

The existing reference runner writes into the **new copied reference directory**,
preserving its original Phase 1 outputs and all retained numerical evidence.
All423 mirror/production results, gray images, selected components, filled masks
and transforms are exact. The rerun of the existing frozen reference check covers
all335 originals plus88 saved rotations, with the same status/reason, exact
gray/component/filled data, **centroid error0**, maximum angle error
1.0480505352461478e-13 degrees and maximum affine coefficient error
7.389644451905042e-13. Harmonic-strength error is1.7763568394002505e-15.
Existing1e-6 frozen pose/affine tolerances are unchanged. Its retained bridge also
reproduces all8 encodings and4 native maps with maximum error0.

**Three focused profiling tests pass; the full relevant Java suite passes183
tests**,0 failures/errors/skips. Focused tests compare geometry/masks and source
preservation through normal/empty/weak/border/reuse cases, exact randomized RGB
reduction/histogram, and repeated selected-mask restoration/hole filling/moments.
Existing M0/VSA/Adjoint/M2, geometry, encoder, persistence and metric regressions
remain intact. Expensive Bottle training/quality tests are excluded by the stated
standard command; this task runs geometry/reference checks rather than a quality
campaign. `git diff --check`, new-file whitespace and local documentation links
are checked. No test is removed, skipped or weakened to pass this task.

All150 captured source/resource hashes and1,099 retained input/evidence hashes
remain unchanged after timing, JFR and parity. All26 trial records preserve the
same nine fixture identities and BGR type. No benchmark-relevant source changed
after measurement. Raw JMH JSON, per-fork histograms, `timing-summary.json`,
`geometry.jfr`, full-depth events/`jfr-summary.json`, mirrored/frozen parity results
and completion/source checks remain under the evidence root. Logs are
`target/geometry-profile-{focused,compile,prepare,jmh,jfr,parity,java-tests}.log`.
Python source is not touched; its regression suite is not required for this
Java-only measurement checkpoint. Java LSP is unavailable; Maven/JUnit provide
compile/test diagnostics. No profiling stack is installed.

## Ranking and one next target

| Rank /stage | p50(us) | Mean context | GC bytes/op | Likely avoidable? /classification | Confidence |
|---|---:|---:|---:|---|---|
| 1. Bulk getRGB |4243.456|80.3%|394.37|Source acquisition necessary; generic conversion overhead likely removable|High for dominance; replacement benefit unmeasured|
| 2. Luminance +BOX reduction |428.032|8.6%|51.61|Structurally necessary fused work; avoidable fraction uncertain|Medium|
| 3. Threshold +components |249.600|5.0%|32.62|Structurally necessary topology; implementation overhead uncertain|Medium|
| 4. Hole fill |54.656|1.1%|10.92|Structurally necessary; implementation overhead uncertain|Medium; untimed reset/cache effect|
| 5. Harmonic4 |49.856|0.7%|4.02|Structurally necessary mathematics; implementation fraction uncertain|Medium; fork dispersion|
| 6. Centroid /area /border |20.384|0.3%|2.77|Structurally necessary statistics|Medium|
| 7. Histogram |13.488|0.3%|2.33|Structurally necessary counts; pass overhead uncertain|Medium|
| 8. Selected mask |7.896|0.2%|1.57|Necessary representation; pass overhead uncertain|Medium; coarse JFR line attribution|
| 9. Otsu† |0.409|0.0083%|0.06|Structurally necessary threshold selection|Medium for average cost; tiny|
| 10. Orientation +transform† |0.032|0.0007%|120.00|Required output mathematics/ownership; transform outside control|Medium for average cost; tiny|

Mean context is the isolated-scope mean divided by the directly measured whole
mean, rounded for scale. It is **not an additive percentage budget or causal CPU
share**. Batched rows compare average scales, not tail distributions. Final
orientation/transform includes an operation outside the whole-control scope.
Controls/aggregates are excluded from the contributor ranking to avoid counting
their constituent work twice.

**Exactly one first optimization target:** replace generic bulk getRGB with a
contract-preserving acquisition path for validated TYPE_3BYTE_BGR inputs. Future
validation must preserve default-sRGB pixel values, layout/stride/subimage and
fallback semantics before any scientific inference uses it. No such path is
implemented here; the recommendation changes no source/geometry mathematics.

**Conservative opportunity:** the measured typical acquisition scope is4.243ms.
Treat roughly **4ms per typical geometry call as an optimistic scope-removal
ceiling**, requiring most generic conversion overhead to disappear. Required
pixel reads, packing and writes still cost time, and replacement cost is unmeasured,
so actual savings must be smaller and may be substantially smaller. The5.672ms
mean includes long tails and is not all demonstrably removable CPU work. This is
an opportunity estimate for one scope, not a predicted new geometry median,
a statistical bound, or an end-to-end detector speedup. No total is obtained by
subtracting medians. No production optimization has been performed.
