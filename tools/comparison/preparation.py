# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: imported by tools/comparison/run.py.
from __future__ import annotations

import json
import shutil
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from common import (CATEGORIES, METHODS, PRERUN, ROOT, ContractError, capture,
                    capture_hardware, checked_command, java_command,
                    sha256_file, validate_manifests, write_json)


@dataclass(frozen=True, slots=True)
class RunSettings:
    dataset: Path
    categories: tuple[str, ...]
    methods: tuple[str, ...]
    seed: int = 42

    def validate(self) -> None:
        for selection, allowed in ((self.categories, CATEGORIES), (self.methods, METHODS)):
            if not selection or len(set(selection)) != len(selection) or not set(selection) <= set(allowed):
                raise ContractError(f"Invalid run selection: {selection}")
        if not -(2**63) <= self.seed < 2**63:
            raise ContractError("Split seed must be a signed Java long")
        required = ("meta.json", "train/img", "train/ann", "test/img", "test/ann")
        if not (self.dataset / "meta.json").is_file() or any(
                not (self.dataset / part).is_dir() for part in required[1:]):
            raise ContractError(f"Missing DatasetNinja dataset at {self.dataset}; expected " + ", ".join(required))


def initialize(run: Path, smoke: bool, settings: RunSettings) -> None:
    settings.validate()
    if run.exists():
        raise ContractError(f"Use an absent run directory: {run}")
    run.mkdir(parents=True)
    capture(run / "workspace")
    shutil.copy2(ROOT / "docs/benchmark/FULL_DATASET_EXECUTION.md", run / "contract.md")
    shutil.copytree(PRERUN, run / "environment/prerun")
    checked_command(java_command("DatasetManifests", [str(settings.dataset), str(run / "data"),
                    str(settings.seed), ",".join(settings.categories)]), run / "split-generation.log")
    sources = sorted({*ROOT.glob("src/main/**/*.java"), *ROOT.glob("src/test/**/*.java"),
                      *ROOT.glob("src/test/resources/*"), *ROOT.glob("src/jmh/**/*.java"),
                      *ROOT.glob("tools/comparison/*.py"), *ROOT.glob("tools/prerun/*.*"),
                      *ROOT.glob("src/main/resources/models/*.onnx"), ROOT / "pom.xml",
                      ROOT / "docs/benchmark/FULL_DATASET_EXECUTION.md", PRERUN / "configurations.json"})
    checksums = {path.relative_to(ROOT).as_posix(): sha256_file(path) for path in sources if path.is_file()}
    write_json(run / "source-hashes.json", checksums)
    write_json(run / "manifest.json", {"format": "comparison-run/v2", "smoke": smoke,
               "created_at": datetime.now(timezone.utc).isoformat(), "seed": settings.seed,
               "projection_seed": 42, "split_policy": "floor(4*N/5), clamped to [1,N-2]; Java Random shuffle",
               "dataset": str(settings.dataset.resolve()), "categories": list(settings.categories),
               "method_order": list(settings.methods), "second_process_order": list(reversed(settings.methods)),
               "execution": "serial; fail-fast; new output directory only"})
    manifest_hashes = {path.relative_to(run).as_posix(): sha256_file(path)
                       for path in (run / "data").rglob("*") if path.is_file()}
    write_json(run / "manifest-hashes.json", manifest_hashes)
    validate_manifests(run)
    images = json.loads((run / "data/images.json").read_text(encoding="utf-8"))
    data_hashes = {"meta.json": sha256_file(settings.dataset / "meta.json")}
    for key, entries in images.items():
        role = key.split("/")[1]
        for entry in entries:
            for relative in (f"{role}/img/{entry['filename']}", f"{role}/ann/{entry['filename']}.json"):
                data_hashes[relative] = sha256_file(settings.dataset / relative)
    write_json(run / "dataset-hashes.json", data_hashes)
    capture_hardware(run / "hardware.json")
    (run / "checksums.sha256").write_text("".join(f"{digest}  {relative}\n" for relative, digest in checksums.items()), encoding="utf-8")
