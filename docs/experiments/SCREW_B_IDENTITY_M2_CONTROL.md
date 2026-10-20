# Screw matched B_IDENTITY M2 control

Completed 2026-10-20. **POSE_EFFECT_SUPPORTED_BUT_MIXED**. This is a
qualification-only causal control; Screw is not promoted to production.
Metric values are category results, not all-category macro averages.

## 1. Causal question

Keep the new Screw source/filter/encoder/model/evaluation pipeline, replacing
only its spatial pose transform with exact identity. Compare fresh matched
B_IDENTITY M2 with the retained [canonical qualification](SCREW_CANONICAL_M2_QUALIFICATION.md).
The [control protocol](SCREW_B_IDENTITY_CONTROL_PROTOCOL.md) was frozen before
extraction, fitting and new TEST access. No parameter was selected on TEST.

## 2. Why the historical comparison was confounded

Historical M2 used whole-native-image preprocessing and FIT256/CAL64.
Canonical Screw introduced logical-sRGB conversion, a bicubic700 view, sampler B,
FIT253/CAL63, pose normalization and a localization composition. Historical to
canonical therefore did not isolate pose. Historical to identity measures a
compound pipeline/population effect. Identity to canonical is the primary matched
pose comparison, including fresh fitting/calibration in each representation.

## 3. Exact matched identity contract

Native1024x1024 -> logical default-sRGB -> unchanged Graphics2D bicubic/quality700
view (`GeneralityImages.view`) -> unchanged direct B sampler -> half-pixel
bilinear224/ImageNet normalization/float32 NCHW -> frozen MobileNetV4 SPATIAL_14
(14x14x96 HWC) -> unchanged strict M2. There is no crop, scale adjustment or
centroid placement. `RigidTransform(1,0,0,0)` means cos1/sin0/tx0/ty0, scale1.
No geometry estimator or saved pose values participate in identity sampling;
the subsequent map diagnostics also read no pose values.

Encoder SHA-256:
`48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`.
All476 usable FIT/CAL/TEST tensors equal the direct audited B identity call
exactly. All253 fresh FIT unit descriptors equal retained audit identity units
exactly, in the same order. Production preprocessing and M0/M2 mathematics are
unchanged. The reference equivalence validates reuse of B; it is not an
independent alternative image-resampling implementation.

## 4. Matched populations

| Role | Attempted | Usable | Matched exclusions | New failures |
|---|---:|---:|---|---:|
| FIT | 256 | 253 | good096, good158, good196 | 0 |
| CAL | 64 | 63 | good179 | 0 |
| TEST | 160 | 160 | None | 0 |

Exclusions are the canonical source-border failures, retained before CNN despite
identity needing no pose. No identities are restored or reordered. TEST contains
41 good/119 anomalous images. Historical M2 used256/64, not253/63.
Usable manifest SHA-256 values:

- FIT: `61d0bfa1fcb5765fa765c9168d4fe928b6b5eca005c3a9826d5a1e3c9e33d9dd`
- CAL: `d89e343b3d8000090f8198900049bc1896a0cfc2e054b15195cf2dff77f5f13f`
- TEST: `3cfc2f129079328aae6ac59d13d4fd967ebfecbdef06fd913c79d9e27ef478c2`

## 5. Model and calibration statistics

Fresh FIT local mean/sample std on normalized96-channel descriptors; norm below
1e-6 is rejected. Effective local std is max(sampleStd,1e-6).
`raw=mean_channels(((u-mean)/effectiveStd)^2)`. Fresh held-out CAL gives
`alpha=mean(raw)`, `beta=max(sampleStd(raw),1e-6)` and unclamped
`Z=(raw-alpha)/beta`. Image score is max over196 native cells. Canonical numerical
state is not reused; numerical payloads differ. Reload maximum error is0.

| Statistic | Count | Minimum | Median | P95 | Maximum | Mean |
|---|---:|---:|---:|---:|---:|---:|
| Local sample std | 18816 | .000896558010 | .015200533218 | .183275636091 | .379409162831 | .045556565239 |
| Alpha | 196 | .840081524248 | 1.006607797974 | 1.096169057448 | 1.166024241326 | 1.007786596090 |
| Beta sample std | 196 | .177171529113 | .486433543448 | .680046313301 | .858502307358 | .502283917103 |

Local and beta floor counts are both0; effective and sample std arrays coincide.
Full means/std/calibration arrays, FIT/CAL raw/Z/native-max scores and unit
features are retained. Independent NumPy2.2.5 reconstruction maximum errors:
mean6.66e-16, sample std2.50e-16, CAL raw3.55e-15, alpha1.11e-15,
beta4.44e-16. These are validation errors, not relaxed equivalence tolerances.

