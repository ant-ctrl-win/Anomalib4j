"""Frozen local-memory ablation for Anomalib4j (POST-HOC, read-only).

Controlled internal ablation of four positional scorers under the *frozen*
benchmark split and evaluation conventions:

    M0  current model        raw0[p] = 1 - A_p . z_p,
                             z_p = (y_p - mu_global) / sigma_global
    M1  local CNN centroid   raw1[p] = 1 - c_p . u_p,  c_p = m_p/||m_p||,
                             m_p = mean_k u_p,k
    M2  local CNN dispersion raw2[p] = mean_c ((u_c - mu2[p,c]) / s2[p,c])^2
    M3  local VSA dispersion raw3[p] = mean_d ((y_d - mu3[p,d]) / s3[p,d])^2

with u = x / max(||x||_2, 1e-6), y = R u / sqrt(D).

Nothing in the production detector is modified; no benchmark is rerun; only
derived arrays/CSV/JSON are written under

    target/comparison/final-comparison-20261003/diagnostics/local-memory-ablation/

The CNN descriptors are re-encoded with the *production* Java encoder via the
already-verified read-only harness ``PositionalVarianceFeatureDump``.  The
Rademacher sign matrix and learning-state reader are reused from the verified
``positional_global_variance_diagnostic`` module.  Image AUROC / pixel AUROC /
AUPRO@0.30 reuse the validated Python port of ``LocalizationMetrics.java``.

Run:
    target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe \
        tools/prerun/local_memory_ablation.py
"""

from __future__ import annotations

import importlib.util
import json
import math
import os
import struct
import subprocess
import time
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / "target/comparison/final-comparison-20261003"
DATASET = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
ABL = RUN / "diagnostics/local-memory-ablation"
FEAT = ABL / "features"
CLASSPATH_FILE = ROOT / "target/comparison-classpath.txt"
ONNX = ROOT / "src/main/resources/models/mobilenetv4_spatial_14x14.onnx"

GRID = 14
CHANNELS = 96
CELLS = GRID * GRID
VSA_DIM = 10000
PROJECTION_SEED = 42
NORM_EPS = 1e-6
STD_FLOOR_VSA = 1e-8
STD_FLOOR_CNN = 1e-6
CAL_FLOOR = 1e-6

CATEGORIES = {
    "bottle": {"fit": 167, "calibration": 42, "test": 83, "size": 900},
    "metal_nut": {"fit": 176, "calibration": 44, "test": 115, "size": 700},
}
ROLE_MANIFEST = {
    "fit": ("anomalib4j-fit.txt", "train"),
    "calibration": ("anomalib4j-calibration.txt", "train"),
    "test": ("test.txt", "test"),
}

_MASK48 = (1 << 48) - 1


# --------------------------------------------------------------------------- #
# reuse of already-verified modules
# --------------------------------------------------------------------------- #
def _load(name: str):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


VAR = _load("positional_global_variance_diagnostic")   # java_rademacher_signs, read_learning_state
POSE = _load("metal_nut_pose_diagnostic")              # image_auroc, localization_metrics


# --------------------------------------------------------------------------- #
# feature dump
# --------------------------------------------------------------------------- #
def _classpath() -> str:
    return os.pathsep.join([
        str(ROOT / "target/test-classes"),
        str(ROOT / "target/classes"),
        CLASSPATH_FILE.read_text(encoding="utf-8").strip(),
    ])


def ensure_features(category: str, role: str) -> Path:
    out = FEAT / category / role
    if (out / "features.npy").is_file() and (out / "images.txt").is_file():
        return out
    manifest_name, java_role = ROLE_MANIFEST[role]
    out.mkdir(parents=True, exist_ok=True)
    command = [
        "java", "-Xmx2g", "-cp", _classpath(),
        "io.github.antctrlwin.anomalib4j.evaluation.PositionalVarianceFeatureDump",
        str(DATASET), str(out), str(RUN / "data" / category / manifest_name), category, java_role,
    ]
    subprocess.run(command, cwd=ROOT, check=True)
    return out


def load_split(category: str, role: str) -> tuple[np.ndarray, list[str]]:
    out = ensure_features(category, role)
    features = np.load(out / "features.npy").astype(np.float64)
    names = [s for s in (out / "images.txt").read_text(encoding="utf-8").splitlines() if s.strip()]
    patches = features.reshape(features.shape[0], CELLS, CHANNELS)
    norms = np.linalg.norm(patches, axis=2, keepdims=True)
    unit = patches / np.maximum(norms, NORM_EPS)
    return unit, names


