# Screw CAL-only photometric nuisance control

Completed2026-10-20. **PHOTOMETRIC_SENSITIVITY_PARTIAL**. Canonical has additional
tails in some brightness/contrast conditions, but both models have severe shared
brightening sensitivity; gamma effects are much smaller. No corrective experiment,
refit, recalibration, geometry estimation, production change or commit.

## 1. Scientific question

Does appearance alone disproportionately amplify canonical normal nuisance
responses, with the pose estimator removed from the intervention? The preceding
[head analysis](SCREW_HEAD_RESPONSE_ANALYSIS.md) motivated this control, but this
run accesses no TEST images/maps and makes no new anomaly-quality claim.
The comparison is between complete frozen identity/canonical representations and
their respective fitted models, not a single-model feature intervention.

## 2. Frozen models and poses

Read-only bundles, unchanged from the [matched control](SCREW_B_IDENTITY_M2_CONTROL.md)
and [canonical qualification](SCREW_CANONICAL_M2_QUALIFICATION.md):

- Identity:306,136bytes, SHA
  `0ca27deba30f59805ac0e9c6dfe17c4e67abc9c4f63ac6e36d0584080addf26c`.
- Canonical:306,658bytes, SHA
  `cdf64b691d790213b9608f42b0f5d4d4006ec7b93267e7a14384f3d39843a0d6`.
- Encoder SHA `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`;
  unchanged SPATIAL_14/14x14x96 and strict M2 normalization/raw/Z/native max.

Every canonical variant reuses the original CAL cos/sin/tx/ty, including fixed
safe placement. Identity is exactly(1,0,0,0). No segmentation, centroid, axis or
polarity is recomputed. `cal-cases.json` binds poses, source hashes and retained
identity tensor hashes; SHA
`dc8ade6478b2a32b107afaf245aa5a4c7d15e20f20eb76dfea3635011df2295a`.

## 3. CAL-only scope

64 historical CAL identities accounted,63 usable scored; good179 remains excluded
without opening its image. Same original seeded order, no replacement or split.
Usable manifest SHA:
`d89e343b3d8000090f8198900049bc1896a0cfc2e054b15195cf2dff77f5f13f`.

Only explicit91 allowlisted inputs are hashed/read:63 train/img sources and28
selected metadata/model/CAL-reference/protocol files. No prior campaign tree is
scanned or copied. Only selected CAL cases/manifests/arrays are retained; no mixed
image metadata or TEST scientific data is copied into this evidence root.
Source-code hashing is separate from scientific data access. Access audits cover
preparation, baseline and perturbation phases; the allowlist rejects the forbidden
data partition before I/O. Only the six new diagnostic source files are copied.
Frozen bundle metadata retains its original identities, without changing contracts.

## 4. Perturbation definitions

The [frozen protocol](SCREW_CAL_PHOTOMETRIC_PROTOCOL.md) and `perturbations.json`
use exactly BRIGHTNESS/CONTRAST/GAMMA at.80,.90,1,1.10,1.20. For each logical
default-sRGB8-bit channel v, Java double arithmetic:

```text
brightness: v*f
contrast:   127.5 + (v-127.5)*f
gamma:      255*pow(v/255,g)
output:     Math.round(clamp(value,0,255))
```

RGB order, alpha discarded, no linear-light conversion. Each variant starts from
the original logical native1024 RGB, before unchanged Graphics2D bicubic/quality700,
frozen-transform direct B224/ImageNet/NCHW and encoder. These formulas/rounding
match the [existing robustness convention](M2_ROBUSTNESS_PLAN.md); the earlier
experiment applied them at224, whereas this user-requested control applies them
at native1024. No chaining, blur, noise, geometric or color-space intervention.

Clipping is measured before rounding. Median fraction of native channels clipped:
brightness1.10 **12.3999%**, brightness1.20 **81.4165%**, contrast1.20 **1.0262%**;
other listed conditions have median0%. Gamma never creates out-of-range channels.
These doses are not matched in physical illumination or saturation severity.

## 5. Baseline parity

Freeze timestamp2026-10-20T13:24:52.981260100Z, before encoding. Protocol SHA:
`871e8820d24cb14b3b44fccca59dd2ca719c1e9628234899afa7af7025f02a98`.
The unchanged normal-only bands have SHA
`1dbcc9f646a5c681093347a718ade3a0c5e1e53ce33e6aba3f73c044f0cc2b88`.

