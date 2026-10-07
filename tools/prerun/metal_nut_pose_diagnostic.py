"""Post-hoc diagnostic for the Anomalib4j Metal Nut position/rotation hypothesis.

Read-only over frozen benchmark artifacts. Writes only under
target/comparison/final-comparison-20261003/diagnostics/.

Run with:
  target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/prerun/metal_nut_pose_diagnostic.py
"""

from __future__ import annotations

import base64
import csv
import io
import json
import math
import zlib
from pathlib import Path

import numpy as np
import scipy.ndimage as ndi
from PIL import Image
from scipy.stats import pearsonr, spearmanr, rankdata
from skimage.filters import threshold_otsu

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "target/comparison/final-comparison-20261003"
CASE = RUN / "anomalib4j/metal_nut"
DIAG = RUN / "diagnostics"
VIS = DIAG / "metal_nut_good_visuals"
DATASET = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
TEST_IMG = DATASET / "test/img"
TEST_ANN = DATASET / "test/ann"
TRAIN_IMG = DATASET / "train/img"
FIT_MANIFEST = RUN / "data/metal_nut/anomalib4j-fit.txt"

W = H = 700
FPR_LIMIT = 0.30
N_ANG = 360
K_CANDIDATES = list(range(2, 13))
BAND = 25
OUT_FLOAT = 12


# --------------------------------------------------------------------------- #
# basic IO
# --------------------------------------------------------------------------- #
def read_predictions() -> list[dict]:
    rows = []
    with (CASE / "predictions.csv").open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            rows.append(row)
    return rows


def load_eval_map(rel: str) -> np.ndarray:
    arr = np.load(CASE / rel)
    assert arr.shape == (H, W), (rel, arr.shape)
    return arr.astype(np.float64)


def load_native_map(rel: str) -> np.ndarray:
    return np.load(CASE / rel)


def load_mask(filename: str) -> np.ndarray:
    path = TEST_ANN / (filename + ".json")
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


def gray(filename: str) -> np.ndarray:
    return np.asarray(Image.open(TEST_IMG / filename).convert("L"))


def gray_train(filename: str) -> np.ndarray:
    return np.asarray(Image.open(TRAIN_IMG / filename).convert("L"))


# --------------------------------------------------------------------------- #
# geometry estimation
# --------------------------------------------------------------------------- #
def object_component(arr: np.ndarray):
    threshold = threshold_otsu(arr)
    lab, n = ndi.label(arr > threshold)
    if n == 0:
        return None
    sizes = np.bincount(lab.ravel())
    sizes[0] = 0
    comp = ndi.binary_fill_holes(lab == int(sizes.argmax()))
    return comp


def estimate_geometry(arr: np.ndarray) -> dict:
    """Deterministic object geometry.

    Object = Otsu threshold -> largest 8-connected component -> hole fill.
    Orientation = phase of the dominant angular harmonic of the object-pixel
    angular histogram around the centroid (robust to the near-isotropy that
    makes the raw principal axis unstable).  The harmonic order K is chosen
    globally from the training set; orientation is then defined modulo the
    corresponding rotational symmetry period 360/K.
    """
    comp = object_component(arr)
    if comp is None:
        return {"valid": False}
    area = int(comp.sum())
    ys, xs = np.nonzero(comp)
    cy, cx = float(ys.mean()), float(xs.mean())
    y0, y1 = int(ys.min()), int(ys.max())
    x0, x1 = int(xs.min()), int(xs.max())
    yc = cy - ys  # flip y so angles are in a standard (y-up) frame
    xc = xs - cx
    theta = np.arctan2(yc, xc) % (2 * np.pi)
    bins = (theta / (2 * np.pi) * N_ANG).astype(int) % N_ANG
    hist = np.bincount(bins, minlength=N_ANG).astype(float)
    F = np.fft.fft(hist)
    mags = np.abs(F)
    dominant_k = int(K_CANDIDATES[int(np.argmax(mags[K_CANDIDATES]))])
    dx = cx - (W - 1) / 2.0
    dy = cy - (H - 1) / 2.0
    return {
        "valid": True,
        "cx": cx,
        "cy": cy,
        "dx_from_center": dx,
        "dy_from_center": dy,
        "center_distance": math.hypot(dx, dy),
        "bbox": (x0, y0, x1, y1),
        "object_area_fraction": area / float(W * H),
        "area": area,
        "radius": math.sqrt(area / math.pi),
        "F": F,
        "dominant_k": dominant_k,
        "component": comp,
    }


