# Screw head-response failure analysis

Completed2026-10-20, diagnostic only. **COMPETING_MAX_DOMINANCE is supported;
CALIBRATION_SENSITIVITY_SHIFT is present, but does not systematically suppress
head response. The upstream cause of the nuisance tails remains UNRESOLVED.**
No detector, model, calibration, pose, framing, B sampler, encoder, image-max rule,
localization or primary metric changed. No refit, CNN inference or commit.

## 1. Question

Why does canonicalization improve aggregate Screw quality and four defect groups
while scratch_head loses Image/Pixel/AUPRO? The [matched identity control](SCREW_B_IDENTITY_M2_CONTROL.md)
isolated the pose intervention; it did not identify its downstream failure mechanism.
This analysis uses the same frozen models/maps, not a corrective experiment.

The main finding is **not uniform loss of scratch response**: head maxima and
GT-local scores generally increase. Successful neck/thread groups gain much more,
while canonical normal/background nuisance scores become stronger competitors.
All statements below concern this known split, without independent replication.

## 2. Frozen evidence and model identities

Evidence root: `target/experiments/screw-head-response-analysis-20261020`.
Entry HEAD: `9ec188cbd1c80fa45d84075b5975a03f0f241272`, initially clean.
Read-only input roots:

- Identity: `target/experiments/screw-b-identity-control-20261020-validated`;
  bundle306,136bytes, SHA
  `0ca27deba30f59805ac0e9c6dfe17c4e67abc9c4f63ac6e36d0584080addf26c`.
- Canonical: `target/experiments/screw-canonical-m2-qualification-20261020`;
  bundle306,658bytes, SHA
  `cdf64b691d790213b9608f42b0f5d4d4006ec7b93267e7a14384f3d39843a0d6`.
- Both use253 FIT/63 CAL/160 TEST, including41 good/119 anomalous TEST;
  original3 FIT/1 CAL exclusions remain. All required arrays/maps/poses exist.

177 source/resource hashes/copies include the previous172 entries byte-unchanged.
2,454 input hashes bind prior evidence, source images and annotations. Source,
normal-freeze, analysis and resampling guards preserve phase outputs and inputs.
No old evidence is overwritten. Dataset/ignored artifacts need separate restoration.
Exact ONNX export/checkpoint provenance remains unresolved; this task never encodes.

## 3. Normal-only head-region definition

The [protocol](SCREW_HEAD_RESPONSE_PROTOCOL.md) fixes geometric bands before TEST
response/GT access. Separate medians of253 usable FIT silhouette minX/maxX/minY/
maxY define the rectangle. +x is the retained directed head end. Head is the last
quarter, neck the preceding quarter, thread the first half, all half-open.

| Band | X interval in canonical700 | Shared Y interval |
|---|---|---|
| Head | [504.35190933687426,661.8119884634538) | [250.9537380373451,448.6038083708521) |
| Neck | [346.89183021029487,504.35190933687426) | Same |
| Thread | [31.971671957135936,346.89183021029487) | Same |

Native14 centers are `(column+.5)*50-.5`, similarly for rows. Canonical head
contains12 cells: rows5..8, columns10..12, zero-based. Identity membership maps
each identity center through that image's retained pose into these fixed bands.
No band is changed after inspecting TEST; no diagnostic maximum enters scoring.
These rectangles are anatomical proxies, not object/defect segmentation. "Other"
means outside the fixed bands, not necessarily physical background; CNN receptive
fields overlap bands. This limits interpretation of origin counts.

`head-support.json` SHA:
`1dbcc9f646a5c681093347a718ade3a0c5e1e53ce33e6aba3f73c044f0cc2b88`.
`normal-freeze.json` timestamp2026-10-20T12:35:06.429320500Z records
`test_responses_read:false`, `test_gt_read:false`.
Protocol SHA: `5dfbf1eebfb9dd13aba95f5806080b2ba61d69086e6ef42095914130fe4a32fc`.

Four representative CAL normals and four scratch_head names were selected by
sorted indices floor(i*(N-1)/3), i=0..3, before response/GT inspection:
good008/112/200/313 and scratch_head000/007/015/023. Selection is systematic by
identity, not an assertion of statistical representativeness. Names SHA:
`c49634c188bcd1bc08bec5fe74422ec14440773ac9a9c83e301696c83f28e155`.