All three1.00 controls run on both models before any nonidentity factor:
**378 evaluations pass exactly**. Retained normalized descriptors, raw maps,
Z maps and native image maxima have maximum error0. Identity tensor hashes match
189 times (63 identities x3 families). Canonical historical tensor hashes are
unavailable:189 canonical tensor checks instead use unchanged B applied to the
original decoded source view and saved transform. The factor1 logical-RGB path
matches that executable reference bit-for-bit. No historical tensor hash or raw
CNN feature archive is invented; retained descriptor parity refers to normalized
CNN units. `baseline-parity.json` discloses the reference distinction.

Baseline CAL max-Z medians I/C are3.346801/4.075579. CAL estimated the frozen Z
statistics, so these are descriptive reused-CAL results, not independent FPRs.

## 6. Identity nuisance response

In Sections6/7, each vector is **median / p90 / p95 / maximum**, across63 images.
Raw maxima are maxima of the raw map, not raw values at the Z-winning cell.
Full min/mean/quantiles and per-image raw/Z deltas reside in `response-summary.json`
and the baseline/perturbed records. Values are rounded here, not in evidence.

| Family; level | Identity max Z | Identity native raw max |
|---|---|---|
| Brightness .80 |3.805271 /6.339883 /7.129193 /10.602290|3.301435 /4.796933 /5.179521 /7.261974|
| Brightness .90 |3.707666 /5.317901 /5.843366 /7.945163|3.286660 /4.129782 /4.721392 /5.682455|
| Brightness1.00 |3.346801 /4.661382 /5.390892 /6.482803|3.048262 /3.856107 /4.806849 /4.964042|
| Brightness1.10 |10.713123 /31.653993 /36.005191 /42.255678|6.153162 /13.459226 /15.191079 /17.678882|
| Brightness1.20 |39.132175 /40.728419 /40.728701 /40.731356|16.435673 /17.071006 /17.071118 /17.072175|
| Contrast .80 |3.618303 /5.121161 /5.750662 /7.569123|3.237777 /4.325199 /5.178978 /5.715270|
| Contrast .90 |3.503099 /5.344859 /5.927932 /8.340093|3.100357 /4.727616 /4.977333 /5.917219|
| Contrast1.00 |3.346801 /4.661382 /5.390892 /6.482803|3.048262 /3.856107 /4.806849 /4.964042|
| Contrast1.10 |3.869470 /5.646575 /6.084600 /6.967971|3.218315 /4.434327 /4.879948 /5.092759|
| Contrast1.20 |6.940986 /21.793575 /23.398617 /36.047035|4.900829 /11.291870 /12.029000 /15.207734|
| Gamma .80 |3.443429 /5.134250 /5.854216 /7.234718|3.127192 /4.511883 /4.923818 /5.260133|
| Gamma .90 |3.434439 /4.786133 /5.574970 /6.378198|3.079345 /3.718983 /4.879569 /5.228930|
| Gamma1.00 |3.346801 /4.661382 /5.390892 /6.482803|3.048262 /3.856107 /4.806849 /4.964042|
| Gamma1.10 |3.502386 /4.784431 /5.369105 /6.498333|3.108690 /3.900809 /4.800255 /5.095245|
| Gamma1.20 |3.429527 /4.884017 /5.989823 /7.637337|3.118931 /4.080926 /4.789867 /5.287973|

Identity is strongly affected by brightening too; it is not a robust photometric
control. At1.20 brightness all63 images exceed10Z; at1.20 contrast23/63 do.

## 7. Canonical nuisance response

