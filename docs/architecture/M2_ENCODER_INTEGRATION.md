# Generic M2: Small / Large encoder integration

Engineering checkpoint, 2026-10-22. No all-15 S/L quality campaign or long JMH
campaign was run. Historical scientific results and timings retain their original
Small/source identities. Small remains the default; Large is ready for the matched
comparison, not selected by new quality evidence.

**M2 operates on a declared spatial descriptor grid and is not tied mathematically
to a specific CNN backbone. Anomalib4j validates the implementation with MobileNetV4
Conv Small and Conv Large encoders using the same 14x14 spatial geometry.**
This implements exactly two explicit M2 contracts, not arbitrary-network loading.

## Artifact contracts

Both accept float32 NCHW `[1,3,224,224]` and return float32 NHWC spatial features,
flattened HWC. Both use the unchanged Java whole-frame RGB bicubic224 / ImageNet
preprocessing, `imagenet-rgb-resize224-bicubic-v1`. The Large upstream tag's `r256`
does not change the prepared artifact's fixed 224 input or this matched protocol.

| Field | `SMALL_SPATIAL_14` | `LARGE_SPATIAL_14` |
|---|---|---|
| Upstream identity | `mobilenetv4_conv_small.e2400_r224_in1k` | `mobilenetv4_conv_large.e500_r256_in1k` |
| Resource under `src/main/resources/models/` | `mobilenetv4_spatial_14x14.onnx` | `mobilenetv4_large_spatial_14x14.onnx` |
| I/O names | `input_image` / `spatial_features` | `input` / `features` |
| Output | `[1,14,14,96]` | `[1,14,14,192]` |
| File bytes | 1,269,313 | 13,578,076 |
| Graph nodes / initializers | 46 / 48 | 93 / 108 |
| Actual initializer elements | 314,272 | 3,335,376 |
| SHA-256 | `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e` | `ac83d397ee76768ba7ea5b2ec054337e07a454286403e27e72c832100021d6c3` |

Initializer elements are the sum of initializer tensor dimension products in each
actual graph, not full-classifier or trainable-parameter counts. Local ONNX checker
passes for both. Small's checkpoint/export provenance remains unresolved in the
[Small contract](ONNX_MODEL_CONTRACT.md). Large's supplied Colab parity, environment
and independent local inspection are attributed separately in the
[external verification record](MOBILENETV4_LARGE_EXPORT_VERIFICATION.md).
Local Java ORT 1.30.0 tests are not a local rerun of Colab PyTorch/ORT 1.31.0 parity.

## Audit and minimal implementation

Before this checkpoint, Small assumptions occurred in:

- `OnnxMobileNetV4Encoder`: resource, model ID, graph I/O names and output geometry.
- `M2Model`: 96 channels / 18,816 elements in validation, normalization and raw mean.
- `M2Trainer`: FIT accumulation size and Small-only model construction.
- `M2Detector` / `M2ModelStore`: Small resource hash, default session and fixed v1 geometry.
- `PositionalVarianceFeatureDump`, `M2Runner`, `java_m2.py`: Small feature metadata,
  cached descriptor dimensions, fit/reload and retained Small quality references.
- `M2Benchmark` / `M2CpuEvidence`: Small resource, I/O names, output allocation and provider probe.
- Existing M2 tests: Small-specific descriptor and archive sizes.

`EncoderSpec` carries stable enum/model identities, resource path, pinned SHA-256,
input dimensions/names, output names/grid/channels and preprocessing ID. Loading
checks resource bytes and ONNX float32 I/O. Existing default/legacy overloads remain
Small. Legacy SPATIAL_28, M0, canonical Metal Nut and historical diagnostics are not
promoted to Large; canonical bundles explicitly reject a Large M2 state.

Only C changes in the M2 formulas. Descriptor L2 normalization and norm guard,
sample std (N-1), 1e-6 floors, strict positional correspondence, mean squared
standardized residual, held-out alpha/beta, unclamped Z, native max, interpolation
and evaluator definitions are unchanged. Existing Small constants/static helpers
remain for compatibility; selected-spec overloads and model-owned spec drive Large.

## Persistence and API

New generic archives use **`anomalib4j-m2/v2`**: enum identity, upstream model ID,
encoder SHA-256, preprocessing ID, H/W/C and float64 mean/effective-std/alpha/beta
arrays. No observations, extra statistics or inverse scales are added.
New reads use an explicit expected `EncoderSpec` and reject cross-encoder loads,
bad identity/hash/preprocessing/geometry, invalid scales, truncation and trailing data.
`anomalib4j-m2/v1` reads remain Small-only. The existing `read(path, expectedHash)`
overload also remains Small-only; it cannot silently infer Large. New writes are v2,
so archive header bytes differ even when Small numerical arrays are identical.
Canonical bundle formats/payloads remain unchanged and Small-only.

```java
var spec = EncoderSpec.LARGE_SPATIAL_14; // or SMALL_SPATIAL_14
var model = M2ModelStore.read(modelPath, spec);
try (var detector = new M2Detector(model)) {
    double[] nativeZ = detector.nativeMap(image);
    double imageScore = M2Model.imageScore(nativeZ);
}
```

