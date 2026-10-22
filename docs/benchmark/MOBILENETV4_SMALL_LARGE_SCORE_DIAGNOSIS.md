# MobileNetV4 Small / Large: retained-score diagnosis

## 1. Question

Why does Large lose category Image AUROC despite improved localization in
Hazelnut, Toothbrush and Zipper? **Hazelnut is mixed, with a larger FP-like
component; Toothbrush is mixed without a clear dominant side; Zipper is
predominantly FP-like. Bottle's single lost image-ranking pair is a joint
good-up/anomaly-down effect.** These are descriptive findings, without an
operational threshold or a representation-level causal explanation.

This analysis, completed **2026-10-22**, reads only the retained campaign
predictions/maps/metrics. No inference, refitting, encoder execution, scoring
change or benchmark was performed.

## 2. Evidence used

The sole scientific input is
[`target/comparison/m2-small-large-final-20261022-validated`](../../target/comparison/m2-small-large-final-20261022-validated),
under `{SMALL_SPATIAL_14,LARGE_SPATIAL_14}/M2/{category}/`:

- `predictions.csv`: identities, good/anomaly label, defect subtype, image score,
  image SHA-256, geometry and native/evaluated map paths.
- `image-scores.npy`, `native-z.npy` and each `maps/*.native.npy` plus sidecars:
  all **772 predictions / 386 unique TEST images** across the four categories.
- `metrics-unified.json`: retained Image/Pixel/AUPRO and evaluator identity.
  Original-resolution evaluated maps exist, but GT masks are not retained here.
- TEST and CAL manifests are present. CAL per-image raw/Z maps or max-Z scores
  are **absent**. `inputs/<category>/calibration.npy` contains normalized CNN
  descriptors, not scores; alpha/beta are aggregate calibration statistics.

Derived evidence lives separately in
[`target/diagnostics/mobilenetv4-small-large-score-20261022`](../../target/diagnostics/mobilenetv4-small-large-score-20261022):
[analysis script](../../target/diagnostics/mobilenetv4-small-large-score-20261022/analyze.py),
[summary](../../target/diagnostics/mobilenetv4-small-large-score-20261022/summary.json),
[distributions](../../target/diagnostics/mobilenetv4-small-large-score-20261022/distributions.csv),
[all-image rankings](../../target/diagnostics/mobilenetv4-small-large-score-20261022/per-image-ranking.csv),
[all native maxima](../../target/diagnostics/mobilenetv4-small-large-score-20261022/native-max.csv),
[subtypes](../../target/diagnostics/mobilenetv4-small-large-score-20261022/subtypes.csv),
[pair mechanisms](../../target/diagnostics/mobilenetv4-small-large-score-20261022/pair-mechanisms.json)
and input hashes/inventory. These ignored local artifacts require separate
preservation; this repository report retains the principal numerical evidence.

Checks matched Small/Large identity, labels, subtype, original geometry and image
hashes; validated native array hashes/sidecars; checked exact equality of CSV
scores, saved score arrays, and native-map maxima; and reconstructed every
retained Image AUROC with the existing evaluator's convention. The entire input
root's file names/sizes/modification times remained unchanged.

All quantiles below use linear interpolation at `q*(n-1)`, matching the
repository percentile convention. Scores and deltas are native Z, rounded to
three decimals; calculations use full precision. S/L means Small/Large.
Per-image ranks are within their class: descending for good, ascending for
anomaly. The union lists preserve each encoder's complete top/bottom 10,
including images that rank outside Large's 10. `Inv S/L` counts opposite-class
ranking errors contributed by that image. Good-side and anomaly-side counts
describe the same pairs and must not be added together.

## 3. Hazelnut

Image **0.617500 -> 0.462857**; Pixel **0.895854 -> 0.935446**;
AUPRO **0.781311 -> 0.784088**. There are 40 good and 70 anomalous images.

**Mixed; the good-score increase is the larger descriptive component.** Good
p95 rises **11.400 -> 20.239** and median **7.346 -> 8.809**. Anomaly median
falls **7.809 -> 6.509**. There are **621 newly inverted pairs and 188 recovered
pairs**, net **+433**. Symmetric score-substitution bookkeeping assigns
+280 to good scores and +153 to anomaly scores (section 7); this is not causal
attribution or proof that only the most extreme normals matter.

The largest good-side net contributions are `hazelnut_good_023.png`
**6.560 -> 11.563, +38 pairs**, `good_015` **5.067 -> 8.702, +37**, and
`good_012` **4.505 -> 6.954, +33**. These are not all Large's top-10 normals.
The largest anomaly-side contributions are `hazelnut_crack_013.png`
**12.092 -> 3.761, +31**, `print_007` **22.844 -> 5.430, +25**, and
`hole_013` **14.387 -> 5.959, +24**. Filename abbreviations in prose retain
the category prefix and `.png` extension.

| Defect | Anomalies | Inversions S -> L | Median score S -> L |
|---|---:|---:|---:|
| crack | 18 | 238 -> 377 | 8.364 -> 7.129 |
| cut | 17 | 269 -> 295 | 7.388 -> 8.475 |
| hole | 18 | 346 -> 396 | 6.786 -> 6.212 |
| print | 17 | 218 -> 436 | 8.359 -> 5.425 |

Print supplies +218 of the +433 net inversions. Cut still loses ranking despite
a higher anomaly median, illustrating the opposing normal distribution's role.
Improved retained Pixel AUROC establishes better aggregate foreground/background
pixel ranking; nearly stable AUPRO establishes slightly better region-coverage
integral. Neither proves that these influential individual defects retain strong
inside-GT peaks: the necessary masks are absent.

| TEST group | Encoder | n | Min | p10 | p25 | Median | p75 | p90 | p95 | Max | Mean |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| good | S | 40 | 1.886 | 2.960 | 4.439 | 7.346 | 8.915 | 10.635 | 11.400 | 17.640 | 7.032 |
| anomaly | S | 70 | 3.372 | 4.664 | 5.595 | 7.809 | 10.957 | 13.207 | 14.184 | 22.844 | 8.506 |
| good | L | 40 | 2.720 | 3.137 | 4.581 | 8.809 | 14.074 | 17.136 | 20.239 | 22.990 | 9.454 |
| anomaly | L | 70 | 3.509 | 4.132 | 5.125 | 6.509 | 10.381 | 13.789 | 16.816 | 22.431 | 7.993 |