## 6. Identity bundle

Qualification-only format `anomalib4j-screw-b-identity-m2-qualification/v1`.
Preprocessing ID:
`screw-logical-srgb1024-bicubic700-identity-v1/canonical700-to224-bilinear-imagenet-v1`.
The20 exact contract fields, encoder SHA, payload SHA/length and sorted UTF
metadata precede big-endian float64 local mean18816/effective std18816/
alpha196/effective beta196. Payload304,192bytes; complete bundle306,136bytes.
Strict readers reject mismatched contracts/encoders, corrupt/truncated/trailing
bytes, duplicates and invalid state. Foreign canonical loaders reject the bundle.
No Metal Nut persistence, production API or strategy registry changes.

File: `screw-b-identity.m2` in the final evidence root below.

- Bundle SHA: `0ca27deba30f59805ac0e9c6dfe17c4e67abc9c4f63ac6e36d0584080addf26c`
- Payload SHA: `d34f6f4c54f3f9908ee4b6a12ba1df81276ea7837b67f26a0eb39ea981b14a8c`
- Contract JSON SHA: `e918b0a313669f3e0f72dc095439ee7ead762716d6384b0176341e57b37de8b1`

## 7. Frozen TEST protocol

`train-freeze.json` records2026-10-20T12:02:02.067880800Z and
`test_opened:false`, with model/contract/manifests/protocol identities. Every
phase verifies source/input/prior output hashes. Control protocol SHA:
`c6baf46d8a22bf7b588e430992955b1e06d18622bcd05ed077913c3ba5eaf7f2`.
Copied canonical evaluation protocol SHA:
`5a1444a71bff34f8847c067771256c3dee51c5207ec2c893dc018414ca2c10f0`.

All160 TEST images use the existing exact composition: native14 Z -> existing
upsample700 -> existing backproject700 with identity -> existing upsample1024.
No interpolation shortcut. Existing Image/Pixel AUROC and normalized
AUPRO@.30 evaluator/GT are unchanged, including8-connected regions/all-good
background. Shared counts:135 regions,418611 foreground and167353549 background
pixels (167772160 total). Primary metrics froze before group/map diagnostics.

## 8. Primary three-way metric table

H=historical retained M2; I=matched B_IDENTITY; C=retained canonical Screw.
Full precision resides in `three-way-primary.json`.

| Metric | Historical | B_IDENTITY | Canonical | Historical->Identity | Identity->Canonical |
|---|---:|---:|---:|---:|---:|
| Image AUROC | .564869850379 | .564869850379 | .663455626153 | .000000000000 | +.098585775774 |
| Pixel AUROC | .863995764276 | .864740286726 | .961903057736 | +.000744522450 | +.097162771009 |
| AUPRO@.30 | .602107273779 | .607138296977 | .845029261070 | +.005031023199 | +.237890964093 |

## 9. Pipeline-effect deltas

H->I is0 /+.000744522450 /+.005031023199 in Image/Pixel/AUPRO order.
The compound pipeline/population intervention explains0%,.7604%,2.0710% of the
observed aggregate H->C gain by arithmetic decomposition. Equality of Image
AUROC does not imply equality of per-image scores, normal features or subgroups.
The experiment does not separately identify filtering, training coverage or
evaluation interpolation effects within this small net pipeline delta.

## 10. Pose-effect deltas

I->C is+.098585775774 /+.097162771009 /+.237890964093.
Pose accounts for100%,99.2396%,97.9290% of the net H->C gain, respectively.
These are descriptive shares of measured deltas, not confidence intervals or
independent causal proportions. The intervention includes the consequences of
pose for learned local/calibration statistics and map-coordinate remapping.

## 11. Defect-group three-way results

Each group uses the same41 normals. Entries are Image/Pixel/AUPRO, rounded to9
decimals; `three-way-defects.json` retains exact results and both delta vectors.

| Defect (anomalies) | Historical | B_IDENTITY | Canonical |
|---|---|---|---|
| manipulated_front (24) | .626016260 / .949402131 / .821326982 | .604674797 / .947984236 / .817098798 | .691056911 / .966198934 / .861803266 |
| scratch_head (24) | .569105691 / .940366107 / .784869062 | .553861789 / .936421268 / .773150692 | .538617886 / .909815871 / .713909013 |
| scratch_neck (25) | .590243902 / .875652691 / .631479885 | .604878049 / .878124911 / .646990373 | .698536585 / .976308127 / .921157974 |
| thread_side (23) | .510074231 / .773628352 / .385998070 | .529162248 / .774574888 / .394061564 | .660657476 / .945428699 / .802041119 |
| thread_top (23) | .523860021 / .820572443 / .471153770 | .527041357 / .818591862 / .467782973 | .729586426 / .984185564 / .938883183 |

