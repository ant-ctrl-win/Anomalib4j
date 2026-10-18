# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/ablation/java_m2.py --help
from pathlib import Path
import json
import shutil
import sys

import numpy as np
import typer

from inputs import ROOT, CATEGORIES, read_split, ROLE_FILES, java_command, checked_command, sha256_file
from models import normalize, ModelError
from progress import phase, stream_command
from common import write_json, capture, validate_manifests
from local_memory_ablation import raw_m2


def compare(actual: np.ndarray, expected: np.ndarray) -> dict[str, float]:
    if actual.shape != expected.shape or not np.isfinite(actual).all() or not np.isfinite(expected).all():
        raise ModelError("Parity shape/nonfinite failure")
    difference = np.abs(actual - expected)
    np.testing.assert_allclose(actual, expected, atol=1e-8, rtol=1e-10)
    return {"max_abs": float(difference.max()), "mean_abs": float(difference.mean())}


def validate_parity(output: Path, units: dict[str, np.ndarray], retained: Path) -> None:
    training = units["fit"]
    mean = training.mean(axis=0)
    std = training.std(axis=0, ddof=1)
    effective = np.maximum(std, 1e-6)
    cal_raw = raw_m2(units["calibration"], mean, effective)
    alpha, beta = cal_raw.mean(axis=0), np.maximum(cal_raw.std(axis=0, ddof=1), 1e-6)
    raw = raw_m2(units["test"], mean, effective)
    z = (raw - alpha) / beta
    references = {"mean": mean, "sample-std": std, "effective-std": effective,
                  "alpha": alpha[None], "beta": beta[None], "raw": raw,
                  "native-z": z, "reload-z": z, "image-scores": z.max(axis=1)[:, None]}
    errors = {name: compare(np.load(output / (name + ".npy")), expected) for name, expected in references.items()}
    errors["retained_raw"] = compare(np.load(output / "raw.npy"), np.load(retained / "raw.npy"))
    errors["retained_z"] = compare(np.load(output / "native-z.npy"), np.load(retained / "native-z.npy"))
    np.testing.assert_array_equal(np.load(output / "native-z.npy"), np.load(output / "reload-z.npy"))
    write_json(output / "parity.json", errors)