<details>
<summary>Highest-scoring good images: union of the Small and Large top 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| hazelnut_good_001.png | good | 1/1 | 17.640 | 22.990 | +5.350 | 69/70 | +1 |
| hazelnut_good_005.png | good | 10/2 | 9.212 | 20.809 | +11.596 | 45/68 | +23 |
| hazelnut_good_010.png | good | 2/3 | 13.490 | 20.208 | +6.718 | 63/68 | +5 |
| hazelnut_good_007.png | good | 6/4 | 9.683 | 17.919 | +8.235 | 49/67 | +18 |
| hazelnut_good_009.png | good | 14/5 | 8.241 | 17.049 | +8.808 | 38/66 | +28 |
| hazelnut_good_004.png | good | 13/6 | 8.544 | 16.524 | +7.980 | 40/66 | +26 |
| hazelnut_good_013.png | good | 7/7 | 9.605 | 16.008 | +6.403 | 48/66 | +18 |
| hazelnut_good_018.png | good | 3/8 | 11.290 | 15.295 | +4.005 | 55/64 | +9 |
| hazelnut_good_002.png | good | 4/9 | 11.136 | 14.555 | +3.419 | 54/63 | +9 |
| hazelnut_good_003.png | good | 15/10 | 8.069 | 14.336 | +6.267 | 36/63 | +27 |
| hazelnut_good_039.png | good | 5/17 | 10.580 | 10.147 | -0.433 | 51/52 | +1 |
| hazelnut_good_017.png | good | 9/19 | 9.269 | 8.942 | -0.327 | 45/49 | +4 |
| hazelnut_good_025.png | good | 8/24 | 9.496 | 6.781 | -2.716 | 46/37 | -9 |

</details>

<details>
<summary>Lowest-scoring anomalous images: union of the Small and Large bottom 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| hazelnut_cut_013.png | cut | 12/1 | 5.004 | 3.509 | -1.495 | 28/33 | +5 |
| hazelnut_crack_016.png | crack | 15/2 | 5.247 | 3.573 | -1.674 | 27/33 | +6 |
| hazelnut_print_004.png | print | 35/3 | 7.661 | 3.696 | -3.965 | 18/33 | +15 |
| hazelnut_crack_013.png | crack | 60/4 | 12.092 | 3.761 | -8.331 | 2/33 | +31 |
| hazelnut_hole_016.png | hole | 6/5 | 4.314 | 3.899 | -0.415 | 30/33 | +3 |
| hazelnut_crack_012.png | crack | 26/6 | 7.027 | 3.993 | -3.034 | 21/33 | +12 |
| hazelnut_print_014.png | print | 32/7 | 7.492 | 3.999 | -3.494 | 19/33 | +14 |
| hazelnut_print_015.png | print | 16/8 | 5.285 | 4.146 | -1.139 | 27/32 | +5 |
| hazelnut_cut_001.png | cut | 31/9 | 7.388 | 4.195 | -3.192 | 20/32 | +12 |
| hazelnut_print_011.png | print | 39/10 | 8.359 | 4.348 | -4.011 | 13/31 | +18 |
| hazelnut_cut_016.png | cut | 1/12 | 3.372 | 4.434 | +1.063 | 34/30 | -4 |
| hazelnut_hole_004.png | hole | 5/17 | 4.312 | 5.021 | +0.709 | 30/25 | -5 |
| hazelnut_print_003.png | print | 2/22 | 3.887 | 5.236 | +1.349 | 32/25 | -7 |
| hazelnut_crack_004.png | crack | 8/29 | 4.701 | 5.673 | +0.972 | 29/25 | -4 |
| hazelnut_cut_004.png | cut | 3/30 | 3.898 | 5.716 | +1.819 | 32/25 | -7 |
| hazelnut_hole_009.png | hole | 10/31 | 4.777 | 5.728 | +0.951 | 29/25 | -4 |
| hazelnut_cut_010.png | cut | 9/44 | 4.771 | 7.904 | +3.133 | 29/21 | -8 |
| hazelnut_hole_005.png | hole | 7/58 | 4.324 | 11.038 | +6.714 | 30/13 | -17 |
| hazelnut_hole_003.png | hole | 4/59 | 4.172 | 11.155 | +6.983 | 31/13 | -18 |

</details>

## 4. Toothbrush

Image **0.902778 -> 0.863889**; Pixel **0.959277 -> 0.975422**;
AUPRO **0.690294 -> 0.752928**. There are 12 good and 30 defective images.

**Mixed, approximately balanced; no clear dominant side.** Good p75 rises
**16.251 -> 23.792**, while anomaly median falls **59.036 -> 54.625**.
There are **18 new / 4 recovered inversions**, net **+14**; the symmetric
components are +7.5 good / +6.5 anomaly. The good maximum actually decreases
**35.978 -> 32.977**, so a claim that a new, larger extreme normal maximum
alone explains this category would be wrong.

`toothbrush_good_003.png` rises **11.709 -> 22.239**, contributing **+8**
net pairs. `good_007` adds +3 and `good_005` +2.
`toothbrush_defective_020.png` falls **32.273 -> 15.822**,
`defective_021` **39.622 -> 27.543**, and `defective_027`
**30.987 -> 20.643**, each adding **+3** pairs. Their global-maximum cells
are unchanged at (9,12), (4,4) and (13,5), respectively. Decreased maxima coexist
with better aggregate localization metrics; without GT masks their relation
to the actual defect regions cannot be established.

| TEST group | Encoder | n | Min | p10 | p25 | Median | p75 | p90 | p95 | Max | Mean |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| good | S | 12 | 2.226 | 3.419 | 4.243 | 6.537 | 16.251 | 30.489 | 32.997 | 35.978 | 12.570 |
| anomaly | S | 30 | 6.654 | 13.506 | 20.272 | 59.036 | 141.901 | 215.674 | 262.243 | 453.110 | 97.954 |
| good | L | 12 | 3.360 | 3.907 | 4.084 | 7.477 | 23.792 | 31.328 | 32.246 | 32.977 | 13.533 |
| anomaly | L | 30 | 9.663 | 12.289 | 18.002 | 54.625 | 138.643 | 158.175 | 162.381 | 197.718 | 75.231 |

