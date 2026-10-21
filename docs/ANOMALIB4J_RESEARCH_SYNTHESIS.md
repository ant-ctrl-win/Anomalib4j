# Anomalib4j research synthesis and architectural checkpoint

Evidence reviewed: **2026-10-21**. This is the high-level interpretation of the
retained research and current implementation, not a new experiment or a replacement
for the detailed reports. Historical measurements, decisions and identifiers retain
their original meaning. The other canonical documents have not been reconciled in
this task.

## 1. Executive conclusion

Anomalib4j began as a CNN -> VSA positional memory -> compiled Adjoint detector.
Its exact compilation remains a valid and useful engineering result. Subsequent
ablations support a simpler detector: frozen spatial CNN descriptors with strict
position-specific, variance-aware normal statistics and held-out calibration.
That selected detector is **M2**, with a separate Java implementation and no VSA
projection or Adjoint scoring. The tested random expansion supplies neither new
independent input information nor an observed macro-quality advantage over the
direct cosine control. [E01], [E02], [E03], [E20], [E21]

Canonicalization improves the coordinates in which strict M2 operates. Metal Nut
has a qualified fourfold-pose Java extension, now measured at **4.423680 ms p50**
for complete score-only decisions on the retained desktop CPU fixtures. Screw
provides strong second-family support for registration and aggregate quality, but
its head-scratch regression prevents promotion. The main scientific limitation is
discriminating defect response from residual normal nuisance under variable
geometry and appearance; channel covariance is a plausible next hypothesis, not
an established explanation or solution. [E07], [E10], [E11], [E13], [E16], [E17], [E18], [E19]

The repository supports a **bounded v1/publication preparation phase now**. It does
not support a universal canonical detector, illumination/reflection robustness,
camera-throughput claims or production qualification of Screw. Publication still
needs a coherent public narrative and restorable evidence/provenance package.
One further covariance cycle is optional research, not a prerequisite for accurately
publishing the existing result.

## 2. Evidence interpretation and scope

The current-state reading used [PROJECT_STATE](PROJECT_STATE.md),
[DECISION_LOG](DECISION_LOG.md) and [EXPERIMENT_INDEX](EXPERIMENT_INDEX.md), then the
reports and selected quantitative artifacts listed in Section 16. The frozen
[local-model definitions](experiments/LOCAL_MODEL_ABLATION_PLAN.md),
[correspondence protocol](experiments/M2_SPATIAL_CORRESPONDENCE_PLAN.md) and
[ONNX contract](architecture/ONNX_MODEL_CONTRACT.md) constrain interpretation.
Current `M2Model`, `CanonicalM2Detector` and `CanonicalM2Contract` were also inspected.

**32 evidence reports/artifacts were inspected: 21 reports/reviews and 11 retained
quantitative/validation artifacts**, in addition to the six canonical/contract/plan
documents above, AGENTS and those three implementation files. Inspection is not
fresh execution or a comprehensive rehash of every campaign input. This task ran
no fitting, evaluation, inference, scientific tests or performance measurement.

Throughout this document:

- **Scientific quality** means retained TEST Image AUROC, global Pixel AUROC and
  normalized AUPRO@0.30. A category result is not an all-category macro average.
- **Diagnostic evidence** includes normal-only perturbations, FIT coherence and
  post-hoc response accounting. These are not operational false-positive rates.
- **Performance** refers to a directly measured scope under its recorded protocol.
  Separate medians are never added to estimate a complete path.
- **ESTABLISHED** means directly demonstrated or algebraically determined within
  the specified contract; **SUPPORTED** means the evidence favors an interpretation
  with unresolved generalization or mechanism; **MIXED** means material positive
  and negative evidence coexist; **REJECTED** rejects the stated claim in the
  tested setting; **UNTESTED** denotes a hypothesis without the required experiment.
  None means universal proof across acquisitions.

The repeated use of known MVTec splits limits independent confirmation. Retained
`target/` evidence and the external DatasetNinja dataset require separate restoration
in a fresh clone. Historical source guards must not be bypassed to call a current
HEAD execution an exact reproduction of an older campaign.

## 3. Research path: what each stage changed in the interpretation

### Stage A: M0 and the value of exact compilation

M0 normalizes each 96-channel CNN patch, projects it through a fixed dense
Rademacher matrix into D=10,000 dimensions, applies globally pooled VSA mean/std,
and learns a unit positional archetype from each position's standardized mean.
Projection seed is 42; CNN norm epsilon is 1e-6 and global std floor is 1e-8.
For normalized patch `u`, explicit similarity and its compilation are:

```text
y = R*u/sqrt(D); z = (y-mu)/sigma; similarity = A[p]^T*z
q[p] = A[p]/sigma
W[p] = R^T*q[p]/sqrt(D); b[p] = -mu^T*q[p]
similarity = W[p]^T*u + b[p]
raw[p] = 1-similarity
```

This removes runtime projection while preserving the explicit score numerically.
The original equivalence evidence records real maximum errors around 1.3e-12;
the later frozen-state reconstruction independently agrees at floating-point
tolerance. This result remains useful even though M0 is no longer selected.
Positional held-out Z and native max are subsequent score operations, not the
Adjoint compilation itself. [AGENTS](../AGENTS.md), D01-D05, [E20], [E21]

The weakness is statistical and geometric, not a failed equivalence. A positional
direction relative to pooled global statistics need not describe the normal
distribution at that cell. Bottle has strong between-position structure; Metal
Nut has much more comparable within/between variation. The variance diagnostic
is an association, not proof that global standardization caused every failure.
The old pre-L2 CNN diagnostic was corrected to compare the same normalized
inputs; its `preL2_legacy` branch is historical context only. [E20]

Early Bottle RAW, in-sample-Z, held-out-Z, split-sensitivity and SPATIAL_28 results
are distinct generations in the experiment index. They must not be combined
into a single baseline. SPATIAL_28 changes stage and channels as well as grid
size. The 2026-10-03 external comparison covered Bottle and Metal Nut; the later
2026-10-16 M0 campaign actually covered all 15 categories. [E01], D04-D17

### Stage B: direct local statistics outperform the tested expansion

The earlier two-category post-hoc M1/M2/M3 experiment motivated a prospective
all-15 local-model comparison. Historical **M3 already means VSA dispersion**;
it is not a name available for a future covariance model. [E21]

The 2026-10-17 comparison shares encoder bytes, feature arrays, manifests and
evaluator. It consumes the retained all-15 M0 result rather than rerunning M0.
Every candidate has its own held-out positional Z:

- **C1:** cosine discrepancy from the unit local CNN centroid.
- **V1:** exact cosine discrepancy after the fixed Rademacher projection,
  including the query norm denominator; the Gram-form scorer is checked against
  explicit projected cosine. It does not use M0's global VSA standardization.
- **M2:** mean squared channel residual standardized by each position/channel's
  FIT sample std. This retains the historical M2 mathematical definition.

V1's 10,000 coordinates lie in the image of the original 96-dimensional CNN
space. Expansion cannot create independent information about the image. It can
change the metric and inductive bias; lack of independent information alone
does not prove that every projection-based model must perform identically.
Here exact V1 fails to improve any of the three macro metrics over C1. M2 leads
the macros and improves localization substantially; it does not win every
category's Image AUROC. [E02], frozen local-model plan, [S02]

