# Canonicalization generality screening protocol

Frozen before new FIT geometry/coherence extraction or TEST inspection,2026-10-20.
Entry HEAD `3af68f2`, clean worktree. Scope: Metal Nut, Bottle, Screw, Hazelnut,
Cable and Grid only. Experimental test-source tooling; no production changes,
M2 fitting/calibration/scoring, new anomaly metrics, timing or commits.

## Boundaries and controls

Reuse the completed M0 campaign's exact FIT/CAL/TEST manifests and ordering.
All FIT normals are used for coherence. CAL and TEST receive geometry only.
The estimator receives pixels/category only, never filename/labels/masks.
Labels are used afterward to group blind TEST diagnostics. No masks are read.
Freeze estimator source/config and viable candidate list before CNN extraction
or TEST geometry; record hashes and reject drift. No rescue/tuning after TEST.

Sampler B is frozen at700->224. Metal Nut stays native700. Other square source
images are converted to an **experimental700 view** with Java Graphics2D
bicubic/quality hints, after the legacy logical-sRGB color conversion policy:
nonstandard/gray/alpha images first pass through getRGB into opaque INT_RGB;
ordinary opaque sRGB INT_RGB/BGR sources retain their pixel representation.
This is not production support for
other sizes. Preserve three distinct comparisons:

- LEGACY: unchanged native image -> current bicubic encoder preprocessing.
- B_IDENTITY: same700 view -> unchanged B sampler with identity rigid transform.
- candidate: same700 view -> frozen pose -> unchanged B sampler.

Candidate minus B_IDENTITY isolates alignment from the extra resize/filter
boundary. Candidate minus LEGACY alone cannot establish registration benefit.
Reject a smoothing/collapse interpretation using spatial feature contrast,
input tensor gradient energy, per-position variance and displacement diagnostics.
No scale estimation, crop, padding, illumination normalization or deformation.

## Small candidate set and deterministic definitions

Metal Nut: unchanged production centroid/H4 and failure rules, modulo90.
Validate FIT units against retained Phase2 B; do not rerun its quality campaign.