<details>
<summary>Highest-scoring good images: union of the Small and Large top 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| toothbrush_good_005.png | good | 2/1 | 30.557 | 32.977 | +2.420 | 10/12 | +2 |
| toothbrush_good_007.png | good | 3/2 | 29.876 | 31.648 | +1.772 | 9/12 | +3 |
| toothbrush_good_006.png | good | 1/3 | 35.978 | 28.449 | -7.530 | 12/12 | +0 |
| toothbrush_good_003.png | good | 4/4 | 11.709 | 22.239 | +10.530 | 2/10 | +8 |
| toothbrush_good_011.png | good | 5/5 | 10.274 | 11.823 | +1.549 | 1/3 | +2 |
| toothbrush_good_008.png | good | 12/6 | 2.226 | 9.132 | +6.906 | 0/0 | +0 |
| toothbrush_good_009.png | good | 6/7 | 6.665 | 5.822 | -0.842 | 1/0 | -1 |
| toothbrush_good_001.png | good | 11/8 | 3.384 | 4.904 | +1.520 | 0/0 | +0 |
| toothbrush_good_002.png | good | 7/9 | 6.410 | 4.089 | -2.321 | 0/0 | +0 |
| toothbrush_good_004.png | good | 10/10 | 3.732 | 4.068 | +0.337 | 0/0 | +0 |
| toothbrush_good_010.png | good | 9/11 | 4.413 | 3.889 | -0.525 | 0/0 | +0 |
| toothbrush_good_000.png | good | 8/12 | 5.617 | 3.360 | -2.257 | 0/0 | +0 |

</details>

<details>
<summary>Lowest-scoring anomalous images: union of the Small and Large bottom 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| toothbrush_defective_013.png | defective | 2/1 | 10.844 | 9.663 | -1.181 | 4/5 | +1 |
| toothbrush_defective_004.png | defective | 4/2 | 13.685 | 9.974 | -3.712 | 3/5 | +2 |
| toothbrush_defective_001.png | defective | 1/3 | 6.654 | 11.695 | +5.040 | 6/5 | -1 |
| toothbrush_defective_011.png | defective | 3/4 | 11.890 | 12.355 | +0.466 | 3/4 | +1 |
| toothbrush_defective_006.png | defective | 5/5 | 15.853 | 15.287 | -0.566 | 3/4 | +1 |
| toothbrush_defective_028.png | defective | 10/6 | 30.501 | 15.347 | -15.154 | 2/4 | +2 |
| toothbrush_defective_020.png | defective | 12/7 | 32.273 | 15.822 | -16.451 | 1/4 | +3 |
| toothbrush_defective_024.png | defective | 6/8 | 16.711 | 17.892 | +1.181 | 3/4 | +1 |
| toothbrush_defective_003.png | defective | 7/9 | 17.036 | 18.330 | +1.294 | 3/4 | +1 |
| toothbrush_defective_027.png | defective | 11/10 | 30.987 | 20.643 | -10.344 | 1/4 | +3 |
| toothbrush_defective_018.png | defective | 8/11 | 19.470 | 23.517 | +4.047 | 3/3 | +0 |
| toothbrush_defective_007.png | defective | 9/14 | 22.678 | 37.656 | +14.978 | 3/0 | -3 |

</details>

## 5. Zipper

Image **0.959821 -> 0.922532**; Pixel **0.960991 -> 0.967632**;
AUPRO **0.882107 -> 0.899092**. There are 32 good and 119 anomalous images.

**Predominantly FP-like, with individual FN-like exceptions.** Good p95 rises
**8.582 -> 23.781**, while anomaly median improves **16.758 -> 22.264**.
There are **260 new / 118 recovered inversions**, net **+142**. In 188/260
new errors, changing only the good score is sufficient; changing only the
anomaly score is sufficient in just 6/260 exclusive cases. The symmetric
components are +237 good / -95 anomaly: the anomaly population partly offsets
the degradation on the retained Z scale.

`zipper_good_022.png` **8.911 -> 30.514** adds **+70** pairs and
`good_023` **5.922 -> 27.159** adds **+68**. Together these two rows account
for **+138 of the +142 net change**; other normals have offsetting changes.
`good_021` **7.863 -> 21.017** adds another +43.

Individual anomaly losses still matter: `zipper_fabric_interior_007.png`
**11.374 -> 5.917, +12 pairs**, `squeezed_teeth_012`
**15.677 -> 6.091, +11**, and `squeezed_teeth_013`
**17.686 -> 8.537, +7**. Improved population ranking does not imply every
defect image improves. The native peaks of these exceptions also weaken;
inside/outside-GT attribution is unavailable.

| TEST group | Encoder | n | Min | p10 | p25 | Median | p75 | p90 | p95 | Max | Mean |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| good | S | 32 | 2.267 | 2.859 | 3.643 | 4.946 | 6.459 | 7.815 | 8.582 | 15.912 | 5.339 |
| anomaly | S | 119 | 5.128 | 8.553 | 11.200 | 16.758 | 30.117 | 44.738 | 50.013 | 64.972 | 22.111 |
| good | L | 32 | 1.810 | 2.767 | 4.196 | 5.522 | 6.978 | 11.526 | 23.781 | 30.514 | 7.509 |
| anomaly | L | 119 | 5.000 | 10.130 | 13.383 | 22.264 | 32.523 | 42.954 | 51.443 | 64.095 | 24.857 |