# --------------------------------------------------------------------------- #
# geometry / gold evaluation
# --------------------------------------------------------------------------- #
def upsample(src: np.ndarray, size: int) -> np.ndarray:
    """Half-pixel, border-replicate bilinear == LocalizationMaps.upsample."""
    sw, sh = src.shape[1], src.shape[0]
    sy = np.clip((np.arange(size) + 0.5) * sh / size - 0.5, 0, sh - 1)
    sx = np.clip((np.arange(size) + 0.5) * sw / size - 0.5, 0, sw - 1)
    y0 = np.floor(sy).astype(int)
    x0 = np.floor(sx).astype(int)
    y1 = np.minimum(y0 + 1, sh - 1)
    x1 = np.minimum(x0 + 1, sw - 1)
    dy = (sy - y0)[:, None]
    dx = (sx - x0)[None, :]
    top = (1 - dx) * src[y0][:, x0] + dx * src[y0][:, x1]
    bot = (1 - dx) * src[y1][:, x0] + dx * src[y1][:, x1]
    return (1 - dy) * top + dy * bot


def load_mask(filename: str, size: int) -> np.ndarray:
    import base64
    import io
    import zlib
    from PIL import Image

    obj = json.loads((DATASET / "test/ann" / (filename + ".json")).read_text(encoding="utf-8"))
    mask = np.zeros((size, size), dtype=bool)
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
        arr = np.asarray(Image.open(io.BytesIO(raw)))
        if arr.ndim == 3 and arr.shape[2] == 4:
            fg = arr[..., 3] != 0
        elif arr.ndim == 3:
            fg = arr[..., :3].sum(axis=2) != 0
        else:
            fg = arr != 0
        h, w = fg.shape
        mask[oy:oy + h, ox:ox + w] |= fg
    return mask


def defect_map(category: str) -> dict[str, str]:
    import csv
    result = {}
    with (RUN / "anomalib4j" / category / "predictions.csv").open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            result[row["filename"]] = row["defect"]
    return result


def evaluate(raw_maps: np.ndarray, z_maps: np.ndarray, names: list[str],
             defects: dict[str, str], size: int, masks: list[np.ndarray]) -> dict:
    good = [i for i, name in enumerate(names) if defects[name] == "good"]
    anomaly = [i for i, name in enumerate(names) if defects[name] != "good"]

    raw_img = raw_maps.max(axis=1)
    z_img = z_maps.max(axis=1)

    raw_full = [upsample(raw_maps[i].reshape(GRID, GRID), size) for i in range(len(names))]
    z_full = [upsample(z_maps[i].reshape(GRID, GRID), size) for i in range(len(names))]

    raw_local = POSE.localization_metrics(raw_full, masks)
    z_local = POSE.localization_metrics(z_full, masks)

    return {
        "raw": {
            "image_auroc": float(POSE.image_auroc(raw_img[anomaly], raw_img[good])),
            "pixel_auroc": raw_local["pixel_auroc"],
            "aupro030": raw_local["aupro030"],
        },
        "z": {
            "image_auroc": float(POSE.image_auroc(z_img[anomaly], z_img[good])),
            "pixel_auroc": z_local["pixel_auroc"],
            "aupro030": z_local["aupro030"],
        },
        "n_good": len(good),
        "n_anomaly": len(anomaly),
    }


# --------------------------------------------------------------------------- #
# models
# --------------------------------------------------------------------------- #
def project(unit: np.ndarray, signs: np.ndarray, root_d: float) -> np.ndarray:
    """unit (..., CHANNELS) -> (..., VSA_DIM); y = (R u)/sqrt(D)."""
    return (unit @ signs) / root_d


