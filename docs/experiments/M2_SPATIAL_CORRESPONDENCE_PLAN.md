# Frozen M2 correspondence ablation

2026-10-18, before execution. Exactly three modes: P (query cell p compares to
reference p), N1 (minimum over clipped 3x3 references), G (minimum over all 196).
Use existing normalized descriptors and frozen Java M2 mean/effective-std arrays.
Distance is the unchanged channel-mean squared standardized residual. Preserve
test-position indexing; q affects only the reference distribution. Ties choose
the lowest row-major q. No reference reassignment of output cells.

P delegates to the original Java scorer and retains its calibration. N1/G use
separate Welford sample raw-score moments on the same frozen held-out normals,
with the existing 1e-6 floor and positional Z formula. Never use test descriptors
to calibrate; never fit local distributions in this task. Persist each variant's
alpha/beta separately from the unmodified base model archive.

Quality: all 15 frozen test splits and unchanged Java evaluator/interpolation.
Verify P native outputs against retained M2, reuse retained P metrics with source
identity explicit, evaluate N1/G. Report per-category and equal-category macro
Image AUROC, Pixel AUROC and AUPRO@0.30, including fixed five-texture/ten-object
groups. No tuning or extra scoring variants.

Robustness: same 467 normal images, original transform helper and canonical RGB
adapter. Only independent X/Y shifts +/-2,4,8 pixels, rotations +/-5,10,20 degrees,
scales .90,.95,1.05,1.10 (22 levels). Encode each image/level once for all modes.
Compare P pairs against saved robustness pairs. Per-method diagnostic reference
is its held-out calibration maximum native max-Z, strict exceedance; not a
production FPR. Retain original scores, paired full/interior changes, per-category
median/mean/p90/p95, then equal-category means of each statistic. Do not label
the mean of category p95s as a pooled p95. Original exceedance is also retained.

Optional q diagnostics: per-image same-position fraction and mean Euclidean
displacement in grid cells, on quality and normal/perturbed images. Diagnostics
do not influence calibration or scoring.

Approximate scorer timing only: three fresh JVM processes, same eight Bottle
held-out normalized descriptors, two-second warmup per mode, nine interleaved
rounds of 64 maps per mode. Rotate mode order by fork/round. Measure complete raw
map generation including validation, allocation and argmin, excluding Z,
normalization, CNN and I/O. Consume results through a volatile sink. Report
median/mean/min/max of batch-average us/map, not per-call latency percentiles.
No JMH or complete-pipeline claim; no optimization in this ablation.

New root `target/ablation/m2-spatial-correspondence-20261018`; refuse existing
outputs, retain hashes, manifests, model identities, calibration arrays, native
maps, evaluator artifacts, pairs, summaries, progress and operational duration.
Answer the quality/robustness trade-off and choose one subsequent direction from
the user's four choices, without implementing it. No commits or old artifact edits.
