"""Post-hoc, read-only border-exclusion sensitivity analysis.

Frozen run: target/comparison/final-comparison-20261003
Categories: bottle (900x900) and metal_nut (700x700), Anomalib4j SPATIAL_14.

It answers: how much do the peripheral cells of the 14x14 grid (rows/cols 0 and
13) contribute to the frozen metrics?

Nothing here modifies benchmark, model, maps, parameters or official metrics.
New artifacts are written only under
target/comparison/final-comparison-20261003/diagnostics/border-exclusion/.

All results are labelled POST_HOC_SENSITIVITY_ONLY.

Semantics reused from the frozen pipeline (raw_vs_z_diagnostic.py):

    raw[p] = 1 - compiledScore(features, p)          # <stem>.raw.npy
    z[p]   = (raw[p] - mu[p]) / max(sigma[p], 1e-6)  # <stem>.native.npy
    image_score = max_p z[p]                          # predictions.csv
    evaluated   = upsample(z, 14, 14, size, size)     # <stem>.evaluated.npy

Run with the pinned interpreter:
  target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/prerun/border_exclusion_sensitivity.py
"""

from __future__ import annotations

import base64
import csv
import importlib.util
import io
import json
import math
import zlib
from pathlib import Path

import numpy as np
import scipy.ndimage as ndi
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "target/comparison/final-comparison-20261003"
DIAG = RUN / "diagnostics" / "border-exclusion"
DATASET = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
TEST_ANN = DATASET / "test/ann"

GRID = 14
LABEL = "POST_HOC_SENSITIVITY_ONLY"

# Classification scale for |delta| (AUROC / AUPRO) used in the report.
THRESH = {"marginal": 0.01, "moderate": 0.05}