def calibration_of(raw: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    mean = raw.mean(axis=0)
    sigma = np.maximum(raw.std(axis=0, ddof=1), CAL_FLOOR)
    return mean, sigma


def calibrate(raw: np.ndarray, mean: np.ndarray, sigma: np.ndarray) -> np.ndarray:
    return (raw - mean) / sigma


def raw_m1(unit: np.ndarray, centroid: np.ndarray) -> np.ndarray:
    return 1.0 - np.einsum("pc,ipc->ip", centroid, unit)


def raw_m2(unit: np.ndarray, mu2: np.ndarray, s2: np.ndarray) -> np.ndarray:
    r = (unit - mu2[None]) / s2[None]
    return (r * r).mean(axis=2)


def raw_m0_m3(unit: np.ndarray, signs: np.ndarray, root_d: float,
              mu_global: np.ndarray, sigma_global: np.ndarray, archetypes: np.ndarray,
              mu3: np.ndarray, s3: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    n = unit.shape[0]
    raw0 = np.empty((n, CELLS))
    raw3 = np.empty((n, CELLS))
    for i in range(n):
        y = project(unit[i], signs, root_d)
        z = (y - mu_global[None]) / sigma_global[None]
        raw0[i] = 1.0 - np.einsum("pc,pc->p", archetypes, z)
        r3 = (y - mu3) / s3
        raw3[i] = (r3 * r3).mean(axis=1)
    return raw0, raw3


def distribution(values: np.ndarray) -> dict:
    flat = np.asarray(values, dtype=np.float64).ravel()
    return {
        "n": int(flat.size),
        "min": float(flat.min()),
        "p1": float(np.percentile(flat, 1)),
        "p5": float(np.percentile(flat, 5)),
        "p25": float(np.percentile(flat, 25)),
        "median": float(np.median(flat)),
        "p75": float(np.percentile(flat, 75)),
        "p95": float(np.percentile(flat, 95)),
        "max": float(flat.max()),
        "mean": float(flat.mean()),
    }


# --------------------------------------------------------------------------- #
# per-category analysis
# --------------------------------------------------------------------------- #
def analyze(category: str) -> dict:
    spec = CATEGORIES[category]
    size = spec["size"]
    signs = VAR.java_rademacher_signs(CHANNELS, VSA_DIM, PROJECTION_SEED).T   # (CHANNELS, VSA_DIM)
    root_d = math.sqrt(VSA_DIM)

    unit_fit, fit_names = load_split(category, "fit")
    unit_cal, cal_names = load_split(category, "calibration")
    unit_test, test_names = load_split(category, "test")
    assert len(fit_names) == spec["fit"] and len(cal_names) == spec["calibration"] and len(test_names) == spec["test"]

    n_fit = unit_fit.shape[0]

    # ---- M1 / M2 (CNN space) ----
    m1_sum = unit_fit.sum(axis=0)                       # (CELLS, CHANNELS)
    centroid_norm = np.linalg.norm(m1_sum, axis=1)
    centroid = m1_sum / np.maximum(centroid_norm, NORM_EPS)[:, None]
    mu2 = unit_fit.mean(axis=0)
    std2 = unit_fit.std(axis=0, ddof=1)
    s2 = np.maximum(std2, STD_FLOOR_CNN)

    # ---- M0 / M3 (VSA space) : one fit pass ----
    mu_global = np.zeros(VSA_DIM)
    sigma_global = np.zeros(VSA_DIM)
    mu3 = np.zeros((CELLS, VSA_DIM))
    s3 = np.zeros((CELLS, VSA_DIM))
    pos_sum = np.zeros((CELLS, VSA_DIM))
    pos_sumsq = np.zeros((CELLS, VSA_DIM))
    global_sum = np.zeros(VSA_DIM)
    global_sumsq = np.zeros(VSA_DIM)
    for i in range(n_fit):
        y = project(unit_fit[i], signs, root_d)
        pos_sum += y
        pos_sumsq += y * y
        global_sum += y.sum(axis=0)
        global_sumsq += (y * y).sum(axis=0)

    n_global = n_fit * CELLS
    mu_global = global_sum / n_global
    var_global = np.clip((global_sumsq - global_sum * global_sum / n_global) / (n_global - 1), 0.0, None)
    sigma_global = np.maximum(np.sqrt(var_global), STD_FLOOR_VSA)

    mu3 = pos_sum / n_fit
    var3 = np.clip((pos_sumsq - pos_sum * pos_sum / n_fit) / (n_fit - 1), 0.0, None)
    std3 = np.sqrt(var3)
    s3 = np.maximum(std3, STD_FLOOR_VSA)

    z_sum = (pos_sum - n_fit * mu_global[None]) / sigma_global[None]
    archetype_norm = np.linalg.norm(z_sum, axis=1)
    archetypes = z_sum / archetype_norm[:, None]

    # ---- frozen M0 fidelity ----
    frozen = VAR.read_learning_state(RUN / "anomalib4j" / category / "learning-state.bin")
    fidelity = {
        "global_mean_max_abs_diff": float(np.abs(frozen["mean"] - mu_global).max()),
        "global_sigma_max_abs_diff": float(np.abs(frozen["sigma"] - sigma_global).max()),
        "archetype_max_abs_diff": float(np.abs(frozen["archetypes"] - archetypes).max()),
        "frozen_cells": int(frozen["cells"]),
        "frozen_calibration_count": int(frozen["calibration"]),
    }

    # ---- RAW score maps ----
    timing = {}
    t0 = time.perf_counter()
    test_raw_m1 = raw_m1(unit_test, centroid)
    timing["M1_raw_scoring_explicit"] = (time.perf_counter() - t0) / unit_test.shape[0]
    t0 = time.perf_counter()
    test_raw_m2 = raw_m2(unit_test, mu2, s2)
    timing["M2_raw_scoring_explicit"] = (time.perf_counter() - t0) / unit_test.shape[0]
    t0 = time.perf_counter()
    test_raw_m0, test_raw_m3 = raw_m0_m3(unit_test, signs, root_d, mu_global, sigma_global,
                                         archetypes, mu3, s3)
    timing["M0_raw_scoring_explicit_projection"] = (time.perf_counter() - t0) / unit_test.shape[0]
    timing["M3_raw_scoring_explicit_projection"] = timing["M0_raw_scoring_explicit_projection"]

    cal_raw_m1 = raw_m1(unit_cal, centroid)
    cal_raw_m2 = raw_m2(unit_cal, mu2, s2)
    cal_raw_m0, cal_raw_m3 = raw_m0_m3(unit_cal, signs, root_d, mu_global, sigma_global,
                                       archetypes, mu3, s3)

    raw_test = {"M0": test_raw_m0, "M1": test_raw_m1, "M2": test_raw_m2, "M3": test_raw_m3}
    raw_cal = {"M0": cal_raw_m0, "M1": cal_raw_m1, "M2": cal_raw_m2, "M3": cal_raw_m3}

    # ---- frozen artifact cross-check (M0) ----
    maps_dir = RUN / "anomalib4j" / category / "maps"
    frozen_raw = np.stack([np.load(maps_dir / (name.replace(".png", "") + ".raw.npy")) for name in test_names])
    frozen_z = np.stack([np.load(maps_dir / (name.replace(".png", "") + ".native.npy")) for name in test_names])
    frozen_cmp = {
        "recomputed_vs_frozen_raw_max_abs": float(np.abs(frozen_raw.reshape(len(test_names), CELLS) - test_raw_m0).max()),
    }

    # ---- evaluation ----
    defects = defect_map(category)
    masks = [load_mask(name, size) for name in test_names]
    metrics = {}
    for model in ("M0", "M1", "M2", "M3"):
        mean, sigma = calibration_of(raw_cal[model])
        z_test = calibrate(raw_test[model], mean, sigma)
        metrics[model] = evaluate(raw_test[model], z_test, test_names, defects, size, masks)
        metrics[model]["calibration_floored_positions"] = int(np.count_nonzero(raw_cal[model].std(axis=0, ddof=1) < CAL_FLOOR))

    # frozen M0 z cross-check
    frozen_cmp["recomputed_vs_frozen_native_max_abs"] = float(
        np.abs(frozen_z.reshape(len(test_names), CELLS) - calibrate(test_raw_m0, *calibration_of(cal_raw_m0))).max())

    # ---- optional compiled M3 ----
    compiled = compiled_m3_validation(unit_test, test_raw_m3, signs, root_d, mu3, s3)

    # ---- floor diagnostics ----
    diagnostics = {
        "M1_sum_norm": distribution(centroid_norm),
        "M1_mean_norm": distribution(centroid_norm / n_fit),
        "M1_degenerate_mean_centroids": int(np.count_nonzero(centroid_norm / n_fit < 1e-12)),
        "M2_std_before_floor": distribution(std2),
        "M2_std_below_floor_count": int(np.count_nonzero(std2 < STD_FLOOR_CNN)),
        "M2_std_below_floor_fraction": float(np.count_nonzero(std2 < STD_FLOOR_CNN) / std2.size),
        "M2_std_exactly_zero_count": int(np.count_nonzero(std2 == 0.0)),
        "M2_dimensions_total": int(std2.size),
        "M3_std_before_floor": distribution(std3),
        "M3_std_below_floor_count": int(np.count_nonzero(std3 < STD_FLOOR_VSA)),
        "M3_std_below_floor_fraction": float(np.count_nonzero(std3 < STD_FLOOR_VSA) / std3.size),
        "M3_std_exactly_zero_count": int(np.count_nonzero(std3 == 0.0)),
        "M3_dimensions_total": int(std3.size),
        "M0_sigma_below_floor_count": int(np.count_nonzero(np.sqrt(var_global) < STD_FLOOR_VSA)),
        "M0_sigma_exactly_zero_count": int(np.count_nonzero(var_global == 0.0)),
        "M0_dimensions_total": int(VSA_DIM),
    }

    state_bytes = {
        "M0_learning_state_archetypes_plus_global": int(CELLS * VSA_DIM * 8 + 2 * VSA_DIM * 8),
        "M0_minimal_inference_state_compiled_W_plus_b": int(CELLS * CHANNELS * 8 + CELLS * 8),
        "M1_minimal_inference_state_centroid": int(CELLS * CHANNELS * 8),
        "M2_minimal_inference_state_mean_plus_std": int(2 * CELLS * CHANNELS * 8),
        "M3_learning_state_mean_plus_std": int(2 * CELLS * VSA_DIM * 8),
        "per_model_calibration_mean_plus_sigma": int(2 * CELLS * 8),
    }

    summary = {
        "category": category,
        "size": size,
        "splits": spec,
        "counts": {"fit": n_fit, "calibration": int(unit_cal.shape[0]), "test": int(unit_test.shape[0])},
        "frozen_M0_fidelity": fidelity,
        "frozen_M0_gold_crosscheck": frozen_cmp,
        "metrics": metrics,
        "diagnostics": diagnostics,
        "state_bytes": state_bytes,
        "latency_seconds_per_image": timing,
        "compiled_m3": compiled,
    }
    _write_outputs(category, summary)
    return summary


def compiled_m3_validation(unit_test, explicit, signs, root_d, mu3, s3):
    """Compile raw3 = u^T Q u + w^T u + b and compare to explicit projected M3."""
    d = VSA_DIM
    inv_s2 = 1.0 / (s3 * s3)                       # (CELLS, VSA_DIM)
    g = math.sqrt(d) * mu3                         # (CELLS, VSA_DIM)
    q = np.empty((CELLS, CHANNELS, CHANNELS))
    w = np.empty((CELLS, CHANNELS))
    b = np.empty(CELLS)
    for p in range(CELLS):
        t = signs * inv_s2[p][None, :]             # (CHANNELS, VSA_DIM)
        q[p] = (t @ signs.T) / (d * d)
        w[p] = -2.0 * (t @ g[p]) / (d * d)
        b[p] = float(g[p] @ (inv_s2[p] * g[p])) / (d * d)
    compiled = np.empty_like(explicit)
    for i in range(unit_test.shape[0]):
        u = unit_test[i]                            # (CELLS, CHANNELS)
        quad = np.einsum("pc,pcd,pd->p", u, q, u)
        lin = np.einsum("pc,pc->p", w, u)
        compiled[i] = quad + lin + b
    diff = np.abs(compiled - explicit)
    return {
        "max_abs_error": float(diff.max()),
        "median_abs_error": float(np.median(diff)),
        "relative_to_explicit_max": float(diff.max() / max(1e-12, float(np.abs(explicit).max()))),
    }


def _write_outputs(category: str, summary: dict) -> None:
    import csv
    ABL.mkdir(parents=True, exist_ok=True)
    rows = []
    for model in ("M0", "M1", "M2", "M3"):
        for form in ("raw", "z"):
            m = summary["metrics"][model][form]
            rows.append({"category": category, "model": model, "form": form,
                         "image_auroc": m["image_auroc"], "pixel_auroc": m["pixel_auroc"],
                         "aupro030": m["aupro030"]})
    with (ABL / f"{category}_metrics.csv").open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    (ABL / f"{category}_summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    ABL.mkdir(parents=True, exist_ok=True)
    summaries = {category: analyze(category) for category in CATEGORIES}
    meta = {
        "onnx_sha256": _sha256(ONNX),
        "projection_seed": PROJECTION_SEED,
        "vsa_dim": VSA_DIM,
        "norm_epsilon": NORM_EPS,
        "std_floor_vsa": STD_FLOOR_VSA,
        "std_floor_cnn": STD_FLOOR_CNN,
        "calibration_floor": CAL_FLOOR,
        "categories": CATEGORIES,
    }
    (ABL / "summary.json").write_text(json.dumps({"meta": meta, "categories": summaries}, indent=2) + "\n",
                                      encoding="utf-8")
    print(json.dumps(_view(summaries), indent=2))


def _sha256(path: Path) -> str:
    import hashlib
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def _view(summaries: dict) -> dict:
    view = {}
    for category, s in summaries.items():
        view[category] = {
            "metrics": {model: s["metrics"][model] for model in ("M0", "M1", "M2", "M3")},
            "frozen_M0_fidelity": s["frozen_M0_fidelity"],
            "frozen_M0_gold_crosscheck": s["frozen_M0_gold_crosscheck"],
            "floor_diagnostics": s["diagnostics"],
            "compiled_m3": s["compiled_m3"],
            "latency_seconds_per_image": s["latency_seconds_per_image"],
            "state_bytes": s["state_bytes"],
        }
    return view


if __name__ == "__main__":
    main()
