# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/oracle/run.py
import csv
from pathlib import Path
from typing import Final, Literal

import numpy as np
from pydantic import BaseModel, ConfigDict

GRID: Final = {"ROTATION": (-20., -10., -5., 5., 10., 20.), "SCALE": (.9, .95, 1.05, 1.1)}
TEXTURES: Final = frozenset(("carpet", "grid", "leather", "tile", "wood"))
RECOVERY_MIN: Final = .1


class OracleError(RuntimeError):
    pass


class Pair(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    category: str
    image: str
    family: Literal["ROTATION", "SCALE"]
    level: float
    s0: float
    sp: float
    sc: float
    interior_s0: float
    interior_sp: float
    interior_sc: float


class Observation(Pair):
    map_mae: float
    map_rmse: float
    map_max: float
    interior_map_mae: float
    interior_map_rmse: float
    interior_map_max: float
    rgb_mae: float
    rgb_rmse: float
    rgb_max: float
    perturbed_map_mae: float
    perturbed_map_rmse: float
    perturbed_map_max: float
    perturbed_interior_map_mae: float
    perturbed_interior_map_rmse: float
    perturbed_interior_map_max: float


class ScoreSummary(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    median_dp: float
    median_dc: float
    mean_dc: float
    p90_dc: float
    p95_dc: float
    recovery_count: int
    median_recovery: float | None
    within_1: float
    within_2: float
    within_5: float


def score_summary(dp: np.ndarray, dc: np.ndarray) -> ScoreSummary:
    valid = np.abs(dp) > RECOVERY_MIN
    ratios = 1 - dc[valid] / dp[valid]
    return ScoreSummary(median_dp=np.median(dp), median_dc=np.median(dc), mean_dc=dc.mean(),
        p90_dc=np.quantile(dc, .9), p95_dc=np.quantile(dc, .95), recovery_count=int(valid.sum()),
        median_recovery=float(np.median(ratios)) if ratios.size else None,
        within_1=np.mean(np.abs(dc) <= 1), within_2=np.mean(np.abs(dc) <= 2), within_5=np.mean(np.abs(dc) <= 5))


def validate_pairs(rows: list[Pair]) -> None:
    originals: dict[tuple[str, str], tuple[float, float]] = {}
    seen: set[tuple[str, str, str, float]] = set()
    for r in rows:
        key = (r.category, r.image)
        original = (r.s0, r.interior_s0)
        if originals.setdefault(key, original) != original:
            raise OracleError(f"Inconsistent baseline: {key}")
        observation = (*key, r.family, r.level)
        if observation in seen or r.level not in GRID[r.family]:
            raise OracleError(f"Duplicate/invalid observation: {observation}")
        seen.add(observation)


def collect(root: Path, retained: Path) -> None:
    """Validate stored maps and retained score pairs, then aggregate equal categories."""
    summaries = []
    for directory in sorted(p for p in root.iterdir() if (p / "per-image.csv").exists()):
        category = directory.name
        with (directory / "per-image.csv").open(newline="") as stream:
            rows = [Observation.model_validate(r) for r in csv.DictReader(stream)]
        validate_pairs(rows)
        names = (directory / "normal-test.txt").read_text().splitlines()
        if len(rows) != len(names) * 10:
            raise OracleError("Incomplete image coverage")
        with (retained / category / "per-image.csv").open(newline="") as stream:
            old = {(r["image"], r["family"], float(r["level"])): r for r in csv.DictReader(stream)}
        baseline = np.load(directory / "baseline-z.npy", allow_pickle=False)
        perturbed = np.load(directory / "perturbed-z.npy", allow_pickle=False)
        corrected = np.load(directory / "canonicalized-z.npy", allow_pickle=False)
        if baseline.shape != (len(names), 196) or perturbed.shape != (len(rows), 196) or corrected.shape != perturbed.shape:
            raise OracleError("Unexpected native map dimensions")
        if not all(np.isfinite(a).all() for a in (baseline, perturbed, corrected)):
            raise OracleError("Nonfinite native maps")
        for i, r in enumerate(rows):
            prior = old[r.image, r.family, r.level]
            for new, previous in (("s0", "s0"), ("sp", "st"), ("interior_s0", "interior_s0"), ("interior_sp", "interior_st")):
                if getattr(r, new) != float(prior[previous]):
                    raise OracleError(f"Retained pair changed: {category}/{r.image}/{r.level}")
            z0 = baseline[names.index(r.image)].reshape(14, 14)
            zp, zc = perturbed[i].reshape(14, 14), corrected[i].reshape(14, 14)
            for prefix, selection in (("", np.s_[:, :]), ("interior_", np.s_[1:13, 1:13])):
                for field, z in (("s0", z0), ("sp", zp), ("sc", zc)):
                    if getattr(r, prefix + field) != z[selection].max():
                        raise OracleError("CSV/native-map score mismatch")
                for stem, z in ((prefix + "map_", zc), ("perturbed_" + prefix + "map_", zp)):
                    diff = z[selection] - z0[selection]
                    values = (np.abs(diff).mean(), np.sqrt(np.square(diff).mean()), np.abs(diff).max())
                    np.testing.assert_allclose([getattr(r, stem + m) for m in ("mae", "rmse", "max")], values, rtol=1e-12, atol=1e-12)
        for family, levels in GRID.items():
            for level in levels:
                group = [r for r in rows if r.family == family and r.level == level]
                if {r.image for r in group} != set(names) or any(r.category != category for r in group):
                    raise OracleError("Unpaired category/level coverage")
                result = {"category": category, "family": family, "level": level, "n": len(group)}
                for prefix in ("", "interior_"):
                    dp = np.array([getattr(r, prefix + "sp")-getattr(r, prefix + "s0") for r in group])
                    dc = np.array([getattr(r, prefix + "sc")-getattr(r, prefix + "s0") for r in group])
                    result.update({prefix + k: v for k, v in score_summary(dp, dc).model_dump().items()})
                for key in Observation.model_fields.keys() - Pair.model_fields.keys():
                    values = np.array([getattr(r, key) for r in group])
                    result[key + "_mean"] = float(values.mean())
                    if key.endswith("max"):
                        result[key + "_observed"] = float(values.max())
                rgb = np.array([r.rgb_rmse for r in group])
                for key, values in (("map", [r.map_rmse for r in group]), ("score", [abs(r.sc-r.s0) for r in group])):
                    result["rgb_" + key + "_pearson"] = float(np.corrcoef(rgb, values)[0, 1]) if rgb.std() > 0 and np.std(values) > 0 else None
                summaries.append(result)
        (directory / "parity.json").write_text('{"exact_retained_pairs":' + str(len(rows)) + ',"native_map_validation":true}\n')
    with (root / "aggregate.csv").open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    macros = []
    for name, categories in (("all", {r["category"] for r in summaries}), ("texture", TEXTURES),
                             ("object", {r["category"] for r in summaries} - TEXTURES)):
        for family, levels in GRID.items():
            for level in levels:
                group = [r for r in summaries if r["category"] in categories and r["family"] == family and r["level"] == level]
                if not group:
                    continue
                result = {"group": name, "family": family, "level": level, "categories": len(group)}
                for key in summaries[0].keys() - {"category", "family", "level", "n"}:
                    values = [r[key] for r in group if r[key] is not None]
                    result[key] = float(np.mean(values)) if values else None
                macros.append(result)
    with (root / "macro.csv").open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(macros[0]))
        writer.writeheader()
        writer.writerows(macros)
