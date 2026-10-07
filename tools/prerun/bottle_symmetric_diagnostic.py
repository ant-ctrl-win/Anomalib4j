"""Post-hoc symmetric diagnostic for the Anomalib4j Bottle case (read-only).

Mirrors the Metal Nut diagnostics (docs/diagnostics/METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md and
docs/diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md) on the category where Anomalib4j works
well, to check whether the same technical failure modes exist there.

Uses only frozen artifacts in

    target/comparison/final-comparison-20261003/anomalib4j/bottle/

It never modifies maps, model, calibration, config or dataset; it only writes
derived CSV/JSON/PNG under

    target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/

Semantics (from ComparisonRunner.infer and HeldOutBottleCalibration):

    raw[p] = 1 - compiledScore(features, p)          # exported as <stem>.raw.npy
    z[p]   = (raw[p] - mu[p]) / max(sigma[p], 1e-6)  # exported as <stem>.native.npy
    image_score = max_p z[p]                          # frozen image score
    evaluated = upsample(z, 14, 14, 900, 900)         # <stem>.evaluated.npy

The pixel evaluator is the faithful Python port of LocalizationMetrics.java
(imported from metal_nut_pose_diagnostic.py) already validated to reproduce the
frozen aggregate metrics exactly.

Run with:
  target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/prerun/bottle_symmetric_diagnostic.py
"""

from __future__ import annotations

import base64
import csv
import importlib.util
import io
import json
import math
import zlib
from collections import Counter
from pathlib import Path

import numpy as np
import scipy.ndimage as ndi
from PIL import Image
from scipy.stats import pearsonr, spearmanr
from skimage.filters import threshold_otsu

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "target/comparison/final-comparison-20261003"
CASE = RUN / "anomalib4j/bottle"
DIAG = RUN / "diagnostics" / "bottle-symmetric"
DIAG.mkdir(parents=True, exist_ok=True)

DATASET = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
TEST_IMG = DATASET / "test/img"
TRAIN_IMG = DATASET / "train/img"
FIT_MANIFEST = RUN / "data/bottle/anomalib4j-fit.txt"

W = H = 900
GRID = 14
DEFECTS = ["broken_large", "broken_small", "contamination"]
FROZEN_Z = {
    "image_auroc": 1.0,
    "pixel_auroc": 0.9616640201342855,
    "aupro030": 0.8774472899967225,
}
# Metal Nut references for the final comparison table (from the frozen report).
METAL_NUT = {
    "image_raw": 0.7047898338220919,
    "image_z": 0.7575757575757576,
    "pixel_raw": 0.625562015711313,
    "pixel_z": 0.6926300242781803,
    "aupro_raw": 0.3453858679360738,
    "aupro_z": 0.3797941159804979,
    "always_bg_cells": 76,
    "always_bg_cells_hosting_argmax": 7,
    "n_good": 22,
}


def load_pose_module():
    spec = importlib.util.spec_from_file_location(
        "metal_nut_pose_diagnostic", Path(__file__).with_name("metal_nut_pose_diagnostic.py")
    )
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


POS = load_pose_module()


# --------------------------------------------------------------------------- #
# basic IO
# --------------------------------------------------------------------------- #
def read_predictions() -> list[dict]:
    with (CASE / "predictions.csv").open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def load_mask(filename: str) -> np.ndarray:
    """Bottle-specific DatasetNinja mask loader at the native 900x900 size.

    The pose module's loader hardcodes 700x700 (Metal Nut), so it cannot place
    the Bottle bitmaps correctly.  Same decoding logic, but sized (H, W).
    """
    path = DATASET / "test/ann" / (filename + ".json")
    obj = json.loads(path.read_text(encoding="utf-8"))
    mask = np.zeros((H, W), dtype=bool)
    for item in obj.get("objects", []):
        bm = item["bitmap"]
        origin = bm["origin"]
        if isinstance(origin, dict):
            ox, oy = int(origin["x"]), int(origin["y"])
        else:
            ox, oy = int(origin[0]), int(origin[1])
        raw = base64.b64decode(bm["data"])
        if raw[:8] != b"\x89PNG\r\n\x1a\n":
            raw = zlib.decompress(raw)
        img = Image.open(io.BytesIO(raw))
        a = np.asarray(img)
        if a.ndim == 3 and a.shape[2] == 4:
            fg = a[..., 3] != 0
        elif a.ndim == 3:
            fg = a[..., :3].sum(axis=2) != 0
        else:
            fg = a != 0
        h, w = fg.shape
        mask[oy:oy + h, ox:ox + w] |= fg
    return mask


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


