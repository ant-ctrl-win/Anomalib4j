# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy"]
# ///
"""Positional-vs-global variance diagnostic (post-hoc).

Read-only means production and frozen inputs are not modified. Derived diagnostic
arrays, CSV and JSON are written under the local run diagnostics directory.

Question
--------
The M0 anomaly model learns one positional VSA archetype ``A_p`` per grid cell
and scores patch ``x`` at cell ``p`` with ``(1/sqrt(D)) R (x/max(||x||, eps))``.
The current archetype is exactly the direction of the positional offset

    A_p = offset[p] / ||offset[p]||
    offset[p, d] = (mu_pos[p, d] - mu_global[d]) / sigma_global[d]

H and offset magnitudes describe the observed positional/global statistics.
Large offsets or between-position dominance do not prove detector quality or
statistical reliability of an archetype direction.

What it computes
----------------
For each category (bottle, metal_nut) over the *fit-normal* split only:

* exact within/between decomposition of every dimension, in CNN patch space and
  in VSA (projected) space::

      (N*P - 1) * s_global[d]^2
        = (N - 1) * sum_p s_pos[p, d]^2
        + N * sum_p (mu_pos[p, d] - mu_global[d])^2

* ``H[d] = N * sum_p (mu_pos - mu_global)^2 / ((N*P - 1) * s_global[d]^2)``,
  the fraction of total sample sum-of-squares attributable to between-position
  differences, with distribution statistics;

* ``std_ratio[p, d] = sigma_pos[p, d] / sigma_global[d]`` (never called a
  variance ratio; ``variance_ratio = std_ratio^2``), using *observed* (unfloored)
  standard deviations only;

* ``||offset[p]||_2`` for every position, flagging unusually small magnitudes;

* numerical diagnostics: CNN descriptors with norm below the normalization
  epsilon, dimensions with zero observed standard deviation before flooring,
  dimensions actually affected by the standard-deviation floor.

Two CNN branches are reported.  The **normalized-CNN** branch uses the same
L2-normalized descriptors that feed the projection,
``u = x / max(||x||, 1e-6)``, so the CNN-vs-VSA comparison uses comparable
inputs.  The **pre-L2 legacy** branch (raw patch values, the original
diagnostic) is still computed and reported separately, clearly labelled, so no
scientific meaning is silently overwritten.

Data source
-----------
Per-position statistics are not stored in the frozen ``learning-state.bin``, so
the CNN patch descriptors are re-encoded with the *production* Java encoder
(``OnnxMobileNetV4Encoder``) via the read-only harness
``PositionalVarianceFeatureDump`` (exact same ONNX runtime, bicubic resize and
HWC grid as the frozen run).  The Java LCG Rademacher projection and every
statistic here are recomputed in Python.  Correctness is verified end-to-end
against the frozen ``learning-state.bin`` (global mean/std and every archetype).

No detector component is touched, no benchmark is rerun, metrics are unchanged.

Prerequisites: external DatasetNinja data at the fixed relative DATASET path,
frozen fit manifests and learning-state.bin under RUN, and the classpath generated
by tools/comparison/bootstrap.ps1. A fresh clone does not include those target
artifacts. See docs/TECHNICAL_OVERVIEW.md for the complete reproducibility map.

How to run (prepared local Python that has numpy)::

    target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe \
        tools/prerun/positional_global_variance_diagnostic.py
"""

from __future__ import annotations

import json
import math
import os
import struct
import subprocess
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "target/comparison/final-comparison-20261003"
DATASET = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
DIAG = RUN / "diagnostics/positional-global-variance"
DUMP = DIAG / "dump"
CLASSPATH_FILE = ROOT / "target/comparison-classpath.txt"

GRID = 14
CHANNELS = 96
VSA_DIM = 10000
PROJECTION_SEED = 42
NORM_EPS = 1e-6
STD_FLOOR = 1e-8
CATEGORIES = ("bottle", "metal_nut")

