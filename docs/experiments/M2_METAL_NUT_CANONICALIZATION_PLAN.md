# Metal Nut canonicalized-training pilot

2026-10-19. Explicit new user scope supersedes the previous oracle experiment's
recommended next diagnostic. One category only. Preserve production M2/M0 and
all retained evidence; no N1/G, all-category run, JMH or commit.

## Frozen method before calibration/test metrics

FIT-only inspection establishes four-fold outline symmetry: all 176 FIT normals
have dominant harmonic4 among k=2..12. On the first 16 frozen FIT images, four
source rotations give median/p95/max modulo90 orientation error
0.0323/0.0877/0.1311 degrees. No test images, labels or masks inform this rule.

Estimate geometry from 175x175 BOX-reduced luminance: deterministic Otsu, largest
8-connected foreground component, fill holes, centroid, mean exp(4i*angle).
Orientation = arg(mean)/4 in [-45,45) degrees, clockwise coordinates. Canonical
phase0 modulo90; no intensity-based branch selection among equivalent lobes.
Changes of 90 degrees are physically equivalent outline orientations, but surface
appearance/illumination need not be symmetric. Record branch crossings explicitly.
Failure: filled area outside [.2,.8], component touching frame, or harmonic4<.03.
No silent fallback/drop; retain every status and stop if geometry fails.

Apply negative estimated angle and center translation to (349.5,349.5), scale1.
FIT area dispersion is small (std0.00289); record radius/area but do not rescale
and potentially normalize a shape anomaly away. Output remains700x700. One
Pillow bilinear RGB warp at source resolution, with sufficient edge-replicated
padding for all inverse sample coordinates; uint8 output. Explicit pixel-center
to Pillow-coordinate conversion. Fixed canvas; no content-adaptive crop.
Then unchanged Java bicubic224/ImageNet/encoder/normalization/M2.

Freeze 176 FIT /44 CAL /115 TEST (22 normal,93 anomaly) identities. Canonicalize
all roles identically; fit local statistics on FIT only and Z on CAL only with
the existing Java M2Trainer/M2Runner. No mask/label API in the canonicalizer.

For pixel metrics, upsample canonical Z with existing LocalizationMaps, then
bilinearly sample through the source-to-canonical matrix back onto original700
coordinates (replicated map boundary). Evaluate original masks with unchanged
ComparisonEvaluator/LocalizationMetrics. Image score remains canonical native max.
Backprojection is required coordinate transport, not a metric-definition change.

## Diagnostics and acceptance

Retain poses/transforms for every image and perturbed normal, manifests, models,
features and scores. FIT sample-std quantiles/mean, low-std bins <=1e-6/1e-5/1e-4/
1e-3, per-position mean variance, mean pairwise descriptor cosine, directional
dispersion1-||mean_unit||^2. Compare CAL raw sample-std per position. No test fit.

Controlled rotations -10,-5,+5,+10 at source700, then estimate geometry blindly
and canonicalize. Compare B with its unperturbed canonicalized score. Also measure
unchanged A on exactly the same perturbed source images: old224-warp robustness
is context only, not directly comparable. Keep all22 normals and all88 pairs.
Orientation reliability gate: modulo90 residual p95<=1 degree and max<=2 on
normal rotations; record modulo branch crossings, do not use known angle to correct.

GO requires quality preservation (no metric loss >.01 absolute), meaningful
coherence (at least10% reduction in median FIT local std), at least50% reduction
of average absolute median paired rotation score change vs matched A, reliable
geometry, and approximate total canonicalization median<=2ms on this host.
These are pilot decision thresholds, frozen before test metrics, not significance
claims. Otherwise NO-GO. No tuning or alternate method after viewing test quality.

Time estimation, warp and total separately on eight FIT inputs in three processes,
warmup and interleaved batches. Total is measured, not summed. No I/O or CNN.
Artifacts: target/experiments/m2-metal-nut-canonicalization-20261019.
If NO-GO, recommend closing this phase with M2-P, aligned-acquisition assumptions,
publication of validated results and further invariance work deferred.