The old M1-Z/isotropic-distance derivation also matters: for unit descriptors
and nonzero local mean, squared isotropic distance is a position-constant positive
affine transform of cosine discrepancy. With inactive calibration floors, Z removes
that affine difference. The measured M2 gain therefore supports anisotropic local
weighting, rather than merely using a non-unit centroid. [E21]

Java M2 subsequently reproduces Python quality on Bottle, Metal Nut and Hazelnut
with zero reported metric deltas. Bottle native-Z maximum error is approximately
1.36e-12; six freshly encoded images have maximum native-map error 8.53e-14.
This is implementation parity on retained data, not independent scientific
replication. There has been no separate all-15 Java M2 TEST-quality campaign.
The later Java all-15 normal robustness and correspondence evidence has its own
scope and does not silently become that missing campaign. [E03], [E04], [E05]

### Stage C: correspondence is useful information, not just a nuisance

**P** uses reference position p for query p. **N1** takes the minimum standardized
residual over the clipped 3x3 reference neighborhood. **G** takes the minimum over
all 196 references. Reference mean/std follows the selected reference q; the output
stays at query p. N1/G receive separately estimated held-out calibration at the
query position. They do not refit the base local distributions.

Normal geometric score shifts decrease P -> N1 -> G, while all three quality
macros decline. N1's Pixel/AUPRO macro loss is small and category-dependent;
Metal Nut loses materially. G is much more permissive and loses quality in 11/15
categories under the report's mean-three-metric summary. The experiment supports
useful spatial identity, not a claim that every neighboring feature is anomalous.
[E05], [S03]

The correspondence report originally recommended N1. **D20 explicitly supersedes
that recommendation**, preserving P and moving toward upstream geometric control.
The architectural decision is to preserve strict local matching and address
geometric variance upstream rather than weaken correspondence downstream.
This is a design choice informed by the tradeoff, not a rewrite of the report's
small macro losses into a catastrophic N1 failure.

### Stage D: from diagnostic geometry to a qualified Metal Nut path

1. **Controlled robustness:** 467 normal tests/all 15, 19,147 paired observations.
   Small rotations/scales cause broad score increases; Bottle has extreme
   photometric outer-grid responses. These are joint encoder/model responses,
   not a causal isolation of wrong-cell comparisons. [E04]
2. **Oracle round trips:** known inverse parameters partly recover degradation,
   especially shrink/restore scale (.90/.95 recovery 97.0%/92.3%). Rotation
   recovery and texture behavior remain poor; resampling and lost content are
   not invertible merely because coordinate parameters are known. No anomaly
   quality or real pose estimator was established by this diagnostic. [E06]
3. **Metal Nut scientific pilot:** RGB-only centroid/H4, scale1, source700
   canonicalization on FIT/CAL/TEST plus fresh M2 statistics improves quality,
   coherence and source-rotation stability. Python canonicalization costs
   12.308 ms as a median of batch averages, failing its frozen 2 ms gate.
   The historical procedural verdict remains NO-GO. [E07]
4. **Architecture review:** the user retired that cost gate for the subsequent
   Java investigation. The review preferred footprint-aware D and treated B
   as a cheap diagnostic; that was a sampling hypothesis, not a finding. [E08]
5. **Java Phase 1:** geometry parity on 335 originals plus 88 rotations;
   eight retained encodings/four maps exactly reproduced; prepared encoder seam
   and scalar B/C/D coordinate/filter infrastructure validated. No scientific
   sampler winner or latency benefit follows from this checkpoint. [E09]
6. **Phase 2 qualification:** independently refitted B/C/D all pass six frozen
   gates on the same 176 FIT/44 CAL/115 TEST and 88 rotation pairs. B wins on the
   measured quality/cost tradeoff; C's small Image advantage and D's tighter
   distributions/lower signed rotation medians remain explicit. D does not win
   primary quality and has a worse +10-degree tail. B's qualification supersedes
   the review's provisional preference for D. [E10]
7. **Production integration:** a distinct Metal Nut canonical bundle, shared
   FIT/CAL/inference boundary and fail-closed Java API reproduce all 115 B maps,
   scores and metrics exactly. Early complete-decision p50 was 10.108928 ms. [E11]
8. **Profile then optimize:** geometry profiling identifies generic getRGB as
   the largest measured scope. A guarded BGR acquisition replacement preserves
   exact scientific behavior and lowers the matched complete decision to
   4.423680 ms p50. These are engineering parity/performance findings, not a new
   model-quality gain. [E12], [E13], [S08], [S09]

## 4. Decision-relevant numerical hierarchy

### All-15 macro scientific TEST quality

Equal-category arithmetic means; same retained manifests/encoder/evaluator.
M0 is the preceding Java campaign; C1/V1/M2 are the Python scientific ablation
using shared Java features. Values rounded to six decimals. [E01], [E02], [S01], [S02]

| Model | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---:|---:|---:|
| M0 | 0.818690 | 0.807989 | 0.575592 |
| C1 | 0.831351 | 0.866853 | 0.682340 |
| V1 | 0.830704 | 0.866527 | 0.681510 |
| M2 | **0.871803** | **0.931016** | **0.805887** |

This is a measured ordering on this dataset/protocol, not a significance claim
or a current all-15 comparison against PaDiM/PatchCore.

### Spatial correspondence: all-15 TEST quality plus normal-only diagnostics

The first three columns are quality macros. Rotation/scale columns are means
over tested nonidentity levels of equal-category mean paired median Z shifts;
they are not AUROC, pooled medians or FPR. Lower shifts indicate better tested
normal stability. [E05], [S03]

| Model | Image AUROC | Pixel AUROC | AUPRO@0.30 | Rotation shift Z | Scale shift Z | Interpretation |
|---|---:|---:|---:|---:|---:|---|
| P | 0.871803 | 0.931016 | 0.805887 | 47.802 | 34.055 | Best quality macros; most geometric sensitivity |
| N1 | 0.860174 | 0.929119 | 0.805503 | 16.990 | 22.734 | Better stability; modest macro losses, material category exceptions |
| G | 0.844808 | 0.915837 | 0.784730 | 8.876 | 10.524 | Greatest tolerance; larger quality loss and scoring cost |

### Metal Nut: category-specific scientific quality and normal diagnostics

176 FIT/44 CAL/115 TEST. A is retained unaligned M2; B is the qualified direct
Java fused-bilinear candidate, **not** the earlier Python pilot also called B.
The pilot's metrics are 0.964321/0.886982/0.648731 and must not replace these.
[E07], [E10], [E11], [S04], [S05]

| Scope / quantity | Unaligned A | Selected Java B |
|---|---:|---:|
| TEST Image AUROC | 0.845552 | 0.960899 |
| TEST Pixel AUROC | 0.849318 | 0.886602 |
| TEST AUPRO@0.30 | 0.624208 | 0.646812 |
| FIT mean same-position pairwise cosine | 0.548333 | 0.870472 |
| FIT median channel sample std | 0.010995 | 0.008075 |
| Source-rotation diagnostic: average absolute signed level-median delta Z | 9.012412 | 0.472908 |

