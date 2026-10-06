"""RAW-vs-Z diagnostic for the Anomalib4j Metal Nut case (read-only).

Uses only frozen artifacts in

    target/comparison/final-comparison-20261003/anomalib4j/metal_nut/

and the already-produced diagnostics.  It never modifies maps, model or
calibration; it only writes derived CSV/JSON under

    target/comparison/final-comparison-20261003/diagnostics/raw-vs-z/

Semantics (from ComparisonRunner.infer and HeldOutBottleCalibration):

    raw[p] = 1 - compiledScore(features, p)          # exported as <stem>.raw.npy
    z[p]   = (raw[p] - mu[p]) / max(sigma[p], 1e-6)  # exported as <stem>.native.npy
    image_score = max_p z[p]                          # frozen image score
    evaluated = upsample(z, 14, 14, size, size)       # <stem>.evaluated.npy

The pixel evaluator is the faithful Python port of LocalizationMetrics.java
(imported from metal_nut_pose_diagnostic.py) already validated to reproduce the
frozen aggregate metrics exactly.  RAW pixel metrics localize raw with the same
deterministic upsample used for z.
"""

from __future__ import annotations

import csv
import importlib.util
import json
import math
from collections import Counter
from pathlib import Path

import numpy as np
from scipy.stats import pearsonr, spearmanr, rankdata

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "target/comparison/final-comparison-20261003"
CASE = RUN / "anomalib4j/metal_nut"
DIAG = RUN / "diagnostics" / "raw-vs-z"
DIAG.mkdir(parents=True, exist_ok=True)

DATASET = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
TEST_ANN = DATASET / "test/ann"

W = H = 700
GRID = 14
DEFECTS = ["bent", "color", "flip", "scratch"]
FROZEN_Z = {
    "image_auroc": 0.7575757575757576,
    "pixel_auroc": 0.6926300242781803,
    "aupro030": 0.3797941159804979,
}