_MASK48 = (1 << 48) - 1
_MULT = 0x5DEECE66D
_ADD = 0xB


def java_rademacher_signs(channels: int, dimensions: int, seed: int) -> np.ndarray:
    """Reproduce DenseRademacherProjection: channel-first java.util.Random LCG."""

    state = (seed ^ _MULT) & _MASK48
    bits = np.empty(channels * dimensions, dtype=np.float64)
    for k in range(channels * dimensions):
        state = (state * _MULT + _ADD) & _MASK48
        bits[k] = 1.0 if (state >> 47) & 1 else -1.0
    return bits.reshape(channels, dimensions).T


def classpath() -> str:
    dependency = CLASSPATH_FILE.read_text(encoding="utf-8").strip()
    return os.pathsep.join([
        str(ROOT / "target/test-classes"),
        str(ROOT / "target/classes"),
        dependency,
    ])


def ensure_dump(category: str) -> Path:
    out = DUMP / category
    if (out / "features.npy").is_file() and (out / "meta.json").is_file():
        return out
    out.mkdir(parents=True, exist_ok=True)
    manifest = RUN / "data" / category / "anomalib4j-fit.txt"
    command = [
        "java", "-Xmx2g", "-cp", classpath(),
        "io.github.antctrlwin.anomalib4j.evaluation.PositionalVarianceFeatureDump",
        str(DATASET), str(out), str(manifest), category,
    ]
    subprocess.run(command, cwd=ROOT, check=True)
    return out


def read_learning_state(path: Path) -> dict:
    raw = path.read_bytes()
    tag_len = int.from_bytes(raw[0:2], "big")
    pos = 2 + tag_len
    dimensions = int.from_bytes(raw[pos:pos + 4], "big", signed=True)
    pos += 4
    mean = np.frombuffer(raw, dtype=">f8", count=dimensions, offset=pos).astype(np.float64)
    pos += 8 * dimensions
    sigma = np.frombuffer(raw, dtype=">f8", count=dimensions, offset=pos).astype(np.float64)
    pos += 8 * dimensions
    cells = int.from_bytes(raw[pos:pos + 4], "big", signed=True)
    pos += 4
    counts = np.empty(cells, dtype=np.int64)
    archetypes = np.empty((cells, dimensions), dtype=np.float64)
    for p in range(cells):
        counts[p] = struct.unpack_from(">q", raw, pos)[0]
        pos += 8
        archetypes[p] = np.frombuffer(raw, dtype=">f8", count=dimensions, offset=pos)
        pos += 8 * dimensions
    calibration = int.from_bytes(raw[pos:pos + 4], "big", signed=True)
    return {"dimensions": dimensions, "cells": cells, "mean": mean, "sigma": sigma,
            "counts": counts, "archetypes": archetypes, "calibration": calibration}


def dist_stats(values: np.ndarray) -> dict:
    flat = np.asarray(values, dtype=np.float64).ravel()
    finite = flat[np.isfinite(flat)]
    return {
        "n": int(flat.size),
        "finite": int(finite.size),
        "non_finite": int(flat.size - finite.size),
        "min": float(finite.min()) if finite.size else None,
        "p10": float(np.percentile(finite, 10)) if finite.size else None,
        "p25": float(np.percentile(finite, 25)) if finite.size else None,
        "median": float(np.median(finite)) if finite.size else None,
        "p75": float(np.percentile(finite, 75)) if finite.size else None,
        "p90": float(np.percentile(finite, 90)) if finite.size else None,
        "max": float(finite.max()) if finite.size else None,
        "frac_gt_0.25": float(np.mean(flat > 0.25)),
        "frac_gt_0.50": float(np.mean(flat > 0.50)),
        "frac_gt_0.75": float(np.mean(flat > 0.75)),
    }


