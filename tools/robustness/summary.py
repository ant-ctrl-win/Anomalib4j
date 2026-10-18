# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "matplotlib"]
# ///
# How to run: imported by tools/robustness/run.py
from collections import defaultdict
import csv
from pathlib import Path
from typing import Final, Literal

import numpy as np
from pydantic import BaseModel, ConfigDict

Family = Literal["TRANSLATION_X", "TRANSLATION_Y", "ROTATION", "SCALE", "BRIGHTNESS", "CONTRAST", "GAMMA"]
GRID: Final = {"TRANSLATION_X": (-8, -4, -2, 0, 2, 4, 8), "TRANSLATION_Y": (-8, -4, -2, 0, 2, 4, 8),
    "ROTATION": (-20, -10, -5, 0, 5, 10, 20), "SCALE": (.9, .95, 1., 1.05, 1.1),
    "BRIGHTNESS": (.8, .9, 1., 1.1, 1.2), "CONTRAST": (.8, .9, 1., 1.1, 1.2), "GAMMA": (.8, .9, 1., 1.1, 1.2)}
TEXTURES: Final = frozenset(("carpet", "grid", "leather", "tile", "wood"))


class RobustnessError(RuntimeError):
    pass


class Observation(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    category: str
    image: str
    family: Family
    level: float
    s0: float
    st: float
    delta: float
    interior_s0: float
    interior_st: float
    interior_delta: float
    reference: float
    exceeds: bool


class Summary(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    category: str
    family: Family
    level: float
    n: int
    median_s0: float
    median_st: float
    median_delta: float
    mean_delta: float
    p90_delta: float
    p95_delta: float
    max_delta: float
    interior_median_s0: float
    interior_median_st: float
    interior_median_delta: float
    interior_mean_delta: float
    interior_p90_delta: float
    interior_p95_delta: float
    interior_max_delta: float
    reference: float
    exceedance_fraction: float


def validate_pairing(rows: list[Observation]) -> None:
    originals: dict[tuple[str, str], tuple[float, float, float]] = {}
    seen: set[tuple[str, str, Family, float]] = set()
    for row in rows:
        key = (row.category, row.image)
        original = (row.s0, row.interior_s0, row.reference)
        if originals.setdefault(key, original) != original:
            raise RobustnessError(f"Inconsistent original for {key}")
        observation = (*key, row.family, row.level)
        if observation in seen:
            raise RobustnessError(f"Duplicate image/level: {observation}")
        seen.add(observation)
        if row.level not in GRID[row.family] or not np.isclose(row.delta, row.st-row.s0, rtol=0, atol=1e-12):
            raise RobustnessError(f"Invalid delta/grid: {observation}")
        if not np.isclose(row.interior_delta, row.interior_st-row.interior_s0, rtol=0, atol=1e-12):
            raise RobustnessError(f"Invalid interior pair: {observation}")
        if row.exceeds != (row.st > row.reference):
            raise RobustnessError(f"Invalid reference indicator: {observation}")


def summarize(rows: list[Observation]) -> Summary:
    validate_pairing(rows)
    first = rows[0]
    if any((r.category, r.family, r.level, r.reference) != (first.category, first.family, first.level, first.reference) for r in rows):
        raise RobustnessError("Mixed summary group")
    values = {}
    for prefix in ("", "interior_"):
        delta = np.array([getattr(r, prefix + "delta") for r in rows])
        values.update({prefix + "median_s0": float(np.median([getattr(r, prefix + "s0") for r in rows])),
            prefix + "median_st": float(np.median([getattr(r, prefix + "st") for r in rows])),
            prefix + "median_delta": float(np.median(delta)), prefix + "mean_delta": float(delta.mean()),
            prefix + "p90_delta": float(np.quantile(delta, .90)), prefix + "p95_delta": float(np.quantile(delta, .95)),
            prefix + "max_delta": float(delta.max())})
    return Summary(category=first.category, family=first.family, level=first.level, n=len(rows),
                   reference=first.reference, exceedance_fraction=np.mean([r.exceeds for r in rows]), **values)


def aggregate(root: Path, categories: tuple[str, ...]) -> list[Summary]:
    groups: defaultdict[tuple[str, Family, float], list[Observation]] = defaultdict(list)
    for category in categories:
        with (root / category / "per-image.csv").open(newline="") as stream:
            rows = [Observation.model_validate(row) for row in csv.DictReader(stream)]
        validate_pairing(rows)
        expected = set((root / category / "normal-test.txt").read_text().splitlines())
        for family, levels in GRID.items():
            for level in levels:
                observed = {r.image for r in rows if r.family == family and r.level == level and r.category == category}
                if observed != expected:
                    raise RobustnessError(f"Incomplete paired grid: {category}/{family}/{level}")
        for row in rows:
            groups[row.category, row.family, row.level].append(row)
    summaries = [summarize(rows) for rows in groups.values()]
    with (root / "aggregate.csv").open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(Summary.model_fields))
        writer.writeheader()
        writer.writerows(row.model_dump() for row in summaries)
    (root / "aggregate.json").write_text("[\n" + ",\n".join(row.model_dump_json() for row in summaries) + "\n]")
    return summaries


def plots(root: Path, summaries: list[Summary]) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    directory = root / "plots"
    directory.mkdir()
    macro_rows = []
    for family, levels in GRID.items():
        fig, axes = plt.subplots(1, 2, figsize=(11, 4), constrained_layout=True)
        for group, color in (("all", "black"), ("texture", "tab:blue"), ("object", "tab:orange")):
            selected = [r for r in summaries if r.family == family and (group == "all" or (r.category in TEXTURES) == (group == "texture"))]
            if not selected:
                continue
            full, interior, exceedance = [], [], []
            for level in levels:
                rows = [r for r in selected if r.level == level]
                full.append(float(np.mean([r.median_delta for r in rows])))
                interior.append(float(np.mean([r.interior_median_delta for r in rows])))
                exceedance.append(float(np.mean([r.exceedance_fraction for r in rows])))
                macro_rows.append({"group": group, "family": family, "level": level, "categories": len(rows),
                    "mean_category_median_delta": full[-1], "mean_category_interior_median_delta": interior[-1],
                    "mean_category_exceedance": exceedance[-1]})
            axes[0].plot(levels, full, "o-", color=color, label=group + " full")
            axes[0].plot(levels, interior, "--", color=color, label=group + " interior")
            axes[1].plot(levels, exceedance, "o-", color=color, label=group)
        axes[0].axhline(0, color="grey", linewidth=.5)
        axes[0].set_ylabel("Mean of category median paired delta (Z)")
        axes[1].set_ylabel("Mean category diagnostic exceedance fraction")
        axes[1].set_ylim(0, 1)
        for ax in axes:
            ax.set_xlabel(family + " level")
            ax.grid(alpha=.2)
            ax.legend(fontsize=8)
        fig.savefig(directory / (family.lower() + ".png"), dpi=150)
        plt.close(fig)
    with (root / "macro.csv").open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(macro_rows[0]))
        writer.writeheader()
        writer.writerows(macro_rows)