# --------------------------------------------------------------------------- #
# geometry / occupancy (independent of anomaly maps)
# --------------------------------------------------------------------------- #
def object_mask(arr: np.ndarray) -> np.ndarray:
    """Otsu -> dark class -> largest 8-connected component -> hole fill.

    Bottle object is dark on a white background, so the object is the class
    below the Otsu threshold.  Deterministic and independent of the maps.
    """
    thr = threshold_otsu(arr)
    bright = arr > thr
    dark = ~bright
    lab, n = ndi.label(dark)
    if n == 0:
        return np.zeros_like(dark)
    sizes = np.bincount(lab.ravel())
    sizes[0] = 0
    return ndi.binary_fill_holes(lab == int(sizes.argmax()))


def cell_edges(size: int, n: int) -> list[int]:
    return [int(round(i * size / n)) for i in range(n + 1)]


ROW_EDGES = cell_edges(H, GRID)
COL_EDGES = cell_edges(W, GRID)


def occupancy_grid(comp: np.ndarray) -> np.ndarray:
    frac = np.zeros((GRID, GRID), dtype=np.float64)
    for r in range(GRID):
        for c in range(GRID):
            block = comp[ROW_EDGES[r]:ROW_EDGES[r + 1], COL_EDGES[c]:COL_EDGES[c + 1]]
            frac[r, c] = block.mean()
    return frac


def cells_to_pixels(cell_mask: np.ndarray) -> np.ndarray:
    """Expand a 14x14 cell mask to a 900x900 pixel mask using the same edges."""
    px = np.zeros((H, W), dtype=bool)
    for r in range(GRID):
        for c in range(GRID):
            if cell_mask[r, c]:
                px[ROW_EDGES[r]:ROW_EDGES[r + 1], COL_EDGES[c]:COL_EDGES[c + 1]] = True
    return px


def groups_mask() -> dict[str, np.ndarray]:
    rr, cc = np.mgrid[0:GRID, 0:GRID]
    outer = (rr == 0) | (rr == GRID - 1) | (cc == 0) | (cc == GRID - 1)
    second = ~outer & ((rr == 1) | (rr == GRID - 2) | (cc == 1) | (cc == GRID - 2))
    interior = ~outer & ~second
    return {
        "outer_border": outer,
        "second_ring": second,
        "interior": interior,
        "top_row": rr == 0,
        "bottom_row": rr == GRID - 1,
        "left_col": cc == 0,
        "right_col": cc == GRID - 1,
    }