Rotation uses the same 22 normal originals and four source700 rotations
{-10,-5,+5,+10} degrees, each model's own fitted Z. It differs from the original
224-grid robustness perturbation. The rotation aggregate is not the mean of
per-image absolute errors. Coherence measures are coupled for unit vectors;
they are not independent proofs of quality. B retains limitations including
flip AUPRO 0.287480 and the pilot's bent_011 post-warp framing diagnostic. [E07], [E10]

**Metal Nut performance, separate from quality:** final matched JMH complete
decision below; milliseconds per operation, predecoded nine BGR fixtures, same
bundle, one caller, two forks, warm JVM/CPU. [E13], [S08]

| Path | p50 ms | Mean ms | p95 ms |
|---|---:|---:|---:|
| Matched generic acquisition comparator | 10.272768 | 10.173606 | 12.992512 |
| Current guarded BGR path | **4.423680** | **4.363740** | **5.816320** |

This is complete score-only inference, excluding decode/I/O/loading/localization.
It is neither scorer-only latency nor measured camera throughput. Material fork
variation and sequential trial ordering limit universal speedup claims.

### Screw: category-specific scientific TEST quality

Same 160 TEST identities. H uses historical 256 FIT/64 CAL; I and C use matched
253/63 usable normals after the frozen source-border exclusions. I and C share
logical-RGB1024->700/B, CNN, M2 and evaluation composition; the pose intervention
includes its consequences for refitted local/Z state and map coordinates.
[E16], [E17], [S06]

| Model | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---:|---:|---:|
| H: historical unaligned M2 | 0.564870 | 0.863996 | 0.602107 |
| I: matched B_IDENTITY | 0.564870 | 0.864740 | 0.607138 |
| C: safe canonical axis+polarity | 0.663456 | 0.961903 | 0.845029 |
| I -> C absolute delta | +0.098586 | +0.097163 | +0.237891 |

H -> I is 0/+0.000745/+0.005031. The retained descriptive decomposition assigns
100%/99.2396%/97.9290% of net H -> C gain to I -> C. These are arithmetic shares,
not independent causal proportions or confidence intervals; small net pipeline
effects can contain cancellation. [E17]

**Screw FIT registration diagnostics, matched 253 normals:** [E15], [E17]

| Quantity | I | C, safe placement |
|---|---:|---:|
| Pooled same-position cosine mean | 0.469488 | 0.784784 |
| Pooled same-position cosine median | 0.541021 | 0.830232 |
| Median channel sample std | 0.015201 | 0.010933 |
| LOO same-cell fraction | 0.166914 | 0.618248 |
| LOO best-cell drift, native cells | 1.001650 | 0.407709 |

**Counterexample: scratch_head TEST subgroup**, 24 anomalies plus the same 41
normals, not a category macro. Other four defect groups improve all three
metrics versus I. [E17], [S07]

| Model | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---:|---:|---:|
| H | 0.569106 | 0.940366 | 0.784869 |
| I | 0.553862 | 0.936421 | 0.773151 |
| C | 0.538618 | 0.909816 | 0.713909 |
| I -> C delta | -0.015244 | -0.026605 | -0.059242 |

## 5. Central claims taxonomy

Each classification applies to the claim as worded and the measured setting.

| Claim | Classification | Evidence | Important limitation |
|---|---|---|---|
| Random VSA expansion improves this single-embedding detector's anomaly quality over direct local controls | **REJECTED** in the tested comparisons | V1 macros slightly below C1; historical M3 localization below M2 [E02], [E21] | Other projections/models/tasks are not exhausted; individual metric/category orderings differ |
| The 10,000-dimensional projection supplies independent information beyond its CNN input | **REJECTED** | Deterministic linear image of 96D descriptors; exact V1 definition | A changed metric can still alter performance; this does not assert information loss |
| VSA could help heterogeneous/multimodal fusion | **UNTESTED / FUTURE HYPOTHESIS** | No multimodal experiment here | Cannot use current detector results to establish fusion value |
| Local variance-aware modeling improves over C1/V1 | **ESTABLISHED** on retained all-15 macros | M2 quality hierarchy [E02], [S02] | One encoder/protocol, no independent acquisition study; not every Image metric wins |
| Strict positional correspondence carries useful anomaly information | **SUPPORTED** | P/N1/G quality/stability tradeoff [E05] | Some categories benefit from N1/G; no universal optimality proof |
| Unaligned M2 tolerates arbitrary geometric variation | **REJECTED** | Broad small rotation/scale/translation responses [E04] | Synthetic tested ranges do not enumerate every real variation |
| Diagonal M2 fully captures residual normal variability | **UNTESTED** | No covariance-aware comparison | Existing tails do not themselves identify channel correlation as cause |
| Pose normalization can materially improve strict local anomaly detection | **SUPPORTED** | Metal qualification; matched Screw aggregate gain [E10], [E17] | Category/acquisition dependent; Screw head counterexample |
| Metal Nut H4 canonical detector is qualified | **ESTABLISHED** within frozen qualification | Six gates, integration parity and BGR regression [E10], [E11], [E13] | Known split, fixed700 contract; not universal acquisition or reflection validation |
| Screw axis+polarity improves normal registration | **ESTABLISHED** within audited population | Coherence, drift, framing and endpoint diagnostics [E15], [E17], [E18] | Silhouette proxies, no true pose ground truth; four training source failures |
| Pose explains nearly all net Screw aggregate improvement | **SUPPORTED** | Matched I -> C versus small H -> I [E17], [S06] | Includes refitting/calibration/remapping; no isolation of each downstream mechanism |
| Canonical Screw is uniformly better and production qualified | **MIXED** | Large aggregate/neck/thread gains but head loses all metrics [E16], [E17] | Experimental only; image discrimination remains weak |
| One canonicalization strategy generalizes to all MVTec object classes | **REJECTED** as a current claim | Six-category audit finds different/no useful pose concepts [E14] | Rejects uniform tested strategy, not all future category-specific methods |
| Thresholded foreground always yields semantic pose | **REJECTED** | Hazelnut print anchor; Cable broken-ring anchor [E14] | Numeric confidence/equivariance is not semantic validity |
| Lower fitted local variance automatically improves anomaly detection | **REJECTED** | Screw head regression; D tighter without best Metal quality [E10], [E17], [E18] | Tightness can help, but must be checked with defects and nuisance |
| Direct B/fused bilinear is qualified for Metal Nut | **ESTABLISHED** within frozen gates | Independently refitted B, six gates, exact integration [E10], [E11] | Does not prove ideal anti-aliasing or equivalence to every sampler/category |
| Current canonical score-only decisions have low single-digit millisecond CPU latency on the measured desktop path | **ESTABLISHED** | Saved JMH 4.423680 ms p50, CPU node evidence [E13], [S08] | Resident BGR fixtures, host variability, decode/localization excluded |
| These timings prove camera/system throughput | **REJECTED** as an inference | Benchmark scope explicitly excludes acquisition/decode/queues [E13] | Actual sustained system throughput remains unmeasured |
| A generalized production pose-strategy registry is now justified | **REJECTED** as a readiness claim | Only Metal is integrated/qualified; six-category outcomes differ [E14], [E17] | Conceptual separation is reusable; implementation generalization is premature |
| Screw head regression is explained by uniform loss of scratch signal | **REJECTED** as the dominant account | Head max strengthens in 17/24; GT-local response usually rises [E18] | Seven head-max decreases and limited resampling evidence remain |
| Photometric sensitivity completely explains the original Screw nuisance tails | **MIXED**, completeness unestablished | Frozen-pose CAL control gives partial excess/shared sensitivity [E19] | No direct causal attribution of original TEST tails; clipping/reflections unresolved |