<details>
<summary>Highest-scoring good images: union of the Small and Large top 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| zipper_good_022.png | good | 2/1 | 8.911 | 30.514 | +21.603 | 15/85 | +70 |
| zipper_good_023.png | good | 10/2 | 5.922 | 27.159 | +21.238 | 6/74 | +68 |
| zipper_good_021.png | good | 4/3 | 7.863 | 21.017 | +13.154 | 10/53 | +43 |
| zipper_good_008.png | good | 32/4 | 2.267 | 11.574 | +9.307 | 0/18 | +18 |
| zipper_good_012.png | good | 23/5 | 3.745 | 11.091 | +7.346 | 0/16 | +16 |
| zipper_good_015.png | good | 20/6 | 4.482 | 10.844 | +6.362 | 0/15 | +15 |
| zipper_good_013.png | good | 18/7 | 4.786 | 9.046 | +4.261 | 0/9 | +9 |
| zipper_good_027.png | good | 6/8 | 7.315 | 7.339 | +0.024 | 10/5 | -5 |
| zipper_good_006.png | good | 28/9 | 2.887 | 6.858 | +3.971 | 0/3 | +3 |
| zipper_good_004.png | good | 8/10 | 6.612 | 6.840 | +0.228 | 7/3 | -4 |
| zipper_good_026.png | good | 1/13 | 15.912 | 6.037 | -9.875 | 56/2 | -54 |
| zipper_good_011.png | good | 3/21 | 8.313 | 4.730 | -3.582 | 12/0 | -12 |
| zipper_good_018.png | good | 7/25 | 7.075 | 4.143 | -2.932 | 9/0 | -9 |
| zipper_good_030.png | good | 9/27 | 6.408 | 3.544 | -2.864 | 6/0 | -6 |
| zipper_good_000.png | good | 5/28 | 7.386 | 2.913 | -4.473 | 10/0 | -10 |

</details>

<details>
<summary>Lowest-scoring anomalous images: union of the Small and Large bottom 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| zipper_squeezed_teeth_008.png | squeezed_teeth | 2/1 | 5.213 | 5.000 | -0.213 | 13/19 | +6 |
| zipper_fabric_interior_007.png | fabric_interior | 31/2 | 11.374 | 5.917 | -5.457 | 1/13 | +12 |
| zipper_squeezed_teeth_012.png | squeezed_teeth | 54/3 | 15.677 | 6.091 | -9.586 | 1/12 | +11 |
| zipper_squeezed_teeth_005.png | squeezed_teeth | 15/4 | 8.816 | 7.024 | -1.792 | 2/8 | +6 |
| zipper_rough_008.png | rough | 11/5 | 8.225 | 7.071 | -1.154 | 3/8 | +5 |
| zipper_split_teeth_005.png | split_teeth | 18/6 | 9.298 | 7.671 | -1.627 | 1/7 | +6 |
| zipper_squeezed_teeth_006.png | squeezed_teeth | 37/7 | 12.017 | 8.000 | -4.017 | 1/7 | +6 |
| zipper_squeezed_teeth_013.png | squeezed_teeth | 64/8 | 17.686 | 8.537 | -9.150 | 0/7 | +7 |
| zipper_squeezed_teeth_007.png | squeezed_teeth | 16/9 | 8.930 | 8.903 | -0.027 | 1/7 | +6 |
| zipper_split_teeth_008.png | split_teeth | 20/10 | 9.538 | 9.228 | -0.310 | 1/6 | +5 |
| zipper_broken_teeth_002.png | broken_teeth | 4/12 | 5.303 | 10.029 | +4.725 | 12/6 | -6 |
| zipper_fabric_interior_002.png | fabric_interior | 10/15 | 7.129 | 10.530 | +3.402 | 6/6 | +0 |
| zipper_split_teeth_001.png | split_teeth | 5/16 | 5.476 | 11.056 | +5.581 | 10/5 | -5 |
| zipper_split_teeth_000.png | split_teeth | 9/19 | 7.072 | 11.662 | +4.591 | 7/3 | -4 |
| zipper_broken_teeth_015.png | broken_teeth | 7/20 | 6.601 | 11.727 | +5.125 | 8/3 | -5 |
| zipper_fabric_interior_001.png | fabric_interior | 6/30 | 5.796 | 13.350 | +7.554 | 10/3 | -7 |
| zipper_squeezed_teeth_009.png | squeezed_teeth | 8/35 | 7.022 | 14.777 | +7.755 | 7/3 | -4 |
| zipper_fabric_interior_012.png | fabric_interior | 1/42 | 5.128 | 18.085 | +12.957 | 14/3 | -11 |
| zipper_broken_teeth_009.png | broken_teeth | 3/55 | 5.279 | 21.202 | +15.923 | 13/2 | -11 |

</details>

## 6. Bottle control

Image **1.000000 -> 0.999206**; Pixel **0.975697 -> 0.966159**;
AUPRO **0.915738 -> 0.882232**. There are 20 good and 63 anomalous images.

**Mixed for the actual lost ranking pair.** Normal median rises
**5.671 -> 6.277** and p95 **12.077 -> 16.931**; anomaly median falls
**97.551 -> 64.277**. Separation weakens, but remains sufficient for all
except **one of 1,260 pairs**:

| Image | Small | Large | Delta |
|---|---:|---:|---:|
| bottle_good_003.png | 6.680 | 20.410 | +13.730 |
| bottle_contamination_012.png | 33.242 | 17.843 | -15.399 |

For this same pair, neither the good-only substitution (20.410 < 33.242)
nor the anomaly-only substitution (6.680 < 17.843) inverts Small's order;
**both changes together do**. This cannot be described solely as a higher
normal peak or solely as a lower anomalous score.

The contamination image's global maximum remains at **(8,8)**. Its above-10
cell count falls **24 -> 11**, and its above-10 peak component **24 -> 10**.
The good image moves from **(1,3) to (1,4)**, with above-10 count **0 -> 2**.
This is an interacting pair with a compact multi-cell normal response, not a
new single-cell normal spike by itself.

Bottle differs from the other three categories because both retained
localization metrics also deteriorate. Its native anomaly-score attenuation
is consistent with weaker response, but no retained GT permits testing whether
inside-GT response itself weakens, background grows, or both. The pixel/region
metric losses cannot be attributed exclusively to this one image-ranking pair.

| TEST group | Encoder | n | Min | p10 | p25 | Median | p75 | p90 | p95 | Max | Mean |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| good | S | 20 | 2.469 | 3.520 | 4.540 | 5.671 | 7.560 | 11.136 | 12.077 | 16.665 | 6.663 |
| anomaly | S | 63 | 17.694 | 41.475 | 63.198 | 97.551 | 134.933 | 157.525 | 225.648 | 2283.544 | 152.881 |
| good | L | 20 | 2.915 | 3.690 | 4.684 | 6.277 | 8.408 | 14.469 | 16.931 | 20.410 | 7.759 |
| anomaly | L | 63 | 17.843 | 34.946 | 44.313 | 64.277 | 81.531 | 97.347 | 235.061 | 2314.401 | 120.363 |