def main(output: Path = typer.Option(...), category: list[str] | None = None, all_categories: bool = False,
         reextract: bool = False, evaluate: bool = True) -> None:
    retained = ROOT / "target/ablation/local-models-20261017"
    baseline = ROOT / "target/comparison/m0-all15-baseline-20261016"
    selected = tuple(CATEGORIES) if all_categories else tuple(category or ("bottle", "metal_nut", "hazelnut"))
    if (all_categories and category) or len(set(selected)) != len(selected) or not set(selected) <= set(CATEGORIES):
        raise ModelError("Invalid category selection")
    output = output.resolve()
    dataset = Path(json.loads((baseline / "manifest.json").read_text())["dataset"])
    if output.exists() or any(root == output or root in output.parents for root in (retained, baseline, dataset)):
        raise ModelError("Output must be new and outside evidence/dataset")
    validate_manifests(baseline)
    sources = json.loads((retained / "source-hashes.json").read_text())
    evaluation_contract = {"LocalizationMaps.java", "LocalizationMetrics.java", "ComparisonEvaluator.java",
                           "DatasetNinjaMasks.java", "PositionalVarianceFeatureDump.java"}
    for name, digest in sources.items():
        if (name.startswith("src/main/") or Path(name).name in evaluation_contract) and sha256_file(ROOT / name) != digest:
            raise ModelError(f"Retained encoder/M0 source identity changed: {name}")
    output.mkdir(parents=True)
    capture(output / "workspace")
    write_json(output / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p)
        for pattern in ("src/**/*.java", "tools/ablation/*.py") for p in ROOT.glob(pattern)})
    write_json(output / "plan.json", {"categories": list(selected), "retained": str(retained), "baseline": str(baseline),
        "dataset": str(dataset), "reextract": reextract, "evaluate": evaluate, "command": sys.argv})
    shutil.copytree(baseline / "data", output / "data")
    dataset_hashes = json.loads((baseline / "dataset-hashes.json").read_text())
    for index, name in enumerate(selected, 1):
        label = f"[{index:02}/{len(selected):02}] {name}"
        split = read_split(output / "data", name)
        inputs = output / "inputs" / name
        inputs.mkdir(parents=True)
        directory = output / "M2" / name
        directory.parent.mkdir(exist_ok=True)
        units = {}
        hashes = {}
        with phase(output / "progress.log", label, "INPUTS"):
            for role in ROLE_FILES:
                names = getattr(split, role)
                feature_dir = retained / "features" / name / role
                manifest = output / "data" / name / ROLE_FILES[role]
                if reextract or evaluate:
                    for filename in names:
                        for kind, suffix in (("img", ""), ("ann", ".json")):
                            relative = f"{'test' if role == 'test' else 'train'}/{kind}/{filename}{suffix}"
                            if sha256_file(dataset / relative) != dataset_hashes[relative]:
                                raise ModelError(f"Dataset identity mismatch: {relative}")
                    if sha256_file(dataset / "meta.json") != dataset_hashes["meta.json"]:
                        raise ModelError("Dataset metadata identity mismatch")
                if reextract:
                    feature_dir = inputs / role
                    with phase(output / "progress.log", label, "EXTRACT " + role.upper()):
                        checked_command(java_command("PositionalVarianceFeatureDump", [str(dataset), str(feature_dir),
                            str(manifest), name, "test" if role == "test" else "train"]), inputs / (role + "-extract.log"))
                if (feature_dir / "images.txt").read_text().splitlines() != list(names):
                    raise ModelError("Feature manifest mismatch")
                values = np.load(feature_dir / "features.npy", allow_pickle=False)
                if values.shape != (len(names), 14, 14, 96):
                    raise ModelError("Feature geometry mismatch")
                units[role] = normalize(values.reshape(len(names), 196, 96))
                np.save(inputs / (role + ".npy"), units[role].reshape(len(names), -1))
                hashes[str(feature_dir / "features.npy")] = sha256_file(feature_dir / "features.npy")
                hashes[str(manifest)] = sha256_file(manifest)
            write_json(inputs / "input-hashes.json", hashes)
        with phase(output / "progress.log", label, "JAVA FIT/CALIBRATE/INFER/RELOAD"):
            stream_command(java_command("M2Runner", [str(inputs), str(directory), label]), inputs / "java.log", output / "progress.log")
        write_json(directory / "config.json", {"method": "M2", "implementation": "Java", "category": name,
            "model_sha256": sha256_file(directory / "model.bin"), "input_hashes": hashes,
            "fit_count": len(split.fit), "calibration_count": len(split.calibration),
            "test_manifest_sha256": sha256_file(output / "data" / name / "test.txt")})
        with phase(output / "progress.log", label, "PARITY"):
            validate_parity(directory, units, retained / "M2" / name)
        if evaluate:
            with phase(output / "progress.log", label, "EVALUATE"):
                command = java_command("LocalAblationBridge", ["evaluate", str(dataset), str(directory),
                    str(output / "data" / name / "test.txt"), "M2", name])
                command[1] = "-Xmx4g"
                checked_command(command, directory / "evaluate.log")
                actual = json.loads((directory / "metrics-unified.json").read_text())
                expected = json.loads((retained / "M2" / name / "metrics-unified.json").read_text())
                delta = {key: actual[key] - expected[key] for key in ("image_auroc", "pixel_auroc", "aupro030")}
                if any(abs(value) > 1e-10 for value in delta.values()):
                    raise ModelError(f"Quality regression: {delta}")
                write_json(directory / "quality-delta.json", delta)
        write_json(directory / "complete.json", {"category": name, "model_bytes": (directory / "model.bin").stat().st_size})
    write_json(output / "complete.json", {"categories": list(selected)})


if __name__ == "__main__":
    typer.run(main)