| Family; level | Canonical max Z | Canonical native raw max |
|---|---|---|
| Brightness .80 |5.532742 /7.731640 /8.326699 /9.579372|3.757189 /5.623790 /6.595587 /9.372808|
| Brightness .90 |4.779504 /6.691587 /6.941064 /8.216331|3.614628 /5.175910 /5.961351 /9.390891|
| Brightness1.00 |4.075579 /5.550813 /6.312071 /6.685429|3.375472 /4.775806 /5.356863 /8.629543|
| Brightness1.10 |9.651310 /31.954678 /33.886541 /35.583282|6.280818 /14.413260 /14.531045 /15.334846|
| Brightness1.20 |39.356549 /44.005675 /44.541360 /45.362206|15.937738 /16.983010 /17.189995 /17.696036|
| Contrast .80 |4.784250 /6.615406 /7.204514 /9.882768|3.565680 /4.911154 /5.806472 /8.319624|
| Contrast .90 |4.331803 /5.724231 /6.620492 /6.990979|3.208135 /4.565393 /5.496765 /8.961578|
| Contrast1.00 |4.075579 /5.550813 /6.312071 /6.685429|3.375472 /4.775806 /5.356863 /8.629543|
| Contrast1.10 |4.901558 /6.615770 /6.780170 /7.863127|3.557288 /5.210318 /5.833172 /9.969448|
| Contrast1.20 |7.708648 /30.192610 /31.868671 /39.009107|5.248545 /13.353529 /13.702430 /17.146931|
| Gamma .80 |4.216740 /6.069294 /6.549279 /6.691225|3.231123 /4.869475 /5.192094 /8.636137|
| Gamma .90 |4.246366 /6.034216 /6.145209 /7.454209|3.302339 /4.922658 /5.280666 /9.504208|
| Gamma1.00 |4.075579 /5.550813 /6.312071 /6.685429|3.375472 /4.775806 /5.356863 /8.629543|
| Gamma1.10 |4.441267 /6.044949 /6.730417 /7.645684|3.397181 /4.945086 /5.536485 /9.689596|
| Gamma1.20 |4.657046 /6.065622 /6.444021 /6.889222|3.431568 /4.941621 /5.397088 /8.364971|

Descriptive exceedances below give **total cells (fraction of images)** above
3;5;10Z. Each image fraction is written exactly as count/63. Total cell denominator
is12348 per condition. No value is selected as a detector threshold.

| Condition | Identity >3; >5; >10 | Canonical >3; >5; >10 |
|---|---|---|
| Baseline1, each family |132(46/63);4(4/63);0(0/63)|206(52/63);22(17/63);0(0/63)|
| Brightness .80 |195(57/63);16(13/63);1(1/63)|559(63/63);82(40/63);0(0/63)|
| Brightness .90 |157(52/63);10(7/63);0(0/63)|323(59/63);46(29/63);0(0/63)|
| Brightness1.10 |2116(63/63);1345(60/63);456(35/63)|3878(63/63);2423(61/63);1332(31/63)|
| Brightness1.20 |7625(63/63);6433(63/63);3272(63/63)|10576(63/63);9912(63/63);8306(63/63)|
| Contrast .80 |145(52/63);10(8/63);0(0/63)|282(59/63);44(25/63);0(0/63)|
| Contrast .90 |130(47/63);10(8/63);0(0/63)|212(51/63);27(20/63);0(0/63)|
| Contrast1.10 |185(56/63);11(10/63);0(0/63)|375(54/63);52(30/63);0(0/63)|
| Contrast1.20 |1330(63/63);747(52/63);222(23/63)|2744(63/63);1487(55/63);674(23/63)|
| Gamma .80 |123(49/63);8(7/63);0(0/63)|181(53/63);26(17/63);0(0/63)|
| Gamma .90 |127(49/63);5(5/63);0(0/63)|182(51/63);24(18/63);0(0/63)|
| Gamma1.10 |145(50/63);7(5/63);0(0/63)|253(58/63);31(23/63);0(0/63)|
| Gamma1.20 |140(49/63);7(6/63);0(0/63)|293(56/63);34(25/63);0(0/63)|

High brightness affects both models, with much broader canonical above10Z support.
Contrast1.20 has the same23-image exceedance count but more canonical high cells.
Gamma has no10Z exceedance in either model anywhere in the prescribed range.

## 8. Paired nuisance amplification

For each image, delta is its perturbed native maximum minus its own factor1
maximum. Amplification is deltaC-deltaI. It is **not** the difference of cohort
medians. Exact per-image deltas, including negatives, reside in the retained records.

