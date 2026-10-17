# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/ablation/run.py --all-categories --dry-run
from pathlib import Path
import csv
import json
import os
import shutil
import subprocess
import sys

import numpy as np
import typer

from inputs import (CATEGORIES, METHODS, ROOT, baseline_contract, checked_command,
                    extract, java_command, read_split, sha256_file)
from models import FLOOR, ModelError, calibrate_fit, explicit_v1, fit, score
from common import capture, write_json


def category_run(dataset: Path, output: Path, category: str, projection: np.ndarray, gram: np.ndarray) -> None:
    manifests = output / "data"
    split = read_split(manifests, category)
    fitted = fit(extract(dataset, output, manifests, category, "fit"), gram)
    cal_unit = extract(dataset, output, manifests, category, "calibration")
    cal_raw = score(fitted, cal_unit)
    calibrations = {method: calibrate_fit(values) for method, values in cal_raw.items()}
    explicit = explicit_v1(fitted, cal_unit[:2], projection)
    raw_error = float(np.max(np.abs(explicit - cal_raw.v1[:2])))
    z_error = float(np.max(np.abs(calibrations["V1"].apply(explicit) - calibrations["V1"].apply(cal_raw.v1[:2]))))
    diagnostic = output / "diagnostics" / category
    diagnostic.mkdir(parents=True)
    write_json(diagnostic / "summary.json", {
        "C1_centroid_norms": fitted.centroid_norm.tolist(),
        "V1_centroid_norms": fitted.projected_centroid_norm.tolist(), "degenerate_positions": [],
        "V1_parity": {"raw_max_abs": raw_error, "z_max_abs": z_error,
                      "scope": "first two ordered calibration images; all 196 positions",
                      "raw_tolerance": 1e-10, "z_tolerance": 1e-7},
        "M2_variance_floor_count": int(np.count_nonzero(fitted.std < FLOOR)),
        "calibration_floor_counts": {method: int(np.count_nonzero(cal.sample_std < FLOOR))
                                     for method, cal in calibrations.items()}})
    if raw_error > 1e-10 or z_error > 1e-7:
        raise ModelError(f"V1 parity failed: {category}, raw={raw_error}, Z={z_error}")
    np.savez(diagnostic / "fitted-state.npz", mean=fitted.mean, centroid=fitted.centroid,
             weights=fitted.weights, std=fitted.std, effective_std=fitted.effective_std)
    test_raw = score(fitted, extract(dataset, output, manifests, category, "test"))
    for method, raw in test_raw.items():
        directory = output / method / category
        directory.mkdir(parents=True)
        calibration = calibrations[method]
        np.save(directory / "raw.npy", raw, allow_pickle=False)
        np.save(directory / "native-z.npy", calibration.apply(raw), allow_pickle=False)
        np.savez(directory / "calibration.npz", alpha=calibration.alpha, beta=calibration.beta,
                 sample_std=calibration.sample_std)
        write_json(directory / "config.json", {"method": method, "category": category, "seed": 42,
                   "fit_manifest_sha256": sha256_file(manifests / category / "anomalib4j-fit.txt"),
                   "calibration_manifest_sha256": sha256_file(manifests / category / "anomalib4j-calibration.txt"),
                   "test_manifest_sha256": sha256_file(manifests / category / "test.txt"),
                   "fit_count": len(split.fit), "calibration_count": len(split.calibration),
                   "shared_features": str(output / "features" / category)})
        command = java_command("LocalAblationBridge", ["evaluate", str(dataset), str(directory),
                               str(manifests / category / "test.txt"), method, category])
        command[1] = "-Xmx" + os.environ.get("ANOMALIB_EVALUATION_HEAP", "4g")
        checked_command(command, directory / "evaluate.log")
        actual = json.loads((directory / "metrics-unified.json").read_text())
        reference = json.loads((output / "M0" / category / "metrics-unified.json").read_text())
        if any(actual[key] != reference[key] for key in ("n_good", "n_anomaly")):
            raise ModelError(f"M0/local evaluation coverage mismatch: {category}")