<details>
<summary>Highest-scoring good images: union of the Small and Large top 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| bottle_good_003.png | good | 9/1 | 6.680 | 20.410 | +13.730 | 0/1 | +1 |
| bottle_good_017.png | good | 2/2 | 11.836 | 16.748 | +4.912 | 0/0 | +0 |
| bottle_good_007.png | good | 7/3 | 6.946 | 14.216 | +7.270 | 0/0 | +0 |
| bottle_good_016.png | good | 8/4 | 6.880 | 12.536 | +5.656 | 0/0 | +0 |
| bottle_good_006.png | good | 3/5 | 11.058 | 10.257 | -0.801 | 0/0 | +0 |
| bottle_good_019.png | good | 11/6 | 5.409 | 7.791 | +2.383 | 0/0 | +0 |
| bottle_good_018.png | good | 4/7 | 9.424 | 7.223 | -2.201 | 0/0 | +0 |
| bottle_good_002.png | good | 10/8 | 5.933 | 6.791 | +0.857 | 0/0 | +0 |
| bottle_good_000.png | good | 6/9 | 7.113 | 6.768 | -0.345 | 0/0 | +0 |
| bottle_good_010.png | good | 1/10 | 16.665 | 6.496 | -10.168 | 0/0 | +0 |
| bottle_good_012.png | good | 5/13 | 8.901 | 5.472 | -3.429 | 0/0 | +0 |

</details>

<details>
<summary>Lowest-scoring anomalous images: union of the Small and Large bottom 10</summary>

| Image | Defect | Rank S/L | S score | L score | Delta | Inv S/L | Delta inv |
|---|---|---:|---:|---:|---:|---:|---:|
| bottle_contamination_012.png | contamination | 3/1 | 33.242 | 17.843 | -15.399 | 0/1 | +1 |
| bottle_contamination_016.png | contamination | 4/2 | 33.726 | 23.684 | -10.042 | 0/0 | +0 |
| bottle_contamination_009.png | contamination | 5/3 | 36.229 | 25.872 | -10.357 | 0/0 | +0 |
| bottle_contamination_010.png | contamination | 8/4 | 41.806 | 27.284 | -14.522 | 0/0 | +0 |
| bottle_broken_small_015.png | broken_small | 10/5 | 44.980 | 30.811 | -14.169 | 0/0 | +0 |
| bottle_contamination_020.png | contamination | 12/6 | 48.291 | 31.467 | -16.824 | 0/0 | +0 |
| bottle_contamination_001.png | contamination | 9/7 | 43.989 | 34.826 | -9.163 | 0/0 | +0 |
| bottle_contamination_019.png | contamination | 1/8 | 17.694 | 35.430 | +17.736 | 0/0 | +0 |
| bottle_broken_small_000.png | broken_small | 40/9 | 111.608 | 35.822 | -75.786 | 0/0 | +0 |
| bottle_contamination_000.png | contamination | 25/10 | 77.842 | 35.973 | -41.869 | 0/0 | +0 |
| bottle_contamination_003.png | contamination | 6/11 | 37.713 | 37.071 | -0.642 | 0/0 | +0 |
| bottle_contamination_004.png | contamination | 2/12 | 31.597 | 37.394 | +5.798 | 0/0 | +0 |
| bottle_contamination_015.png | contamination | 7/13 | 41.393 | 38.707 | -2.686 | 0/0 | +0 |

</details>

## 7. FP-like / FN-like summary

The existing evaluator awards 1 when anomaly > good and 0.5 for a tie. Thus
inversion weight is `good > anomaly` plus half the ties. **No ties occur** here,
so `good >= anomaly` gives exactly the same counts.
`Image AUROC = 1 - inversions / possible pairs`.

| Category | Good x anomaly pairs | S inversions / fraction | L inversions / fraction | Delta count | Delta fraction | New / recovered |
|---|---:|---:|---:|---:|---:|---:|
| hazelnut | 2,800 | 1,071 / 0.382500 | 1,504 / 0.537143 | +433 | +0.154643 | 621 / 188 |
| toothbrush | 360 | 35 / 0.097222 | 49 / 0.136111 | +14 | +0.038889 | 18 / 4 |
| zipper | 3,808 | 153 / 0.040179 | 295 / 0.077468 | +142 | +0.037290 | 260 / 118 |
| bottle | 1,260 | 0 / 0 | 1 / 0.000794 | +1 | +0.000794 | 1 / 0 |

For descriptive substitution only, let I(g,a) count inversions with good scores
from g and anomaly scores from a. SS and LL are the real retained systems;
LS/SL combine already saved scalar scores for bookkeeping. The symmetric
good component is `[(I_LS-I_SS)+(I_LL-I_SL)]/2`; the anomaly component is
the complementary symmetric expression. They sum to the observed net change.

| Category | I_LS | I_SL | Good component | Anomaly component | Classification |
|---|---:|---:|---:|---:|---|
| hazelnut | 1,409 | 1,282 | +280 | +153 | Mixed, larger FP-like component |
| toothbrush | 43 | 42 | +7.5 | +6.5 | Mixed, no clear dominance |
| zipper | 421 | 89 | +237 | -95 | FP-like dominant; anomaly population offsets |
| bottle | 1 | 0 | +1 | 0 | Joint mixed actual pair; net components conceal pair replacement |

These components are **scale-dependent bookkeeping, not causal shares**:
encoder-specific Z calibrations are distinct, and independent monotone rescaling
can change mixed-system comparisons without changing either real AUROC.
Bottle demonstrates why a zero net anomaly component does not mean anomalous
images played no role. Its good-only error is against
`bottle_contamination_019.png`, whereas the actual Large error is against
`bottle_contamination_012.png`; pair identities must be inspected.

For each **newly inverted** pair, the following table distinguishes which
substitutions suffice. “Either” means either population change alone suffices;
“joint” means neither alone suffices. Categories are mutually exclusive.