def space_stats(sum_: np.ndarray, sumsq: np.ndarray, n: int) -> tuple[np.ndarray, np.ndarray]:
    mean = sum_ / n
    variance = np.clip((sumsq - sum_ * sum_ / n) / (n - 1), 0.0, None)
    return mean, variance


def decomposition(global_sum: np.ndarray, global_sumsq: np.ndarray, pos_sum: np.ndarray,
                  pos_sumsq: np.ndarray, n_images: int) -> dict:
    cells = pos_sum.shape[0]
    n_global = n_images * cells
    mu_global, var_global = space_stats(global_sum, global_sumsq, n_global)
    mu_pos, var_pos = space_stats(pos_sum, pos_sumsq, n_images)
    lhs = (n_global - 1) * var_global
    within = (n_images - 1) * var_pos.sum(axis=0)
    between = n_images * ((mu_pos - mu_global) ** 2).sum(axis=0)
    residual = np.abs(lhs - within - between)
    h = np.where(lhs > 0, between / np.where(lhs > 0, lhs, 1.0), np.nan)
    return {"mu_global": mu_global, "var_global": var_global, "mu_pos": mu_pos,
            "var_pos": var_pos, "lhs": lhs, "within": within, "between": between,
            "residual_max": float(residual.max()), "residual_scale": float(lhs.max()),
            "H": h}


