# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/correspondence/run.py --output target/ablation/m2-spatial-correspondence-20261018
import json
from pathlib import Path
import shutil
import sys
from time import perf_counter
from typing import Final

import numpy as np
from pydantic import TypeAdapter
import typer

ROOT: Final = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/ablation"))
from inputs import CATEGORIES, baseline_contract, read_split, java_command, sha256_file
from models import normalize, ModelError
from java_m2 import compare
from progress import phase, stream_command
from common import capture, capture_hardware, write_json
from correspondence_results import METHODS, GEOMETRY, verify_p_pairs, collect


def main(output: Path = typer.Option(...), category: str | None = None) -> None:
    started = perf_counter()
    selected = (category,) if category else tuple(CATEGORIES)
    if not set(selected) <= set(CATEGORIES):
        raise ModelError("Unknown category")
    destination = output.resolve()
    baseline = ROOT / "target/comparison/m0-all15-baseline-20261016"
    retained = ROOT / "target/ablation/local-models-20261017"
    robust = ROOT / "target/robustness/m2-controlled-20261018"
    if destination.exists() or any(p == destination or p in destination.parents for p in (baseline, retained, robust)):
        raise ModelError("Output must be new and outside retained evidence")
    print("PREFLIGHT - frozen source, split and dataset verification - started", flush=True)
    contract = baseline_contract(baseline, selected, verify_data=True)
    if destination == contract.dataset or contract.dataset in destination.parents:
        raise ModelError("Output cannot be inside dataset")
    old_sources = TypeAdapter(dict[str, str]).validate_json((robust / "source-hashes.json").read_text())
    for name, digest in old_sources.items():
        if name.startswith("src/main/") or Path(name).name in ("RobustnessTransforms.java", "CanonicalRgb.java"):
            if sha256_file(ROOT / name) != digest:
                raise ModelError(f"Frozen implementation changed: {name}")
    destination.mkdir(parents=True)
    capture(destination / "workspace")
    source_files = {p for pattern in ("src/**/*.java", "tools/correspondence/*.py", "tools/robustness/*.py", "tools/ablation/*.py") for p in ROOT.glob(pattern)}
    source_files.add(ROOT / "docs/experiments/M2_SPATIAL_CORRESPONDENCE_PLAN.md")
    write_json(destination / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p) for p in sorted(source_files)})
    write_json(destination / "plan.json", {"categories": selected, "methods": METHODS, "geometry": GEOMETRY,
        "dataset": str(contract.dataset), "baseline": str(baseline), "retained": str(retained), "models": str(robust),
        "command": sys.argv, "python": sys.version, "numpy": np.__version__, "refit_local_statistics": False,
        "calibration": "P retained; N1/G independent held-out raw moments; sample std floor 1e-6"})
    shutil.copytree(baseline / "data", destination / "data")
    capture_hardware(destination / "hardware.json")
    progress = destination / "progress.log"
    for index, name in enumerate(selected, 1):
        label = f"[{index:02}/{len(selected):02}] {name}"
        split = read_split(destination / "data", name)
        inputs = destination / "inputs" / name
        inputs.mkdir(parents=True)
        hashes = {}
        with phase(progress, label, "FROZEN INPUTS"):
            prior_hashes = TypeAdapter(dict[str, str]).validate_json((robust / name / "input-hashes.json").read_text())
            model_path = robust / name / "model.bin"
            if sha256_file(model_path) != prior_hashes[str(model_path)]:
                raise ModelError("Frozen model archive changed")
            shutil.copyfile(model_path, inputs / "model.bin")
            hashes[str(model_path)] = sha256_file(model_path)
            for role in ("calibration", "test"):
                names = getattr(split, role)
                directory = retained / "features" / name / role
                if (directory / "images.txt").read_text().splitlines() != list(names):
                    raise ModelError("Retained feature order mismatch")
                values = np.load(directory / "features.npy", allow_pickle=False)
                if values.shape != (len(names), 14, 14, 96):
                    raise ModelError("Feature shape mismatch")
                units = normalize(values.reshape(len(names), 196, 96)).reshape(len(names), -1)
                if role == "calibration":
                    if sha256_file(directory / "features.npy") != prior_hashes[str(directory / "features.npy")]:
                        raise ModelError("Held-out feature bytes changed")
                    np.testing.assert_array_equal(units, np.load(robust / name / "calibration.npy"))
                np.save(inputs / (role + ".npy"), units)
                hashes[str(directory / "features.npy")] = sha256_file(directory / "features.npy")
                hashes[str(directory / "images.txt")] = sha256_file(directory / "images.txt")
            normals = (robust / name / "normal-test.txt").read_text().splitlines()
            if normals != [n for n in split.test if n.startswith(name + "_good_")]:
                raise ModelError("Normal-test coverage changed")
            shutil.copyfile(robust / name / "normal-test.txt", inputs / "normal-test.txt")
            write_json(inputs / "input-hashes.json", hashes)
            for method in METHODS:
                (destination / method / name).mkdir(parents=True)
        with phase(progress, label, "CALIBRATION/QUALITY SCORES/GEOMETRY"):
            stream_command(java_command("CorrespondenceRunner", [str(destination), str(contract.dataset), name, label]), inputs / "java.log", progress)
        with phase(progress, label, "P PARITY"):
            p = destination / "M2-P" / name
            errors = {key: compare(np.load(p / (key + ".npy")), np.load(retained / "M2" / name / (key + ".npy")))
                      for key in ("raw", "native-z")}
            count = verify_p_pairs(p / "robustness.csv", robust / name / "per-image.csv")
            write_json(p / "parity.json", {"retained_quality_arrays": errors, "exact_retained_robustness_pairs": count,
                "exact_java_baseline": True, "test_images": len(split.test)})
            shutil.copyfile(retained / "M2" / name / "metrics-unified.json", p / "metrics-retained.json")
        for method in METHODS:
            directory = destination / method / name
            write_json(directory / "config.json", {"method": method, "category": name,
                "base_model_sha256": sha256_file(inputs / "model.bin"), "input_hashes": hashes,
                "calibration_manifest_sha256": sha256_file(destination / "data" / name / "anomalib4j-calibration.txt"),
                "test_manifest_sha256": sha256_file(destination / "data" / name / "test.txt"),
                "alpha_sha256": sha256_file(directory / "alpha.npy"), "beta_sha256": sha256_file(directory / "beta.npy"),
                "quality_source": "retained M2 metrics after native parity" if method == "M2-P" else "unchanged Java evaluator"})
            if method != "M2-P":
                with phase(progress, label, method + " QUALITY METRICS"):
                    command = java_command("LocalAblationBridge", ["evaluate", str(contract.dataset), str(directory),
                        str(destination / "data" / name / "test.txt"), method, name])
                    command[1] = "-Xmx4g"
                    stream_command(command, directory / "evaluate.log", progress)
        write_json(inputs / "complete.json", {"category": name})
    with phase(progress, "CAMPAIGN", "SUMMARIES"):
        collect(destination, selected)
    if "bottle" in selected:
        with phase(progress, "CAMPAIGN", "MATCHED RAW SCORER TIMING"):
            for fork in range(3):
                stream_command(java_command("CorrespondenceTiming", [str(destination / "inputs/bottle"),
                    str(destination / f"timing-{fork}.csv"), str(fork)]), destination / f"timing-{fork}.log", progress)
    write_json(destination / "complete.json", {"categories": selected, "wall_seconds": perf_counter()-started})


if __name__ == "__main__":
    typer.run(main)