For fitting, normalize descriptors with `M2Model.normalize(features, spec)`, then
use `M2Trainer.fitStatistics(spec, fitUnits)` and `uncalibrated(spec, moments)`.
Held-out calibration APIs are unchanged and preserve the fitted model's spec.

## Prepared evaluation commands (not executed)

The existing runner selects `--encoder SMALL_SPATIAL_14` or
`--encoder LARGE_SPATIAL_14`. Large requires `--reextract`; Small can still use
retained descriptors for regression. A matched fresh comparison reextracts both.
The same retained baseline FIT/CAL/TEST manifests, seed-42 historical split,
dataset hashes, M2-P implementation and evaluator are reused without regeneration.

```shell
mvn -B -Pbenchmark test-compile dependency:build-classpath "-Dmdep.outputFile=target/comparison-classpath.txt" "-Dmdep.includeScope=test"
python tools/ablation/java_m2.py --output target/ablation/m2-small-final-new --all-categories --reextract --encoder SMALL_SPATIAL_14
python tools/ablation/java_m2.py --output target/ablation/m2-large-final-new --all-categories --reextract --encoder LARGE_SPATIAL_14
```

Replace `--all-categories` with `--category bottle` for a single category. These
commands require separately restored dataset/manifests/evidence and new output
namespaces. They are future commands, not authorization to run this checkpoint's
final experiment. The dataset location remains the retained baseline manifest's
configured DatasetNinja path. Test feature extraction is not fitting.

The old byte-identity guard is explicitly superseded only for the two modified
encoder/dump adapter source paths. All other captured M0, preprocessing, evaluator,
mask and interpolation identities remain guarded. New runs capture current source
hashes and encoder selection; they do not claim byte identity with frozen sources.
Small retains Python/old-array and quality-delta checks. Large compares Java with
the same independent NumPy formulas on its own descriptors, and is not required
to reproduce Small's arrays/metrics. No old evidence is overwritten.

## Prepared JMH scopes

`M2Benchmark` accepts `-p encoderSpec=SMALL_SPATIAL_14` or `LARGE_SPATIAL_14`.
Its default remains Small for historical callers. Select the matching model run
per invocation, not a Cartesian product of mismatched runs/specs. For example,
after a separately authorized campaign has produced the corresponding model:

```powershell
$cp = "target/test-classes;target/classes;" + (Get-Content target/comparison-classpath.txt -Raw).Trim()
java -cp $cp org.openjdk.jmh.Main '.*M2Benchmark\.(cnnExtraction|m2Detection|m2Localization)$' -p encoderSpec=LARGE_SPATIAL_14 -p run=target/ablation/m2-large-final-new -p dataset=PATH_TO_DATASET -p category=bottle -p benchmarkName=matched-sl-new -rf json -rff target/m2-large-matched-new.json
```

Repeat with Small's matching run/spec and the same dataset/fixture/host/protocol.
Scopes retain encoder extraction without preprocessing, complete generic detection
and complete 900x900 Bottle localization. No stage timings are added/subtracted.
The M0 benchmark scopes remain Small-only and require an explicit retained M0 baseline;
they reject a Large selection. Existing benchmark helper defaults remain Small.
The test uses `good_007` to prove fixture selection does not depend on `good_000`.

## Validation observed

- Original encoder/M2 tests plus new encoder/contract tests: 24 targeted tests pass.
- Complete dataset-free Java suite: 260 pass; the subsequently added feature-dump
  test passes both parameter cases (2 additional tests).
- Python ablation suite: 18 pass, including independent NumPy/Java 96/192 fit,
  raw/Z/image-score parity and rejection of Large with retained Small descriptors.
  Existing tolerances are unchanged; two upstream Typer/Click warnings remain.
- `EncoderSpecTest`: both resources, hashes, float32 I/O, finite output, repeated
  inference and every HWC element checked against the nested ORT tensor.
- `M2EncoderContractTest`: both channel counts, last channel participation,
  normalization, sample std/floors, exact persistence, wrong shape/encoder/header,
  legacy v1 and Small-only canonical rejection.
- JMH functional smoke: both parameter values execute encoder, detection and
  900x900 localization bodies. Untimed node profiles require exactly
  `{CPUExecutionProvider}`; available providers are CPU/Azure. No JMH timing claim.
- Small retained descriptor regression: Bottle, Metal Nut and Hazelnut produce
  **bit-identical** mean, sample/effective std, alpha/beta, raw, native/reloaded Z
  and image-score arrays versus the retained Java run, maximum difference 0.
  Evidence: `target/validation/m2-encoders-20261022/small-regression.json` and
  category outputs. No image re-extraction, metric reevaluation or all-15 campaign.
- Python rule checker and `git diff --check` pass. The final targeted Python
  regression rerun passes all 5 tests after narrowing the adapter path allowlist.

Remaining evidence boundary: the raw Colab notebook/report is not archived here;
its supplied summary is preserved. Large has no project MVTec quality results or
matched latency measurements yet. These require the separately authorized final
campaign; the new engineering tests do not establish Large superiority.