| Condition | Amplification median /p90 /p95 /max Z | C larger absolute change | Positive signed amplification |
|---|---|---:|---:|
| Brightness .80 |.614543 /3.231081 /3.734527 /5.126997|41/63|41/63|
| Brightness .90 |.593269 /1.756963 /2.397113 /4.157317|50/63|46/63|
| Brightness1.10 |.097163 /6.725828 /7.909324 /12.436380|33/63|33/63|
| Brightness1.20 |2.507084 /9.882700 /11.329074 /15.089580|41/63|41/63|
| Contrast .80 |.604939 /2.186978 /2.579371 /5.569620|48/63|45/63|
| Contrast .90 |.057071 /.793112 /.885106 /2.376879|43/63|33/63|
| Contrast1.10 |.066881 /1.224009 /1.856704 /2.337360|40/63|36/63|
| Contrast1.20 |.462092 /8.771035 /10.398160 /18.718742|38/63|38/63|
| Gamma .80 |-.027821 /.722629 /1.372174 /2.146427|46/63|29/63|
| Gamma .90 |.026233 /.839358 /1.024779 /1.622989|44/63|34/63|
| Gamma1.10 |.144336 /1.286234 /1.615121 /1.856431|45/63|35/63|
| Gamma1.20 |.330113 /1.101356 /1.342738 /2.408699|49/63|46/63|

All factor1 deltas/amplifications are exactly0. The largest cohort amplification
median and p95 occur at brightness1.20; the largest individual amplification is
contrast1.20 good125, +18.718742Z. There is substantial negative amplification
too: brightness1.10 minimum-10.016912Z. This is not uniform canonical dominance.

Across each image's largest absolute change over four nonidentity levels:
brightness C/I larger41/22, contrast39/24, gamma53/10, ties0. These are63-image
counts per family, not252 independent images. Gamma's higher counts coexist with
small absolute score changes. `family-stability.json` retains this distinction.

## 9. Raw versus calibrated decomposition

For the Z-winning cell p at each level, retain raw,alpha,beta,Z, baseline raw/Z
and baseline global maximum. Within each frozen model:

```text
deltaGlobal = deltaRaw_at_winner/beta
              + baselineZ_at_winner - baselineGlobalMax
```

The second term is a nonpositive winner-switch penalty. Alpha/beta never change.
For paired winners, the preregistered accounting splits amplification into:

```text
raw term     = (deltaRawC-deltaRawI)/betaI
scaling term = deltaRawC*(1/betaC-1/betaI)
switch term  = penaltyC-penaltyI
```

Different winning cells/models are compared; these are arithmetic components,
not three independently manipulated causes or a hybrid scorer. Mean components
are additive; medians of components need not sum to median amplification.

| Condition | Mean raw /scaling /switch term | Mean amplification |
|---|---|---:|
| Brightness1.10 |-1.525133 /+1.512876 /+.257754|+.245497|
| Brightness1.20 |-.639084 /+3.940448 /-.584656|+2.716708|
| Contrast1.20 |-.434281 /+1.710849 /+.528093|+1.804660|
| Gamma1.20 |+.485597 /+.114775 /-.297389|+.302983|

Raw discrepancy rises strongly in **both** models under brightening. Canonical's
additional mean high-factor response is often associated with its selected-cell
Z scaling, rather than universally larger raw growth. Individual cases differ:

- Brightness1.20 good296: I raw11.341729/alpha.844372/beta.370997 ->28.294992Z;
  C raw16.795386/alpha.989091/beta.357348 ->44.232266Z. Additional growth terms
  raw+14.511820, scaling+1.654314, switch-1.076553 =+15.089580Z.
- Contrast1.20 good125: I raw10.334389/alpha.957283/beta.511681 ->18.326075Z;
  C raw11.896386/alpha1.001353/beta.279295 ->39.009107Z. Terms+1.548714,
 +16.572896,+.597132 =+18.718742Z, principally scaling in this accounting.

The answer is **both**, depending on condition/image. No floor pathology or
calibration-only explanation is established. Channel contributions are retained
for all winners: changes in squared standardized residual/96, including negatives.
At the worst canonical case in each family:

| Case | Top5 channel indices, zero-based | Positive-growth share | Positive channels | Effective participation |
|---|---|---:|---:|---:|
| Brightness1.20 good296 |32,26,63,38,5|.540722|70|12.427456|
| Contrast1.20 good125 |32,24,19,1,29|.475339|71|13.016938|
| Gamma1.20 good137 |10,43,36,9,61|.358270|61|21.456550|

Growth spans many channels but is uneven: top5 account for roughly half in the
two largest cases; gamma is more diffuse. These are unnamed CNN channels, not
identified physical causes. No feature weighting changes.

## 10. Spatial origin

Reuse the exact frozen head/neck/thread rectangles and transported identity-cell
labels. Other means outside those bands, not verified physical background.
Baseline origins(head/neck/thread/other): I7/3/17/36, C1/2/9/51.