def analyze(category: str) -> dict:
    dump = ensure_dump(category)
    features = np.load(dump / "features.npy").astype(np.float64)
    n_images = features.shape[0]
    cells = GRID * GRID
    patches = features.reshape(n_images, cells, CHANNELS)

    cnn_global_sum = patches.sum(axis=(0, 1))
    cnn_global_sumsq = (patches * patches).sum(axis=(0, 1))
    cnn_pos_sum = patches.sum(axis=0)
    cnn_pos_sumsq = (patches * patches).sum(axis=0)

    norms = np.linalg.norm(patches, axis=2)
    low_norm_count = int(np.count_nonzero(norms < NORM_EPS))

    unit_patches = patches / np.maximum(norms, NORM_EPS)[:, :, None]
    cnn_norm_global_sum = unit_patches.sum(axis=(0, 1))
    cnn_norm_global_sumsq = (unit_patches * unit_patches).sum(axis=(0, 1))
    cnn_norm_pos_sum = unit_patches.sum(axis=0)
    cnn_norm_pos_sumsq = (unit_patches * unit_patches).sum(axis=0)

    signs = java_rademacher_signs(CHANNELS, VSA_DIM, PROJECTION_SEED)
    root_d = math.sqrt(VSA_DIM)

    vsa_global_sum = np.zeros(VSA_DIM)
    vsa_global_sumsq = np.zeros(VSA_DIM)
    vsa_pos_sum = np.zeros((cells, VSA_DIM))
    vsa_pos_sumsq = np.zeros((cells, VSA_DIM))
    for i in range(n_images):
        patch = patches[i]
        scale = np.maximum(norms[i], NORM_EPS)
        projected = (patch / scale[:, None]) @ signs.T / root_d
        vsa_global_sum += projected.sum(axis=0)
        vsa_global_sumsq += (projected * projected).sum(axis=0)
        vsa_pos_sum += projected
        vsa_pos_sumsq += projected * projected

    cnn_prel2 = decomposition(cnn_global_sum, cnn_global_sumsq, cnn_pos_sum, cnn_pos_sumsq, n_images)
    cnn_norm = decomposition(cnn_norm_global_sum, cnn_norm_global_sumsq,
                             cnn_norm_pos_sum, cnn_norm_pos_sumsq, n_images)
    vsa = decomposition(vsa_global_sum, vsa_global_sumsq, vsa_pos_sum, vsa_pos_sumsq, n_images)

    n_global = n_images * cells
    sigma_global = np.sqrt(vsa["var_global"])
    floored = sigma_global < STD_FLOOR
    sigma_global_floored = np.maximum(sigma_global, STD_FLOOR)
    zero_global_std = int(np.count_nonzero(sigma_global == 0.0))
    cnn_zero_std = int(np.count_nonzero(cnn_norm["var_global"] == 0.0))
    cnn_prel2_zero_std = int(np.count_nonzero(cnn_prel2["var_global"] == 0.0))

    sigma_pos = np.sqrt(vsa["var_pos"])
    std_ratio = np.where(sigma_global > 0, sigma_pos / np.where(sigma_global > 0, sigma_global, 1.0), np.nan)

    sigma_global_cnn = np.sqrt(cnn_norm["var_global"])
    sigma_pos_cnn = np.sqrt(cnn_norm["var_pos"])
    std_ratio_cnn = np.where(sigma_global_cnn > 0,
                             sigma_pos_cnn / np.where(sigma_global_cnn > 0, sigma_global_cnn, 1.0), np.nan)
    sigma_global_cnn_legacy = np.sqrt(cnn_prel2["var_global"])
    sigma_pos_cnn_legacy = np.sqrt(cnn_prel2["var_pos"])
    std_ratio_cnn_legacy = np.where(sigma_global_cnn_legacy > 0,
                                    sigma_pos_cnn_legacy / np.where(sigma_global_cnn_legacy > 0,
                                                                    sigma_global_cnn_legacy, 1.0), np.nan)

    offset = np.where(sigma_global > 0, (vsa["mu_pos"] - vsa["mu_global"]) / np.where(sigma_global > 0, sigma_global, 1.0), 0.0)
    offset_norm = np.linalg.norm(offset, axis=1)

    frozen = read_learning_state(RUN / "anomalib4j" / category / "learning-state.bin")
    mean_diff = float(np.abs(frozen["mean"] - vsa["mu_global"]).max())
    sigma_diff = float(np.abs(frozen["sigma"] - sigma_global_floored).max())
    zdir = (vsa["mu_pos"] - frozen["mean"][None, :]) / frozen["sigma"][None, :]
    archetype_pred = zdir / np.linalg.norm(zdir, axis=1)[:, None]
    archetype_diff = np.abs(archetype_pred - frozen["archetypes"]).max(axis=1)

    flagged = sorted(
        ({"position": int(p), "row": int(p // GRID), "column": int(p % GRID),
          "offset_norm": float(offset_norm[p])}
         for p in range(cells) if offset_norm[p] < max(0.05, float(np.percentile(offset_norm, 10)))),
        key=lambda entry: entry["offset_norm"])

    summary = {
        "category": category,
        "fit_images": int(n_images),
        "cells": cells,
        "global_samples": int(n_global),
        "projection_seed": PROJECTION_SEED,
        "norm_epsilon": NORM_EPS,
        "std_floor": STD_FLOOR,
        "cnn_descriptors_below_norm_epsilon": low_norm_count,
        "cnn_descriptors_below_norm_epsilon_fraction": low_norm_count / n_global,
        "cnn_dimensions_zero_observed_std_before_floor": cnn_zero_std,
        "cnn_dimensions_zero_observed_std_before_floor_preL2_legacy": cnn_prel2_zero_std,
        "cnn_dimensions_total": CHANNELS,
        "vsa_dimensions_zero_observed_std_before_floor": zero_global_std,
        "vsa_dimensions_total": VSA_DIM,
        "vsa_dimensions_affected_by_std_floor": int(np.count_nonzero(floored)),
        "vsa_dimensions_affected_by_std_floor_fraction": float(np.count_nonzero(floored) / VSA_DIM),
        "decomposition_residual_max_vsa": vsa["residual_max"],
        "decomposition_residual_max_cnn_normalized": cnn_norm["residual_max"],
        "decomposition_residual_max_cnn_preL2_legacy": cnn_prel2["residual_max"],
        "H_vsa": dist_stats(vsa["H"]),
        "H_cnn_normalized": dist_stats(cnn_norm["H"]),
        "H_cnn_preL2_legacy": dist_stats(cnn_prel2["H"]),
        "std_ratio_vsa": dist_stats(std_ratio),
        "variance_ratio_vsa": dist_stats(std_ratio ** 2),
        "std_ratio_cnn_normalized": dist_stats(std_ratio_cnn),
        "std_ratio_cnn_preL2_legacy": dist_stats(std_ratio_cnn_legacy),
        "offset_norm": dist_stats(offset_norm),
        "offset_norm_flagged_positions": flagged,
        "validation": {
            "global_mean_max_abs_diff": mean_diff,
            "global_sigma_floored_max_abs_diff": sigma_diff,
            "archetype_max_abs_diff": float(archetype_diff.max()),
            "archetype_max_abs_diff_median": float(np.median(archetype_diff)),
            "frozen_cells": int(frozen["cells"]),
            "frozen_calibration_count": int(frozen["calibration"]),
        },
    }

    _write_csv(DIAG / f"{category}_vsa_h.csv", [
        {"dimension": d, "H": float(vsa["H"][d]), "var_global": float(vsa["var_global"][d]),
         "var_between": float(vsa["between"][d]), "var_within": float(vsa["within"][d])}
        for d in range(VSA_DIM)])
    _write_csv(DIAG / f"{category}_cnn_normalized_h.csv", [
        {"channel": c, "H": float(cnn_norm["H"][c]), "var_global": float(cnn_norm["var_global"][c]),
         "var_between": float(cnn_norm["between"][c]), "var_within": float(cnn_norm["within"][c])}
        for c in range(CHANNELS)])
    _write_csv(DIAG / f"{category}_cnn_preL2_legacy_h.csv", [
        {"channel": c, "H": float(cnn_prel2["H"][c]), "var_global": float(cnn_prel2["var_global"][c]),
         "var_between": float(cnn_prel2["between"][c]), "var_within": float(cnn_prel2["within"][c])}
        for c in range(CHANNELS)])
    _write_csv(DIAG / f"{category}_vsa_offset.csv", [
        {"position": p, "row": p // GRID, "column": p % GRID,
         "offset_norm": float(offset_norm[p]),
         "std_ratio_median": float(np.nanmedian(std_ratio[p])),
         "flagged_small_offset": bool(any(entry["position"] == p for entry in flagged))}
        for p in range(cells)])

    (DIAG / f"{category}_summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    return summary


def _write_csv(path: Path, rows: list[dict]) -> None:
    import csv

    if not rows:
        return
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    DIAG.mkdir(parents=True, exist_ok=True)
    summaries = {category: analyze(category) for category in CATEGORIES}
    (DIAG / "summary.json").write_text(json.dumps(summaries, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(_report_view(summaries), indent=2))


def _report_view(summaries: dict) -> dict:
    view = {}
    for category, summary in summaries.items():
        view[category] = {
            key: summary[key]
            for key in ("fit_images", "global_samples", "H_vsa", "H_cnn_normalized",
                        "H_cnn_preL2_legacy", "std_ratio_vsa", "std_ratio_cnn_normalized",
                        "std_ratio_cnn_preL2_legacy",
                        "offset_norm", "cnn_descriptors_below_norm_epsilon",
                        "cnn_descriptors_below_norm_epsilon_fraction",
                        "cnn_dimensions_zero_observed_std_before_floor",
                        "cnn_dimensions_zero_observed_std_before_floor_preL2_legacy",
                        "vsa_dimensions_zero_observed_std_before_floor",
                        "vsa_dimensions_affected_by_std_floor",
                        "vsa_dimensions_affected_by_std_floor_fraction",
                        "decomposition_residual_max_vsa", "decomposition_residual_max_cnn_normalized",
                        "decomposition_residual_max_cnn_preL2_legacy",
                        "validation")
        }
    return view


if __name__ == "__main__":
    main()