## 4. Normal head statistics

Identity fitted-state summaries weight cells by normal FIT semantic membership
frequency; canonical uses its fixed memberships. Thus these compare state met by
normal semantic regions, not identical numerical cell indices. Full weights,
per-cell values and distributions reside in `*-normal-statistics.json`.
Medians below are weighted cell/channel summaries, not recomputed model state.

| Normal fitted statistic | Head I / C | Neck I / C | Thread I / C |
|---|---|---|---|
| Local mean norm | .618890 / .906669 | .490510 / .928544 | .604020 / .923139 |
| Channel sample std | .016740 / .008275 | .017617 / .008552 | .016681 / .008402 |
| CAL alpha | .997858 / .944407 | 1.018038 / .979787 | 1.006157 / .991254 |
| CAL beta | .471670 / .343122 | .392266 / .379386 | .434562 / .361980 |
| Z gain1/beta | 2.120126 / 2.914494 | 2.549288 / 2.636926 | 2.301169 / 2.762928 |
| Residual coefficient1/(96*std^2*beta) | 79.179554 / 436.572963 | 92.089464 / 350.541577 | 85.853809 / 418.091380 |
| FIT cell raw | 1.578972 / .895033 | .907994 / .914102 | 1.193404 / .916200 |
| CAL cell raw | 1.563997 / .877169 | .914121 / .907945 | 1.215868 / .915721 |
| CAL band max Z | 2.494658 / 1.568920 | 1.594919 / 1.399906 | 2.885552 / 1.876367 |

Local std roughly halves in **all three regions**; head is not uniquely collapsed.
Head beta falls more than neck beta, increasing head Z sensitivity. Neither model
uses its1e-6 local/calibration floor here. Lower std is not intrinsically better:
the model becomes more sensitive to residual appearance differences and nuisance
responses. Identity semantic selection is conditional on image pose; selected
CAL head raw/Z need not have unconditional per-cell mean alpha/zero.

## 5. scratch_head native response

All24 images retain per-image global/head max, first row-major argmax, top5 cells/
values, strongest-head rank and head-global margin in `native-responses.json`.

| Per-image diagnostic, median | Identity | Canonical |
|---|---:|---:|
| Native global max Z | 4.902479 | 5.649611 |
| Head-band max Z | 2.654039 | 4.127453 |
| Strongest head rank among196 | 5.5 | 3 |
| Head max minus global max | -1.566948 | -1.384721 |
| Head raw maximum | 2.391488 | 2.502270 |
| GT-overlapping native max Z | 2.190458 | 3.260223 |
| GT-overlapping native mean Z | 1.723145 | 2.456744 |
| GT-overlapping cells | 3 | 2 |

Head max increases in17/24, decreases in7/24; median paired delta+.804624Z.
The difference of cohort medians is a different quantity. All canonical GT-overlap
cells lie in the frozen head band in these24 cases. GT cells are found by mapping
original pixel centers through the half-pixel700 mapping/pose and assigning the
containing50x50 native cell. They approximate spatial support, not actual CNN
receptive fields. The dominant pattern is mixed with **surviving/stronger localized
head signal**, not a uniform A-type attenuation or wholesale displacement.

## 6. Successful-group controls

Same native diagnostics were run for all25 neck/23 side/23 top images; head-band
records are retained even when their defect belongs elsewhere. Defect-local
native peaks show the useful contrast more directly:

| Group | GT-native peak median I / C | Original GT-mean median I / C | Images with GT mean increase |
|---|---|---|---:|
| scratch_head | 2.190458 / 3.260223 | 1.754773 / 2.329149 | 15/24 |
| scratch_neck | 2.927610 / 8.790117 | 1.553889 / 5.283008 | 24/25 |
| thread_side | 1.584933 / 5.664166 | .920436 / 2.620138 | 21/23 |
| thread_top | 1.970388 / 7.780548 | 1.039292 / 5.112224 | 22/23 |