| Condition | Identity origins H/N/T/O | Canonical origins H/N/T/O |
|---|---|---|
| Brightness .80 |4/1/11/47|2/0/5/56|
| Brightness .90 |5/1/18/39|1/1/7/54|
| Brightness1.10 |0/0/0/63|1/0/0/62|
| Brightness1.20 |0/0/0/63|0/0/0/63|
| Contrast .80 |3/1/13/46|2/1/9/51|
| Contrast .90 |7/2/20/34|1/2/10/50|
| Contrast1.10 |9/3/7/44|1/2/7/53|
| Contrast1.20 |0/1/0/62|1/1/0/61|
| Gamma .80 |7/3/16/37|1/3/9/50|
| Gamma .90 |8/4/16/35|2/2/12/47|
| Gamma1.10 |6/5/17/35|2/0/3/58|
| Gamma1.20 |10/3/13/37|1/3/4/55|

Large positive-factor nuisance maxima overwhelmingly occur in Other in both
models. The experiment does not identify head as the preferential nuisance origin.
For example, worst brightness canonical cell165 is(row11,col11), while worst
contrast cell55 is(row3,col13). No region is changed after observing these cells.

## 11. Sensitivity slopes

Native image-score centered slope is(S1.10-S.90)/.20. Maximum absolute one-sided
secant is max(abs(S.90-S1),abs(S1.10-S1))/.10. Units are Z per unit factor.
Both are reported because winner switches/nonlinearity can cancel a centered slope.
Each vector is median/p90/p95/max across63 images; full records are retained.

| Family/model | Centered slope | Maximum absolute one-sided secant |
|---|---|---|
| Brightness I |34.947582/137.910211/153.011606/197.371048|76.063204/280.808749/320.917502/392.152539|
| Brightness C |26.694980/135.811627/144.680621/151.996274|65.173623/277.262921/293.011496/302.814700|
| Contrast I |.714742/9.030965/12.584663/22.703598|3.013235/19.206662/27.255272/45.280920|
| Contrast C |1.368830/10.038218/11.917071/20.551937|6.603353/19.329238/22.133396/25.565709|
| Gamma I |.165689/2.057299/2.988570/10.108732|1.431713/7.495260/8.305161/21.819069|
| Gamma C |1.761231/8.990058/10.612103/13.049243|4.574993/15.098883/17.149184/21.619267|

Local canonical brightness sensitivity is not uniformly larger: its median and
upper one-sided tails here are lower than identity. Gamma/contrast median local
sensitivity is higher, but gamma absolute nuisance maxima remain modest. No
nonlinear robustness model is fitted.

## 12. Relationship to fitted statistics

For each of196 cells, mean maximum absolute one-sided Z sensitivity across63
images is compared with frozen channel-std median, beta and median residual
coefficient1/(96*std^2*beta). Tie-aware Spearman correlations:

| Family/model | Std median | Beta | Residual coefficient |
|---|---:|---:|---:|
| Brightness I |-.786950|+.454677|+.506213|
| Brightness C |+.676579|+.081352|-.617834|
| Contrast I |-.739190|+.210133|+.668361|
| Contrast C |+.204746|-.242887|-.037764|
| Gamma I |-.583243|+.049273|+.580575|
| Gamma C |+.108005|-.253093|+.055538|

Winning-cell categories below use pre-encoding model-specific lower quartiles,
not learned detector thresholds. There are252 nonidentity image-level winners
per row, not252 independent images:

| Family/model | Low std only | Low beta only | Both | Neither |
|---|---:|---:|---:|---:|
| Brightness I |101|9|100|42|
| Brightness C |19|39|6|188|
| Contrast I |137|21|30|64|
| Contrast C |40|39|11|162|
| Gamma I |99|34|17|102|
| Gamma C |48|36|13|155|

Canonical nuisance sensitivity is **not concentrated in its lowest-std cells**;
brightness shows the opposite rank relationship. Most canonical winners are in
neither lower quartile. A relatively smaller C beta than the matched I winner can
still amplify a cross-model comparison without being low-ranked within C.
Spatial dependence and model/feature confounding prevent causal inference from
these correlations. No normalization or covariance change is proposed as a result.

## 13. Worst normal cases

Unique-image worst five per family, ranked by each image's maximum signed paired
amplification over the four nonidentity levels. Scores are baseline I/C -> perturbed
I/C. Both semantic origins are Other except where explicitly marked.

