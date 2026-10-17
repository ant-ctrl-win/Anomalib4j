# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pytest", "Pillow", "pydantic>=2", "typer"]
# ///
# How to run: python -m pytest tools/ablation/test_pipeline.py -q
import base64
import io
import json
from pathlib import Path
import subprocess
import sys

import numpy as np
from PIL import Image
import pytest

from inputs import ROOT, java_command, read_split, sha256_file
from models import ModelError, calibrate_fit, explicit_v1, fit, normalize, score

sys.path.insert(0, str(ROOT / "tools/prerun"))
from positional_global_variance_diagnostic import java_rademacher_signs


def test_projection_and_v1_when_using_real_java_convention(tmp_path):
    # Given the production Java projection, exported twice.
    for name in ("first.npy", "second.npy"):
        subprocess.run(java_command("LocalAblationBridge", ["projection", str(tmp_path / name)]), check=True)
    projection = np.load(tmp_path / "first.npy").T
    expected = java_rademacher_signs(96, 10000, 42) / 100
    # When fitting and scoring independent synthetic unit descriptors.
    rng = np.random.default_rng(44)
    training = normalize(rng.normal(size=(5, 3, 96)))
    query = normalize(rng.normal(size=(2, 3, 96)))
    model = fit(training, projection.T @ projection)
    compiled = score(model, query).v1
    explicit = explicit_v1(model, query, projection)
    # Then seed, orientation, scale and cosine parity agree.
    np.testing.assert_array_equal(projection, expected)
    np.testing.assert_array_equal(projection, np.load(tmp_path / "second.npy").T)
    np.testing.assert_allclose(model.gram, expected.T @ expected, atol=0, rtol=0)
    error = float(np.max(np.abs(compiled - explicit)))
    print(f"V1 synthetic D=10000 maximum raw absolute error: {error:.17g}")
    assert error <= 1e-10


def test_calibration_isolation_when_queries_change():
    # Given independently generated fit/calibration/test observations.
    rng = np.random.default_rng(3)
    training = normalize(rng.normal(size=(5, 2, 3)))
    calibration = normalize(rng.normal(size=(4, 2, 3)))
    query = normalize(rng.normal(size=(2, 2, 3)))
    model = fit(training, np.eye(3))
    before = model.mean.copy()
    fitted_calibration = calibrate_fit(score(model, calibration).m2)
    alpha = fitted_calibration.alpha.copy()
    # When scoring test images, no fitting API receives them.
    fitted_calibration.apply(score(model, query).m2)
    # Then neither fitted model nor calibration state changes.
    np.testing.assert_array_equal(model.mean, before)
    np.testing.assert_array_equal(fitted_calibration.alpha, alpha)
    np.testing.assert_array_equal(before, training.mean(axis=0))


def test_split_overlap_is_rejected(tmp_path):
    # Given conflicting fit/calibration manifests.
    directory = tmp_path / "wood"
    directory.mkdir()
    for name in ("anomalib4j-fit.txt", "anomalib4j-calibration.txt", "test.txt"):
        (directory / name).write_text("wood_good_000.png\nwood_good_001.png\n")
    # When binding shared inputs, then overlap is rejected.
    with pytest.raises(ModelError, match="overlap"):
        read_split(tmp_path, "wood")