Canonical maxima originate in neck for19/25 scratch_neck, thread for14/23 side
and17/23 top. Their corresponding identity counts are7/25,3/23,4/23.
Neck raw maximum median rises2.309749->4.644210; thread raw maximum rises
2.491556->2.826810 for side and2.531038->3.261543 for top. These groups gain
much stronger target response than head, against the same41-normal nuisance pool.

## 7. Original-coordinate diagnostics

Retained1024 maps are read directly, without regenerating inference or maps.
GT uses unchanged `DatasetNinjaMasks`; surrounding ring is a fixed8-pixel
Chebyshev dilation minus all GT. Each image retains GT mean/median/peak, ring
mean/median, peak percentile, global-peak overlap and8-connected component peaks.
Percentile is the fraction of full-image pixels <= GT peak; it is not an ROC
operating point. Values below are medians across images, separately by variant.

| Group | GT median I / C | Ring mean I / C | Best GT response I / C | GT peak percentile I / C | Global peak inside GT I / C |
|---|---|---|---|---|---|
| scratch_head | 1.761336 / 2.348213 | 1.732844 / 1.882184 | 2.151468 / 3.203847 | .966805 / .979999 | 0/24 / 2/24 |
| scratch_neck | 1.491092 / 5.665716 | 1.508309 / 4.985661 | 2.537252 / 8.056929 | .976309 / .999903 | 1/25 / 7/25 |
| thread_side | .878287 / 2.585902 | .917473 / 2.769135 | 1.376270 / 5.367138 | .889028 / .997598 | 0/23 / 5/23 |
| thread_top | 1.011233 / 4.880279 | .990982 / 4.726521 | 1.773822 / 7.680680 | .940709 / 1.000000 | 0/23 / 12/23 |

Head GT-minus-ring mean median rises.023893->.364801; peak percentile improves
in14/24. Nevertheless mean per-image head peak percentile is.960853->.959151,
showing a lower-performing tail. Component peak percentile medians I/C are
.966805/.979999 head, .975891/.999841 neck, .867982/.990368 side,
.940709/1.000000 top. All135 existing regions are accounted for, including the
manipulated_front group retained in the output. No thresholded overlap score is
invented. Existing group quality remains authoritative:

| Group | Retained Pixel AUROC I / C | Retained normalized AUPRO I / C |
|---|---|---|
| scratch_head | .936421268 / .909815871 | .773150692 / .713909013 |
| scratch_neck | .878124911 / .976308127 | .646990373 / .921157974 |
| thread_side | .774574888 / .945428699 | .394061564 / .802041119 |
| thread_top | .818591862 / .984185564 | .467782973 / .938883183 |

A closed-form accounting of the **existing** head Pixel AUROC compares62339
foreground pixels with68095101 background pixels. It separates background
sources without changing the metric or selecting a threshold:

| Background source | Pixels | Positive-win fraction I / C | Weighted contribution to Pixel delta |
|---|---:|---|---:|
|41 good images | 42991616 | .938390750 / .905889200 | -.020519745329 |
|24 scratch_head image backgrounds | 25103485 | .933048380 / .916540590 | -.006085651704 |

Thus77.13% of the rank loss is against normal-image backgrounds and22.87% against
anomalous-image backgrounds. Both foreground and background scores change: this
does not identify a background-only causal intervention. Within-image peak ranks
can improve while cross-image pooled Pixel AUROC deteriorates. This also fits the
AUPRO nuisance tradeoff, but does not quantitatively decompose its full curve.

## 8. Resampling findings

Only the frozen8 representatives are revisited at image/tensor level. The actual
unchanged logical1024 -> bicubic700 -> B224 path uses saved transforms; no geometry
estimator or CNN runs. There is no materialized pose700 raster in direct B.
All8 identity tensor hashes reproduce retained references exactly. Canonical
tensor hashes are newly retained; prior canonical tensor hashes were unavailable,
so no nonexistent canonical tensor parity is claimed.

Intensity uses gamma-encoded logical RGB weighted.299/.587/.114, not physical
illumination. Tensor statistics use inverse ImageNet normalization in floating
point. Mask projection is nearest-neighbor labeling for this diagnostic only.
The source annotation's principal minor-axis equivalent width is not a measured
physical scratch width or CNN receptive-field size.