| Family; level; filename suffix | Baseline I / C | Perturbed I / C | Amplification Z | Origins I / C |
|---|---|---|---:|---|
| Brightness1.20; good296 |2.332247/3.179941|28.294992/44.232266|15.089580|Other/Other|
| Brightness1.20; good097 |4.534695/2.314090|30.280399/40.718010|12.658216|Other/Other|
| Brightness1.10; good175 |3.850209/3.184227|20.582607/32.353005|12.436380|Other/Other|
| Brightness1.20; good241 |3.014480/3.266081|27.762554/40.157120|12.142965|Other/Other|
| Brightness1.20; good044 |3.371132/4.901859|28.702088/41.627189|11.394374|Other/Other|
| Contrast1.20; good125 |3.360846/5.325136|18.326075/39.009107|18.718742|Other/Other|
| Contrast1.20; good013 |2.700510/4.371936|17.793323/30.588567|11.123817|Other/Other|
| Contrast1.20; good029 |3.040424/4.858592|15.174442/27.409882|10.417273|Other/Other|
| Contrast1.20; good160 |3.779302/4.075579|15.825041/26.536429|10.415112|Other/Other|
| Contrast1.20; good154 |3.702356/5.422078|21.733855/33.699168|10.245590|Other/Other|
| Gamma1.20; good137 |2.968880/3.940737|2.969602/6.350158|2.408699|Other/Other|
| Gamma .80; good030 |4.160249/3.921318|3.088284/4.995780|2.146427|Other/Other|
| Gamma1.10; good249 |2.621501/1.952463|2.700399/3.887793|1.856431|Thread/Other|
| Gamma .80; good091 |3.926851/3.730422|3.481460/5.122394|1.837363|Thread/Other|
| Gamma1.10; good282 |3.290841/1.596913|3.205066/3.325328|1.814190|Other/Other|

Full filenames are `screw_good_NNN.png`; all15 exact rows, cells, raw/alpha/beta,
channel concentration and paired values are in `worst-five-per-family.json`.
Clipped fractions in the largest B/C examples are84.5454% for good296 and35.2355%
for contrast good125. Cohort clipping and individual worst cases must be separated.

## 14. Decision classification

**PHOTOMETRIC_SENSITIVITY_PARTIAL.** Brightness1.20 and contrast1.20 have meaningful
canonical additional tails/spatial exceedances, but severe brightening sensitivity
is shared. Brightness1.10 even has lower canonical median/p95/max absolute scores.
Gamma has small changes and no10Z case. Image-level consistency counts do not
establish uniform or equally large effects. Results are not dominated solely by
a single pathological CAL image: high brightness affects all63 in both models.
No new numerical classification gate or preferred outcome is introduced.

## 15. What is ruled out

Pose-estimator changes cannot explain this experiment: poses are fixed and no
geometry is invoked. Baseline path drift, mismatched models, restored exclusions,
refitting/recalibration, changed M2 math/max rule, gamma direction, contrast
midpoint or altered clipping semantics do not explain the result. The data reject
a universal canonical-only photometric failure and a universal lowest-std/beta
explanation. They do not establish that all appearance sensitivity is absent.

## 16. What remains unresolved

Global photometric factors and saturation are coupled. In brightness1.20 most
channels clip; this can erase input contrast as well as change brightness. This
run does not isolate clipping from nonsaturating appearance changes, prove physical
illumination/reflection causation, or explain every earlier nuisance response.
Local glare, actual reflectance variation, covariance structure and pose-estimator
photometric sensitivity remain untested. Dose severities differ between families.
CAL also estimated Z: this is a controlled diagnostic, not independent normal
validation, production FPR or anomaly-quality evidence. Exact ONNX checkpoint/
export provenance remains unresolved. No production promotion follows.

## 17. Exact next experiment

Recommend one separately authorized **CAL-only clipping-isolation factorial**.
Keep the same63 images, frozen models/poses/B/encoder/scoring and existing1.10/
1.20 brightness/contrast levels. For each original channel, predeclare whether
the existing unrounded formula falls outside[0,255]. Compare four arms: original;
change only those clipping channels to the existing clipped output; change only
the nonclipping channels to their existing transformed output; full existing
transform. The clipping mask is deterministic from the original channel/formula,
not a learned ROI or result-selected threshold. Freeze the protocol and require
baseline parity before scoring. Retain interaction/nonadditivity, raw/Z/max and
origin records. No normalization, correction, refit or TEST access. This tests how
much of the shared response comes from saturation before selecting a correction.
The factorial is recommended only; it was not implemented or run here.

