# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer", "matplotlib"]
# ///
# How to run: python tools/robustness/run.py --output target/robustness/NEW --all-categories
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
from inputs import baseline_contract, read_split, CATEGORIES, ROLE_FILES, sha256_file, java_command
from models import normalize
from java_m2 import compare
from local_memory_ablation import raw_m2
from common import capture, write_json
from progress import phase, stream_command
from summary import GRID, RobustnessError, aggregate, plots


def main(output: Path = typer.Option(...), category: list[str] | None = None, all_categories: bool = False) -> None:
    started = perf_counter()
    selected = tuple(CATEGORIES) if all_categories else tuple(category or ("bottle",))
    if (all_categories and category) or len(set(selected)) != len(selected) or not set(selected) <= set(CATEGORIES):
        raise RobustnessError("Invalid category selection")
    baseline = ROOT / "target/comparison/m0-all15-baseline-20261016"
    retained = ROOT / "target/ablation/local-models-20261017"
    java = ROOT / "target/ablation/java-m2-20261018-validated"
    manifest = baseline_contract(baseline, selected, verify_data=True)
    destination = output.resolve()
    if destination.exists() or any(p == destination or p in destination.parents for p in (baseline, retained, java, manifest.dataset)):
        raise RobustnessError("Output must be new and outside retained evidence/dataset")
    hashes = TypeAdapter(dict[str, str]).validate_json((java / "source-hashes.json").read_text())
    for path in (ROOT / "src/main/java/io/github/antctrlwin/anomalib4j/m2").glob("*.java"):
        if hashes[path.relative_to(ROOT).as_posix()] != sha256_file(path):
            raise RobustnessError(f"Validated M2 implementation changed: {path}")
    destination.mkdir(parents=True)
    capture(destination / "workspace")
    source_files = [p for pattern in ("src/**/*.java", "tools/robustness/*.py", "tools/ablation/*.py") for p in ROOT.glob(pattern)]
    source_files.append(ROOT / "docs/experiments/M2_ROBUSTNESS_PLAN.md")
    write_json(destination / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p) for p in source_files})
    write_json(destination / "plan.json", {"categories": selected, "grid": GRID, "dataset": str(manifest.dataset),
        "baseline": str(baseline), "retained": str(retained), "java": str(java), "command": sys.argv,
        "reference_rule": "empirical maximum of held-out calibration native max Z; strict exceedance",
        "protocol": "docs/experiments/M2_ROBUSTNESS_PLAN.md", "boundary": "replication", "interpolation": "bilinear"})
    shutil.copytree(baseline / "data", destination / "data")
    progress = destination / "progress.log"
    for index, name in enumerate(selected, 1):
        label = f"[{index:02}/{len(selected):02}] {name}"
        directory = destination / name
        directory.mkdir()
        split = read_split(destination / "data", name)
        normals = tuple(n for n in split.test if n.startswith(name + "_good_"))
        if not normals:
            raise RobustnessError(f"No normal test images: {name}")
        (directory / "normal-test.txt").write_text("\n".join(normals) + "\n")
        units = {}
        input_hashes = {}
        with phase(progress, label, "MODEL INPUTS"):
            for role in ("fit", "calibration"):
                names = getattr(split, role)
                feature_dir = retained / "features" / name / role
                if (feature_dir / "images.txt").read_text().splitlines() != list(names):
                    raise RobustnessError(f"Feature order mismatch: {name}/{role}")
                features = np.load(feature_dir / "features.npy", allow_pickle=False)
                if features.shape != (len(names), 14, 14, 96):
                    raise RobustnessError("Feature geometry mismatch")
                units[role] = normalize(features.reshape(len(names), 196, 96))
                np.save(directory / (role + ".npy"), units[role].reshape(len(names), -1))
                for path in (feature_dir / "features.npy", feature_dir / "images.txt", destination / "data" / name / ROLE_FILES[role]):
                    input_hashes[str(path)] = sha256_file(path)
            for filename in normals:
                path = manifest.dataset / "test/img" / filename
                input_hashes[str(path)] = sha256_file(path)
        with phase(progress, label, "MODEL STATE"):
            existing = java / "M2" / name / "model.bin"
            if existing.exists():
                config = json.loads((existing.parent / "config.json").read_text())
                if sha256_file(existing) != config["model_sha256"]:
                    raise RobustnessError("Retained Java model hash mismatch")
                shutil.copyfile(existing, directory / "model.bin")
                input_hashes[str(existing)] = sha256_file(existing)
            else:
                # M2Runner's diagnostic query set is calibration only, never anomalous tests.
                shutil.copyfile(directory / "calibration.npy", directory / "test.npy")
                stream_command(java_command("M2Runner", [str(directory), str(directory / "fitted"), label]),
                               directory / "fit.log", progress)
                shutil.copyfile(directory / "fitted/model.bin", directory / "model.bin")
                compare(np.load(directory / "fitted/mean.npy"), units["fit"].mean(axis=0))
                compare(np.load(directory / "fitted/effective-std.npy"), np.maximum(units["fit"].std(axis=0, ddof=1), 1e-6))
            input_hashes[str(directory / "model.bin")] = sha256_file(directory / "model.bin")
            write_json(directory / "input-hashes.json", input_hashes)
        with phase(progress, label, "ROBUSTNESS"):
            stream_command(java_command("M2RobustnessRunner", [str(manifest.dataset), str(directory), name, label]),
                           directory / "java.log", progress)
        with phase(progress, label, "CALIBRATION PARITY"):
            raw = raw_m2(units["calibration"], units["fit"].mean(axis=0), np.maximum(units["fit"].std(axis=0, ddof=1), 1e-6))
            z = (raw - raw.mean(axis=0)) / np.maximum(raw.std(axis=0, ddof=1), 1e-6)
            write_json(directory / "calibration-parity.json", compare(np.load(directory / "calibration-z.npy"), z))
        write_json(directory / "complete.json", {"category": name, "normal_images": len(normals)})
    with phase(progress, "CAMPAIGN", "AGGREGATE/PLOTS"):
        plots(destination, aggregate(destination, selected))
    write_json(destination / "complete.json", {"categories": selected, "wall_seconds": perf_counter()-started})


if __name__ == "__main__":
    typer.run(main)