| Category | Good only | Anomaly only | Either alone | Joint | Total new |
|---|---:|---:|---:|---:|---:|
| hazelnut | 171 | 147 | 204 | 99 | 621 |
| toothbrush | 4 | 5 | 6 | 3 | 18 |
| zipper | 188 | 6 | 15 | 51 | 260 |
| bottle | 0 | 0 | 0 | 1 | 1 |

This is a ranking analysis without an operational FP/FN threshold. No mixed
score population is deployed or substituted for the unchanged native-max score.

## 8. CAL-good vs TEST-good comparison

| Category | CAL normals per encoder | TEST good | CAL max-Z median/p90/p95/max | TEST S median/p90/p95/max | TEST L median/p90/p95/max |
|---|---:|---:|---|---|---|
| hazelnut | 79 | 40 | Unavailable | 7.346 / 10.635 / 11.400 / 17.640 | 8.809 / 17.136 / 20.239 / 22.990 |
| toothbrush | 12 | 12 | Unavailable | 6.537 / 30.489 / 32.997 / 35.978 | 7.477 / 31.328 / 32.246 / 32.977 |
| zipper | 48 | 32 | Unavailable | 4.946 / 7.815 / 8.582 / 15.912 | 5.522 / 11.526 / 23.781 / 30.514 |
| bottle | 42 | 20 | Unavailable | 5.671 / 11.136 / 12.077 / 16.665 | 6.277 / 14.469 / 16.931 / 20.410 |

**A stronger CAL-to-TEST normal-tail shift for Large is not established.**
CAL descriptors and aggregate alpha/beta cannot identify the distribution of
per-image CAL maxima. Computing new CAL scores would violate this task's
no-inference scope, so no missing values are reconstructed or assumed.

## 9. Native-max behavior

Every retained native map was checked: its maximum equals its saved image score.
The [native-max CSV](../../target/diagnostics/mobilenetv4-small-large-score-20261022/native-max.csv)
contains max, second-highest value, max-minus-second gap, second/max ratio,
argmax cell, counts Z>5/Z>10, and eight-connected component size around the peak,
for all 772 predictions. Coordinates are zero-based (y,x) on the 14x14 grid;
ties would use the first row-major maximum.

N5/N10 below are total cell counts above 5/10; C5/C10 are the corresponding
eight-connected component sizes containing the maximum. These are descriptive
levels explicitly requested for this diagnosis, **not decision thresholds**.

| Category | Good images | Good max>10 S -> L | Single-cell peak component >10 S -> L | Median C5 of highest 10 good S -> L |
|---|---:|---:|---:|---:|
| hazelnut | 40 | 5 -> 17 | 5 -> 17 | 1.5 -> 2.0 |
| toothbrush | 12 | 5 -> 5 | 2 -> 1 | 1.0 -> 1.5 |
| zipper | 32 | 1 -> 6 | 1 -> 5 | 1.0 -> 1.0 |
| bottle | 20 | 3 -> 5 | 3 -> 4 | 1.5 -> 2.0 |

**Hazelnut:** the three highest Large normals peak at (0,8), (0,9), (0,8),
the same cells as Small. Max/second values are **22.990/7.943**,
**20.809/6.586**, **20.208/7.162**: each has a single-cell >10 component,
within a small 2-4-cell >5 peak component. High normal peaks become more frequent,
not rarer. The median peak gap over all normals actually falls
**1.826 -> 1.175** and C5 of the highest ten grows: stronger high-tail maxima do
not establish universally increased spatial isolation.

**Zipper:** good_022 has max/second **30.514/8.827** at (12,3), C5=3,
C10=1; good_023 **27.159/5.707** at (9,10), C5=C10=1. This directly supports
large concentrated normal maxima for the two main contributors.
Good_021 has **21.017/19.869**, C10=2: the issue is not uniformly single-cell.

**Toothbrush:** the highest normal has C10 **4 -> 3**; high normal peaks are
small clusters, and the largest normal maximum decreases. There is no evidence
of a generic increase in isolated normal spikes.
**Bottle:** the sole influential normal has C10=2 and C5=4. This particular
regression is also not a single-cell-only phenomenon.

<details>
<summary>Paired native peaks for all top-good images listed in sections 3-6</summary>

<details>
<summary>hazelnut: paired peaks for the union of both highest-good top 10 lists</summary>

| Good image | Max S -> L | Cell S -> L | Second S -> L | N5/N10 S -> L | C5/C10 S -> L |
|---|---:|---|---:|---|---|
| hazelnut_good_001.png | 17.640 -> 22.990 | (0,8) -> (0,8) | 5.252 -> 7.943 | 2/1 -> 4/1 | 2/1 -> 4/1 |
| hazelnut_good_005.png | 9.212 -> 20.809 | (0,9) -> (0,9) | 5.832 -> 6.586 | 3/0 -> 2/1 | 1/0 -> 2/1 |
| hazelnut_good_010.png | 13.490 -> 20.208 | (0,8) -> (0,8) | 5.109 -> 7.162 | 2/1 -> 4/1 | 2/1 -> 3/1 |
| hazelnut_good_007.png | 9.683 -> 17.919 | (0,8) -> (0,8) | 2.953 -> 5.766 | 1/0 -> 2/1 | 1/0 -> 2/1 |
| hazelnut_good_009.png | 8.241 -> 17.049 | (0,7) -> (0,7) | 6.703 -> 6.621 | 3/0 -> 3/1 | 1/0 -> 1/1 |
| hazelnut_good_004.png | 8.544 -> 16.524 | (0,8) -> (0,8) | 5.675 -> 6.845 | 2/0 -> 3/1 | 2/0 -> 2/1 |
| hazelnut_good_013.png | 9.605 -> 16.008 | (0,8) -> (0,8) | 2.949 -> 8.337 | 1/0 -> 2/1 | 1/0 -> 2/1 |
| hazelnut_good_018.png | 11.290 -> 15.295 | (0,8) -> (0,8) | 6.808 -> 3.804 | 2/1 -> 1/1 | 1/1 -> 1/1 |
| hazelnut_good_002.png | 11.136 -> 14.555 | (0,9) -> (0,9) | 6.424 -> 7.897 | 3/1 -> 2/1 | 3/1 -> 2/1 |
| hazelnut_good_003.png | 8.069 -> 14.336 | (0,8) -> (0,8) | 5.432 -> 5.338 | 2/0 -> 2/1 | 2/0 -> 2/1 |
| hazelnut_good_039.png | 10.580 -> 10.147 | (8,13) -> (8,12) | 8.019 -> 6.223 | 5/1 -> 7/1 | 3/1 -> 5/1 |
| hazelnut_good_017.png | 9.269 -> 8.942 | (0,4) -> (0,4) | 8.481 -> 3.197 | 2/0 -> 1/0 | 1/0 -> 1/0 |
| hazelnut_good_025.png | 9.496 -> 6.781 | (10,3) -> (10,2) | 6.202 -> 6.414 | 4/0 -> 3/0 | 3/0 -> 3/0 |

