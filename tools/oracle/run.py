# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/oracle/run.py --output target/robustness/m2-oracle-canonicalization-20261019
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
from common import capture, capture_hardware, write_json
from progress import phase, stream_command
from oracle_results import GRID, RECOVERY_MIN, OracleError, collect


def main(output: Path = typer.Option(...), category: str | None = None) -> None:
    started = perf_counter()
    selected = (category,) if category else tuple(CATEGORIES)
    if not set(selected) <= set(CATEGORIES):
        raise OracleError("Unknown category")
    destination = output.resolve()
    baseline = ROOT / "target/comparison/m0-all15-baseline-20261016"
    retained = ROOT / "target/robustness/m2-controlled-20261018"
    if destination.exists() or any(p == destination or p in destination.parents for p in (baseline, retained)):
        raise OracleError("Output must be new and outside retained evidence")
    print("PREFLIGHT - frozen sources/models/dataset - started", flush=True)
    contract = baseline_contract(baseline, selected, verify_data=True)
    if destination == contract.dataset or contract.dataset in destination.parents:
        raise OracleError("Output cannot be inside dataset")
    hashes = TypeAdapter(dict[str, str]).validate_json((retained / "source-hashes.json").read_text())
    frozen = {name: digest for name, digest in hashes.items()
              if name.startswith("src/main/") or Path(name).name in ("CanonicalRgb.java", "RobustnessTransforms.java")}
    for name, digest in frozen.items():
        if sha256_file(ROOT / name) != digest:
            raise OracleError(f"Frozen source changed: {name}")
    destination.mkdir(parents=True)
    capture(destination / "workspace")
    capture_hardware(destination / "hardware.json")
    sources = {p for pattern in ("src/**/*.java", "tools/oracle/*.py", "tools/ablation/*.py") for p in ROOT.glob(pattern)}
    sources.add(ROOT / "docs/experiments/M2_ORACLE_CANONICALIZATION_PLAN.md")
    write_json(destination / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p) for p in sorted(sources)})
    write_json(destination / "plan.json", {"categories": selected, "grid": GRID, "recovery_min_abs_dp": RECOVERY_MIN,
        "dataset": str(contract.dataset), "models_and_normal_set": str(retained), "baseline": str(baseline),
        "command": sys.argv, "python": sys.version, "numpy": np.__version__, "refit": False,
        "production_sources_verified": frozen})
    shutil.copytree(baseline / "data", destination / "data")
    progress = destination / "progress.log"
    for index, name in enumerate(selected, 1):
        label = f"[{index:02}/{len(selected):02}] {name}"
        directory = destination / name
        directory.mkdir()
        split = read_split(destination / "data", name)
        prior = retained / name
        prior_hashes = TypeAdapter(dict[str, str]).validate_json((prior / "input-hashes.json").read_text())
        model = prior / "model.bin"
        if sha256_file(model) != prior_hashes[str(model)]:
            raise OracleError("Retained archive changed")
        normals = (prior / "normal-test.txt").read_text().splitlines()
        if normals != [n for n in split.test if n.startswith(name + "_good_")]:
            raise OracleError("Normal manifest changed")
        for filename in ("model.bin", "normal-test.txt"):
            shutil.copyfile(prior / filename, directory / filename)
        write_json(directory / "input-hashes.json", {str(prior / f): sha256_file(prior / f)
                   for f in ("model.bin", "normal-test.txt", "per-image.csv")})
        with phase(progress, label, "ORACLE ROUND TRIP"):
            stream_command(java_command("OracleRunner", [str(directory), str(contract.dataset), name]), directory / "java.log", progress)
    with phase(progress, "CAMPAIGN", "PAIR/MAP VALIDATION AND SUMMARIES"):
        collect(destination, retained)
    measurement_seconds = perf_counter()-started
    if "bottle" in selected:
        with phase(progress, "CAMPAIGN", "INVERSE WARP TIMING"):
            for fork in range(3):
                stream_command(java_command("OracleTiming", [str(destination / "bottle"), str(contract.dataset),
                    str(destination / f"timing-{fork}.csv"), str(fork)]), destination / f"timing-{fork}.log", progress)
    for name, digest in frozen.items():
        if sha256_file(ROOT / name) != digest:
            raise OracleError(f"Frozen source changed during execution: {name}")
    write_json(destination / "complete.json", {"categories": selected, "measurement_seconds": measurement_seconds,
               "wall_seconds_including_timing": perf_counter()-started, "production_unchanged": True})


if __name__ == "__main__":
    typer.run(main)