## 18. Tests and provenance

Evidence: `target/experiments/screw-cal-photometric-nuisance-20261020`.
Entry HEAD `4c98b0b4260307f96ea42fe08375fe21f94529a9`, initially clean.
183 source/resource hashes include the previous177 source entries unchanged;
six new test-only Java source files own access, input freeze, photometry,
orchestration, summaries and tests.91 selected input hashes remain stable.
No production, benchmark implementation, configuration or historical output changes.

- Focused92 Java tests pass; relevant full suite246 pass,0 failures/errors/skips.
  The established command excludes only the two expensive legacy Bottle real-data
  integration classes. All other regressions are synthetic/unit operations;
  no real TEST images/maps are opened by this campaign or suite.
- Ten new tests cover all256 channel identities, exact existing formula agreement
  at all15 family/level combinations, clipping/gamma direction, gray logical-RGB
  bridge, unchanged frozen B/pose reuse including uniform input that needs no
  segmentation, forbidden-path rejection before filesystem access, preserved CAL
  exclusion/order, quantiles/ties, paired algebra and baseline phase ordering.
-378 exact baseline plus1512 perturbed evaluations =1890 records.30 condition/model
  directories each retain units[63,18816], raw/Z[63,196] and max[63,1], with sidecars.
  All945 paired rows,378 global-slope rows,1176 cell-sensitivity rows and15 unique
  worst-per-family rows are retained, along with state quartiles and access audits.
- Independent NumPy2.2.5 reconstruction parses the unchanged big-endian bundle
  payloads, then checks all30 condition arrays: max raw absolute error1.953993e-14,
  relative1.321747e-15, max Z error5.684342e-14, unit-norm error1.554312e-15.
  Native maxima agree exactly; raw/Z allclose checks use1e-12 absolute/relative
  numerical validation. Scientific factor1 parity remains bit-exact, not relaxed.
- Source/input/phase guards, complete-diff/scope, whitespace, Markdown links and
  report-structure checks pass. Access audits exclude the forbidden data partition;
  only the63 intended CAL source identities appear, never good179. No TEST
  scientific data appears in the new root; source-code test literals are not data.

`freeze.json`, `baseline-parity.json`, `baseline/perturbed-records.json`,
`response-summary.json`, `paired-records/summary.json`, `family-stability.json`,
`worst-five-per-family.json`, `sensitivity-records/summary.json`,
`cell-sensitivity.json`, `state-sensitivity-relationships.json`,
`winning-state-quartiles.json`, phase hashes, access audits and `completion.json`
retain the result. Execution/independent-validation/table logs are in `logs/`.
No JMH/timing or other category/full-dataset campaign is run.

Reproduction requires the retained CAL references/bundles/bands, dataset and
Maven benchmark test classpath. Use a **new** evidence namespace, never `mvn clean`:

```bash
mvn -B -Pbenchmark '-Dtest=ScrewCalNuisanceTest,ScrewHeadAnalysisTest,ScrewIdentityControlTest,ScrewM2QualificationTest,ScrewFramingTest,GeneralityAuditTest,M2ModelTest,CanonicalM2BundleTest,CanonicalPhase2BridgeTest,LocalizationMapsTest,LocalizationMetricsTest,RobustnessTransformsTest' test dependency:build-classpath '-Dmdep.outputFile=target/comparison-classpath.txt' '-Dmdep.includeScope=test'
mvn -B -Pbenchmark '-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest' test
CP="target/test-classes;target/classes;$(cat target/comparison-classpath.txt)"
RUNNER=io.github.antctrlwin.anomalib4j.evaluation.ScrewCalNuisance
ROOT=target/experiments/screw-cal-photometric-nuisance-20261020
java -Xmx8g -cp "$CP" "$RUNNER" prepare "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" baseline "$ROOT"
# Proceed only after baseline-parity.json and baseline-evidence-hashes.json exist.
java -Xmx8g -cp "$CP" "$RUNNER" run "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" summarize "$ROOT"
java -Xmx8g -cp "$CP" "$RUNNER" verify "$ROOT"
git diff --check
```