def orientation_from_geometry(g: dict, K: int) -> float:
    period = 360.0 / K
    F = g["F"]
    ang = -math.degrees(math.atan2(F[K].imag, F[K].real)) / K
    return ang % period


def circ_mean_mod(angles_deg, period: float) -> float:
    a = np.radians(np.asarray(angles_deg, dtype=float) * (360.0 / period))
    mean = math.atan2(float(np.mean(np.sin(a))), float(np.mean(np.cos(a))))
    return (math.degrees(mean) / (360.0 / period)) % period


def circ_dev_mod(a: float, b: float, period: float) -> float:
    half = period / 2.0
    return abs(((a - b + half) % period) - half)


def circ_std_mod(angles_deg, period: float) -> float:
    a = np.radians(np.asarray(angles_deg, dtype=float) * (360.0 / period))
    R = math.hypot(float(np.mean(np.cos(a))), float(np.mean(np.sin(a))))
    if R <= 1e-12:
        return float("inf")
    return math.degrees(math.sqrt(-2.0 * math.log(R))) / (360.0 / period)


# --------------------------------------------------------------------------- #
# metrics
# --------------------------------------------------------------------------- #
def image_auroc(pos, neg) -> float:
    pos = np.asarray(pos, dtype=float)
    neg = np.asarray(neg, dtype=float)
    allv = np.concatenate([pos, neg])
    ranks = rankdata(allv)
    npos, nneg = pos.size, neg.size
    return float((ranks[:npos].sum() - npos * (npos + 1) / 2.0) / (npos * nneg))


def _interp(x0, y0, x1, y1, x):
    return y0 + (y1 - y0) * ((x - x0) / (x1 - x0))


def localization_metrics(maps, masks) -> dict | None:
    """Faithful Python port of LocalizationMetrics.java (pixel AUROC + AUPRO)."""
    scores_parts, regions_parts = [], []
    areas = [0]
    for m, mask in zip(maps, masks):
        mf = m.ravel().astype(np.float64)
        scores_parts.append(mf)
        lab, n = ndi.label(mask.astype(np.uint8), structure=np.ones((3, 3), dtype=int))
        reg = np.zeros(mf.size, dtype=np.int64)
        if n > 0:
            flat = lab.ravel()
            for old in range(1, n + 1):
                idx = np.nonzero(flat == old)[0]
                gid = len(areas)
                areas.append(int(idx.size))
                reg[idx] = gid
        regions_parts.append(reg)
    scores = np.concatenate(scores_parts)
    regions = np.concatenate(regions_parts)
    used = scores.size
    positives = sum(areas)
    negatives = used - positives
    if positives == 0 or negatives == 0:
        return None
    num_regions = len(areas) - 1
    weights = np.zeros(len(areas), dtype=np.float64)
    for i in range(1, len(areas)):
        weights[i] = 1.0 / areas[i] / num_regions
    # ascending, so s[-1] is the highest score (mirrors LocalizationMetrics.sort)
    order = np.argsort(scores, kind="stable")
    s = scores[order]
    r = regions[order]
    tp = fp = 0
    pro = auc = pro_area = 0.0
    end = s.size - 1
    while end >= 0:
        old_fpr = fp / negatives
        old_tpr = tp / positives
        old_pro = pro
        thr = s[end]
        while True:
            region = int(r[end])
            end -= 1
            if region == 0:
                fp += 1
            else:
                tp += 1
                pro += weights[region]
            if not (end >= 0 and s[end] == thr):
                break
        fpr = fp / negatives
        tpr = tp / positives
        auc += (fpr - old_fpr) * (old_tpr + tpr) / 2.0
        if fpr > old_fpr and old_fpr < FPR_LIMIT:
            stop = min(fpr, FPR_LIMIT)
            stop_pro = _interp(old_fpr, old_pro, fpr, pro, stop)
            pro_area += (stop - old_fpr) * (old_pro + stop_pro) / 2.0
    return {
        "pixel_auroc": auc,
        "aupro030": pro_area / FPR_LIMIT,
        "regions": num_regions,
        "foreground_pixels": int(positives),
        "background_pixels": int(negatives),
    }