## 6. What the project disproved or weakened

- **Dimensional expansion as the source of accuracy:** exact V1 does not beat
  C1 macros, and direct M2 localization beats historical projected dispersion.
  The 10,000 coordinates are not independent observations. This weakens this
  particular single-CNN VSA accuracy rationale, not all VSA applications. [E02], [E21]
- **Global pooled statistics as sufficient positional modeling:** M0's global
  reference/archetype does not match the quality of local mean/dispersion here.
  The within/between diagnostic explains why position and dispersion deserve
  separate treatment, but does not isolate a universal global-statistics cause.
  [E01], [E02], [E20]
- **Spatial permissiveness is automatically beneficial:** N1/G improve normal
  perturbation stability while reducing quality macros. Easier matching can also
  explain away defect evidence. N1 remains a measured tradeoff, not a broken
  implementation; G's losses are much broader. [E05]
- **Synthetic geometric robustness is enough:** lower perturbation score shifts
  do not establish anomaly sensitivity. Oracle inverses cannot restore lost
  content/resampling detail. Screw coherence and aggregate gains coexist with a
  real head-defect regression. [E05], [E06], [E17]
- **Any numeric centroid/axis is semantic pose:** a print fragment or broken
  ring can provide a stable, confident anchor for the wrong component. Synthetic
  equivariance of that anchor is insufficient. [E14]
- **Canonicalization can be uniform across categories:** H4 suits Metal Nut;
  Screw needs a directed axis and different placement; Bottle is already nearly
  coherent; Grid lacks a bounded whole-object pose. Only six categories were
  audited. [E14], [E15]
- **Lower fitted std automatically means better quality:** it changes both defect
  and nuisance sensitivity. Metal D and Screw head supply counterexamples to
  automatic adoption based on tightness alone. [E10], [E17], [E18]
- **Head scratches were simply erased:** the dominant retained pattern is stronger
  local head response competing with stronger normal maxima. This does not rule
  out subtle sampling/pose effects in individual cases. [E18]
- **Photometry alone explains the nuisance mechanism:** the control establishes
  sensitivity under the prescribed factors, not complete attribution of existing
  tails. Saturation is coupled to strong brightening; local reflections, residual
  pose, coverage and covariance remain unresolved. A photometric correction has
  not been qualified. [E19]

## 7. Current architectural model: three independent layers

The best-supported conceptual direction is:

```text
input under a declared acquisition contract
  -> category/acquisition-specific semantic pose, when meaningful
  -> fail-closed pose/acquisition validity check
  -> canonical rigid sampling, when qualified
  -> frozen MobileNetV4 SPATIAL_14
  -> unit local CNN descriptors
  -> strict position-specific normal statistical model
  -> held-out positional Z
  -> native 14x14 anomaly map
  -> native max for fast decision
  -> localization/backprojection only when needed
```

This diagram is a conceptual architecture, not a claim that a universal production
front end or production backprojector exists. Generic Java `M2Detector` still uses
the unaligned whole-image RGB bicubic224/ImageNet path. Only Metal Nut has the
separate integrated canonical production path. Screw lives in qualification code.
[E03], [E11], [E16], [E17]

**A. Pose/acquisition model:** declares foreground semantics, symmetry/polarity,
framing, resolution/color and explicit failure policy. It is category-specific
and unsolved generally. It must not absorb a defect through adaptive cropping,
scale fitting or unreported rejection. Current Metal/Screw transforms use scale1;
there is no claim of learned scale invariance.

**B. Representation:** the frozen MobileNetV4 encoder produces float32
NHWC[1,14,14,96], HWC flattened. Input is float32 NCHW[1,3,224,224]; the prepared
tensor path permits an explicit upstream sampler without changing the CNN. The
legacy preprocessing contract is already implemented and known. Its existence
does not resolve checkpoint/export provenance. [E09], ONNX contract

**C. Normal statistical model:** current M2 normalizes each patch in float64,
rejects norm <1e-6 and uses FIT local sample mean/std, std floor1e-6:

```text
u[p] = x[p]/||x[p]||
raw[p] = mean_c(((u[p,c]-mu[p,c])/s[p,c])^2)
alpha[p] = mean_CAL(raw[p])
beta[p] = max(sampleStd_CAL(raw[p]),1e-6)
Z[p] = (raw[p]-alpha[p])/beta[p]
imageScore = max_p Z[p]
```

No VSA/Adjoint, score clamp, regional head score or neighborhood search is present
in selected M2. Held-out Z is score normalization, not a probability or an
operational false-alarm guarantee. [E03], [M2Model](../src/main/java/io/github/antctrlwin/anomalib4j/m2/M2Model.java)

Keeping the three layers conceptually independent permits attribution: pose
corrects coordinates, representation observes appearance, statistics defines
normal variation. Changing several together requires matched controls and a new
preprocessing/model identity; improvement cannot be assigned by intuition.
M0's valid Adjoint invariant remains separate and preserved.

## 8. Six-category state matrix

This updates the original screening interpretation with the subsequent Screw
checkpoints. It makes no pose claim for the other nine MVTec categories.
[E14], [E15], [E16], [E17], [E18]

| Category | Usable pose concept | Registration evidence | Anomaly-side geometry reliability | Detector evidence | Current status | Recommended action |
|---|---|---|---|---|---|---|
| Metal Nut | Filled centroid + H4 modulo90, fixed scale1/canvas700 | Large coherence/drift gain; controlled rotation recovery | 115/115 source poses pass; color/deformation outliers and bent_011 post-warp limitation remain | Qualified B metrics and exact integrated parity | Qualified, sole canonical production category | Retain bounded contract and known limits |
| Screw | Strong major axis + head/tip mass polarity; FIT-frozen safe placement | Matched cosine .469488->.784784; safe253 FIT/63 CAL support; strong endpoint alignment | 160/160 TEST pass, no framing-margin violations; no external pose ground truth;3/1 training failures explicit | Matched aggregate gain, all-metric scratch_head regression | MIXED / test-only experimental | Do not promote; retain stress case for optional statistical research |
| Bottle | End-on circular object; center only, rotation not observable | Already high identity cosine .973798; centering .974945, negligible gain |20/20 normals,61/63 anomalies pass; two contaminants reach source border | Historical unaligned M2 quality; no canonical detector qualification | MAYBE center-only, low priority | Preserve unaligned baseline/acquisition assumptions |
| Hazelnut | Tested center/undirected axis are not reliable semantic anchors | Center cosine .711013->.667944; axis worsens matched center .660303->.624708 | Broad gates can return OK for bright print fragments; weak axes common | No new canonical M2 quality result | NO-GO for tested strategies | Reject these anchors; any later model needs a different hypothesis |
| Cable | Tested whole-cross-section rigid pose lacks stable axis | Tiny centering benefit;97.2% FIT axes weak | Broken-ring/deformation changes anchor; geometric OK is insufficient | No canonical detector qualification | NO-GO for tested rigid approach | Keep as a separate representation problem |
| Grid | No bounded whole-object centroid/polarity | Filtering control only; no object registration | No invented pose or anomaly-side pose certification | Unaligned all-15 M2 evidence only | NONE for whole-object pose | Treat texture invariance separately |