| Representative | C/I local gradient energy | Scratch-ring contrast I / C |224 mask pixels I / C |224 minor width I / C | Occupied14 bins I / C |
|---|---:|---|---|---|---|
| head000 | .671398 | .089135 / .090531 | 133 / 129 | 8.467624 / 8.216325 | 4 / 2 |
| head007 | .889046 | .057440 / .060113 | 130 / 130 | 8.540174 / 8.642677 | 2 / 2 |
| head015 | 1.031059 | .086482 / .089761 | 172 / 170 | 9.695265 / 9.621899 | 4 / 2 |
| head023 | .792245 | .042578 / .044587 | 119 / 117 | 8.839878 / 8.706786 | 3 / 2 |

Median head contrast rises.071961->.074937. Median annotation area131.5->129.5;
minor width8.690026->8.674732, without a scale change. Gradient falls in3/4,
while normal head-area gradient C/I ratios are1.005189/1.063722/.968821/1.037732.
This is evidence of sampling/orientation sensitivity in some detail, **not proof
that resampling causes the metric regression**. Directional finite differences,
mask rasterization and grid alignment also change. The frozen contact sheet was
visually inspected: head placement is consistent, scratches remain visible,
both224 paths smooth detail, and canonical does not consistently erase contrast.
Fewer occupied bins after alignment do not alone mean lost information.

## 9. Pose-semantic findings

Endpoint proxy is retained support maxX and vertical midpoint, mapped back for
identity. It is a silhouette-bounds proxy, not an independent head landmark.

| Cohort | Identity endpoint std X/Y (700px) | Canonical endpoint std X/Y | Canonical X median | Head margin min/median |
|---|---|---|---:|---|
| FIT253 | 214.808737 / 208.160415 | 9.844398 / 8.897378 | 661.811988 | 8.712179 / 37.188012 |
| CAL63 | 220.800368 / 208.681733 | 10.525128 / 8.784009 | 662.203500 | 7.310381 / 36.796500 |
| TEST good41 | 211.030812 / 227.583787 | 11.667403 / 8.092105 | 666.433709 | 9.379265 / 32.566291 |
| scratch_head24 | 228.180459 / 200.188695 | 9.460649 / 7.775956 | 663.429367 | 9.918078 / 35.570633 |

Scratch_head canonical endpoint X range653.636831..689.081922 and Y
339.708082..361.459733 lie inside FIT ranges652.312990..690.287821 and
334.871559..365.140671. Feature-coordinate std is.189213/.155519 cells versus
4.563609/4.003774 identity. Canonical support height, head margin and feature
coordinates are retained per image. Median anisotropy.930683 and absolute
polarity.354219 closely match FIT.930964/.358119; their scratch_head ranges also
lie inside FIT ranges. No clipping/margin/polarity failure appears.

This argues against gross head placement instability as the dominant mechanism.
No paired clean/scratched acquisitions exist; these summaries cannot establish
whether scratches perturb a pose slightly or whether all surface landmarks align.
Axis-normalized bounds are partly consistent by construction.

## 10. Calibration decomposition

For each of12 frozen canonical head cells and every TEST image, its center maps
back to an identity native cell. Retained raw/alpha/beta/Z give the exact identity:

```text
Zc-Zi = (rc-ri)/bi - (ac-ai)/bi + (rc-ac)*(1/bc-1/bi)
          raw          alpha                 beta
```

The1920 records retain both cell IDs and all values. Pairing is approximate
spatial correspondence between different receptive fields/models. It is not a
cross-calibrated detector. Across288 scratch_head head-cell pairs, mean component
contributions are raw-.891268, alpha+.089703, beta+.225764, total-.575800Z.
Many non-peak head cells become quieter, while localized peaks strengthen. This
pooled reduction must not be misreported as attenuation of the scratch itself.

At each representative's strongest canonical head cell (selected descriptively
after all outputs freeze), raw/alpha/beta terms show different mixtures:

| Image; C/I cell | Identity raw/alpha/beta -> Z | Canonical raw/alpha/beta -> Z | Raw / alpha / beta contribution |
|---|---|---|---|
|000;110/46 |1.554559/.991326/.399631 ->1.409382 |3.222587/1.014732/.314292 ->7.024852 |+4.173921 /-.058569 /+1.500118 |
|007;108/87 |1.624828/1.025284/.476056 ->1.259397 |1.611771/.948119/.307346 ->2.159301 |-.027427 /+.162092 /+.765239 |
|015;110/20 |2.798962/.963839/.520587 ->3.525102 |2.730869/1.014732/.314292 ->5.460325 |-.130801 /-.097760 /+2.163784 |
|023;94/131 |1.417753/1.004079/.353689 ->1.169596 |2.040680/1.042995/.341340 ->2.922849 |+1.761228 /-.110030 /+.102054 |

Calibration frequently **amplifies**, rather than erases, head evidence. Degradation
cannot be assigned to a uniform loss of head Z sensitivity. Nuisance response also
exists before calibration: canonical normal good005 has raw21.672602 at cell121
(row8,col9, neck band), alpha.971163/beta.387081 -> Z53.480865. No beta floor or
near-zero beta explains that extreme. Local feature/model discrepancy and normal
score scaling both matter; this is not proof of a calibration-only defect.

## 11. Native max-score origin

Counts use the frozen geometric bands, with no changes to native max scoring:

| Cohort/variant | Head | Neck | Thread | Other |
|---|---:|---:|---:|---:|
| Good identity |5|1|11|24|
| Good canonical |1|4|3|33|
| scratch_head identity |1|0|7|16|
| scratch_head canonical |6|3|0|15|

Good global max median rises4.508537->5.938939Z, maximum10.308118->53.480865.
Head-defect global max median rises4.902479->5.649611, now below the good median.
Good head max instead falls2.602967->1.751325. Thus increased **non-head-band**
normal maxima compete with surviving local scratch evidence. They occur across
many cells, not one fixed border hotspot. "Other" remains an unclassified spatial
bucket, not a claim that every maximum is irrelevant background.

Exact Image AUROC accounting across984 good/head pairs loses234 former wins and
gains219: net-15/984=-.015243902439. This reproduces retained Image delta, without
introducing a regional scorer. Combined with Section7's pixel accounting, the
problem is substantially one of nuisance discrimination, not missing all head signal.

## 12. Mechanism classification

- **COMPETING_MAX_DOMINANCE: supported.** Canonical good global maxima rise
  outside the head band, reducing global image discrimination despite a stronger
  head response. Most pooled Pixel rank loss is against normal-image backgrounds.
- **CALIBRATION_SENSITIVITY_SHIFT: present, secondary.** Head/local std and beta
  change sensitivity; representative head Z often benefits. High normal raw
  discrepancy already exists before Z, so calibration alone is not the explanation.
- **UNRESOLVED:** why some normal acquisitions produce large canonical raw tails,
  and how much resampling/appearance/cell covariance contributes upstream. Mixed
  individual head responses and sampling sensitivity remain visible, without
  promoting HEAD_SIGNAL_ATTENUATION, RESAMPLING_LOSS or POSE_SEMANTIC_INSTABILITY
  to established causes.

## 13. What is ruled out within the observed evidence

No missing/corrupt TEST maps, changed max rule, new failed-image coverage, model
refit or calibration reuse explains this result. There is no uniform loss of head
contrast/width, no universal reduction of head Z sensitivity, no gross observed
head displacement/clipping and no std-floor pathology. Better normal coherence
does not guarantee better anomaly discrimination. These are bounded findings,
not proof that every individual resampling or pose error is absent.

## 14. What remains unresolved

The nuisance tails may reflect appearance/illumination/reflections, normal
intra-cell covariance, residual registration or training coverage. This experiment
does not intervene on those causes. Rectangular band and point-cell pairing do
not describe exact CNN receptive fields. Four resampling cases do not estimate a
population-wide causal effect, and annotations are not physical scratch metrology.
Original-frame peak/ring summaries do not decompose the complete AUPRO curve.
The same known TEST split supports description, not parameter selection or new
independent validation. Screw remains unpromoted; Metal Nut remains unchanged.

## 15. Exact next experiment