</details>

<details>
<summary>toothbrush: paired peaks for the union of both highest-good top 10 lists</summary>

| Good image | Max S -> L | Cell S -> L | Second S -> L | N5/N10 S -> L | C5/C10 S -> L |
|---|---:|---|---:|---|---|
| toothbrush_good_005.png | 30.557 -> 32.977 | (13,10) -> (13,10) | 19.009 -> 12.218 | 4/4 -> 3/3 | 4/4 -> 3/3 |
| toothbrush_good_007.png | 29.876 -> 31.648 | (13,10) -> (13,9) | 27.890 -> 29.551 | 8/4 -> 10/3 | 5/4 -> 4/3 |
| toothbrush_good_006.png | 35.978 -> 28.449 | (13,10) -> (13,10) | 21.977 -> 19.859 | 5/5 -> 8/4 | 4/4 -> 5/3 |
| toothbrush_good_003.png | 11.709 -> 22.239 | (5,13) -> (5,13) | 9.401 -> 20.173 | 6/1 -> 8/4 | 4/1 -> 5/3 |
| toothbrush_good_011.png | 10.274 -> 11.823 | (2,0) -> (2,0) | 6.358 -> 6.853 | 3/1 -> 3/1 | 1/1 -> 2/1 |
| toothbrush_good_008.png | 2.226 -> 9.132 | (10,12) -> (9,4) | 2.207 -> 2.709 | 0/0 -> 1/0 | 0/0 -> 1/0 |
| toothbrush_good_009.png | 6.665 -> 5.822 | (3,11) -> (13,1) | 6.167 -> 4.510 | 2/0 -> 1/0 | 1/0 -> 1/0 |
| toothbrush_good_001.png | 3.384 -> 4.904 | (11,5) -> (13,10) | 3.261 -> 4.641 | 0/0 -> 0/0 | 0/0 -> 0/0 |
| toothbrush_good_002.png | 6.410 -> 4.089 | (10,3) -> (11,1) | 1.978 -> 3.267 | 1/0 -> 0/0 | 1/0 -> 0/0 |
| toothbrush_good_004.png | 3.732 -> 4.068 | (12,9) -> (13,6) | 3.251 -> 3.681 | 0/0 -> 0/0 | 0/0 -> 0/0 |
| toothbrush_good_010.png | 4.413 -> 3.889 | (0,6) -> (7,11) | 2.684 -> 3.375 | 0/0 -> 0/0 | 0/0 -> 0/0 |
| toothbrush_good_000.png | 5.617 -> 3.360 | (13,9) -> (5,11) | 3.429 -> 2.948 | 1/0 -> 0/0 | 1/0 -> 0/0 |

</details>

<details>
<summary>zipper: paired peaks for the union of both highest-good top 10 lists</summary>

| Good image | Max S -> L | Cell S -> L | Second S -> L | N5/N10 S -> L | C5/C10 S -> L |
|---|---:|---|---:|---|---|
| zipper_good_022.png | 8.911 -> 30.514 | (12,3) -> (12,3) | 4.264 -> 8.827 | 1/0 -> 4/1 | 1/0 -> 3/1 |
| zipper_good_023.png | 5.922 -> 27.159 | (9,10) -> (9,10) | 4.622 -> 5.707 | 1/0 -> 3/1 | 1/0 -> 1/1 |
| zipper_good_021.png | 7.863 -> 21.017 | (13,3) -> (13,3) | 7.033 -> 19.869 | 4/0 -> 5/2 | 3/0 -> 4/2 |
| zipper_good_008.png | 2.267 -> 11.574 | (3,1) -> (1,13) | 2.108 -> 8.840 | 0/0 -> 5/1 | 0/0 -> 5/1 |
| zipper_good_012.png | 3.745 -> 11.091 | (9,9) -> (11,4) | 3.322 -> 4.167 | 0/0 -> 1/1 | 0/0 -> 1/1 |
| zipper_good_015.png | 4.482 -> 10.844 | (4,4) -> (4,4) | 3.277 -> 5.474 | 0/0 -> 3/1 | 0/0 -> 1/1 |
| zipper_good_013.png | 4.786 -> 9.046 | (6,10) -> (8,4) | 3.214 -> 4.157 | 0/0 -> 1/0 | 0/0 -> 1/0 |
| zipper_good_027.png | 7.315 -> 7.339 | (10,2) -> (12,2) | 7.077 -> 6.898 | 6/0 -> 6/0 | 4/0 -> 6/0 |
| zipper_good_006.png | 2.887 -> 6.858 | (13,5) -> (13,5) | 2.435 -> 3.505 | 0/0 -> 1/0 | 0/0 -> 1/0 |
| zipper_good_004.png | 6.612 -> 6.840 | (13,13) -> (13,11) | 4.467 -> 5.694 | 1/0 -> 3/0 | 1/0 -> 1/0 |
| zipper_good_026.png | 15.912 -> 6.037 | (7,4) -> (12,7) | 3.943 -> 4.703 | 1/1 -> 1/0 | 1/1 -> 1/0 |
| zipper_good_011.png | 8.313 -> 4.730 | (0,12) -> (0,11) | 4.259 -> 3.034 | 1/0 -> 0/0 | 1/0 -> 0/0 |
| zipper_good_018.png | 7.075 -> 4.143 | (8,10) -> (6,5) | 5.981 -> 3.500 | 3/0 -> 0/0 | 1/0 -> 0/0 |
| zipper_good_030.png | 6.408 -> 3.544 | (5,1) -> (2,3) | 4.621 -> 2.790 | 1/0 -> 0/0 | 1/0 -> 0/0 |
| zipper_good_000.png | 7.386 -> 2.913 | (1,11) -> (1,7) | 5.598 -> 2.829 | 3/0 -> 0/0 | 2/0 -> 0/0 |