## 9. Exact Metal Nut production status

Production classes under `src/main/java/.../canonical/` include geometry, rigid
transform, affine sampler, preprocessing/acquisition, canonical M2 input/training,
bundle/store and detector/result ownership. The contract is explicitly
`metal-nut-otsu175-h4-scale1-v1/canonical700-to224-bilinear-imagenet-v1`.
It accepts source700x700, default-sRGB logical pixels, geometry175 with pinned
reduction/Otsu/connectivity/fill/H4, scale1 and center349.5, four-tap B with logical
replicated edges and direct float NCHW. It does not silently adapt arbitrary source
sizes. [E09], [E11], [CanonicalM2Contract](../src/main/java/io/github/antctrlwin/anomalib4j/canonical/CanonicalM2Contract.java)

`anomalib4j-metal-nut-canonical-m2/v1` binds the exact full preprocessing contract,
encoder SHA and numerical payload SHA. Readers reject unknown/missing/extra or
duplicate fields, identity mismatch, invalid state, corruption, truncation and
trailing bytes. The legacy loader remains separate. Current qualified bundle
identity is `8771287ad2777083c8a14f3e862142b9f36f4ef56dcbdec46cf156f30ebf9bb0`.
Stored inference arrays are local means18,816, effective stds18,816, alpha196 and
effective beta196, float64: **304,192-byte numerical payload**, **306,066-byte
canonical bundle**. Encoder bytes are separate. No poses/training images/VSA state
are stored in the model. These sizes are not RSS. [E11]

Geometry failure throws before CNN invocation. There is no stale-buffer scoring,
unaligned fallback or silent image removal. The detector/workspace is sequential;
concurrent use needs separate instances or external serialization including close.
`detectScore` performs source -> geometry/B -> CNN -> strict M2 -> native max.
`inferWithMap` performs inference and returns owned native Z, transform, dimensions
and identity. It allows future localization without another CNN run; calling both
APIs separately does run inference twice. Production original-frame localization
is still deferred. The quality harness's two-stage interpolation/backprojection
remains qualification code, separate from rendering and public detector inference.
[E11], [CanonicalM2Detector](../src/main/java/io/github/antctrlwin/anomalib4j/canonical/CanonicalM2Detector.java)

The final optimization changes only acquisition for validated built-in
TYPE_3BYTE_BGR rasters. Layout/stride/translation/offset/color checks gate direct
packing into the existing buffer; unsupported representations use generic getRGB.
423 inputs preserve exact pixels through scores, and 115 TEST metrics reproduce
the same bundle without refitting. CPU/Azure are available, but all 400 tagged
executed-node events across 16 untimed profiles use CPUExecutionProvider. This
proves the recorded ONNX execution path, not one native CPU worker. [E13], [S09]

Latest complete decision p50/mean/p95 is4.423680/4.363740/5.816320 ms; matched
generic comparator10.272768/10.173606/12.992512 ms. Earlier prototype, integration
and geometry-profile times retain their own scopes and host conditions. Generic
unaligned M2's Bottle scorer17.696 us, CNN extraction624.640 us, complete detection
2.760704 ms and complete localization7.356416 ms are separate historical matched
measurements, not component forecasts for Metal Nut. [E03], [E11], [E12], [E13]

The latest complete canonical decision allocates306,178.818 B/op under the GC
profiler; the reusable packed source has1.96 MB capacity. Neither is process
memory. No measured RSS or camera/decode/queue/streaming-throughput result exists
for this checkpoint. No latency inverse is needed to establish its bounded CPU
advantage. Current production capability is a callable qualified category path,
not validation of an industrial camera installation.

## 10. Screw: architectural support with a meaningful failure

Screw is scientifically useful because its directed elongated geometry is unlike
Metal Nut's fourfold outline. The initial generality audit exposed scale1 clipping
under mass-centroid centering. FIT-only extent analysis solved placement with one
global target `(395.50859765961616,349.2832413193618)`, preserving scale1 and all
trusted FIT/CAL supports. It did not recover the four incomplete source images:
FIT good096/158/196 and CAL good179 remain rejected and accounted for. TEST later
has160/160 usable cases and no margin violations. [E14], [E15], [E16]

Matched I/C controls show that the small compound filtering/population change
cannot explain the large net aggregate gains. Registration improves strongly,
and pose plus its learned/remapping consequences explains nearly all net gain.
This supports the architecture in a second family on this split. It does not
prove every surface feature is aligned, every defect benefits or Screw is a second
qualified production strategy. Its source/pose/bundle/orchestration classes remain
under `src/test/java/.../evaluation/`. [E16], [E17]

The head counterexample refines the model of failure. Head-band max increases in
17/24 scratch images, median2.654039->4.127453 Z; GT-native peak median increases
2.190458->3.260223. Neck/thread defect peaks grow much more. Normal global-max
median rises4.508537->5.938939, with maximum10.308118->53.480865;33/41 canonical
normal maxima lie outside the fixed diagnostic anatomical bands. "Other" does
not mean verified physical background. The head image loss is a net15/984 lost
positive/normal rankings;77.13% of the Pixel rank loss is against good-image
backgrounds. Both foreground and background changed, so this accounting is not
a background-only causal intervention or a new scorer. [E18]

Local std roughly halves in head, neck and thread; head calibration often
amplifies rather than suppresses localized signal. A normal extreme already has
raw21.672602 before Z, so beta alone does not explain it. Four fixed resampling
representatives retain contrast/width but show some gradient changes; subtle
sampling/pose causes remain unresolved. Independent rank versus Java ROC
accumulation differs by up to6.04e-12; retained Java metrics remain authoritative.
These findings support **competing nuisance maxima**, not uniform scratch erasure.
[E18]

The later frozen-pose, no-refit CAL-only control supplies **partial photometric
evidence**, not new TEST quality. All378 factor1 evaluations match normalized
units/raw/Z/max exactly;1512 perturbed evaluations cover brightness/contrast/gamma.
At brightness1.20, I/C max-Z medians are39.132175/39.356549 and paired amplification
median/p95 is2.507084/11.329074 Z. Contrast1.20 has extra canonical upper tails;
gamma stays much smaller. Strong brightening sensitivity is shared, and median
81.4165% of native channels clip at brightness1.20. Canonical sensitivity is not
concentrated in its lowest-std cells; diffuse multi-cell and multi-channel growth
occurs. Correlation, scaling and winner-switch accounting do not establish causes.
[E19], [S10], [S11]

The complete interpretation is therefore: **Screw supports semantic registration
before strict local modeling in a second geometric family, but remains MIXED and
experimental because nuisance discrimination and scratch_head quality are unresolved.**

## 11. Next scientific bottleneck: a hypothesis about residual covariance

The most useful remaining model question is whether diagonal M2 becomes limiting
after registration makes same-position normal distributions tighter. M2 scores
channels independently; several correlated changes along a normal latent direction
may accumulate squared standardized residual despite being jointly normal.
Registration improves positional coherence, while Screw nuisance tails survive
without a simple lowest-std explanation and photometric perturbations activate
many channels/cells. That combination motivates examining residual channel
dependence. [E10], [E17], [E18], [E19]

