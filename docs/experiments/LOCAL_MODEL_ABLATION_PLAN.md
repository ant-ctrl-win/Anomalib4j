# First local-model quality ablation

Frozen definitions: 2026-10-17. Status updated 2026-10-18: the
15-category C1/V1/M2 experiment completed in `target/ablation/local-models-20261017`;
M2 was selected (see [current state](../PROJECT_STATE.md)). M0 is the unchanged,
already-measured Java reference in `target/comparison/m0-all15-baseline-20261016`.
Its metrics are consumed, never retrained by this runner. Existing M1/M2/M3
historical experiment names and artifacts remain untouched.

## Common contract

Same MobileNetV4 ONNX bytes, RGB/ImageNet bicubic preprocessing, 14x14x96 HWC
features and ordered fit/calibration/test manifests as the retained M0 campaign.
Extract features once for each role/category using the production Java encoder;
all three scorers receive the same float64-normalized arrays.

For finite x, `u=x/||x||`. A norm below 1e-6 aborts with image/position and count;
it is not floored, dropped or silently treated as a cosine descriptor. Require
at least two fit observations and two calibration observations. Centroid mean
norms below 1e-12 abort with the affected positions. No category tuning.

## Frozen scores

Let `m[p]=mean_fit(u[:,p])` and C=96.

- **C1:** `a[p]=m[p]/||m[p]||`; `dC[p]=1-a[p]^T u[p]`.
- **V1:** Java channel-first Rademacher convention, seed 42, D=10000;
  `P=R/sqrt(D)`, `G=P^T P`;
  `w[p]=G m[p]/sqrt(m[p]^T G m[p])`;
  `dV[p]=1-w[p]^T u[p]/sqrt(u[p]^T G u[p])`.
  No global VSA statistics; no numerator-only variant. Query denominators must
  be finite and positive. Explicit `1-cos(Pu,Pm)` is an oracle for the same V1.
- **M2:** `std[p,c]=sampleStd_fit(u[:,p,c])` (ddof=1),
  `s[p,c]=max(std[p,c],1e-6)`;
  `dM[p]=mean_c(((u[p,c]-m[p,c])/s[p,c])^2)`.
  This matches retained Python M2; its historical implementation is not changed.

For each candidate separately: `alpha[p]=mean_cal(d[:,p])`,
`beta[p]=max(sampleStd_cal(d[:,p]),1e-6)`, `Z[p]=(d[p]-alpha[p])/beta[p]`.
Image score is `max_p Z[p]` over the native 14x14 grid. No score clamping.
Java `LocalizationMaps` and `ComparisonEvaluator` perform the same half-pixel
interpolation, masks, Image AUROC, Pixel AUROC and AUPRO@0.30 as M0.

## Parity and diagnostics

Projection is exported by the existing Java DenseRademacherProjection, using
basis vectors and the established scale. G is shared, not a fitted covariance.
Before test scoring, compare explicit and compiled V1 on the first two ordered
calibration images at every position. Require max raw absolute error <=1e-10
and max calibrated absolute error <=1e-7. This is sampled numerical validation,
not an additional model or a claim of bitwise equivalence.

Record only centroid norms/degeneracy, G eigenvalue range/condition/deviation,
explicit/compiled V1 errors, M2 variance-floor counts and candidate calibration
floor counts. Descriptor failures are reported as invalid inputs. Fitted arrays,
source/data identity and phase logs are reproducibility state, not extra variants.

## Execution and artifacts

The CLI accepts one canonical category or all 15, runs serially and stops at
the first error. It refuses an existing output directory or one inside the
baseline/dataset. Before execution it verifies baseline manifest hashes, selected
dataset hashes and unchanged encoder/evaluation sources. It copies shared
manifests and retained M0 metrics into the new namespace; no old artifact is written.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python -m pytest tools/ablation -q --basetemp "target/test-tmp/$([guid]::NewGuid())"
python tools/ablation/run.py --all-categories --dry-run
```

For the subsequent experiment phase, launch from the repository root:

```powershell
$env:ANOMALIB_EVALUATION_HEAP = "4g"
python tools/ablation/run.py --baseline target/comparison/m0-all15-baseline-20261016 --output target/ablation/local-models-20261017 --all-categories
```

Requires the existing Python environment with NumPy, Pydantic v2 and Typer;
tests additionally require pytest and Pillow. No competitor environment/model
is required. `--category wood` selects one category. Dry-run verifies source and
manifest bindings without extracting features or hashing all dataset pixels.
The evaluator heap is offline metric memory, not an inference-memory claim.

Outputs: shared `data/`, `features/<category>/<role>/`, projection and provenance,
`C1|V1|M2/<category>/` raw/native/evaluated maps, calibration, predictions and
metrics; `M0/<category>/` retained metrics; `diagnostics/<category>/`;
`comparison.csv` with category/M0/C1/V1/M2 for each metric, `macro-summary.csv`
and `RESULT.md`. Commands are recorded in run logs. Incomplete runs retain
failure records; no partial macro summary is presented as a complete campaign.

## Out of scope

Resultant-length/concentration weighting; spatial neighborhood search;
translation/rotation/scale correction; ROI alignment; illumination augmentation;
full covariance; multiple centroids/clustering; learned parameters. No JMH,
memory benchmarking, PaDiM or PatchCore execution. No M0 redesign or metric
changes. No README benchmark claims until a separately authorized result phase.
