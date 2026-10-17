# M0 full-dataset execution contract

Infrastructure prepared 2026-10-16; no full-dataset campaign has been run.
The [2026-10-16 preflight report](M0_ALL15_PREFLIGHT_20261016.md) records the exact
local dataset, hashes, prospective manifests, census, resource estimates and
proposed launch command. Data preflight passed; the dirty source tree still
requires a designated release/archive before launch.
This contract extends the retained [benchmark contract](BENCHMARK_CONTRACT.md)
to arbitrary MVTec categories. Historical artifacts and numerical results remain
frozen. M0 mathematics, native image score, interpolation and metric definitions
are unchanged; see [localization conventions](LOCALIZATION_CONVENTIONS.md).

## Inputs and preparation

Use Java 21, Maven and Python >=3.11 on Windows. M0-only execution does not
require the competitor Python environment. PaDiM/PatchCore still require the
frozen Anomalib environment and its dependency/weight gate. Hardware and memory
capture still use PowerShell. Do not run `mvn clean`: retained artifacts live in target.

Supply an existing DatasetNinja export with `meta.json`, `train/img`, `train/ann`,
`test/img`, `test/ann`. No download is performed. Filenames follow
`category_defect_index.png`; annotations are `filename.png.json`. Category/good
tags, class IDs/titles, bitmap type and annotation/image dimensions are validated
against metadata. Test data must contain both classes. Masks are validated by the
existing evaluator. Native MVTec directory layout is not accepted directly.

```powershell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/comparison/run.py run --run-id m0-bottle-baseline --output-root target/comparison --dataset "D:/datasets/mvtec-ad-DatasetNinja" --category bottle --method anomalib4j --seed 42
python tools/comparison/run.py run --run-id m0-all15-baseline --output-root target/comparison --dataset "D:/datasets/mvtec-ad-DatasetNinja" --all-categories --method anomalib4j --seed 42
```

Replace the dataset path with the actual export. `--dry-run` on init/run prints
the ordered plan without dataset access or output creation. `run` defaults to M0;
`init` defaults to all three methods. Omitting category selection retains the
historical Bottle/Metal Nut selection. The canonical category resource is
`src/test/resources/mvtec-categories.txt`, shared by Java and Python.

Each run requires a new directory. Execution is serial: fit, infer, evaluate,
latency, memory for each category/method. The first failure stops execution and
is recorded in `campaign.jsonl`; missing results remain missing, never zero.
Individual existing phase commands remain available with the same run ID,
method and category. Dataset and split settings are fixed at initialization;
later phase commands read them from `manifest.json`. Smoke mode truncates
training/inference and skips evaluation; it is not scientific evidence.

## Split and evidence contract

Sort normal training filenames lexically, then shuffle with Java
`Collections.shuffle(..., new Random(seed))`. Fit count is `floor(4*N/5)`,
clamped to `[1,N-2]`; calibration is the remainder. Reject N<3 and duplicates.
This preserves the historical 167/42 Bottle and 176/44 Metal Nut splits at seed
42 exactly, including ordering; the regression test compares the retained
`benchmark-prerun/data` manifests. Small sets retain at least two calibration
images for sample variance. The seed changes only splitting; projection seed
remains 42. Competitors retain all normal training images. Test anomalies never
enter either training subset.

Generated manifests, per-image dimensions, source/data/manifest hashes, workspace
identity and run configuration are retained in the new run directory. The copied
prerun environment/configuration is historical provenance; generated manifests
define the actual new split. Timing selects the first lexical normal in the
manifest instead of assuming index 000. Full maps use each original image's
width/height; M0 input 224x224 and native 14x14 remain fixed.

The evaluator retains all category pixels in memory (double scores and integer
regions). Its Java heap may need increasing for larger categories; set
`ANOMALIB_EVALUATION_HEAP` (default `4g`) before running. This affects only
offline metric evaluation, not inference, training or JMH measurement settings.

## Validation and boundaries

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
python tools/comparison/test_runner.py
python tools/comparison/test_campaign.py
git diff --check
```

Historical diagnostics remain category-specific. Synthetic validation is not
reproduction of the retained two-category results. Real dataset availability,
validation of all category exports, disk/RAM budget and a fresh source freeze
are prerequisites for the next campaign. Exact ONNX checkpoint/export provenance
remains unresolved; this contract does not resolve it.

Validation on 2026-10-16: 106 Java tests passed (zero failures/errors/skips),
four Python runner tests and five campaign tests passed. The campaign tests
initialize all 15 categories from temporary synthetic metadata/images, validate
manifest/data hashes, and exercise fail-fast orchestration without training or
benchmarking. Java coverage includes mixed rectangular geometry, metadata
rejection, exact historical manifest agreement, calibration counts 2/42/44/79,
corrupt archives and exact raw/Z reload equivalence. The original Bottle helper
rejection test was preserved; its historical category boundary remains explicit.
`BottleTrainingTest`, `BottleRealEvaluationTest` and real-data `*IT` experiments
were excluded from this infrastructure validation. A final availability check
found the expected dataset directory present, contrary to the earlier absence
report, with train/test PNG counts matching the census for all 15 categories.
A sampled annotation matches the metadata schema; complete integrity validation
remains pending. Absence is therefore not a current reason these tests cannot
run. No real-data numerical reproduction or new timing claim is made.