**This is UNTESTED.** No retained experiment estimates a covariance-aware detector's
benefit, identifies the actual residual covariance modes as the cause of tails,
or shows that such modes distinguish nuisance from defects. Unit-normalization
already constrains descriptors, but does not prove a particular useful covariance
model. Limited FIT samples, residual registration, unseen appearance, saturation
and subgroup overlap could remain the limitation even with richer statistics.
Suppressing a normal mode might also suppress scratches.

The latest photometric report/D31 instead recommends a CAL-only clipping-isolation
factorial. That recommendation is preserved and **has not been executed**. This
synthesis prioritizes the user's requested architectural stop/continue decision;
it does not declare the clipping question resolved or silently rewrite D31.
Covariance is the sole proposed additional architectural cycle, if the project
chooses another cycle at all. Neither recommendation authorizes execution here.

## 12. Candidate statistical models: design only

All candidates would keep representation, strict positional identity, disjoint
FIT/CAL roles, native-max semantics and evaluator fixed under a separately frozen
protocol. Changed raw statistics require their own held-out score calibration;
reusing old alpha/beta would confound the comparison. No rank, shrinkage strength,
new model name or implementation is selected here.

| Candidate | Concept | Potential value | Principal risks / cost |
|---|---|---|---|
| Current diagonal M2 | `raw=(1/96)*sum((u-mu)^2/s^2)` | Small, stable, measured Java baseline | Ignores cross-channel dependence; one distribution per position |
| Shrinkage full covariance | `Sigma_lambda=(1-lambda)*Sigma+lambda*D`, where D is a declared positive diagonal target | Could reduce penalties along correlated normal directions while retaining unusual directions |96D estimates at196 cells; limited FIT (Metal176/Screw253); conditioning, near-dependent unit descriptors, stable factorization/solves; more state and quadratic scoring |
| Low-rank plus diagonal | `Sigma approximately D+U*Lambda*U^T` | Explicit correlated normal modes; possible Woodbury scoring and modest storage/runtime if rank stays small | Rank/regularization/model-selection uncertainty; unstable modes, defect suppression and per-position complexity |

For full covariance, having more FIT images than channels does not guarantee
well-conditioned per-position sample covariance or reliable tail estimation.
Shrinkage toward a positive diagonal can regularize the system, but target,
strength, floors and solve method would need a frozen definition. Avoid treating
an arbitrary explicit matrix inverse as a numerical solution.

For the low-rank form, a positive diagonal and suitable nonnegative mode variances
can represent correlated departures. Woodbury can express the inverse as a
diagonal solve plus a small mode-space correction. Runtime/storage advantages
are conditional on small effective rank and implementation; no latency, memory
or quality improvement has been measured. An estimated normal mode may also be
a defect direction. Neither model is called M3: that historical identifier is
already used by projected VSA dispersion. [E21]

## 13. Future research horizons

**Near term, only if another cycle is chosen:** a bounded offline comparison of
diagonal M2, shrinkage covariance and low-rank-plus-diagonal modeling, reusing
retained descriptors where valid. Metal Nut is the qualified canonical positive
case; Screw is the canonical-but-mixed stress case. Freeze FIT-only statistical
choices and held-out calibration before quality comparison. Existing TEST is
already observed and must remain final/descriptive evidence, not a tuning source.
Primary questions are normal-tail reduction, defect preservation, retention of
Metal Nut quality, and scratch_head improvement without neck/thread sacrifice.
CPU scoring/state cost matters before any Java promotion. This is a research
design direction, not authorization or a recommendation to implement production
covariance now.

**Medium term:** categories without meaningful rigid whole-object pose may need
texture-relative models, local prototypes, mixtures, coordinate-relative
representations, multiple templates or multiscale observations. Current evidence
does not choose among them. Rejecting tested Hazelnut/Cable anchors or Grid pose
does not prove which alternative will work. Avoid a generic strategy framework
until multiple genuinely qualified implementations need one. [E14]

**Long term:** heterogeneous image/audio/time-series/text representations,
binding/bundling and shared VSA fusion remain a separate research direction.
No current Anomalib4j detector experiment establishes its value. This preserves
the original conceptual interest without attributing current M2 accuracy to VSA.

## 14. Minimal defensible v1 and publication preparation

If scientific work stops now, v1 should contain the frozen CNN, generic strict
Java M2, the retained all-15 scientific benchmark with its Python/Java boundaries,
and the qualified Metal Nut canonical extension. Include evaluation/robustness
tooling, exact contracts, failure/coverage policy, measured CPU scopes and explicit
limitations. Screw belongs in experimental research evidence, with the head
regression visible. All-15 M2 quality evidence is the Python scientific ablation;
Java parity is the retained subset, not an invented full Java quality rerun.
[E01], [E02], [E03], [E11], [E13], [E17]

M0 should remain a preserved **legacy/reference implementation**, with exact
VSA/Adjoint equivalence and historical measurements. It is not the selected/default
architectural recommendation. Nothing in this conclusion requires deleting M0,
its invariant tests, historical M1/M2/M3 names or evidence. C1/V1/N1/G are useful
controls and negative/tradeoff results, not production candidates by implication.

The project is ready to prepare a publication-quality repository, not to claim
publication completeness or deployment qualification today. Ordered work for a
separate authorized documentation/release task:

1. Reconcile PROJECT_STATE, DECISION_LOG supersession and EXPERIMENT_INDEX against
   this synthesis; preserve dated historical decisions/recommendations.
2. Rebuild the README narrative around current M2, retained M0 and Metal-only
   canonical capability; show concise labeled quality/performance tables and
   the three-layer architecture diagram. Remove generation/scope ambiguity.
3. Explicitly document `src/main` production versus `src/test` qualification and
   `src/jmh` measurement packages, supported input/failure/concurrency contracts,
   and localization still deferred in production.
4. Document dataset layout/licensing/access prerequisites and distinguish runnable
   synthetic tests from external-data/evidence-dependent reproduction commands.
5. Define an ignored/generated evidence policy: publish or archive immutable
   manifests, protocols, source snapshots, environment, summaries and hashes;
   provide a restoration map for large arrays/maps/bundles. A clone containing
   reports alone is not a complete reproduction package. Avoid `mvn clean` on
   an evidence-bearing workspace.
6. Bind each published table to its actual measured source identity, fixture/split
   identity and output artifact. Historical frozen source is not byte-identical
   to current HEAD; preserve measured-source copies and documented postprocessing
   exceptions rather than refresh old hashes.
7. Expose exact SPATIAL_14 ONNX SHA-256
   `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`.
   Keep **checkpoint-to-export provenance unresolved** until independently
   established. Byte identity, graph geometry and implemented preprocessing are
   known; they do not prove correspondence to the nominal pretrained checkpoint.
8. Review the bounded claims/limitations, then release/tag a reproducible source
   snapshot with the evidence restoration instructions. No release/tag is made
   by this synthesis task.

Technically the repository demonstrates Java21/ONNX Runtime inference, explicit
statistical contracts, cross-language numerical parity, frozen experiment design,
JMH with CPU-node verification, geometric sampling/registration, fail-closed
ownership and persistence, source/artifact hashing, and analysis of negative
results. These are supported engineering/scientific capabilities. They do not
imply a universal detector or deployed real-time system. [E03], [E09], [E11], [E13], [E17], [E18], [E19]