def boundary_stats(z: np.ndarray, comp: np.ndarray, cx: float, cy: float, radius: float) -> dict:
    dil = ndi.binary_dilation(comp, iterations=BAND)
    ero = ndi.binary_erosion(comp, iterations=BAND)
    band = dil & ~ero
    interior = ero
    outside = ~dil
    zp = np.clip(z, 0.0, None)
    total = float(zp.sum())
    idx = np.unravel_index(int(np.argmax(z)), z.shape)
    return {
        "z_sum_band_fraction": float(zp[band].sum() / total) if total > 0 else None,
        "z_sum_interior_fraction": float(zp[interior].sum() / total) if total > 0 else None,
        "z_sum_outside_fraction": float(zp[outside].sum() / total) if total > 0 else None,
        "mean_z_band": float(zp[band].mean()),
        "mean_z_interior": float(zp[interior].mean()),
        "mean_z_outside": float(zp[outside].mean()),
        "argmax_dist_to_centroid_over_radius": float(
            math.hypot(idx[1] - cx, idx[0] - cy) / radius) if radius > 0 else None,
    }


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def main() -> None:
    DIAG.mkdir(parents=True, exist_ok=True)
    VIS.mkdir(parents=True, exist_ok=True)
    rows = read_predictions()
    good = [r for r in rows if r["defect"] == "good"]
    good_scores = np.array([float(r["image_score"]) for r in good])

    # ---- 1. good ranking -------------------------------------------------- #
    order = np.argsort(-good_scores)
    with (DIAG / "metal_nut_good_scores.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["rank", "filename", "image_score", "native_map_path", "eval_map_path"])
        for rank, i in enumerate(order, 1):
            r = good[i]
            w.writerow([rank, r["filename"], f"{good_scores[i]:.{OUT_FLOAT}f}",
                        r["native_map_path"], r["eval_map_path"]])
    stats = {
        "n": int(good_scores.size),
        "min": float(np.min(good_scores)),
        "max": float(np.max(good_scores)),
        "mean": float(np.mean(good_scores)),
        "median": float(np.median(good_scores)),
        "p75": float(np.percentile(good_scores, 75)),
        "p90": float(np.percentile(good_scores, 90)),
    }
    print("GOOD SCORE STATS", json.dumps(stats, indent=2))

    # ---- 2. geometry of 22 good + 176 training normals -------------------- #
    geo_good = {}
    for r in good:
        geo_good[r["filename"]] = estimate_geometry(gray(r["filename"]))

    with FIT_MANIFEST.open(encoding="utf-8-sig") as fh:
        train_names = [ln.strip() for ln in fh if ln.strip()]
    geo_train = {}
    for name in train_names:
        info = estimate_geometry(gray_train(name))
        if info["valid"]:
            geo_train[name] = info
    print(f"training normals: {len(train_names)} listed, {len(geo_train)} valid geometry")

    # global symmetry order from training
    dom_k = np.array([g["dominant_k"] for g in geo_train.values()])
    K = int(np.bincount(dom_k, minlength=13)[2:].argmax() + 2)
    period = 360.0 / K
    print(f"symmetry order K={K} (period={period:.1f} deg); dominant_k histogram:",
          {int(k): int(v) for k, v in zip(*np.unique(dom_k, return_counts=True))})

    for g in geo_train.values():
        g["orientation_mod"] = orientation_from_geometry(g, K)
    for g in geo_good.values():
        g["orientation_mod"] = orientation_from_geometry(g, K)

    train_centers = np.array([[g["cx"], g["cy"]] for g in geo_train.values()])
    train_angles = np.array([g["orientation_mod"] for g in geo_train.values()])
    train_mean_center = train_centers.mean(axis=0)
    train_dominant_angle = circ_mean_mod(train_angles, period)
    train_circ_std = circ_std_mod(train_angles, period)
    train_center_std = train_centers.std(axis=0)
    train_area = np.array([g["object_area_fraction"] for g in geo_train.values()])
    hist, edges = np.histogram(train_angles, bins=18, range=(0.0, period))
    dominant_bin = int(hist.argmax())
    print("TRAINING POSE STATS", json.dumps({
        "n": len(geo_train),
        "mean_center": train_mean_center.tolist(),
        "center_std": train_center_std.tolist(),
        "dominant_angle": train_dominant_angle,
        "circ_std": train_circ_std,
        "dominant_bin_edges": [float(edges[dominant_bin]), float(edges[dominant_bin + 1])],
        "area_mean": float(train_area.mean()),
        "area_std": float(train_area.std()),
    }, indent=2))

    with (DIAG / "metal_nut_training_geometry.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["filename", "cx", "cy", "dx_from_center", "dy_from_center",
                    "center_distance", "orientation_deg", "bbox", "object_area_fraction",
                    "dominant_k"])
        for name, g in geo_train.items():
            w.writerow([name] +
                       [f"{g[k]:.{OUT_FLOAT}f}" for k in
                        ("cx", "cy", "dx_from_center", "dy_from_center", "center_distance",
                         "orientation_mod")] +
                       [f"{g['bbox'][0]};{g['bbox'][1]};{g['bbox'][2]};{g['bbox'][3]}",
                        f"{g['object_area_fraction']:.{OUT_FLOAT}f}", g["dominant_k"]])

    # ---- score vs geometry for good -------------------------------------- #
    center_distance, abs_dx, abs_dy = [], [], []
    dev_dominant, dev_nearest, dist_train_center, nearest_train_center = [], [], [], []
    for r in good:
        g = geo_good[r["filename"]]
        center_distance.append(g["center_distance"])
        abs_dx.append(abs(g["dx_from_center"]))
        abs_dy.append(abs(g["dy_from_center"]))
        dev_dominant.append(circ_dev_mod(g["orientation_mod"], train_dominant_angle, period))
        devs = [circ_dev_mod(g["orientation_mod"], a, period) for a in train_angles]
        dev_nearest.append(min(devs))
        dist_train_center.append(math.hypot(g["cx"] - train_mean_center[0], g["cy"] - train_mean_center[1]))
        dc_all = np.hypot(train_centers[:, 0] - g["cx"], train_centers[:, 1] - g["cy"])
        nearest_train_center.append(float(dc_all.min()))
    array = lambda x: np.asarray(x, dtype=float)  # noqa: E731
    center_distance, abs_dx, abs_dy = array(center_distance), array(abs_dx), array(abs_dy)
    dev_dominant, dev_nearest = array(dev_dominant), array(dev_nearest)
    dist_train_center, nearest_train_center = array(dist_train_center), array(nearest_train_center)

    good_by_name = {r["filename"]: r for r in good}
    cached_maps = {r["filename"]: load_eval_map(r["eval_map_path"]) for r in rows}
    cached_masks = {r["filename"]: (np.zeros((H, W), dtype=bool) if r["defect"] == "good"
                                    else load_mask(r["filename"])) for r in rows}

    k_ref = max(1, len(good) // 4)
    low_ref_angles = [geo_good[good[i]["filename"]]["orientation_mod"] for i in order[-k_ref:]]
    low_ref_angle = circ_mean_mod(low_ref_angles, period)
    dev_low_ref = np.array([circ_dev_mod(geo_good[r["filename"]]["orientation_mod"],
                                         low_ref_angle, period) for r in good])
    print("LOW-QUARTILE REFERENCE mean orientation (mod %.1f): %.4f deg" % (period, low_ref_angle))

    boundary_rows = []
    bd_band_frac, bd_argmax_r, bd_outside_frac = [], [], []
    int_mean, int_std, int_bright = [], [], []
    for r in good:
        g = geo_good[r["filename"]]
        bs = boundary_stats(cached_maps[r["filename"]], g["component"], g["cx"], g["cy"], g["radius"])
        bd_band_frac.append(bs["z_sum_band_fraction"])
        bd_outside_frac.append(bs["z_sum_outside_fraction"])
        bd_argmax_r.append(bs["argmax_dist_to_centroid_over_radius"])
        boundary_rows.append((r["filename"], bs))
        vals = gray(r["filename"])[g["component"]].astype(float)
        int_mean.append(float(vals.mean()))
        int_std.append(float(vals.std()))
        int_bright.append(float((vals >= 250).mean()))
    bd_band_frac, bd_outside_frac = array(bd_band_frac), array(bd_outside_frac)
    bd_argmax_r = array(bd_argmax_r)
    int_mean, int_std, int_bright = array(int_mean), array(int_std), array(int_bright)

    with (DIAG / "metal_nut_good_geometry.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["filename", "cx", "cy", "dx_from_center", "dy_from_center", "center_distance",
                    "orientation_deg", "orientation_period_deg", "bbox", "object_area_fraction",
                    "anomaly_score", "train_center_distance", "nearest_train_center_distance",
                    "angular_dev_from_dominant_deg", "angular_dev_nearest_train_deg",
                    "z_mass_on_boundary_fraction", "z_mass_outside_fraction",
                    "z_argmax_dist_to_centroid_over_radius", "dominant_k"])
        for pos, r in enumerate(good):
            g = geo_good[r["filename"]]
            bs = boundary_rows[pos][1]
            w.writerow([r["filename"]] +
                       [f"{g[k]:.{OUT_FLOAT}f}" for k in
                        ("cx", "cy", "dx_from_center", "dy_from_center", "center_distance",
                         "orientation_mod")] +
                       [f"{period:.{OUT_FLOAT}f}",
                        f"{g['bbox'][0]};{g['bbox'][1]};{g['bbox'][2]};{g['bbox'][3]}",
                        f"{g['object_area_fraction']:.{OUT_FLOAT}f}",
                        f"{g['anomaly_score'] if 'anomaly_score' in g else float(r['image_score']):.{OUT_FLOAT}f}",
                        f"{dist_train_center[pos]:.{OUT_FLOAT}f}",
                        f"{nearest_train_center[pos]:.{OUT_FLOAT}f}",
                        f"{dev_dominant[pos]:.{OUT_FLOAT}f}",
                        f"{dev_nearest[pos]:.{OUT_FLOAT}f}",
                        f"{bs['z_sum_band_fraction']:.{OUT_FLOAT}f}" if bs["z_sum_band_fraction"] is not None else "",
                        f"{bs['z_sum_outside_fraction']:.{OUT_FLOAT}f}" if bs["z_sum_outside_fraction"] is not None else "",
                        f"{bs['argmax_dist_to_centroid_over_radius']:.{OUT_FLOAT}f}" if bs["argmax_dist_to_centroid_over_radius"] is not None else "",
                        g["dominant_k"]])

    def corr(name, x):
        x = np.asarray(x, dtype=float)
        p = pearsonr(x, good_scores)
        s = spearmanr(x, good_scores)
        return [name, str(x.size),
                f"{p.statistic:.{OUT_FLOAT}f}", f"{p.pvalue:.{OUT_FLOAT}f}",
                f"{s.statistic:.{OUT_FLOAT}f}", f"{s.pvalue:.{OUT_FLOAT}f}"]

    corr_rows = [
        corr("center_distance", center_distance),
        corr("abs_dx", abs_dx),
        corr("abs_dy", abs_dy),
        corr("angular_dev_from_dominant", dev_dominant),
        corr("angular_dev_nearest_train", dev_nearest),
        corr("angular_dev_from_low_quartile_mean", dev_low_ref),
        corr("distance_from_train_mean_center", dist_train_center),
        corr("nearest_train_center_distance", nearest_train_center),
        corr("z_mass_on_boundary_fraction", bd_band_frac),
        corr("z_mass_outside_fraction", bd_outside_frac),
        corr("z_argmax_dist_to_centroid_over_radius", bd_argmax_r),
        corr("object_intensity_mean", int_mean),
        corr("object_intensity_std", int_std),
        corr("object_bright_pixel_fraction", int_bright),
    ]
    with (DIAG / "metal_nut_geometry_correlations.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["variable", "n", "pearson_r", "pearson_p", "spearman_rho", "spearman_p"])
        w.writerows(corr_rows)
    print("CORRELATIONS")
    for row in corr_rows:
        print("  ", row)

    # ---- 6. per-defect ---------------------------------------------------- #
    aggregate = localization_metrics([cached_maps[r["filename"]] for r in rows],
                                     [cached_masks[r["filename"]] for r in rows])
    frozen = json.loads((CASE / "metrics-unified.json").read_text(encoding="utf-8"))
    agg_image = image_auroc([float(r["image_score"]) for r in rows if r["defect"] != "good"], good_scores)
    aggregate_ok = (abs(aggregate["pixel_auroc"] - frozen["pixel_auroc"]) < 1e-9
                    and abs(aggregate["aupro030"] - frozen["aupro030"]) < 1e-9)
    print("PIXEL PORT VALIDATION", json.dumps({
        "python_pixel_auroc": aggregate["pixel_auroc"], "frozen_pixel_auroc": frozen["pixel_auroc"],
        "abs_delta_pixel": abs(aggregate["pixel_auroc"] - frozen["pixel_auroc"]),
        "python_aupro": aggregate["aupro030"], "frozen_aupro": frozen["aupro030"],
        "abs_delta_aupro": abs(aggregate["aupro030"] - frozen["aupro030"]),
        "python_image_auroc": agg_image, "frozen_image_auroc": frozen["image_auroc"],
        "abs_delta_image": abs(agg_image - frozen["image_auroc"]),
    }, indent=2))

    defects = ["bent", "color", "flip", "scratch"]
    per_defect = []
    for d in defects:
        sub = [r for r in rows if r["defect"] == d]
        pos = np.array([float(r["image_score"]) for r in sub])
        au = image_auroc(pos, good_scores)
        names = [r["filename"] for r in good + sub]
        lm = localization_metrics([cached_maps[n] for n in names], [cached_masks[n] for n in names])
        if not aggregate_ok:
            lm = None
        per_defect.append({
            "defect": d, "n_defect": len(sub), "n_good": len(good), "image_auroc": au,
            "pixel_auroc": None if lm is None else lm["pixel_auroc"],
            "aupro030": None if lm is None else lm["aupro030"],
            "regions": None if lm is None else lm["regions"],
            "foreground_pixels": None if lm is None else lm["foreground_pixels"],
            "background_pixels": None if lm is None else lm["background_pixels"],
            "image_score_mean": float(pos.mean()), "image_score_median": float(np.median(pos)),
            "image_score_min": float(pos.min()), "image_score_max": float(pos.max()),
        })
        print("PER DEFECT", d, json.dumps(per_defect[-1]))

    with (DIAG / "metal_nut_per_defect_diagnostics.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["defect", "n_defect", "n_good", "image_auroc", "pixel_auroc", "aupro030",
                    "regions", "foreground_pixels", "background_pixels",
                    "image_score_mean", "image_score_median", "image_score_min", "image_score_max"])
        for e in per_defect:
            w.writerow([e["defect"], e["n_defect"], e["n_good"],
                        f"{e['image_auroc']:.{OUT_FLOAT}f}",
                        "" if e["pixel_auroc"] is None else f"{e['pixel_auroc']:.{OUT_FLOAT}f}",
                        "" if e["aupro030"] is None else f"{e['aupro030']:.{OUT_FLOAT}f}",
                        "" if e["regions"] is None else e["regions"],
                        "" if e["foreground_pixels"] is None else e["foreground_pixels"],
                        "" if e["background_pixels"] is None else e["background_pixels"],
                        f"{e['image_score_mean']:.{OUT_FLOAT}f}",
                        f"{e['image_score_median']:.{OUT_FLOAT}f}",
                        f"{e['image_score_min']:.{OUT_FLOAT}f}",
                        f"{e['image_score_max']:.{OUT_FLOAT}f}"])

    # ---- 5. visual diagnostics ------------------------------------------- #
    top5 = list(order[:5])
    bottom5 = list(order[-5:])
    sel = top5 + bottom5
    vmax = max(float(cached_maps[good[i]["filename"]].max()) for i in sel)
    for rank, i in enumerate(top5, 1):
        _visual(good[i], geo_good, train_mean_center, train_dominant_angle, train_angles,
                period, vmax, VIS / f"high_{rank:02d}_{good[i]['filename'].replace('.png', '')}")
    for rank, i in enumerate(bottom5, 1):
        _visual(good[i], geo_good, train_mean_center, train_dominant_angle, train_angles,
                period, vmax, VIS / f"low_{rank:02d}_{good[i]['filename'].replace('.png', '')}")

    # ---- 7. mean heatmaps low vs high quartile ---------------------------- #
    k = max(1, len(good) // 4)
    low_idx = list(order[-k:])
    high_idx = list(order[:k])
    mean_low = np.mean([cached_maps[good[i]["filename"]] for i in low_idx], axis=0)
    mean_high = np.mean([cached_maps[good[i]["filename"]] for i in high_idx], axis=0)
    mean_low_native = np.mean([load_native_map(good[i]["native_map_path"]) for i in low_idx], axis=0)
    mean_high_native = np.mean([load_native_map(good[i]["native_map_path"]) for i in high_idx], axis=0)
    np.save(DIAG / "metal_nut_mean_native_low.npy", mean_low_native.astype(np.float32))
    np.save(DIAG / "metal_nut_mean_native_high.npy", mean_high_native.astype(np.float32))

    # mean object mask (good) for reference
    mean_mask = np.mean([geo_good[r["filename"]]["component"].astype(float) for r in good], axis=0)

    fig, axes = plt.subplots(2, 2, figsize=(12, 12))
    for ax, data, title in [
        (axes[0][0], mean_low, f"mean Z full-res, low quartile (n={len(low_idx)})"),
        (axes[0][1], mean_high, f"mean Z full-res, high quartile (n={len(high_idx)})"),
        (axes[1][0], mean_low_native, "mean native Z 14x14, low quartile"),
        (axes[1][1], mean_high_native, "mean native Z 14x14, high quartile"),
    ]:
        im = ax.imshow(data, cmap="inferno")
        ax.set_title(title)
        fig.colorbar(im, ax=ax, fraction=0.046)
    fig.suptitle("Metal Nut Anomalib4j good: mean positional Z, low vs high score quartile")
    fig.tight_layout()
    fig.savefig(DIAG / "metal_nut_mean_heatmaps.png", dpi=110)
    plt.close(fig)

    # overlay mean mask contour on the quartile-difference map
    diff = mean_high - mean_low
    fig, ax = plt.subplots(figsize=(7, 7))
    im = ax.imshow(diff, cmap="coolwarm")
    ax.contour(mean_mask > 0.5, levels=[0.5], colors="lime", linewidths=1.5)
    ax.set_title("mean Z high-quartile minus low-quartile (object outline in green)")
    fig.colorbar(im, ax=ax, fraction=0.046)
    fig.tight_layout()
    fig.savefig(DIAG / "metal_nut_mean_diff.png", dpi=110)
    plt.close(fig)

    print("MEAN HEATMAP DIFF", json.dumps({
        "low_filenames": [good[i]["filename"] for i in low_idx],
        "high_filenames": [good[i]["filename"] for i in high_idx],
        "mean_low_max": float(mean_low.max()), "mean_high_max": float(mean_high.max()),
        "diff_max": float(diff.max()), "diff_min": float(diff.min()),
    }, indent=2))

    summary = {
        "hypothesis": ("strong Anomalib4j Metal Nut drop mainly due to positional memory/"
                       "calibration sensitivity to rotation/translation of the normal object"),
        "symmetry_order_k": K, "orientation_period_deg": period,
        "good_score_stats": stats,
        "trained_normals": len(geo_train),
        "training": {
            "mean_center": train_mean_center.tolist(), "center_std": train_center_std.tolist(),
            "dominant_angle": train_dominant_angle, "circ_std": train_circ_std,
            "center_distance_mean": float(np.hypot(train_mean_center[0] - (W - 1) / 2,
                                                   train_mean_center[1] - (H - 1) / 2)),
        },
        "correlations": corr_rows,
        "per_defect": per_defect,
        "pixel_port_validated": aggregate_ok,
        "aggregate_python": aggregate,
        "aggregate_frozen": {k2: frozen[k2] for k2 in ("image_auroc", "pixel_auroc", "aupro030")},
        "aggregate_python_image_auroc": agg_image,
    }
    (DIAG / "metal_nut_pose_summary.json").write_text(
        json.dumps(summary, indent=2, default=float), encoding="utf-8")
    print("DONE. outputs in", DIAG)


def _visual(row, geo_good, train_mean_center, dominant_angle, train_angles, period, vmax, stem):
    from PIL import Image as _Image
    z = load_eval_map(row["eval_map_path"])
    img = np.asarray(_Image.open(TEST_IMG / row["filename"]).convert("RGB"))
    g = geo_good[row["filename"]]
    dev_dom = circ_dev_mod(g["orientation_mod"], dominant_angle, period)
    dev_near = min(circ_dev_mod(g["orientation_mod"], a, period) for a in train_angles)
    dist_center = math.hypot(g["cx"] - train_mean_center[0], g["cy"] - train_mean_center[1])
    score = float(row["image_score"])

    fig, axes = plt.subplots(1, 3, figsize=(16, 6))
    axes[0].imshow(img)
    axes[0].set_title(f"{row['filename']}  score={score:.3f}")
    im = axes[1].imshow(z, cmap="inferno", vmin=0.0, vmax=vmax)
    axes[1].set_title("positional Z heatmap")
    fig.colorbar(im, ax=axes[1], fraction=0.046)
    axes[2].imshow(img)
    axes[2].imshow(z, cmap="inferno", vmin=0.0, vmax=vmax, alpha=0.45)
    axes[2].scatter([g["cx"]], [g["cy"]], c="cyan", marker="+", s=160, label="centroid")
    L = 120
    axes[2].plot([g["cx"] - L, g["cx"] + L], [g["cy"], g["cy"]], c="lime", lw=1, alpha=0.5)
    axes[2].legend(loc="lower right", fontsize=8)
    axes[2].set_title(f"overlay scale 0-{vmax:.2f} | dev_dom={dev_dom:.1f}° "
                      f"dev_near={dev_near:.1f}° dist_train_center={dist_center:.1f}px")
    for ax in axes:
        ax.axis("off")
    fig.tight_layout()
    fig.savefig(str(stem) + ".png", dpi=110)
    plt.close(fig)


if __name__ == "__main__":
    main()