CATEGORIES = {
    "bottle": {
        "case": RUN / "anomalib4j/bottle",
        "size": 900,
        "defects": ["broken_large", "broken_small", "contamination"],
    },
    "metal_nut": {
        "case": RUN / "anomalib4j/metal_nut",
        "size": 700,
        "defects": ["bent", "color", "flip", "scratch"],
    },
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
# IO helpers
# --------------------------------------------------------------------------- #
def read_predictions(case: Path) -> list[dict]:
    with (case / "predictions.csv").open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def native_path(case: Path, row: dict) -> Path:
    return case / row["native_map_path"]


def raw_path(case: Path, row: dict) -> Path:
    return Path(str(native_path(case, row)).replace(".native.", ".raw."))


def load_parameters(case: Path) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    mu = np.zeros((GRID, GRID))
    sigma = np.zeros((GRID, GRID))
    count = np.zeros((GRID, GRID), dtype=np.int64)
    with (case / "parameters.csv").open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            r, c = int(row["row"]), int(row["column"])
            mu[r, c] = float(row["mu"])
            sigma[r, c] = float(row["sigma_effective"])
            count[r, c] = int(row["count"])
    return mu, sigma, count


def load_mask(filename: str, h: int, w: int) -> np.ndarray:
    """DatasetNinja ann bitmap loader, sized to the requested canvas (H, W)."""
    path = TEST_ANN / (filename + ".json")
    obj = json.loads(path.read_text(encoding="utf-8"))
    mask = np.zeros((h, w), dtype=bool)
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
        fh, fw = fg.shape
        y1, x1 = min(oy + fh, h), min(ox + fw, w)
        mask[oy:y1, ox:x1] |= fg[: y1 - oy, : x1 - ox]
    return mask


# --------------------------------------------------------------------------- #
# upsample (exact LocalizationMaps.upsample port)
# --------------------------------------------------------------------------- #
def upsample(src: np.ndarray, w: int, h: int) -> np.ndarray:
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
# vectorized exact port of LocalizationMetrics (pixel AUROC + AUPRO@0.30)
# --------------------------------------------------------------------------- #
FPR_LIMIT = 0.30


def _interp(x0, y0, x1, y1, x):
    return y0 + (y1 - y0) * ((x - x0) / (x1 - x0))


def localization_metrics(maps, masks, support: np.ndarray | None = None) -> dict | None:
    """Vectorized, semantically identical to metal_nut_pose_diagnostic.localization_metrics.

    If ``support`` (2-D bool) is given, only pixels inside the support enter the
    evaluation; region areas and the region count are recomputed on the support.
    """
    scores_parts, regions_parts = [], []
    areas = [0]
    for m, mask in zip(maps, masks):
        mf = m.ravel().astype(np.float64)
        lab, n = ndi.label(mask.astype(np.uint8), structure=np.ones((3, 3), dtype=int))
        flat = lab.ravel()
        if support is None:
            idx = np.arange(mf.size)
        else:
            idx = np.nonzero(support.ravel())[0]
        lab_sel = flat[idx]
        counts = np.bincount(lab_sel, minlength=n + 1)
        remap = np.zeros(n + 1, dtype=np.int64)
        for old in range(1, n + 1):
            if counts[old] > 0:
                remap[old] = len(areas)
                areas.append(int(counts[old]))
        reg = remap[lab_sel]
        scores_parts.append(mf[idx])
        regions_parts.append(reg)

    scores = np.concatenate(scores_parts)
    regions = np.concatenate(regions_parts)
    positives = int(sum(areas))
    negatives = int(scores.size - positives)
    if positives == 0 or negatives == 0:
        return None
    num_regions = len(areas) - 1
    weights = np.zeros(len(areas), dtype=np.float64)
    for i in range(1, len(areas)):
        weights[i] = 1.0 / areas[i] / num_regions

    order = np.argsort(-scores, kind="stable")  # descending; s[-1] lowest
    s = scores[order]
    r = regions[order]
    is_fp = (r == 0).astype(np.float64)
    is_tp = 1.0 - is_fp
    w = weights[r]
    cum_fp = np.cumsum(is_fp)
    cum_tp = np.cumsum(is_tp)
    cum_pro = np.cumsum(w * is_tp)

    changes = np.nonzero(np.diff(s) != 0)[0]
    ends = np.append(changes, s.size - 1)  # last index of each descending group
    cfp = cum_fp[ends]
    ctp = cum_tp[ends]
    cpro = cum_pro[ends]
    # "after" group g = scores >= threshold of group g
    after_fpr = cfp / negatives
    after_tpr = ctp / positives
    after_pro = cpro
    # "before" group g = after group g-1 (0 for the first/highest group)
    before_fpr = np.concatenate([[0.0], after_fpr[:-1]])
    before_tpr = np.concatenate([[0.0], after_tpr[:-1]])
    before_pro = np.concatenate([[0.0], after_pro[:-1]])

    auc = float(np.sum((after_fpr - before_fpr) * (before_tpr + after_tpr) / 2.0))
    crossing = (after_fpr > before_fpr) & (before_fpr < FPR_LIMIT)
    stop = np.minimum(after_fpr, FPR_LIMIT)
    with np.errstate(divide="ignore", invalid="ignore"):
        stop_pro = before_pro + (after_pro - before_pro) * (
            (stop - before_fpr) / (after_fpr - before_fpr)
        )
    pro_area = float(np.sum(np.where(crossing, (stop - before_fpr) * (before_pro + stop_pro) / 2.0, 0.0)))
    return {
        "pixel_auroc": auc,
        "aupro030": pro_area / FPR_LIMIT,
        "regions": int(num_regions),
        "foreground_pixels": positives,
        "background_pixels": negatives,
    }


# --------------------------------------------------------------------------- #
# supports
# --------------------------------------------------------------------------- #
def nominal_support(size: int) -> tuple[np.ndarray, int, int]:
    """Pixels whose nominal 14x14 cell index is in 1..12 (both axes)."""
    lo = int(math.ceil(size / GRID))
    hi = int(math.ceil((GRID - 1) * size / GRID)) - 1
    axis = np.zeros(size, dtype=bool)
    axis[lo:hi + 1] = True
    return axis[:, None] & axis[None, :], lo, hi


def strict_support(size: int) -> tuple[np.ndarray, int, int]:
    """Pixels whose bilinear interpolation uses no cell of rows/cols 0 or 13.

    Same half-pixel mapping as LocalizationMaps.upsample.  A source cell
    contributes with a positive weight; a pixel is excluded if any contributing
    source cell is on the border row/column.
    """

    def axis_uses_border(n_out: int) -> np.ndarray:
        pos = np.clip((np.arange(n_out) + 0.5) * GRID / n_out - 0.5, 0, GRID - 1)
        i0 = np.floor(pos).astype(np.int64)
        i1 = np.minimum(i0 + 1, GRID - 1)
        frac = pos - i0
        uses_lo = ((i0 == 0) & ((1.0 - frac) > 0)) | ((i1 == 0) & (frac > 0))
        uses_hi = ((i0 == GRID - 1) & ((1.0 - frac) > 0)) | ((i1 == GRID - 1) & (frac > 0))
        return uses_lo | uses_hi

    rb = axis_uses_border(size)
    cb = axis_uses_border(size)
    border = rb[:, None] | cb[None, :]
    support = ~border
    ys = np.nonzero(~rb)[0]
    xs = np.nonzero(~cb)[0]
    lo, hi = int(ys.min()), int(ys.max())
    assert np.array_equal(ys, np.arange(lo, hi + 1))
    assert np.array_equal(xs, np.arange(xs.min(), xs.max() + 1))
    return support, lo, hi


# --------------------------------------------------------------------------- #
# image level
# --------------------------------------------------------------------------- #
def image_level(rows: list[dict], space_key: str) -> dict:
    good = [r for r in rows if r["defect"] == "good"]
    anomalies = [r for r in rows if r["defect"] != "good"]
    inner = np.zeros((GRID, GRID), dtype=bool)
    inner[1:GRID - 1, 1:GRID - 1] = True

    def scores(sel, key):
        full = np.array([float(r[f"_{key}"].max()) for r in sel])
        inner_s = np.array([float(r[f"_{key}"][inner].max()) for r in sel])
        return full, inner_s

    g_full, g_in = scores(good, space_key)
    a_full, a_in = scores(anomalies, space_key)
    img_full = POS.image_auroc(a_full, g_full)
    img_in = POS.image_auroc(a_in, g_in)

    total_pairs = len(anomalies) * len(good)
    correct_full = round(img_full * total_pairs)
    correct_in = round(img_in * total_pairs)
    loss_full = total_pairs - correct_full
    loss_in = total_pairs - correct_in

    removed = 0
    removed_list = []
    for r in rows:
        cell = r[f"_{space_key}"]
        fr, fc = np.unravel_index(int(np.argmax(cell)), cell.shape)
        ir, ic = np.unravel_index(int(np.argmax(np.where(inner, cell, -np.inf))), cell.shape)
        is_removed = not inner[fr, fc]
        if is_removed:
            removed += 1
            removed_list.append({
                "filename": r["filename"],
                "defect": r["defect"],
                "full_argmax_row": int(fr),
                "full_argmax_col": int(fc),
                "new_argmax_row": int(ir),
                "new_argmax_col": int(ic),
                "full_score": float(cell[fr, fc]),
                "inner_score": float(cell[ir, ic]),
            })

    return {
        "image_auroc_full": img_full,
        "image_auroc_inner": img_in,
        "delta_inner_minus_full": img_in - img_full,
        "n_anomaly": len(anomalies),
        "n_good": len(good),
        "total_pairs": total_pairs,
        "correct_pairs_full": correct_full,
        "correct_pairs_inner": correct_in,
        "loss_full": loss_full,
        "loss_inner": loss_in,
        "loss_recovered": loss_full - loss_in,
        "argmax_removed_count": removed,
        "argmax_removed": removed_list,
    }


def per_defect_image(rows: list[dict], space_key: str, defects: list[str]) -> list[dict]:
    good = [r for r in rows if r["defect"] == "good"]
    inner = np.zeros((GRID, GRID), dtype=bool)
    inner[1:GRID - 1, 1:GRID - 1] = True
    g_full = np.array([float(r[f"_{space_key}"].max()) for r in good])
    g_in = np.array([float(r[f"_{space_key}"][inner].max()) for r in good])
    out = []
    for d in defects:
        sel = [r for r in rows if r["defect"] == d]
        if not sel:
            continue
        pos_full = np.array([float(r[f"_{space_key}"].max()) for r in sel])
        pos_in = np.array([float(r[f"_{space_key}"][inner].max()) for r in sel])
        af = POS.image_auroc(pos_full, g_full)
        ai = POS.image_auroc(pos_in, g_in)
        out.append({
            "defect": d,
            "n": len(sel),
            "image_auroc_full": af,
            "image_auroc_inner": ai,
            "delta": ai - af,
        })
    return out


# --------------------------------------------------------------------------- #
# main per category
# --------------------------------------------------------------------------- #
def analyse(category: str, cfg: dict) -> dict:
    case: Path = cfg["case"]
    size: int = cfg["size"]
    rows = read_predictions(case)
    mu, sigma, count = load_parameters(case)
    frozen = json.loads((case / "metrics-unified.json").read_text(encoding="utf-8"))

    for r in rows:
        r["_raw"] = np.load(raw_path(case, r)).astype(np.float64)
        r["_z"] = np.load(native_path(case, r)).astype(np.float64)

    # upsample faithfulness (Z evaluated maps are frozen)
    z0 = rows[0]["_z"]
    ref = np.load(case / rows[0]["eval_map_path"]).astype(np.float64)
    upsample_max_abs = float(np.abs(upsample(z0, size, size) - ref).max())

    # image_score cross-check
    max_z_err = max(abs(float(r["_z"].max()) - float(r["image_score"])) for r in rows)

    good = [r for r in rows if r["defect"] == "good"]
    anomalies = [r for r in rows if r["defect"] != "good"]

    masks = [load_mask(r["filename"], size, size) for r in rows]
    z_up = [upsample(r["_z"], size, size) for r in rows]
    raw_up = [upsample(r["_raw"], size, size) for r in rows]

    nom_support, nom_lo, nom_hi = nominal_support(size)
    str_support, str_lo, str_hi = strict_support(size)

    pix = {}
    pix_validate = {}
    for space, maps in (("Z", z_up), ("RAW", raw_up)):
        full = localization_metrics(maps, masks)
        nominal = localization_metrics(maps, masks, nom_support)
        strict = localization_metrics(maps, masks, str_support)
        pix[space] = {"full": full, "nominal_inner": nominal, "strict_inner": strict}
        if space == "Z":
            pix_validate = {
                "pixel_auroc_frozen": frozen["pixel_auroc"],
                "pixel_auroc_python": full["pixel_auroc"],
                "pixel_auroc_abs_delta": abs(full["pixel_auroc"] - frozen["pixel_auroc"]),
                "aupro_frozen": frozen["aupro030"],
                "aupro_python": full["aupro030"],
                "aupro_abs_delta": abs(full["aupro030"] - frozen["aupro030"]),
            }

    img = {s: image_level(rows, s) for s in ("raw", "z")}
    per_defect = {s: per_defect_image(rows, s, cfg["defects"]) for s in ("raw", "z")}

    return {
        "category": category,
        "size": size,
        "case": str(case.relative_to(ROOT)),
        "n_rows": len(rows),
        "n_good": len(good),
        "n_anomaly": len(anomalies),
        "frozen": frozen,
        "upsample_max_abs": upsample_max_abs,
        "image_score_max_abs_err": max_z_err,
        "pixel_validate": pix_validate,
        "supports": {
            "nominal_inner": {"lo": nom_lo, "hi": nom_hi,
                              "extent": f"{nom_lo}..{nom_hi} both axes",
                              "pixels": int(nom_support.sum())},
            "strict_inner": {"lo": str_lo, "hi": str_hi,
                             "extent": f"{str_lo}..{str_hi} both axes",
                             "pixels": int(str_support.sum())},
        },
        "image": img,
        "per_defect": per_defect,
        "pixel": pix,
    }


def classify(delta: float) -> str:
    a = abs(delta)
    if a < THRESH["marginal"]:
        return "MARGINAL"
    if a < THRESH["moderate"]:
        return "MODERATE"
    return "LARGE"


def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        return
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)