## 15. Stop/continue decision

```text
Is the goal a defensible finite v1 / research publication checkpoint?
  YES -> STOP SCIENTIFIC EXPANSION AND PREPARE v1 PUBLICATION NOW.
         Generic M2 + all-15 evidence + Metal extension + explicit limitations.

Is the goal specifically to test the remaining local-statistics hypothesis,
with a fixed scope and willingness to accept a negative result?
  YES -> Consider ONE separately authorized offline covariance cycle.
         Metal positive control + Screw mixed stress case; no framework expansion.
         Does nuisance discrimination improve while defect/Metal quality and
         practical CPU/state costs remain acceptable?
           YES -> Record the bounded result; only then decide on Java qualification.
           NO  -> Close immediately; publish diagonal-M2 v1 with the negative result.
  NO  -> Publish the current bounded result; do not open another research branch.
```

**Recommendation: publish the bounded v1 now after repository/publication
reconciliation.** The architecture is interpretable, the all-15 baseline exists,
the qualified category extension has parity/performance evidence, and the mixed
result has been investigated rather than concealed. Unknown covariance benefit
does not invalidate those findings. Further generalization would dilute this
finite checkpoint without a currently qualified second category.

If the owner chooses one more cycle, covariance-aware normal modeling is the
only additional architectural cycle prioritized here. It earns continuation only
by reducing relevant normal nuisance while retaining actual defect sensitivity,
Metal Nut quality, and the CPU/state advantage. A prettier coherence plot or a
lower normal score alone is insufficient. Close on no meaningful benefit, head
improvement bought by lost neck/thread/Metal sensitivity, unstable sample-limited
fits, or unacceptable runtime/state cost. Do not invent numeric acceptance gates
after seeing results. No cycle, corrective intervention or publication edit is
implemented by this recommendation.

## 16. Evidence ledger and reproducibility limits

The following are the **21 inspected evidence reports/reviews**. Source documents
remain the authority for full precision, protocols, per-category/per-defect
detail, tests and historical recommendations. Links to `target/` refer to retained
local/ignored evidence, not files guaranteed in a clone.

| ID | Exact report / review | Role in this synthesis |
|---|---|---|
| E01 | [M0 all-15 RESULT](../target/comparison/m0-all15-baseline-20261016/RESULT.md) | Completed baseline category quality |
| E02 | [C1/V1/M2 RESULT](../target/ablation/local-models-20261017/RESULT.md) | All-15 local-model quality |
| E03 | [Java M2 result](benchmark/JAVA_M2_RESULT_20261018.md) | Architecture, parity, stored state and matched CPU scopes |
| E04 | [Controlled robustness](benchmark/M2_ROBUSTNESS_RESULT_20261018.md) | All-15 normal geometry/photometry sensitivity |
| E05 | [Spatial correspondence](benchmark/M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md) | P/N1/G tradeoff; historical N1 recommendation |
| E06 | [Oracle canonicalization](benchmark/M2_ORACLE_CANONICALIZATION_RESULT_20261019.md) | Bounded inverse-transform diagnostic |
| E07 | [Metal Nut pilot](benchmark/M2_METAL_NUT_CANONICALIZATION_RESULT_20261019.md) | Positive science; historical Python timing NO-GO |
| E08 | [Java architecture/performance review](experiments/JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md) | Provisional design, retired gate and staged qualification |
| E09 | [Java Phase 1](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md) | Geometry/bridge parity and sampler infrastructure |
| E10 | [Java Phase 2](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md) | Independent sampler qualification and B selection |
| E11 | [Canonical M2 integration](experiments/JAVA_CANONICAL_M2_INTEGRATION.md) | Current Metal bundle/API and integration parity |
| E12 | [Geometry profile](experiments/JAVA_CANONICAL_GEOMETRY_PROFILE.md) | Measurement-only acquisition bottleneck |
| E13 | [BGR fast path](experiments/JAVA_CANONICAL_BGR_FAST_PATH.md) | Current matched latency, exact parity and CPU evidence |
| E14 | [Generality audit](experiments/CANONICALIZATION_GENERALITY_AUDIT.md) | Six-category geometry/normal-feature screening |
| E15 | [Screw framing](experiments/SCREW_CANONICAL_FRAMING_FEASIBILITY.md) | FIT-frozen scale1 support/placement and coverage |
| E16 | [Screw qualification](experiments/SCREW_CANONICAL_M2_QUALIFICATION.md) | Whole-contract quality and mixed decision |
| E17 | [Screw matched identity](experiments/SCREW_B_IDENTITY_M2_CONTROL.md) | Matched pose effect and subgroup counterexample |
| E18 | [Head-response analysis](experiments/SCREW_HEAD_RESPONSE_ANALYSIS.md) | Signal, nuisance and mechanism limitations |
| E19 | [CAL photometric control](experiments/SCREW_CAL_PHOTOMETRIC_NUISANCE.md) | Partial nuisance evidence; unexecuted clipping follow-up |
| E20 | [Positional/global variance](diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md) | Corrected normalized-input historical diagnostic |
| E21 | [Historical local-memory ablation](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md) | M0/M1/M2/M3 definitions, algebra and bounded results |

The **11 directly inspected retained artifacts** below cross-check selected
tables/validation statements. Array-wide checks, provider traces and source hashes
not revalidated in this task are attributed to their reports, not newly certified.

| ID | Exact retained artifact | Inspected content |
|---|---|---|
| S01 | [M0 macro CSV](../target/comparison/m0-all15-baseline-20261016/macro-summary.csv) |15 verified category macro values |
| S02 | [Local-model macro CSV](../target/ablation/local-models-20261017/macro-summary.csv) | Exact M0/C1/V1/M2 macros |
| S03 | [Correspondence quality macro CSV](../target/ablation/m2-spatial-correspondence-20261018/quality-macro.csv) | P/N1/G all/texture/object macros |
| S04 | [Metal B coherence](../target/experiments/java-canonicalization-phase2-20261019/B/coherence.json) | Std/cosine/calibration summaries |
| S05 | [Metal B rotation summary](../target/experiments/java-canonicalization-phase2-20261019/B/rotation-summary.json) | Per-level and aggregate paired diagnostics |
| S06 | [Screw three-way primary](../target/experiments/screw-b-identity-control-20261020-validated/three-way-primary.json) | H/I/C exact quality and deltas |
| S07 | [Screw three-way defects](../target/experiments/screw-b-identity-control-20261020-validated/three-way-defects.json) | Head and successful-group metrics |
| S08 | [Final BGR JMH JSON](../target/experiments/java-canonical-bgr-fast-20261020/jmh.json) | Eight primary scopes, units, measured mean/percentiles |
| S09 | [BGR parity completion](../target/experiments/java-canonical-bgr-fast-20261020/parity-complete.json) |423 cases,115 TEST, exact parity, retained identity |
| S10 | [Photometric completion](../target/experiments/screw-cal-photometric-nuisance-20261020/completion.json) | CAL scope/counts, outcomes, reference caveat |
| S11 | [Photometric independent validation](../target/experiments/screw-cal-photometric-nuisance-20261020/independent-validation.json) | Saved raw/Z reconstruction errors and exact native max |

Reproducibility caveats that remain material:

- The exact checkpoint-to-export chain remains unresolved despite the fixed
  ONNX byte hash and graph/preprocessing contracts. The nominal MobileNetV4 tag
  is not an independently proven checkpoint match.
- `final-comparison-20261003` and later frozen runs record their measured sources,
  not current HEAD. The additive prepared path and subsequent production changes
  intentionally change source identity. Numerical parity is evidence, not a new
  hash certificate for an old source snapshot.
- Phase 2 preserves two post-measurement Python parser/test changes and measured
  versions. Integration preserves a pre-measurement setup PID collision, only
  the missing preprocessing fork's replacement, and an untimed reporting fix.
  These are disclosed chronology, not hidden scientific/timed-body changes.
  Final BGR source/input stability is reported separately. [E10], [E11], [E13]
- The unsuffixed generality root is the grayscale-conversion diagnostic; final
  screening uses `canonical-generality-audit-20261020-validated`. The unsuffixed
  Screw identity root is a pre-fit JSON representation failure; final scientific
  control uses `screw-b-identity-control-20261020-validated`. [E14], [E17]
- Screw253/63 and historical256/64 differ. Only matched identity/canonical isolates
  the pose intervention at that population/operator level; there is no independent
  acquisition validation. FIT/CAL source failures are not erased by TEST success.
- Some canonical historical tensor hashes are absent. Photometric factor1 has
  exact retained normalized-unit/raw/Z/max parity and an unchanged executable B
  tensor reference; it does not acquire nonexistent historical tensor provenance.
  CAL was also used to estimate Z, so this diagnostic is not independent normal
  validation. [E18], [E19], [S10]
- Metal qualification uses a known split and fixed input contract. Fourfold
  geometry is not fourfold photometric symmetry. Generality screening covers six
  categories; local glare/reflections and application throughput remain untested.
- Dataset/evidence restoration and source/environment binding remain necessary
  before a fresh session can reproduce historical outputs. Report links and
  lightweight document checks alone do not establish fresh numerical reproduction.

[E01]: ../target/comparison/m0-all15-baseline-20261016/RESULT.md
[E02]: ../target/ablation/local-models-20261017/RESULT.md
[E03]: benchmark/JAVA_M2_RESULT_20261018.md
[E04]: benchmark/M2_ROBUSTNESS_RESULT_20261018.md
[E05]: benchmark/M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md
[E06]: benchmark/M2_ORACLE_CANONICALIZATION_RESULT_20261019.md
[E07]: benchmark/M2_METAL_NUT_CANONICALIZATION_RESULT_20261019.md
[E08]: experiments/JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md
[E09]: experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md
[E10]: experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md
[E11]: experiments/JAVA_CANONICAL_M2_INTEGRATION.md
[E12]: experiments/JAVA_CANONICAL_GEOMETRY_PROFILE.md
[E13]: experiments/JAVA_CANONICAL_BGR_FAST_PATH.md
[E14]: experiments/CANONICALIZATION_GENERALITY_AUDIT.md
[E15]: experiments/SCREW_CANONICAL_FRAMING_FEASIBILITY.md
[E16]: experiments/SCREW_CANONICAL_M2_QUALIFICATION.md
[E17]: experiments/SCREW_B_IDENTITY_M2_CONTROL.md
[E18]: experiments/SCREW_HEAD_RESPONSE_ANALYSIS.md
[E19]: experiments/SCREW_CAL_PHOTOMETRIC_NUISANCE.md
[E20]: diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md
[E21]: diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md
[S01]: ../target/comparison/m0-all15-baseline-20261016/macro-summary.csv
[S02]: ../target/ablation/local-models-20261017/macro-summary.csv
[S03]: ../target/ablation/m2-spatial-correspondence-20261018/quality-macro.csv
[S04]: ../target/experiments/java-canonicalization-phase2-20261019/B/coherence.json
[S05]: ../target/experiments/java-canonicalization-phase2-20261019/B/rotation-summary.json
[S06]: ../target/experiments/screw-b-identity-control-20261020-validated/three-way-primary.json
[S07]: ../target/experiments/screw-b-identity-control-20261020-validated/three-way-defects.json
[S08]: ../target/experiments/java-canonical-bgr-fast-20261020/jmh.json
[S09]: ../target/experiments/java-canonical-bgr-fast-20261020/parity-complete.json
[S10]: ../target/experiments/screw-cal-photometric-nuisance-20261020/completion.json
[S11]: ../target/experiments/screw-cal-photometric-nuisance-20261020/independent-validation.json

## 17. Final status matrix

| Area | Status | Evidence level | Production status | Next action |
|---|---|---|---|---|
| M0 | Preserved VSA/Adjoint baseline; superseded selection | ESTABLISHED equivalence and retained baseline quality [E01], [E21] | Java legacy/reference path | Retain invariant and historical evidence; de-emphasize as default |
| C1 | Direct local cosine control | ESTABLISHED measured all-15 control [E02] | Scientific control | Retain comparison |
| V1 | Exact projected-cosine control; no macro advantage | REJECTED tested quality-advantage claim [E02] | Scientific control | Retain negative result; no expansion-based accuracy claim |
| M2 | Selected strict diagonal local model | ESTABLISHED retained quality/parity; bounded robustness [E02], [E03], [E04] | Generic Java path; unaligned default | Publish bounded baseline and limitations |
| M2-P | Selected strict correspondence | SUPPORTED useful spatial identity [E05], D20 | Current M2 correspondence | Preserve strict reference semantics |
| N1/G | Stability/quality tradeoff; not selected | MIXED N1; G quality losses [E05] | Experimental only | Preserve results; no promotion |
| Metal Nut canonicalization | H4/scale1 qualified on frozen split | ESTABLISHED qualification; SUPPORTED broader hypothesis [E10], [E11] | Only integrated canonical category | Publish fixed contract/known limits |
| Sampler B | Qualified direct fused bilinear winner | ESTABLISHED within frozen gates [E10] | Used by Metal canonical path | Retain explicit preprocessing identity |
| BGR fast path | Exact guarded acquisition optimization | ESTABLISHED parity and measured CPU cost [E13], [S08], [S09] | Production with generic fallback | Publish measured scope, no camera FPS claim |
| Screw canonicalization | Strong matched aggregate benefit, head regression | MIXED [E16], [E17] | Test/qualification only | Keep experimental; optional covariance stress case |
| scratch_head diagnosis | Local signal usually stronger; nuisance competition | SUPPORTED competition; upstream cause unresolved [E18] | Diagnostic only | Preserve counterexample; no regional scoring fix inferred |
| Photometric nuisance diagnosis | Shared brightening sensitivity plus partial canonical tails | MIXED / PHOTOMETRIC_SENSITIVITY_PARTIAL [E19] | CAL-only diagnostic | Preserve clipping/appearance uncertainty; no corrective promotion |
| Generalized pose framework | Reusable conceptual separation; universal strategy unqualified | REJECTED current readiness; six-category evidence [E14], [E17] | No production registry | Defer generalization |
| Covariance-aware model | Candidate residual-normal hypothesis | UNTESTED | No implementation/qualification | Optional one bounded offline cycle, or close v1 now |
| VSA multimodal fusion | Separate future representation direction | UNTESTED / FUTURE HYPOTHESIS | Outside current detector | Defer; require its own task and evidence |