</details>

<details>
<summary>bottle: paired peaks for the union of both highest-good top 10 lists</summary>

| Good image | Max S -> L | Cell S -> L | Second S -> L | N5/N10 S -> L | C5/C10 S -> L |
|---|---:|---|---:|---|---|
| bottle_good_003.png | 6.680 -> 20.410 | (1,3) -> (1,4) | 4.388 -> 14.628 | 1/0 -> 6/2 | 1/0 -> 4/2 |
| bottle_good_017.png | 11.836 -> 16.748 | (5,13) -> (5,13) | 3.893 -> 6.451 | 1/1 -> 2/1 | 1/1 -> 1/1 |
| bottle_good_007.png | 6.946 -> 14.216 | (10,10) -> (4,3) | 6.259 -> 7.499 | 5/0 -> 3/1 | 3/0 -> 1/1 |
| bottle_good_016.png | 6.880 -> 12.536 | (10,9) -> (1,10) | 4.620 -> 6.454 | 1/0 -> 3/1 | 1/0 -> 1/1 |
| bottle_good_006.png | 11.058 -> 10.257 | (6,6) -> (7,5) | 9.833 -> 7.701 | 6/1 -> 3/1 | 5/1 -> 3/1 |
| bottle_good_019.png | 5.409 -> 7.791 | (10,10) -> (2,3) | 5.010 -> 6.086 | 2/0 -> 2/0 | 1/0 -> 2/0 |
| bottle_good_018.png | 9.424 -> 7.223 | (8,6) -> (8,6) | 5.572 -> 6.287 | 3/0 -> 3/0 | 3/0 -> 2/0 |
| bottle_good_002.png | 5.933 -> 6.791 | (8,11) -> (8,11) | 3.868 -> 4.829 | 1/0 -> 1/0 | 1/0 -> 1/0 |
| bottle_good_000.png | 7.113 -> 6.768 | (7,1) -> (6,1) | 6.478 -> 6.286 | 6/0 -> 5/0 | 3/0 -> 3/0 |
| bottle_good_010.png | 16.665 -> 6.496 | (11,6) -> (4,2) | 5.550 -> 5.830 | 7/1 -> 4/0 | 2/1 -> 2/0 |
| bottle_good_012.png | 8.901 -> 5.472 | (5,12) -> (13,12) | 4.700 -> 5.223 | 1/0 -> 3/0 | 1/0 -> 2/0 |

</details>

</details>

For influential anomalous images, the saved global maximum changes as follows.
These are map coordinates only; **GT membership is unavailable**.

| Image | Max S -> L | Argmax S -> L | Max inside GT | Max outside GT |
|---|---:|---|---|---|
| hazelnut_crack_013.png | 12.092 -> 3.761 | (6,8) -> (5,7) | Unavailable | Unavailable |
| hazelnut_print_007.png | 22.844 -> 5.430 | (7,7) -> (13,9) | Unavailable | Unavailable |
| hazelnut_hole_013.png | 14.387 -> 5.959 | (4,7) -> (4,7) | Unavailable | Unavailable |
| toothbrush_defective_020.png | 32.273 -> 15.822 | (9,12) -> (9,12) | Unavailable | Unavailable |
| toothbrush_defective_021.png | 39.622 -> 27.543 | (4,4) -> (4,4) | Unavailable | Unavailable |
| toothbrush_defective_027.png | 30.987 -> 20.643 | (13,5) -> (13,5) | Unavailable | Unavailable |
| zipper_fabric_interior_007.png | 11.374 -> 5.917 | (4,8) -> (5,11) | Unavailable | Unavailable |
| zipper_squeezed_teeth_012.png | 15.677 -> 6.091 | (11,7) -> (11,7) | Unavailable | Unavailable |
| zipper_squeezed_teeth_013.png | 17.686 -> 8.537 | (8,7) -> (3,8) | Unavailable | Unavailable |
| bottle_contamination_012.png | 33.242 -> 17.843 | (8,8) -> (8,8) | Unavailable | Unavailable |

## 10. Engineering interpretation

The strongest supported explanation is a change in **class separation of native
max-Z scores**, with category-specific contributions. Hazelnut combines elevated
normal tails with weaker maxima on influential defects, especially print.
Toothbrush combines moderate normal increases with defect-score attenuation.
Zipper loses chiefly through a few elevated normal peaks despite a stronger
anomaly population. Bottle's tiny image loss requires joint changes in one
specific pair and accompanies broader retained localization-metric losses.

Pixel AUROC and AUPRO integrate pixel/region ranking; image AUROC compares only
one maximum per image. Better aggregate localization therefore coexists with
worse image ranking. This is demonstrated by the retained metrics and exact
pair counts, without changing the score aggregation.

The data do **not** establish that an influential anomalous image's maximum is
outside its defect, that Large preserves a strong inside-defect response in that
image, or that a nuisance elsewhere causes its global maximum. Those require
GT-aligned evidence absent from the authorized root. Normal-image peaks are
observable non-defect responses, but their visual/physical origin is unknown.

## 11. Limits

This diagnostic establishes what changed in retained score behavior. It does
not identify channel covariance, channel count, overfitting, illumination,
reflection, misalignment or any other representation mechanism as the cause.
Those remain unsupported hypotheses, not conclusions.

CAL-to-TEST distribution shift and per-image inside/outside-GT maxima are
unavailable. No external dataset images or masks were fetched. Native-cell
coordinates alone do not identify object parts or physical nuisances.
The Z>5/10 summaries do not imply operating FPRs. There is one frozen split and
no repeated-run uncertainty estimate. Image AUROC reconstruction checks existing
metric semantics; Pixel/AUPRO are retained values, not newly recomputed metrics.

The scientific campaign artifacts, model/source files, README and previous
numerical results are preserved. Only this report and separate derived
diagnostic artifacts were added; no branch or commit was created.