Other objects: RGB700 -> the exact existing175 luminance/BOX reduction (called
through the production estimator's diagnostic gray snapshot). Experimental
Otsu first maximum; dark foreground for Bottle/Screw, bright for Hazelnut/Cable;
first largest8-connected component,4-connected exterior hole filling. Nonempty
area fraction[.005,.95] and no source-border contact are broad segmentation
sanity checks, not universal GO thresholds. Failures remain explicit.

Centroid from filled pixels, source `(coordinate+.5)*4-.5`. Second central
moments give major axis `.5*atan2(2*mxy,mxx-myy)`, deterministic[-90,90] branch.
Confidence `(lambda_max-lambda_min)/(lambda_max+lambda_min)`; isotropic/zero
moments have no observable axis. Exact ties choose angle0 but remain weak.
For directed axis, compare foreground mass in the outer25% of projected extent
at each end: signed `(mass_plus-mass_minus)/(mass_plus+mass_minus)`.
Positive direction points toward the heavier end; exact ties are weak, never
silently broken as a trusted head. Axis-angle rotation targets canonical +x.

| Category | Candidates | Weak-axis diagnostic | Weak-polarity diagnostic |
|---|---|---:|---:|
| metal_nut | H4 | existing H4 strength<.03 | not applicable |
| bottle | CENTER_ONLY; AXIS_WITH_POLARITY viability diagnostic | anisotropy<.10 | absolute mass contrast<.10 |
| screw | AXIS_WITH_POLARITY | anisotropy<.50 | absolute mass contrast<.10 |
| hazelnut | CENTER_ONLY; AXIS modulo180 | anisotropy<.10 | not resolved |
| cable | CENTER_ONLY; AXIS viability diagnostic | anisotropy<.10 | not resolved |
| grid | NONE, no fabricated silhouette | not applicable | not applicable |

Initial normal FIT image inspection shows Bottle is end-on/circular, not a side
view with an observable neck-bottom axis; Cable is a cross-section. Thus Bottle
centering is included and an axis is allowed to fail. Numerical weak flags are
diagnostics, not a universal scientific acceptance cutoff. CENTER_ONLY may use a
valid centroid despite weak orientation; AXIS never consumes a weak angle.
An AXIS comparison uses only its explicit successful subset and reports matched
LEGACY/B_IDENTITY statistics on exactly that subset; no silent image omission.

## Geometry first and manageable perturbations

Run FIT/CAL geometry first. On the first12 FIT images/category (five objects,
not Grid), generate native-source translations +/-2% width along x/y and
clockwise rotations -10,-5,+5,+10 degrees around the source center. Bilinear
inverse mapping, replicated edges and nearest-byte RGB rounding; then apply the
same700 view conversion and blind estimator. Retain transform parameters and
pixel hashes. Known transform is used only afterward for recovery error.
Measure centroid error in700-view pixels, angular residual modulo90/180/360 as
appropriate,180-degree polarity reversals for directed axes, weak/failure rates
and source/output clipping. CENTER_ONLY intentionally has no rotation recovery
claim; report its centroid under rotation without inventing an angle.

Before feature extraction, freeze the viable candidates from FIT/CAL and
perturbation evidence. No TEST normal/anomaly pixels influence this choice.

## FIT feature statistics and anomaly diagnostics

Normalize frozen14x14x96 CNN descriptors with the unchanged norm guard.
Compute exact unordered-pair cosine mean and median pooled over196 positions,
plus distributions of per-position cosine means/medians. Distinguish the latter
from the true pooled median. Directional dispersion is `1-||mean(unit)||^2`.
Sample channel std uses ddof1, no floor for these diagnostics; publish
mean/median/p25/p75/p90/p95, counts<=1e-6/1e-5/1e-4/.001, all196 position rows.
Per-position variance is mean channel sample variance. No M2 state is fitted.

Spatial consistency: leave-one-image-out mean-direction templates, compare each
interior patch at its own cell and its3x3 neighborhood; report best-cell drift,
zero-drift fraction and cosine improvement obtainable by moving one cell.
This is a representation diagnostic, not a correspondence scorer. Also retain
mean adjacent-patch squared directional distance and mean tensor spatial squared
gradient; decreases alone do not prove preservation of defect information.

Finally run frozen geometry blindly on all TEST images; group afterward by
normal/anomaly/defect. Publish failures, weak confidence, axis/polarity branches,
centroid/area/angle outliers relative to FIT ranges and clipping. There is no
ground-truth anomaly pose: branch frequency is not a proven polarity error and
an outlier is not proof that a defect caused it. Inspect disclosed extremes
without changing the estimator.

## Decisions and retention

Use category-specific evidence for GO/MAYBE/NO-GO; no universal numerical gate.
GO requires meaningful/reliable pose, perturbation recovery, anomaly-side
reliability and material alignment-specific coherence benefit. MAYBE includes
weak angles, small gains or unresolved pose disturbances. NO-GO rejects the
audited family, not every possible detector. Lower std is never sufficient.
Retain exact source/manifests/config, input/descriptor hashes, poses, perturbation
records, full coherence/per-position data and final decisions under a new root.
Run focused estimator/statistics tests, full relevant Java regression and
`git diff --check`. Shared production source must remain byte-identical; Metal
Nut FIT descriptor parity anchors the audit without a new quality campaign.

## Pre-TEST tooling correction

The first geometry-only namespace `canonical-generality-audit-20261020` is retained
as diagnostic evidence. Its direct gray->700 Graphics2D route disagreed with
equivalent logical RGB->700 images: a failing-first test observed pixel0
-16382458 vs-16711423 on identical logical source pixels. Native Screw and Grid
PNGs are grayscale; perturbations are RGB, so that route confounded recovery.
The corrected policy above is representation-independent in regression tests.
Use the separate `canonical-generality-audit-20261020-validated` namespace for
screening. Original source/protocol snapshots and outputs remain intact. No
CNN statistics or TEST geometry were extracted before this correction. Geometry
rules, thresholds, candidate set, production source and all scientific tolerances
are unchanged; freeze this revision before repeating geometry or unblinding TEST.