def main() -> None:
    DIAG.mkdir(parents=True, exist_ok=True)
    results = {cat: analyse(cat, cfg) for cat, cfg in CATEGORIES.items()}

    # --- aggregated CSVs --------------------------------------------------- #
    image_rows = []
    per_image_rows = []
    defect_rows = []
    pixel_rows = []
    for cat, res in results.items():
        for space, sname in (("raw", "RAW"), ("z", "Z")):
            im = res["image"][space]
            image_rows.append({
                "category": cat, "space": sname, "scope": "ALL",
                "image_auroc_full": im["image_auroc_full"],
                "image_auroc_inner": im["image_auroc_inner"],
                "delta_inner_minus_full": im["delta_inner_minus_full"],
                "total_pairs": im["total_pairs"],
                "loss_full": im["loss_full"], "loss_inner": im["loss_inner"],
                "loss_recovered": im["loss_recovered"],
                "argmax_removed_count": im["argmax_removed_count"],
                "n_good": im["n_good"], "n_anomaly": im["n_anomaly"],
                "classification": classify(im["delta_inner_minus_full"]),
            })
            for rm in im["argmax_removed"]:
                per_image_rows.append({"category": cat, "space": sname, **rm})
            for d in res["per_defect"][space]:
                defect_rows.append({
                    "category": cat, "space": sname, "defect": d["defect"], "n": d["n"],
                    "image_auroc_full": d["image_auroc_full"],
                    "image_auroc_inner": d["image_auroc_inner"],
                    "delta_inner_minus_full": d["delta"],
                    "classification": classify(d["delta"]),
                })
        for space in ("Z", "RAW"):
            p = res["pixel"][space]
            for scope, m in (("full", p["full"]), ("nominal_inner", p["nominal_inner"]),
                             ("strict_inner", p["strict_inner"])):
                if m is None:
                    continue
                pixel_rows.append({
                    "category": cat, "space": space, "scope": scope,
                    "pixel_auroc": m["pixel_auroc"], "aupro030": m["aupro030"],
                    "regions": m["regions"], "foreground_pixels": m["foreground_pixels"],
                    "background_pixels": m["background_pixels"],
                })

    write_csv(DIAG / "border_exclusion_image_level.csv", image_rows)
    write_csv(DIAG / "border_exclusion_argmax_removed.csv", per_image_rows)
    write_csv(DIAG / "border_exclusion_image_per_defect.csv", defect_rows)
    write_csv(DIAG / "border_exclusion_pixel_level.csv", pixel_rows)

    # --- deltas table (Z = official space) --------------------------------- #
    delta_rows = []
    for cat, res in results.items():
        z = res["image"]["z"]
        delta_rows.append({"category": cat, "metric": "Image AUROC (Z)",
                           "full": z["image_auroc_full"], "inner": z["image_auroc_inner"],
                           "delta": z["delta_inner_minus_full"],
                           "classification": classify(z["delta_inner_minus_full"])})
        pz = res["pixel"]["Z"]
        delta_rows.append({"category": cat, "metric": "Pixel AUROC (Z, nominal inner)",
                           "full": pz["full"]["pixel_auroc"],
                           "inner": pz["nominal_inner"]["pixel_auroc"],
                           "delta": pz["nominal_inner"]["pixel_auroc"] - pz["full"]["pixel_auroc"],
                           "classification": classify(pz["nominal_inner"]["pixel_auroc"] - pz["full"]["pixel_auroc"])})
        delta_rows.append({"category": cat, "metric": "AUPRO@0.30 (Z, nominal inner)",
                           "full": pz["full"]["aupro030"],
                           "inner": pz["nominal_inner"]["aupro030"],
                           "delta": pz["nominal_inner"]["aupro030"] - pz["full"]["aupro030"],
                           "classification": classify(pz["nominal_inner"]["aupro030"] - pz["full"]["aupro030"])})
        delta_rows.append({"category": cat, "metric": "Pixel AUROC (Z, strict inner)",
                           "full": pz["full"]["pixel_auroc"],
                           "inner": pz["strict_inner"]["pixel_auroc"],
                           "delta": pz["strict_inner"]["pixel_auroc"] - pz["full"]["pixel_auroc"],
                           "classification": classify(pz["strict_inner"]["pixel_auroc"] - pz["full"]["pixel_auroc"])})
        delta_rows.append({"category": cat, "metric": "AUPRO@0.30 (Z, strict inner)",
                           "full": pz["full"]["aupro030"],
                           "inner": pz["strict_inner"]["aupro030"],
                           "delta": pz["strict_inner"]["aupro030"] - pz["full"]["aupro030"],
                           "classification": classify(pz["strict_inner"]["aupro030"] - pz["full"]["aupro030"])})
    write_csv(DIAG / "border_exclusion_deltas.csv", delta_rows)

    summary = {
        "label": LABEL,
        "run": str(RUN.relative_to(ROOT)),
        "categories": list(CATEGORIES),
        "grid": f"{GRID}x{GRID}",
        "border_cells_definition": (
            "rows 0 and 13, columns 0 and 13 (52 cells); inner = cells r=1..12, c=1..12 (144 cells)"
        ),
        "support_definition": {
            "nominal_inner": "pixels whose nominal cell index is in 1..12 on both axes",
            "strict_inner": "pixels whose bilinear interpolation (half-pixel) uses no source cell in row/col 0 or 13",
        },
        "classification_thresholds": {
            "MARGINAL": f"|delta| < {THRESH['marginal']}",
            "MODERATE": f"{THRESH['marginal']} <= |delta| < {THRESH['moderate']}",
            "LARGE": f"|delta| >= {THRESH['moderate']}",
        },
        "results": results,
        "deltas": delta_rows,
    }
    (DIAG / "border_exclusion_summary.json").write_text(
        json.dumps(summary, indent=2, default=float), encoding="utf-8"
    )

    video = {
        "label": LABEL,
        "report": "docs/diagnostics/BORDER_EXCLUSION_SENSITIVITY.md",
        "artifacts": str(DIAG.relative_to(ROOT)),
        "categories": {},
    }
    for cat, res in results.items():
        z = res["image"]["z"]
        pz = res["pixel"]["Z"]
        video["categories"][cat] = {
            "image_auroc_full_z": z["image_auroc_full"],
            "image_auroc_inner_z": z["image_auroc_inner"],
            "image_delta": z["delta_inner_minus_full"],
            "image_class": classify(z["delta_inner_minus_full"]),
            "pixel_auroc_full_z": pz["full"]["pixel_auroc"],
            "pixel_auroc_nominal_inner_z": pz["nominal_inner"]["pixel_auroc"],
            "pixel_delta_nominal": pz["nominal_inner"]["pixel_auroc"] - pz["full"]["pixel_auroc"],
            "aupro_full_z": pz["full"]["aupro030"],
            "aupro_nominal_inner_z": pz["nominal_inner"]["aupro030"],
            "aupro_delta_nominal": pz["nominal_inner"]["aupro030"] - pz["full"]["aupro030"],
            "argmax_removed_count": z["argmax_removed_count"],
            "support_nominal": res["supports"]["nominal_inner"]["extent"],
            "support_strict": res["supports"]["strict_inner"]["extent"],
        }
    print(json.dumps(video, indent=2))
    print(f"\n{LABEL}: report -> docs/diagnostics/BORDER_EXCLUSION_SENSITIVITY.md ; artifacts -> {DIAG}")


if __name__ == "__main__":
    main()
