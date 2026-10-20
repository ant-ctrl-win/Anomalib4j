# Frozen Screw head-response diagnostic protocol

2026-10-20. Diagnostic only, using retained identity/canonical models and outputs.
No refit, recalibration, encoder inference, detector/pose/framing/sampler changes,
new scorer, TEST tuning, performance run or commit.

1. Before reading TEST responses/GT, use the253 usable canonical FIT geometry
   records. Take separate medians of silhouette support minX/maxX/minY/maxY.
   Within that fixed rectangle, head is the +x final quarter, neck the preceding
   quarter, thread the first half. Bounds are half-open. These are geometric
   diagnostic bands, not learned anatomical segmentation or an ROI scorer.
2. Native14 cell centers in700 are `(column+.5)*50-.5` and similarly for rows.
   Canonical membership uses those fixed bands. Identity membership transports
   each identity center through the retained image pose into canonical coordinates.
   For local fitted-state comparison, identity cells are weighted by their normal
   FIT band-membership frequency; canonical uses fixed membership. Retain cell
   weights and per-cell statistics, not just pooled summaries.
3. Freeze support, normal-state summaries, manifests, model/source/evidence hashes
   and representative names before TEST analysis. Small resampling set: four
   scratch_head names at sorted index floor(i*(N-1)/3), i=0..3, and four CAL-good
   names by the same rule. Selection uses names only, never GT or responses.
4. Analyze retained native raw/Z/global max for all160 TEST, plus FIT/CAL normal
   distributions. Record first row-major argmax, top5 cells, band maxima, strongest
   head rank and head-global margin. GT-cell overlap is descriptive: forward-map
   original GT pixel centers via half-pixel1024->700 and retained rigid pose,
   then assign the containing50x50 native cell; this is not a CNN receptive field.
5. For each fixed canonical head cell, map its center back to the nearest identity
   native cell. Decompose Zc-Zi exactly into `(rc-ri)/bi`, `-(ac-ai)/bi`, and
   `(rc-ac)*(1/bc-1/bi)`. Pairing is a spatial proxy, not identical receptive
   fields. Retain raw/alpha/beta/Z and components. No hybrid scorer is evaluated.
6. Read retained original1024 maps and exact DatasetNinjaMasks GT. Describe GT
   mean/median/peak, a fixed8-pixel Chebyshev surrounding ring excluding all GT,
   fraction of full-image pixels <= GT peak, global-peak-in-GT, and per-component
   peak percentiles (8-connected, as evaluator). Retain existing group metrics;
   do not select a threshold or invent an operating overlap metric.
7. Resampling uses only the frozen8 representatives: logical native RGB, unchanged
   bicubic700 view, and actual unchanged B identity/canonical224 tensors from
   saved transforms. Verify tensor hashes where retained records contain them
   (identity); report unavailable canonical tensor references explicitly. No geometry
   estimation or CNN inference. Describe intensity contrast/gradient energy inside
   projected GT and fixed diagnostic head area for normals, projected mask area,
   principal-axis geometric width and occupied224/native14 bins. Nearest-neighbor
   mask pullback is diagnostic labeling only; do not change prediction mapping.
8. Pose summaries use retained FIT/CAL/TEST silhouette bounds, directed angle,
   anisotropy and polarity. Head endpoint proxy is (support maxX, vertical midpoint)
   in canonical coordinates; transform back for identity. Report distribution and
   feature-center position, head margin and FIT envelope exceedances. There are
   no paired clean versions of scratched images; association cannot prove that
   the scratch caused a pose change or that endpoint proxy is anatomically exact.
9. Classify mechanisms only after freezing all diagnostics. Preserve negative,
   mixed and unresolved findings. All outputs CREATE_NEW under
   target/experiments/screw-head-response-analysis-20261020; previous evidence is
   read-only. No diagnostic region influences the production max/metrics.