def image_auroc(pos, neg) -> float:
    return POS.image_auroc(pos, neg)


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


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def main() -> None:
    rows = read_predictions()
    mu, sigma, count = load_parameters()

    # upsample faithfulness check against a stored evaluated map (produced by Java)
    sample_rel = rows[0]["eval_map_path"]
    z0 = np.load(native_path(rows[0])).astype(np.float64)
    ref = np.load(CASE / sample_rel).astype(np.float64)
    upsample_max_abs = float(np.abs(upsample(z0, W, H) - ref).max())

    for row in rows:
        row["_raw"] = np.load(raw_path(row)).astype(np.float64)
        row["_z"] = np.load(native_path(row)).astype(np.float64)
        row["_raw_max"] = float(row["_raw"].max())
        row["_z_max"] = float(row["_z"].max())
        row["_raw_argmax"] = tuple(int(v) for v in np.unravel_index(int(np.argmax(row["_raw"])), (GRID, GRID)))
        row["_z_argmax"] = tuple(int(v) for v in np.unravel_index(int(np.argmax(row["_z"])), (GRID, GRID)))

    good = [r for r in rows if r["defect"] == "good"]
    anomalies = [r for r in rows if r["defect"] != "good"]
    masks = {r["filename"]: load_mask(r["filename"]) for r in rows}
    raw_full = {r["filename"]: upsample(r["_raw"], W, H) for r in rows}
    z_full = {r["filename"]: upsample(r["_z"], W, H) for r in rows}

    def collect(sel):
        return (
            [raw_full[r["filename"]] for r in sel],
            [z_full[r["filename"]] for r in sel],
            [masks[r["filename"]] for r in sel],
        )

    # --- overall image metrics (raw vs z) ---
    g_raw = [r["_raw_max"] for r in good]
    g_z = [r["_z_max"] for r in good]
    a_raw = [r["_raw_max"] for r in anomalies]
    a_z = [r["_z_max"] for r in anomalies]
    image = {"raw": image_auroc(a_raw, g_raw), "z": image_auroc(a_z, g_z)}

    # --- overall pixel metrics (all 83 images) ---
    m_raw, m_z, m_masks = collect(rows)
    pixel_raw = POS.localization_metrics(m_raw, m_masks)
    pixel_z = POS.localization_metrics(m_z, m_masks)

    # --- per-defect metrics vs the same 20 good ---
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
                "image_raw": image_auroc(dpos, g_raw),
                "image_z": image_auroc(dposz, g_z),
                "pixel_raw": p_raw["pixel_auroc"],
                "pixel_z": p_z["pixel_auroc"],
                "aupro_raw": p_raw["aupro030"],
                "aupro_z": p_z["aupro030"],
                "mean_score_raw": float(np.mean(dpos)),
                "mean_score_z": float(np.mean(dposz)),
                "mean_good_raw": float(np.mean(g_raw)),
                "mean_good_z": float(np.mean(g_z)),
            }
        )

    # --- good ranking ---
    good_sorted_raw = sorted(good, key=lambda r: -r["_raw_max"])
    good_sorted_z = sorted(good, key=lambda r: -r["_z_max"])
    rank_raw = {r["filename"]: i + 1 for i, r in enumerate(good_sorted_raw)}
    rank_z = {r["filename"]: i + 1 for i, r in enumerate(good_sorted_z)}
    good_rows = []
    z_argmax_counter = Counter()
    for r in sorted(good, key=lambda r: -r["_z_max"]):
        rz, cz = r["_z_argmax"]
        rr, cr = r["_raw_argmax"]
        z_argmax_counter[(rz, cz)] += 1
        sc = sigma[rz, cz]
        z_check = (r["_raw"][rz, cz] - mu[rz, cz]) / max(sc, 1e-6)
        good_rows.append(
            {
                "filename": r["filename"],
                "max_raw": r["_raw_max"],
                "max_z": r["_z_max"],
                "rank_raw": rank_raw[r["filename"]],
                "rank_z": rank_z[r["filename"]],
                "rank_shift_z_minus_raw": rank_raw[r["filename"]] - rank_z[r["filename"]],
                "raw_argmax_row": rr,
                "raw_argmax_col": cr,
                "z_argmax_row": rz,
                "z_argmax_col": cz,
                "z_argmax_raw": float(r["_raw"][rz, cz]),
                "z_argmax_mu": float(mu[rz, cz]),
                "z_argmax_sigma": float(sc),
                "z_argmax_z_from_parts": float(z_check),
            }
        )
    good_scores = np.array([r["_z_max"] for r in good], dtype=float)
    good_score_stats = {
        "n": int(good_scores.size),
        "min": float(good_scores.min()),
        "median": float(np.median(good_scores)),
        "mean": float(good_scores.mean()),
        "max": float(good_scores.max()),
        "p75": float(np.percentile(good_scores, 75)),
        "p90": float(np.percentile(good_scores, 90)),
    }
    shifts = np.array([abs(g["rank_shift_z_minus_raw"]) for g in good_rows])
    corr_max_raw_max_z = _corr(
        [g["max_raw"] for g in good_rows], [g["max_z"] for g in good_rows]
    )

    # --- spatial per-cell statistics over the 20 good ---
    z_stack = np.stack([r["_z"] for r in good], axis=0)  # (20,14,14)
    raw_stack = np.stack([r["_raw"] for r in good], axis=0)
    cell_mean_z = z_stack.mean(axis=0)
    cell_median_z = np.median(z_stack, axis=0)
    cell_max_z = z_stack.max(axis=0)
    cell_argmax_count = np.zeros((GRID, GRID), dtype=int)
    for r in good:
        rz, cz = r["_z_argmax"]
        cell_argmax_count[rz, cz] += 1

    # --- occupancy from training normals + test good (map-independent) ---
    fit_names = [ln.strip() for ln in FIT_MANIFEST.read_text(encoding="utf-8").splitlines() if ln.strip()]
    occ_stack = []
    for name in fit_names:
        comp = object_mask(np.asarray(Image.open(TRAIN_IMG / name).convert("L")))
        occ_stack.append(occupancy_grid(comp))
    for r in good:
        comp = object_mask(np.asarray(Image.open(TEST_IMG / r["filename"]).convert("L")))
        occ_stack.append(occupancy_grid(comp))
    occ_mean = np.mean(occ_stack, axis=0)          # mean object fraction per cell
    occ_max = np.max(occ_stack, axis=0)            # ever-object
    always_bg = occ_max <= 0.0                     # never object in train+good
    object_cells = ~always_bg

    # --- calibration groups ---
    gmask = groups_mask()
    cell_sigma_flat = sigma.ravel()
    sigma_order = np.argsort(cell_sigma_flat)
    smallest_sigma = [
        {
            "row": int(i // GRID),
            "col": int(i % GRID),
            "mu": float(mu.ravel()[i]),
            "sigma": float(cell_sigma_flat[i]),
            "count": int(count.ravel()[i]),
            "argmax_count_good": int(cell_argmax_count.ravel()[i]),
        }
        for i in sigma_order[:10]
    ]
    grp_rows = []
    for name, m in gmask.items():
        grp_rows.append(
            {
                "group": name,
                "n_cells": int(m.sum()),
                "mean_mu": float(mu[m].mean()),
                "mean_sigma": float(sigma[m].mean()),
                "min_sigma": float(sigma[m].min()),
                "mean_cell_mean_z": float(cell_mean_z[m].mean()),
                "max_cell_mean_z": float(cell_mean_z[m].max()),
                "cells_mean_z_gt2": int((cell_mean_z[m] > 2).sum()),
                "cells_mean_z_gt3": int((cell_mean_z[m] > 3).sum()),
                "cells_mean_z_gt4": int((cell_mean_z[m] > 4).sum()),
                "argmax_events": int(cell_argmax_count[m].sum()),
                "object_fraction_mean": float(occ_mean[m].mean()) if m.any() else None,
            }
        )
    # add occupancy-derived groups
    for name, m in (("always_background", always_bg), ("object_cells", object_cells)):
        grp_rows.append(
            {
                "group": name,
                "n_cells": int(m.sum()),
                "mean_mu": float(mu[m].mean()) if m.any() else None,
                "mean_sigma": float(sigma[m].mean()) if m.any() else None,
                "min_sigma": float(sigma[m].min()) if m.any() else None,
                "mean_cell_mean_z": float(cell_mean_z[m].mean()) if m.any() else None,
                "max_cell_mean_z": float(cell_mean_z[m].max()) if m.any() else None,
                "cells_mean_z_gt2": int((cell_mean_z[m] > 2).sum()),
                "cells_mean_z_gt3": int((cell_mean_z[m] > 3).sum()),
                "cells_mean_z_gt4": int((cell_mean_z[m] > 4).sum()),
                "argmax_events": int(cell_argmax_count[m].sum()),
                "object_fraction_mean": float(occ_mean[m].mean()) if m.any() else None,
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
    mu_stats = {
        "min": float(mu.min()),
        "median": float(np.median(mu)),
        "mean": float(mu.mean()),
        "max": float(mu.max()),
    }

    # --- peripheral / background activation quantification ---
    # occupancy correlations with calibration / activation
    corr_occ_mu = _corr(occ_mean.ravel(), mu.ravel())
    corr_occ_sigma = _corr(occ_mean.ravel(), sigma.ravel())
    corr_occ_mean_z = _corr(occ_mean.ravel(), cell_mean_z.ravel())
    corr_occ_argmax = _corr(occ_mean.ravel(), cell_argmax_count.ravel())

    # image AUROC using only object cells (mask out always-background cells)
    def max_over_object(r: dict) -> float:
        zc = r["_z"].copy()
        zc[always_bg] = -np.inf
        return float(zc.max())

    def max_over_object_raw(r: dict) -> float:
        rc = r["_raw"].copy()
        rc[always_bg] = -np.inf
        return float(rc.max())

    image_object_z = image_auroc([max_over_object(r) for r in anomalies], [max_over_object(r) for r in good])
    image_object_raw = image_auroc(
        [max_over_object_raw(r) for r in anomalies], [max_over_object_raw(r) for r in good]
    )

    # pixel ablation: zero out always-background cells, re-evaluate
    z_ablated = {r["filename"]: upsample(np.where(always_bg, -1e9, r["_z"]), W, H) for r in rows}
    p_ablated = POS.localization_metrics([z_ablated[r["filename"]] for r in rows], [masks[r["filename"]] for r in rows])

    # pixel-level Z>3 statistics, good (all GT-negative) and anomalies
    def z_gt(z: np.ndarray, t: float) -> int:
        return int((z > t).sum())

    good_total = int(sum(masks[r["filename"]].size for r in good))
    good_z3 = int(sum(z_gt(z_full[r["filename"]], 3.0) for r in good))
    anomaly_total = int(sum(masks[r["filename"]].size for r in anomalies))
    anomaly_z3 = int(sum(z_gt(z_full[r["filename"]], 3.0) for r in anomalies))
    # pixel mass in always-background / object cells (upsampled)
    bg_pixel = cells_to_pixels(always_bg)
    obj_pixel = cells_to_pixels(object_cells)
    good_z3_bg = int(sum(((z_full[r["filename"]] > 3.0) & bg_pixel).sum() for r in good))
    anomaly_z3_bg = int(sum(((z_full[r["filename"]] > 3.0) & bg_pixel).sum() for r in anomalies))
    good_z3_obj = good_z3 - good_z3_bg
    anomaly_z3_obj = anomaly_z3 - anomaly_z3_bg

    # mean Z in object vs always-background pixels (upsampled full maps)
    good_znative_mean_bg = (
        float(np.mean([np.mean(z_full[r["filename"]][bg_pixel]) for r in good]))
        if bg_pixel.any()
        else None
    )
    good_znative_mean_obj = (
        float(np.mean([np.mean(z_full[r["filename"]][obj_pixel]) for r in good]))
        if obj_pixel.any()
        else None
    )

    # --- classification of the hypothesis ---
    z_helps = image["z"] >= image["raw"] - 0.005 and pixel_z["pixel_auroc"] >= pixel_raw["pixel_auroc"] - 0.005
    good_fp_weak = good_scores.max() < min(a_z)  # no good outranks the weakest anomaly (image separation held)
    bg_effect_present = int(always_bg.sum()) > 0 and good_z3_bg > 0
    bg_dominates = abs(image_object_z - image["z"]) > 0.02 or abs(p_ablated["pixel_auroc"] - pixel_z["pixel_auroc"]) > 0.02
    anomaly_signal_dominates = anomaly_z3_bg / max(anomaly_z3, 1) < 0.10 and good_z3_bg / max(good_z3, 1) < 0.30

    if z_helps and good_fp_weak and anomaly_signal_dominates:
        classification = "SAME_TECHNICAL_EFFECTS_BUT_SIGNAL_DOMINATES"
        reason = (
            "peripheral/background effects exist but neither image nor pixel metrics are "
            "materially affected and the real anomaly signal dominates"
        )
    elif bg_dominates:
        classification = "DIFFERENT_FAILURE_MODE"
        reason = "background/peripheral cells materially change the metrics"
    elif not bg_effect_present:
        classification = "BOTTLE_HAS_NO_COMPARABLE_EFFECT"
        reason = "no background/peripheral activation pressure measurable"
    elif not z_helps:
        classification = "MIXED"
        reason = "positional Z does not clearly help and background effects are present"
    else:
        classification = "MIXED"
        reason = "partial evidence"

    summary = {
        "case": str(CASE.relative_to(ROOT)),
        "n_good": len(good),
        "n_anomalies": len(anomalies),
        "raw_definition": "raw = 1 - compiledScore; raw_image_score = max(raw_map)",
        "z_definition": "z = (raw - mu) / max(sigma, 1e-6); image_score = max(z_map)",
        "upsample_check_max_abs": upsample_max_abs,
        "image": image,
        "image_delta_z_minus_raw": image["z"] - image["raw"],
        "frozen_z": FROZEN_Z,
        "pixel_raw": pixel_raw,
        "pixel_z": pixel_z,
        "pixel_delta_z_minus_raw": {
            "pixel_auroc": pixel_z["pixel_auroc"] - pixel_raw["pixel_auroc"],
            "aupro030": pixel_z["aupro030"] - pixel_raw["aupro030"],
        },
        "per_defect": per_defect,
        "good_score_stats": good_score_stats,
        "good_rank_shift_abs_max": int(shifts.max()),
        "good_rank_shift_abs_median": float(np.median(shifts)),
        "corr_max_raw_vs_max_z": corr_max_raw_max_z,
        "mu_stats": mu_stats,
        "sigma_stats": sigma_stats,
        "sigma_smallest_cells": smallest_sigma,
        "z_argmax_cells_top": [
            {"row": int(rc[0]), "col": int(rc[1]), "good_count": int(n)} for rc, n in z_argmax_counter.most_common(10)
        ],
        "groups": grp_rows,
        "occupancy": {
            "always_bg_cells": int(always_bg.sum()),
            "object_cells": int(object_cells.sum()),
            "always_bg_cells_hosting_argmax": int(cell_argmax_count[always_bg].sum()),
            "mean_object_fraction_overall": float(occ_mean.mean()),
            "corr_occ_mu": corr_occ_mu,
            "corr_occ_sigma": corr_occ_sigma,
            "corr_occ_mean_z": corr_occ_mean_z,
            "corr_occ_argmax": corr_occ_argmax,
        },
        "peripheral": {
            "image_object_only_z": image_object_z,
            "image_object_only_raw": image_object_raw,
            "pixel_ablated_bg": p_ablated,
            "good_pixels_total": good_total,
            "good_z_gt3": good_z3,
            "good_z_gt3_fraction": good_z3 / good_total if good_total else None,
            "good_z_gt3_on_background": good_z3_bg,
            "good_z_gt3_on_object": good_z3_obj,
            "anomaly_pixels_total": anomaly_total,
            "anomaly_z_gt3": anomaly_z3,
            "anomaly_z_gt3_fraction": anomaly_z3 / anomaly_total if anomaly_total else None,
            "anomaly_z_gt3_on_background": anomaly_z3_bg,
            "anomaly_z_gt3_on_object": anomaly_z3_obj,
            "good_mean_z_background": good_znative_mean_bg,
            "good_mean_z_object": good_znative_mean_obj,
        },
        "metal_nut_reference": METAL_NUT,
        "classification": classification,
        "classification_reason": reason,
    }

    # --- write artifacts ---
    _write_csv(DIAG / "bottle_raw_vs_z_metrics.csv", [
        {"kind": "image_auroc", "raw": image["raw"], "z": image["z"], "delta_z_minus_raw": image["z"] - image["raw"]},
        {"kind": "pixel_auroc", "raw": pixel_raw["pixel_auroc"], "z": pixel_z["pixel_auroc"],
         "delta_z_minus_raw": pixel_z["pixel_auroc"] - pixel_raw["pixel_auroc"]},
        {"kind": "aupro030", "raw": pixel_raw["aupro030"], "z": pixel_z["aupro030"],
         "delta_z_minus_raw": pixel_z["aupro030"] - pixel_raw["aupro030"]},
        {"kind": "regions", "raw": pixel_raw["regions"], "z": pixel_z["regions"], "delta_z_minus_raw": None},
        {"kind": "foreground_pixels", "raw": pixel_raw["foreground_pixels"], "z": pixel_z["foreground_pixels"],
         "delta_z_minus_raw": None},
        {"kind": "background_pixels", "raw": pixel_raw["background_pixels"], "z": pixel_z["background_pixels"],
         "delta_z_minus_raw": None},
    ])
    _write_csv(DIAG / "bottle_raw_vs_z_per_defect.csv", per_defect)
    _write_csv(DIAG / "bottle_good_raw_vs_z.csv", good_rows)
    _write_csv(DIAG / "bottle_spatial_groups.csv", grp_rows)
    _write_csv(DIAG / "bottle_sigma_cells.csv", [
        {"row": int(i // GRID), "col": int(i % GRID), "mu": float(mu.ravel()[i]),
         "sigma": float(sigma_flat[i]), "count": int(count.ravel()[i]),
         "object_fraction": float(occ_mean.ravel()[i]), "always_background": bool(always_bg.ravel()[i]),
         "mean_z": float(cell_mean_z.ravel()[i]), "max_z": float(cell_max_z.ravel()[i]),
         "argmax_count": int(cell_argmax_count.ravel()[i])}
        for i in range(sigma_flat.size)
    ])
    (DIAG / "bottle_symmetric_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print(json.dumps({
        "upsample_check_max_abs": upsample_max_abs,
        "image_raw": image["raw"],
        "image_z": image["z"],
        "pixel_raw": pixel_raw["pixel_auroc"],
        "pixel_z": pixel_z["pixel_auroc"],
        "aupro_raw": pixel_raw["aupro030"],
        "aupro_z": pixel_z["aupro030"],
        "per_defect": per_defect,
        "good_score_stats": good_score_stats,
        "good_rank_shift_abs_max": int(shifts.max()),
        "corr_max_raw_vs_max_z": corr_max_raw_max_z,
        "sigma_stats": sigma_stats,
        "occupancy": summary["occupancy"],
        "peripheral": summary["peripheral"],
        "classification": classification,
        "classification_reason": reason,
    }, indent=2))


if __name__ == "__main__":
    main()