def load_pose_module():
    spec = importlib.util.spec_from_file_location(
        "metal_nut_pose_diagnostic", Path(__file__).with_name("metal_nut_pose_diagnostic.py")
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


POS = load_pose_module()


def read_predictions() -> list[dict]:
    with (CASE / "predictions.csv").open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def native_path(row: dict) -> Path:
    return CASE / row["native_map_path"]


def raw_path(row: dict) -> Path:
    return Path(str(native_path(row)).replace(".native.", ".raw."))


def eval_path(row: dict) -> Path:
    return CASE / row["eval_map_path"]


def load_parameters() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    mu = np.zeros((GRID, GRID), dtype=np.float64)
    sigma = np.zeros((GRID, GRID), dtype=np.float64)
    count = np.zeros((GRID, GRID), dtype=np.int64)
    with (CASE / "parameters.csv").open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            r, c = int(row["row"]), int(row["column"])
            mu[r, c] = float(row["mu"])
            sigma[r, c] = float(row["sigma_effective"])
            count[r, c] = int(row["count"])
    return mu, sigma, count


def upsample(src: np.ndarray, w: int, h: int) -> np.ndarray:
    """Half-pixel, border-replicate bilinear == LocalizationMaps.upsample."""
    sw, sh = src.shape[1], src.shape[0]
    sy = np.clip((np.arange(h) + 0.5) * sh / h - 0.5, 0, sh - 1)
    sx = np.clip((np.arange(w) + 0.5) * sw / w - 0.5, 0, sw - 1)
    y0 = np.floor(sy).astype(int)
    x0 = np.floor(sx).astype(int)
    y1 = np.minimum(y0 + 1, sh - 1)
    x1 = np.minimum(x0 + 1, sw - 1)
    dy = (sy - y0)[:, None]
    dx = (sx - x0)[None, :]
    top = (1 - dx) * src[y0][:, x0] + dx * src[y0][:, x1]
    bot = (1 - dx) * src[y1][:, x0] + dx * src[y1][:, x1]
    return (1 - dy) * top + dy * bot


def eval_map_score(rel: str) -> np.ndarray:
    return np.load(CASE / rel).astype(np.float64)


def native_auroc(pos, neg) -> float:
    return POS.image_auroc(pos, neg)


def main() -> None:
    rows = read_predictions()
    mu, sigma, count = load_parameters()

    # upsample faithfulness check against a stored evaluated map (produced by Java)
    sample_rel = rows[0]["eval_map_path"]
    z0 = np.load(native_path(rows[0])).astype(np.float64)
    ref = eval_map_score(sample_rel)
    mine = upsample(z0, W, H)
    upsample_max_abs = float(np.abs(mine - ref).max())

    for row in rows:
        row["_raw"] = np.load(raw_path(row)).astype(np.float64)
        row["_z"] = np.load(native_path(row)).astype(np.float64)
        row["_raw_max"] = float(row["_raw"].max())
        row["_z_max"] = float(row["_z"].max())
        row["_raw_argmax"] = np.unravel_index(int(np.argmax(row["_raw"])), row["_raw"].shape)
        row["_z_argmax"] = np.unravel_index(int(np.argmax(row["_z"])), row["_z"].shape)

    good = [r for r in rows if r["defect"] == "good"]
    anomalies = [r for r in rows if r["defect"] != "good"]

    masks = {r["filename"]: POS.load_mask(r["filename"]) for r in rows}
    raw_full = {r["filename"]: upsample(r["_raw"], W, H) for r in rows}
    z_full = {r["filename"]: upsample(r["_z"], W, H) for r in rows}

    def collect(sel):
        return [raw_full[r["filename"]] for r in sel], [z_full[r["filename"]] for r in sel], [
            masks[r["filename"]] for r in sel
        ]

    # --- overall image metrics
    g_raw = [r["_raw_max"] for r in good]
    g_z = [r["_z_max"] for r in good]
    a_raw = [r["_raw_max"] for r in anomalies]
    a_z = [r["_z_max"] for r in anomalies]
    image = {
        "raw": native_auroc(a_raw, g_raw),
        "z": native_auroc(a_z, g_z),
    }

    # --- overall pixel metrics (all 115 images)
    m_raw, z_map, m_masks = collect(rows)
    pixel_raw = POS.localization_metrics(m_raw, m_masks)
    pixel_z = POS.localization_metrics(z_map, m_masks)

    # --- per-defect metrics vs the same 22 good
    per_defect = []
    for d in DEFECTS:
        sel = good + [r for r in rows if r["defect"] == d]
        dr, dz, dm = collect(sel)
        p_raw = POS.localization_metrics(dr, dm)
        p_z = POS.localization_metrics(dz, dm)
        dpos = [r["_raw_max"] for r in rows if r["defect"] == d]
        dposz = [r["_z_max"] for r in rows if r["defect"] == d]
        per_defect.append(
            {
                "defect": d,
                "n": len(dpos),
                "image_raw": native_auroc(dpos, g_raw),
                "image_z": native_auroc(dposz, g_z),
                "pixel_raw": p_raw["pixel_auroc"],
                "pixel_z": p_z["pixel_auroc"],
                "aupro_raw": p_raw["aupro030"],
                "aupro_z": p_z["aupro030"],
                "mean_score_raw": float(np.mean(dpos)),
                "mean_score_z": float(np.mean(dposz)),
                "mean_good_raw": float(np.mean(g_raw)),
            }
        )

    # --- good ranking table
    good_sorted_raw = sorted(good, key=lambda r: -r["_raw_max"])
    good_sorted_z = sorted(good, key=lambda r: -r["_z_max"])
    rank_raw = {r["filename"]: i + 1 for i, r in enumerate(good_sorted_raw)}
    rank_z = {r["filename"]: i + 1 for i, r in enumerate(good_sorted_z)}
    good_rows = []
    z_argmax_counter = Counter()
    for r in sorted(good, key=lambda r: -r["_z_max"]):
        rz, cz = r["_z_argmax"]
        rr, cr = r["_raw_argmax"]
        z_argmax_counter[(int(rz), int(cz))] += 1
        sc = sigma[rz, cz]
        z_check = (r["_raw"][rz, cz] - mu[rz, cz]) / max(sc, 1e-6)
        good_rows.append(
            {
                "filename": r["filename"],
                "max_raw": r["_raw_max"],
                "max_z": r["_z_max"],
                "rank_raw": rank_raw[r["filename"]],
                "rank_z": rank_z[r["filename"]],
                "raw_argmax_row": int(rr),
                "raw_argmax_col": int(cr),
                "z_argmax_row": int(rz),
                "z_argmax_col": int(cz),
                "z_argmax_raw": float(r["_raw"][rz, cz]),
                "z_argmax_mu": float(mu[rz, cz]),
                "z_argmax_sigma": float(sc),
                "z_argmax_z_from_parts": float(z_check),
            }
        )

    sigma_flat = sigma.ravel()
    sigma_stats = {
        "n": int(sigma_flat.size),
        "min": float(sigma_flat.min()),
        "p10": float(np.percentile(sigma_flat, 10)),
        "p25": float(np.percentile(sigma_flat, 25)),
        "median": float(np.median(sigma_flat)),
        "mean": float(sigma_flat.mean()),
        "p75": float(np.percentile(sigma_flat, 75)),
        "p90": float(np.percentile(sigma_flat, 90)),
        "max": float(sigma_flat.max()),
    }
    order = np.argsort(sigma_flat)
    smallest = [
        {
            "row": int(i // GRID),
            "col": int(i % GRID),
            "sigma": float(sigma_flat[i]),
            "mu": float(mu.ravel()[i]),
            "count": int(count.ravel()[i]),
        }
        for i in order[:10]
    ]
    top_argmax_cells = [
        {"row": int(rc[0]), "col": int(rc[1]), "good_count": int(n)} for rc, n in z_argmax_counter.most_common(10)
    ]

    sigma_at_argmax = np.array([gr["z_argmax_sigma"] for gr in good_rows])
    z_scores_good = np.array([gr["max_z"] for gr in good_rows])
    raw_scores_good = np.array([gr["max_raw"] for gr in good_rows])
    corr_max_raw_max_z = _corr(raw_scores_good, z_scores_good)
    corr_max_z_sigma_argmax = _corr(z_scores_good, sigma_at_argmax)
    corr_max_raw_sigma_argmax = _corr(raw_scores_good, sigma_at_argmax)

    focus = {gr["filename"]: gr for gr in good_rows if gr["filename"] in ("metal_nut_good_009.png", "metal_nut_good_010.png")}

    # --- classify good FPs
    raw_poor = image["raw"] < 0.80
    z_not_cause = image["z"] >= image["raw"] - 0.02
    r_raw_z = corr_max_raw_max_z["pearson_r"]
    reshuffle = r_raw_z is not None and r_raw_z < -0.30
    if raw_poor and z_not_cause and reshuffle:
        fp_class = "MIXED"
        fp_reason = (
            "raw separation already weak and worse than Z (raw image AUROC "
            f"{image['raw']:.4f} < Z {image['z']:.4f}), AND positional Z strongly "
            f"reshuffles the good ranking (raw-z corr {r_raw_z:.3f}); aggregate cause is "
            "pre-calibration, with a secondary Z-amplification on individual goods"
        )
    elif raw_poor and z_not_cause:
        fp_class = "RAW_DRIVEN"
        fp_reason = "raw image AUROC already low; positional Z does not lower it"
    elif image["z"] - image["raw"] < -0.12:
        fp_class = "Z_AMPLIFIED"
        fp_reason = "raw separation clearly better; positional Z lowers it"
    else:
        fp_class = "INCONCLUSIVE"
        fp_reason = "no dominant effect"

    # --- classify bent failure (compare raw vs z for bent)
    bent = next(x for x in per_defect if x["defect"] == "bent")
    if bent["image_raw"] < 0.70 and bent["image_z"] < 0.70:
        bent_class = "RAW_REPRESENTATION_LIMIT"
        bent_reason = "bent separation poor even in raw"
    elif bent["image_z"] - bent["image_raw"] < -0.10:
        bent_class = "Z_CALIBRATION_DAMAGE"
        bent_reason = "raw better than Z by >0.10 image AUROC"
    else:
        bent_class = "MIXED"
        bent_reason = "both weak; no clear single cause"

    summary = {
        "case": str(CASE.relative_to(ROOT)),
        "raw_definition": "raw = 1 - compiledScore; raw_image_score = max(raw_map)",
        "z_definition": "z = (raw - mu) / max(sigma, 1e-6); image_score = max(z_map)",
        "upsample_check_max_abs": upsample_max_abs,
        "image": image,
        "frozen_z": FROZEN_Z,
        "pixel_raw": pixel_raw,
        "pixel_z": pixel_z,
        "per_defect": per_defect,
        "sigma_stats": sigma_stats,
        "sigma_smallest_cells": smallest,
        "z_argmax_cells_top": top_argmax_cells,
        "corr_max_raw_vs_max_z": corr_max_raw_max_z,
        "corr_max_z_vs_sigma_at_z_argmax": corr_max_z_sigma_argmax,
        "corr_max_raw_vs_sigma_at_z_argmax": corr_max_raw_sigma_argmax,
        "focus": focus,
        "classification": {
            "good_false_positive": fp_class,
            "good_false_positive_reason": fp_reason,
            "bent": bent_class,
            "bent_reason": bent_reason,
        },
    }

    _write_csv(DIAG / "metal_nut_raw_vs_z_metrics.csv", [
        {"kind": "image_auroc", "raw": image["raw"], "z": image["z"]},
        {"kind": "pixel_auroc", "raw": pixel_raw["pixel_auroc"], "z": pixel_z["pixel_auroc"]},
        {"kind": "aupro030", "raw": pixel_raw["aupro030"], "z": pixel_z["aupro030"]},
        {"kind": "regions", "raw": pixel_raw["regions"], "z": pixel_z["regions"]},
        {"kind": "foreground_pixels", "raw": pixel_raw["foreground_pixels"], "z": pixel_z["foreground_pixels"]},
        {"kind": "background_pixels", "raw": pixel_raw["background_pixels"], "z": pixel_z["background_pixels"]},
    ])
    _write_csv(DIAG / "metal_nut_raw_vs_z_per_defect.csv", per_defect)
    _write_csv(DIAG / "metal_nut_good_raw_vs_z.csv", good_rows)
    _write_csv(
        DIAG / "metal_nut_sigma_cells.csv",
        [
            {"row": int(i // GRID), "col": int(i % GRID), "mu": float(mu.ravel()[i]),
             "sigma": float(sigma_flat[i]), "count": int(count.ravel()[i])}
            for i in range(sigma_flat.size)
        ],
    )
    (DIAG / "metal_nut_raw_vs_z_summary.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )

    print(json.dumps({
        "upsample_check_max_abs": upsample_max_abs,
        "image_raw": image["raw"],
        "image_z": image["z"],
        "pixel_raw": pixel_raw["pixel_auroc"],
        "pixel_z": pixel_z["pixel_auroc"],
        "aupro_raw": pixel_raw["aupro030"],
        "aupro_z": pixel_z["aupro030"],
        "per_defect": per_defect,
        "sigma_stats": sigma_stats,
        "good_fp_class": fp_class,
        "good_fp_reason": fp_reason,
        "bent_class": bent_class,
        "bent_reason": bent_reason,
    }, indent=2))


def _corr(a, b) -> dict:
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    if a.size < 3 or np.std(a) == 0 or np.std(b) == 0:
        return {"pearson_r": None, "pearson_p": None, "spearman_rho": None, "spearman_p": None, "n": int(a.size)}
    pr = pearsonr(a, b)
    sr = spearmanr(a, b)
    return {
        "pearson_r": float(pr.statistic),
        "pearson_p": float(pr.pvalue),
        "spearman_rho": float(sr.statistic),
        "spearman_p": float(sr.pvalue),
        "n": int(a.size),
    }


def _write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        return
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