| Defect | H->I Image / Pixel / AUPRO | I->C Image / Pixel / AUPRO |
|---|---|---|
| manipulated_front | -.021341463 / -.001417895 / -.004228185 | +.086382114 / +.018214698 / +.044704468 |
| scratch_head | -.015243902 / -.003944840 / -.011718370 | -.015243902 / -.026605397 / -.059241678 |
| scratch_neck | +.014634146 / +.002472221 / +.015510489 | +.093658537 / +.098183216 / +.274167600 |
| thread_side | +.019088017 / +.000946536 / +.008063494 | +.131495228 / +.170853811 / +.407979555 |
| thread_top | +.003181336 / -.001980581 / -.003370797 | +.202545069 / +.165593702 / +.471100209 |

## 12. scratch_head diagnosis

Identity already loses some quality, and canonical pose amplifies that loss.
I->C loses.015243902 Image,.026605397 Pixel and.059241678 AUPRO.
H->I loses.015243902,.003944840,.011718370. Thus the head localization result
resembles Case A with a smaller nonzero pipeline contribution: pose accounts for
87.09% of total Pixel loss and83.49% of total AUPRO loss. Image loss splits50/50.
It is neither mainly pipeline Case B nor pose-recovery Case C. No new
"approximately equal" threshold is introduced to force a case label.

The matched intervention associates the added degradation with normalization
and its learned/remapping consequences. It does not prove whether resampling,
head ambiguity, reduced normal variance, calibration or native-max location is
the mechanism. Normal coherence alone cannot exclude a defect-specific loss.

## 13. Matched normal coherence

Matched253 FIT identities. Identity was freshly computed and reproduces the
entire retained audit summary exactly. Safe canonical values are retained from
the [framing checkpoint](SCREW_CANONICAL_FRAMING_FEASIBILITY.md), not re-encoded
in this run; the canonical qualification already checked its253 FIT units exactly.

| Diagnostic | B_IDENTITY | Canonical safe |
|---|---:|---:|
| Pooled same-position cosine mean | .469488238090 | .784784378816 |
| Pooled same-position cosine median | .541020792716 | .830231804827 |
| Channel sample std median | .015200533218 | .010932814409 |
| Mean directional dispersion | .528414877476 | .214364966554 |
| Mean position variance | .005526164187 | .002241829387 |
| LOO same-cell fraction | .166913702240 | .618247694335 |
| LOO best-cell drift | 1.001650479681 | .407708870304 |

Pooled cosine uses6,248,088 pairs and a true pooled median. Sample std uses
ddof1. Existing LOO diagnostic uses interior12x12 cells/3x3-neighbor search;
it does not alter the scorer. Cosine/dispersion/variance are mathematically
coupled summaries, not three independent demonstrations. No diagnostic tunes M2.

## 14. Post-freeze map diagnostics

All160 identity/canonical pairs are compared in original1024 coordinates.
`map-relationships.json` retains each row; `map-relationship-summary.json` retains
five defect groups plus good. All maps are nonconstant. Pearson uses the full
map; hotspot displacement is Euclidean distance between the first row-major
argmax locations; score delta is C-I native-max Z.

| Group | Pearson median | Hotspot distance median / P95 (pixels) | Native score delta median |
|---|---:|---:|---:|
| scratch_head | .198094635397 | 596.151171 / 877.098016 | +1.003705413345 |
| thread_top | .193746158110 | 451.164050 / 833.215707 | +4.372823563269 |
| thread_side | .113700274813 | 406.233923 / 733.154919 | +3.875305707100 |

These large map differences occur for both improved and degraded groups.
Full-map correlation includes background; hotspot distance is not GT localization
error. Separately calibrated Z score differences are not operating thresholds.
No new score, native-argmax rule or corrective mapping is introduced.

## 15. Causal limitations

One known category/split and already observed TEST do not prove universal
generality or independent replication. No confidence interval or new severity
threshold is claimed. Identity/canonical match populations and operators, but
represent whole separately fitted models under the pose intervention. H->I
remains compound and may contain cancellation. Mean coherence does not establish
preservation of head-defect evidence. No new robustness/timing/full15 campaign
was run. Exact ONNX export/checkpoint provenance remains unresolved; frozen byte
identity and implemented contracts are established. Ignored evidence/dataset
must be restored separately in a clone.

## 16. Decision

