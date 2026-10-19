# Frozen oracle canonicalization protocol

2026-10-19, before measurement. Known rotation/scale followed by its mathematical
inverse, then unchanged Java M2-P. No fitting, calibration, neighborhood search,
quality evaluation, production changes or JMH. Use the same 467 normal test
images and 15 archives as `target/robustness/m2-controlled-20261018`.

Decode -> existing canonical uint8 RGB bicubic224 -> T -> uint8 rounding/clipping
-> inverse T -> uint8 rounding/clipping -> existing ImageNet/encoder/M2-P.
Both warps reuse RobustnessTransforms: center111.5, bilinear sampling, replicated
boundaries, clockwise positive rotation. Rotation levels -20,-10,-5,5,10,20
degrees, inverse -theta. Scale .90,.95,1.05,1.10, inverse 1/s (not 2-s).
Clipped information cannot be reconstructed by the inverse coordinate map.

Retain S0/Sp/Sc, full native maps and corresponding interior maxima (rows/cols
1..12). Dp=Sp-S0, Dc=Sc-S0. Recovery=1-Dc/Dp only for abs(Dp)>0.1 Z,
with valid counts; signed ratios can exceed one or be negative. No clipping.
Report category/level medians Dp/Dc, mean/p90/p95 Dc, median valid recovery,
fractions abs(Dc)<=1,2,5; identical interior diagnostics. NumPy linear quantiles.
Macro means give each category equal weight; texture/object grouping follows
the previous harness. Missing recovery values remain missing, never zero.

Map and RGB round-trip MAE/RMSE/max are computed per image. Native map errors
are compared directly in the recovered frame, without another warp or heatmap
interpolation. Retain perturbed map errors as a recovery denominator/reference.
Aggregate error means and observed maxima, and inspect within-category/level
correlations of RGB RMSE with map RMSE and abs(Dc); correlations are descriptive,
not causal separation of interpolation, boundary loss and CNN receptive fields.

Require bit-exact original/canonical and rotation0/scale1 round-trip maps, exact
S0/Sp/interior pairing against the retained robustness CSV, source/model hashes,
full manifests and finite arrays. New root only:
`target/robustness/m2-oracle-canonicalization-20261019`.

Time inverse warps separately in three fresh JVMs with preloaded Bottle normal
RGB inputs, warmup and interleaved batches; include pixel extraction, allocation,
bilinear warp, rounding and output consumption, exclude CNN/I/O. Report batch
average distributions, not per-call percentiles. Existing M2 scorer measurements
are labeled historical references, not added to warp times. No pose estimator
exists or is timed. Conclude exactly A/B/C under the user's decision rule.