Recommend one separately authorized **CAL-only frozen-pose photometric nuisance
control**: use the same63 usable CAL normals and frozen identity/canonical models,
keep each original pose fixed, and apply the [existing robustness protocol](M2_ROBUSTNESS_PLAN.md)'s
brightness/contrast/gamma levels .80,.90,1,1.10,1.20 before the unchanged preprocessing.
Freeze that protocol before new responses; retain raw, alpha/beta/Z, all196-cell
responses and global-max origins. Compare paired nuisance amplification without
refitting, recalibrating, threshold selection or TEST access. This distinguishes
appearance sensitivity from pose-estimator changes without proposing a fix.
No such experiment was implemented or run here.

## 16. Tests and provenance

Five new test-only Java files own support arithmetic, retained-state summaries,
pixel/resampling diagnostics, phase orchestration and tests. Existing source,
benchmarks, configurations and scientific outputs are untouched. Main evidence:
`head-support.json`, `normal-freeze.json`, `*-normal-statistics.json`,
`native-responses.json`, `original-response.json`, `calibration-decomposition.json`,
`pose-records.json`, their summaries, `resampling-records.json`,
`tensor-reference-checks.json`, `head-resampling-contact.png` and phase hashes.

- Focused72 tests pass, including11 new tests; full relevant Java suite236 pass,
  zero failures/errors/skips. Existing two expensive Bottle integration classes
  are excluded by the established suite command; no unrelated campaign runs.
- Tests cover normal-only band boundaries, semantic transport, first-max ties/
  ranks, exact calibration identity, GT projection, Chebyshev ring exclusion,
 8-connectivity, percentile ties, uniform-image contrast and freeze ordering.
-8 retained identity tensors match exactly; all retained model/source/input/output
  guards pass. No new canonical tensor reference is invented.
- Independent NumPy2.2.5 max-state reconstruction agrees within1e-12 and exact
 984 image-pair accounting reproduces Image delta. Python's retained mask decoder
  `tools/prerun/local_memory_ablation.py::load_mask` reproduces all24 head GT counts.
  Its source SHA is `182c13118b1931a2bc689dbe0b5c0f469133ca20428314a4e48207db8eee2824`.
- The initial independent Pixel AUROC check at1e-12 failed for canonical by
 6.0419447223e-12. The rank formula counts positive wins with half ties, while
  Java accumulates millions of ROC trapezoids. The failed log is retained; no
  test or detector tolerance was weakened. Final accounting records, rather than
  hides, arithmetic differences: identity-1.5987211555e-13 and canonical+6.0419447223e-12.
  Retained Java metrics stay authoritative. Weighted rank components sum to their
  closed-form delta within1e-12. This is supplementary numerical evidence, not
  claimed bit-identical Java metric reproduction.
- Diff whitespace, added files/local Markdown links, report structure and scope
  checks pass. No production source or mathematical/metric semantics changed.

Independent pixel accounting is reproducible from the frozen predictions/maps:
sort all head-positive pixel scores P; each background score b receives
`len(P)-(searchsorted(P,b,left)+searchsorted(P,b,right))/2` positive-win credit.
Sum credit separately for good-image and head-image backgrounds, divide by
`len(P)*backgroundCount`, then weight by each source's share of all background.
No sampling, thresholds or rounded scores enter this accounting. Execution logs,
including failed/successful independent checks and per-image contributions, are
retained under the new root's `logs/`; `completion.json` records classification.

```bash
mvn -B -Pbenchmark '-Dtest=ScrewHeadAnalysisTest,ScrewIdentityControlTest,ScrewM2QualificationTest,ScrewFramingTest,GeneralityAuditTest,M2ModelTest,CanonicalM2BundleTest,CanonicalPhase2BridgeTest,LocalizationMapsTest,LocalizationMetricsTest' test
mvn -B -Pbenchmark '-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest' test
CP="target/test-classes;target/classes;$(cat target/comparison-classpath.txt)"
RUNNER=io.github.antctrlwin.anomalib4j.evaluation.ScrewHeadAnalysis
ROOT=target/experiments/screw-head-response-analysis-20261020
java -Xmx8g -cp "$CP" "$RUNNER" prepare "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" analyze "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" resample "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" verify "$ROOT"
git diff --check
```

Use a fresh evidence namespace for reproduction; do not rerun into existing
outputs or use `mvn clean`. Restore prerequisite roots, dataset and Maven test
classpath first. The contact sheet shows diagnostic crops stretched for inspection;
quantitative measurements use full unresized arrays, not preview pixels.