def summarize(output: Path, categories: tuple[str, ...]) -> None:
    columns = ("M0", *METHODS)
    rows = []
    macro = []
    markdown = ["# Local-model quality ablation", "", "M0 is retained, not rerun. No latency or memory claims."]
    for metric in ("image_auroc", "pixel_auroc", "aupro030"):
        markdown += ["", "## " + metric, "", "| category | M0 | C1 | V1 | M2 |", "|---|---:|---:|---:|---:|"]
        values = []
        for category in categories:
            scores = [json.loads((output / method / category / "metrics-unified.json").read_text())[metric] for method in columns]
            if not np.isfinite(scores).all():
                raise ModelError("Nonfinite result cannot be summarized")
            rows.append([metric, category, *scores])
            values.append(scores)
            markdown.append("| " + category + " | " + " | ".join(f"{v:.9f}" for v in scores) + " |")
        macro.append([metric, *np.mean(values, axis=0).tolist()])
    for name, header, data in (("comparison.csv", ["metric", "category", *columns], rows),
                               ("macro-summary.csv", ["metric", *columns], macro)):
        with (output / name).open("x", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream)
            writer.writerow(header)
            writer.writerows(data)
    (output / "RESULT.md").write_text("\n".join(markdown) + "\n", encoding="utf-8")


def main(baseline: Path = ROOT / "target/comparison/m0-all15-baseline-20261016",
         output: Path = ROOT / "target/ablation/local-models-20261017",
         category: str | None = None, all_categories: bool = False, dry_run: bool = False) -> None:
    baseline = baseline.resolve()
    output = output.resolve()
    if category is not None and all_categories:
        raise ModelError("Choose one category or all categories")
    categories = (category,) if category is not None else tuple(CATEGORIES)
    if output.exists() or output == baseline or baseline in output.parents:
        raise ModelError("Output must be absent and outside retained baseline")
    contract = baseline_contract(baseline, categories, verify_data=not dry_run)
    if output == contract.dataset or contract.dataset in output.parents:
        raise ModelError("Output cannot be inside dataset")
    plan = {"baseline": str(baseline), "output": str(output), "categories": list(categories),
            "methods": list(METHODS), "seed": 42, "phases": ["extract-fit", "fit", "extract-calibration",
            "calibrate", "V1-parity", "extract-test", "score", "evaluate"], "M0": "retained metrics only"}
    if dry_run:
        print(json.dumps(plan, indent=2))
        return
    output.mkdir(parents=True)
    write_json(output / "plan.json", plan)
    try:
        capture(output / "workspace")
        paths = sorted({*ROOT.glob("src/**/*.java"), *ROOT.glob("src/main/resources/models/*.onnx"),
                        *ROOT.glob("tools/ablation/*.py"), *ROOT.glob("tools/comparison/*.py"),
                        *ROOT.glob("tools/prerun/*.py"), ROOT / "pom.xml",
                        ROOT / "docs/experiments/LOCAL_MODEL_ABLATION_PLAN.md"})
        write_json(output / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p) for p in paths})
        write_json(output / "environment.json", {"python": sys.version, "numpy": np.__version__,
                   "command": sys.argv, "evaluation_heap": os.environ.get("ANOMALIB_EVALUATION_HEAP", "4g")})
        shutil.copytree(baseline / "data", output / "data")
        for name in ("manifest.json", "manifest-hashes.json", "dataset-hashes.json", "source-hashes.json"):
            shutil.copy2(baseline / name, output / ("baseline-" + name))
        for selected in categories:
            target = output / "M0" / selected
            target.mkdir(parents=True)
            shutil.copy2(baseline / "anomalib4j" / selected / "metrics-unified.json", target / "metrics-unified.json")
        projection_file = output / "projection.npy"
        checked_command(java_command("LocalAblationBridge", ["projection", str(projection_file)]), output / "projection.log")
        projection = np.load(projection_file, allow_pickle=False).T
        gram = projection.T @ projection
        eigenvalues = np.linalg.eigvalsh(gram)
        write_json(output / "gram-diagnostics.json", {"eigenvalue_min": float(eigenvalues.min()),
                   "eigenvalue_max": float(eigenvalues.max()), "condition_number": float(eigenvalues.max() / eigenvalues.min()),
                   "spectral_deviation_from_identity": float(np.max(np.abs(eigenvalues - 1)))})
        for selected in categories:
            write_json(output / (selected + "-started.json"), {"category": selected})
            category_run(contract.dataset, output, selected, projection, gram)
            write_json(output / (selected + "-complete.json"), {"category": selected})
        summarize(output, categories)
    except (ModelError, OSError, RuntimeError, ValueError, subprocess.CalledProcessError) as failure:
        write_json(output / "failure.json", {"error": str(failure)})
        raise


if __name__ == "__main__":
    typer.run(main)