**POSE_EFFECT_SUPPORTED_BUT_MIXED**. Canonical pose materially improves all three
aggregate metrics over matched identity and improves four of five defect groups
on all three metrics. scratch_head worsens on all three. Neither a claim that
the pipeline dominates nor a claim of uniform pose benefit fits the evidence.
D28's historical whole-contract **MIXED_RESULT** remains valid.

## 17. Architectural implication

The matched control supplies convincing aggregate support in a second,
geometrically different object family for pose->B->CNN->strict local M2 on this
split. Pipeline-only net gains are small; most gain appears after pose.
This supports the architectural hypothesis while exposing a head-specific
tradeoff. Screw is not a second generally qualified production category. Metal
Nut remains the only integrated canonical detector. No generalized estimator,
registry, production API, M0 or default unaligned M2 change is justified here.

## 18. Tests, provenance and retained evidence

Entry HEAD `4d378edbaa2e9bce94bef6e9161b6b4f4a2a8230`, initially clean.
Only three test-source files and documentation are added/changed. Final evidence:
`target/experiments/screw-b-identity-control-20261020-validated`.
Historical evidence: `target/ablation/local-models-20261017/M2/screw`.
Canonical evidence: `target/experiments/screw-canonical-m2-qualification-20261020`.
The [generality audit](CANONICALIZATION_GENERALITY_AUDIT.md) supplies the retained
paired identity control. Source copies/172 hashes include all169 original
source/resource entries byte-unchanged.333 initial input hashes,320 TEST
image/annotation hashes and897 canonical evidence hashes guard retained inputs.
Stage guards bind manifests, arrays, bundle, predictions/maps, metrics and
diagnostics. Original canonical evidence is never overwritten or rerun.

The requested unsuffixed root exists and is retained as a **failed pre-fit
diagnostic**, not final evidence. Its253 FIT/63 CAL extractions were exact, but
an exact JSON coherence check rejected equal integer counts represented as
Jackson LongNode versus IntNode. No model was fitted and TEST was unopened there.
One failing-first regression reproduced this representation error. The checker
now compares the serialized/parsed representation exactly; a1e-12 changed
floating statistic still fails. No scoring tolerance changed. Sources were
refrozen and extraction/fitting/evaluation completed in the new `-validated`
namespace; neither original evidence namespace was rewritten.

- Final focused61 tests pass,0 failures/errors/skips, including10 new identity
  tests, Screw qualification/framing/generality, M2/persistence and map/metric tests.
- Relevant full Java suite225 tests pass,0 failures/errors/skips. Two unrelated
  legacy Bottle real-data integration classes are explicitly excluded by the
  established command; Screw real-data phases were run separately in full.
- Exact tensor checks cover316 FIT/CAL plus160 TEST images.253 retained FIT
  descriptors and full identity coherence reproduce exactly. Bundle reload exact.
- Independent NumPy reconstruction passes; source/input/output guards and
  `git diff --check` pass. No Python source changes or Python suite rerun.

Execution/red-green/test logs are retained under the final root's `logs/`.
Final `completion.json` records outcome and evidence identities. Reproduction
requires the retained prerequisite roots, dataset and Maven benchmark classpath.
Commands below describe the completed run; do not rerun into existing roots:

```bash
mvn -B -Pbenchmark '-Dtest=ScrewIdentityControlTest,ScrewM2QualificationTest,ScrewFramingTest,GeneralityAuditTest,M2ModelTest,CanonicalM2BundleTest,CanonicalPhase2BridgeTest,LocalizationMapsTest,LocalizationMetricsTest' test
mvn -B -Pbenchmark '-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest' test
mvn -B -Pbenchmark test-compile dependency:build-classpath '-Dmdep.outputFile=target/comparison-classpath.txt' '-Dmdep.includeScope=test'
CP="target/test-classes;target/classes;$(cat target/comparison-classpath.txt)"
ROOT=target/experiments/screw-b-identity-control-20261020-validated
RUNNER=io.github.antctrlwin.anomalib4j.evaluation.ScrewIdentityControl
java -Xmx8g -cp "$CP" "$RUNNER" prepare target/experiments/screw-canonical-m2-qualification-20261020 "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" train "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" test "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" evaluate "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" diagnose "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" verify "$ROOT"
git diff --check
```

## 19. Exact next step

Recommend a separately authorized **head-response failure analysis** using the
frozen identity/canonical models and retained descriptors/maps. Define normal
head support and diagnostic summaries from FIT/CAL first, then describe native
cell residual/Z tails and max-score origins, and original-coordinate defect
response on the frozen TEST maps/GT. Separate changes in local/calibration
statistics from spatial response where the retained evidence permits; label
unidentified mechanisms explicitly. No detector parameters, TEST-selected ROI,
new transform, scoring rule or production promotion. This diagnostic has not
been implemented or run in this task.
