# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/ablation/run.py.
from dataclasses import dataclass
import json
from pathlib import Path
import sys
from typing import Final

import numpy as np
from pydantic import BaseModel, ConfigDict

from models import Array, ModelError, normalize

ROOT: Final = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/comparison"))
from common import CATEGORIES, checked_command, java_command, sha256_file, validate_manifests

METHODS: Final = ("C1", "V1", "M2")
ROLE_FILES: Final = {"fit": "anomalib4j-fit.txt", "calibration": "anomalib4j-calibration.txt", "test": "test.txt"}


class BaselineManifest(BaseModel):
    model_config = ConfigDict(frozen=True)
    dataset: Path
    categories: tuple[str, ...]
    smoke: bool
    seed: int
    projection_seed: int


@dataclass(frozen=True, slots=True)
class Split:
    fit: tuple[str, ...]
    calibration: tuple[str, ...]
    test: tuple[str, ...]


def read_split(directory: Path, category: str) -> Split:
    values = {role: tuple((directory / category / file).read_text(encoding="utf-8-sig").splitlines())
              for role, file in ROLE_FILES.items()}
    for role, names in values.items():
        if len(names) < 2 or len(set(names)) != len(names):
            raise ModelError(f"Invalid {category}/{role} cardinality or duplicates")
        if any(Path(n).name != n or not n.startswith(category + "_") or "/" in n or "\\" in n for n in names):
            raise ModelError(f"Invalid {category}/{role} filenames")
    if set(values["fit"]) & set(values["calibration"]):
        raise ModelError("Fit/calibration overlap")
    if any(not n.startswith(category + "_good_") for n in values["fit"] + values["calibration"]):
        raise ModelError("Non-normal training input")
    return Split(**values)


def baseline_contract(baseline: Path, categories: tuple[str, ...], verify_data: bool) -> BaselineManifest:
    manifest = BaselineManifest.model_validate_json((baseline / "manifest.json").read_text())
    if manifest.smoke or manifest.seed != 42 or manifest.projection_seed != 42:
        raise ModelError("Expected completed seed-42 non-smoke M0 baseline")
    if not categories or not set(categories) <= set(CATEGORIES) or not set(categories) <= set(manifest.categories):
        raise ModelError("Invalid category selection")
    validate_manifests(baseline)
    protected = ("LocalizationMaps.java", "LocalizationMetrics.java", "DatasetNinjaMasks.java",
                 "ComparisonEvaluator.java", "PositionalVarianceFeatureDump.java", "ImageNetPreprocessor.java")
    sources = json.loads((baseline / "source-hashes.json").read_text())
    required = [p.relative_to(ROOT).as_posix() for p in (ROOT / "src/main").rglob("*") if p.is_file()]
    required += [p.relative_to(ROOT).as_posix() for p in (ROOT / "src/test").rglob("*.java") if p.name in protected]
    for relative in required:
        if sources.get(relative) != sha256_file(ROOT / relative):
            raise ModelError(f"M0 source/asset identity mismatch: {relative}")
    hashes = json.loads((baseline / "dataset-hashes.json").read_text())
    for category in categories:
        split = read_split(baseline / "data", category)
        metrics = json.loads((baseline / "anomalib4j" / category / "metrics-unified.json").read_text())
        if metrics.get("status") != "verified" or metrics.get("category") != category or metrics.get("method") != "anomalib4j":
            raise ModelError(f"Missing verified M0 metrics: {category}")
        if metrics.get("dataset_manifest_sha256") != sha256_file(baseline / "data" / category / "test.txt"):
            raise ModelError(f"M0 metric/manifest mismatch: {category}")
        if verify_data:
            paths = ["meta.json"]
            for role, names in (("train", split.fit + split.calibration), ("test", split.test)):
                paths += [f"{role}/{kind}/{name}{suffix}" for name in names
                          for kind, suffix in (("img", ""), ("ann", ".json"))]
            for relative in paths:
                if hashes.get(relative) != sha256_file(manifest.dataset / relative):
                    raise ModelError(f"M0 dataset identity mismatch: {relative}")
    return manifest


def extract(dataset: Path, output: Path, manifests: Path, category: str, role: str) -> Array:
    directory = output / "features" / category / role
    directory.mkdir(parents=True, exist_ok=False)
    manifest = manifests / category / ROLE_FILES[role]
    checked_command(java_command("PositionalVarianceFeatureDump", [str(dataset), str(directory), str(manifest),
                    category, "test" if role == "test" else "train"]), directory / "extract.log")
    if (directory / "images.txt").read_text().splitlines() != manifest.read_text().splitlines():
        raise ModelError("Feature/manifest ordering mismatch")
    values = np.load(directory / "features.npy", allow_pickle=False)
    if values.shape != (len(manifest.read_text().splitlines()), 14, 14, 96):
        raise ModelError("Unexpected encoder geometry")
    return normalize(values.reshape(len(values), 196, 96))
