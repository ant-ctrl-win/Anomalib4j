# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/correspondence/run.py
from collections import defaultdict
from collections.abc import Sequence
import csv
import json
from pathlib import Path
import sys
from typing import Final

import numpy as np
from pydantic import BaseModel, ConfigDict

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "robustness"))
from summary import Observation, Summary, RobustnessError, summarize, validate_pairing, TEXTURES

METHODS: Final = ("M2-P", "M2-N1", "M2-G")
GEOMETRY: Final = {"TRANSLATION_X": (-8, -4, -2, 2, 4, 8), "TRANSLATION_Y": (-8, -4, -2, 2, 4, 8),
                  "ROTATION": (-20, -10, -5, 5, 10, 20), "SCALE": (.9, .95, 1.05, 1.1)}


class MethodSummary(Summary):
    method: str


class Macro(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    group: str
    method: str
    family: str
    level: float
    categories: int
    median_delta: float
    mean_delta: float
    p90_delta: float
    p95_delta: float
    interior_median_delta: float
    interior_mean_delta: float
    interior_p90_delta: float
    interior_p95_delta: float
    exceedance_fraction: float


class Quality(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    method: str
    category: str
    image_auroc: float
    pixel_auroc: float
    aupro030: float


def write_models(path: Path, rows: Sequence[BaseModel]) -> None:
    with path.open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0].model_dump()))
        writer.writeheader()
        writer.writerows(r.model_dump() for r in rows)


def verify_p_pairs(current: Path, retained: Path) -> int:
    with current.open(newline="") as stream:
        actual = [Observation.model_validate(row) for row in csv.DictReader(stream)]
    with retained.open(newline="") as stream:
        expected = [Observation.model_validate(row) for row in csv.DictReader(stream)]
    validate_pairing(actual)
    validate_pairing(expected)
    lookup = {(r.category, r.image, r.family, r.level): r for r in expected}
    for row in actual:
        if row != lookup.get((row.category, row.image, row.family, row.level)):
            raise RobustnessError(f"M2-P retained pair mismatch: {row.image}/{row.family}/{row.level}")
    return len(actual)


def macros(rows: list[tuple[str, Summary]]) -> list[Macro]:
    output = []
    for group in ("all", "texture", "object"):
        groups: defaultdict[tuple[str, str, float], list[Summary]] = defaultdict(list)
        for method, row in rows:
            if group == "all" or (row.category in TEXTURES) == (group == "texture"):
                groups[method, row.family, row.level].append(row)
        for (method, family, level), selected in groups.items():
            values = {field: float(np.mean([getattr(r, field) for r in selected])) for field in Macro.model_fields
                      if field not in ("group", "method", "family", "level", "categories")}
            output.append(Macro(group=group, method=method, family=family, level=level, categories=len(selected), **values))
    return output


def collect(root: Path, categories: tuple[str, ...]) -> None:
    summaries = []
    quality = []
    references = []
    for category in categories:
        expected = set((root / "inputs" / category / "normal-test.txt").read_text().splitlines())
        for method in METHODS:
            directory = root / method / category
            with (directory / "robustness.csv").open(newline="") as stream:
                rows = [Observation.model_validate(row) for row in csv.DictReader(stream)]
            validate_pairing(rows)
            if len(rows) != len(expected)*22:
                raise RobustnessError("Incomplete geometric grid")
            for family, levels in GEOMETRY.items():
                for level in levels:
                    group = [r for r in rows if r.family == family and r.level == level]
                    if {r.image for r in group} != expected or any(r.category != category for r in group):
                        raise RobustnessError("Incorrect image pairing/coverage")
                    summaries.append((method, summarize(group)))
            original = {r.image: r for r in rows}.values()
            references.append({"category": category, "method": method, "reference": rows[0].reference,
                "n": len(expected), "original_exceedance": float(np.mean([r.s0 > r.reference for r in original]))})
            path = directory / ("metrics-retained.json" if method == "M2-P" else "metrics-unified.json")
            record = json.loads(path.read_text())
            if record["status"] != "verified" or record["category"] != category:
                raise RobustnessError("Unverified quality result")
            quality.append(Quality.model_validate({**record, "method": method}))
    write_models(root / "robustness-category.csv", [MethodSummary(**row.model_dump(), method=m) for m, row in summaries])
    write_models(root / "robustness-macro.csv", macros(summaries))
    write_models(root / "quality-category.csv", quality)
    quality_macro = []
    for group in ("all", "texture", "object"):
        for method in METHODS:
            selected = [r for r in quality if r.method == method and (group == "all" or (r.category in TEXTURES) == (group == "texture"))]
            if selected:
                quality_macro.append(Quality(method=method, category=group,
                    **{k: float(np.mean([getattr(r, k) for r in selected])) for k in ("image_auroc", "pixel_auroc", "aupro030")}))
    write_models(root / "quality-macro.csv", quality_macro)
    with (root / "references.json").open("x") as stream:
        json.dump(references, stream, indent=2)