def synthetic_baseline(root: Path) -> Path:
    baseline = root / "baseline"
    baseline.mkdir()
    dataset = root / "dataset"
    dataset.mkdir()
    (dataset / "meta.json").write_text(json.dumps({"classes": [{"id": 10, "title": "custom"}],
        "tags": [{"id": 1, "name": "wood"}, {"id": 2, "name": "good"}]}))
    rng = np.random.default_rng(55)
    mask = io.BytesIO()
    Image.new("RGBA", (1, 1), (255, 255, 255, 255)).save(mask, format="PNG")
    for role, defect, index in [("train", "good", n) for n in range(5)] + [("test", "good", 7), ("test", "custom", 8)]:
        name = f"wood_{defect}_{index:03d}.png"
        (dataset / role / "img").mkdir(parents=True, exist_ok=True)
        (dataset / role / "ann").mkdir(parents=True, exist_ok=True)
        Image.fromarray(rng.integers(0, 256, size=(12, 17, 3), dtype=np.uint8)).save(dataset / role / "img" / name)
        objects = [] if defect == "good" else [{"classId": 10, "classTitle": "custom", "geometryType": "bitmap",
            "bitmap": {"origin": [0, 0], "data": base64.b64encode(mask.getvalue()).decode()}}]
        (dataset / role / "ann" / (name + ".json")).write_text(json.dumps({"size": {"width": 17, "height": 12},
            "tags": [{"tagId": 1, "name": "wood"}] + ([{"tagId": 2, "name": "good"}] if defect == "good" else []), "objects": objects}))
    subprocess.run(java_command("DatasetManifests", [str(dataset), str(baseline / "data"), "42", "wood"]), check=True)
    (baseline / "manifest.json").write_text(json.dumps({"dataset": str(dataset), "categories": ["wood"],
        "smoke": False, "seed": 42, "projection_seed": 42}))
    (baseline / "manifest-hashes.json").write_text(json.dumps({p.relative_to(baseline).as_posix(): sha256_file(p)
        for p in (baseline / "data").rglob("*") if p.is_file()}))
    (baseline / "dataset-hashes.json").write_text(json.dumps({p.relative_to(dataset).as_posix(): sha256_file(p)
        for p in dataset.rglob("*") if p.is_file()}))
    (baseline / "source-hashes.json").write_text(json.dumps({p.relative_to(ROOT).as_posix(): sha256_file(p)
        for p in (ROOT / "src").rglob("*") if p.is_file()}))
    metrics = baseline / "anomalib4j/wood"
    metrics.mkdir(parents=True)
    (metrics / "metrics-unified.json").write_text(json.dumps({"status": "verified", "category": "wood", "method": "anomalib4j",
        "n_good": 1, "n_anomaly": 1, "image_auroc": .5, "pixel_auroc": .5, "aupro030": .5,
        "dataset_manifest_sha256": sha256_file(baseline / "data/wood/test.txt"), "fixture": "synthetic, not measured M0"}))
    return baseline


def test_cli_shared_pipeline_when_using_synthetic_images(tmp_path):
    # Given a tiny synthetic baseline namespace, never a real experiment.
    baseline = synthetic_baseline(tmp_path)
    before = {p.relative_to(baseline).as_posix(): sha256_file(p) for p in baseline.rglob("*") if p.is_file()}
    output = tmp_path / "ablation"
    # When running all three models through the actual CLI and Java evaluator.
    result = subprocess.run([sys.executable, str(ROOT / "tools/ablation/run.py"), "--baseline", str(baseline),
        "--output", str(output), "--category", "wood"], capture_output=True, text=True)
    # Then they share manifest identities, produce metrics, and preserve the input.
    assert result.returncode == 0, result.stdout + result.stderr
    configs = [json.loads((output / method / "wood/config.json").read_text()) for method in ("C1", "V1", "M2")]
    for key in ("fit_manifest_sha256", "calibration_manifest_sha256", "test_manifest_sha256", "shared_features"):
        assert len({config[key] for config in configs}) == 1
    assert (output / "comparison.csv").is_file()
    assert len((output / "macro-summary.csv").read_text().splitlines()) == 4
    for method in ("C1", "V1", "M2"):
        metrics = json.loads((output / method / "wood/metrics-unified.json").read_text())
        assert metrics["status"] == "verified"
        assert metrics["foreground_pixels"] == 1
        assert metrics["background_pixels"] == 407
    assert before == {p.relative_to(baseline).as_posix(): sha256_file(p) for p in baseline.rglob("*") if p.is_file()}


def test_cli_dry_run_when_selecting_all_categories(tmp_path):
    # Given the retained baseline and a prospective output.
    output = tmp_path / "planned"
    # When invoking the nonexecuting all-category CLI.
    result = subprocess.run([sys.executable, str(ROOT / "tools/ablation/run.py"), "--output", str(output),
        "--all-categories", "--dry-run"], capture_output=True, text=True)
    # Then all canonical categories are selected once without creating output.
    assert result.returncode == 0, result.stderr
    plan = json.loads(result.stdout)
    assert len(plan["categories"]) == len(set(plan["categories"])) == 15
    assert plan["methods"] == ["C1", "V1", "M2"]
    assert not output.exists()
