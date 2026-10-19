# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "matplotlib"]
# ///
# How to run: imported by tools/canonicalization/run.py
import csv
from pathlib import Path

import numpy as np
from pydantic import BaseModel, ConfigDict, TypeAdapter

from common import write_json
from geometry import Pose


class Quality(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    image_auroc: float
    pixel_auroc: float
    aupro030: float


class GeometryRecord(Pose):
    role: str
    image: str
    canonical_angle_residual: float
    canonical_check_status: str


class RotationRecord(Pose):
    image: str
    rotation: float
    orientation_error: float
    equivalent_branch_crossing: bool


class Distribution(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    mean: float
    median: float
    p25: float
    p75: float
    p90: float
    p95: float
    minimum: float
    maximum: float


def distribution(values: np.ndarray) -> Distribution:
    return Distribution(mean=values.mean(), median=np.median(values), p25=np.quantile(values,.25),
        p75=np.quantile(values,.75), p90=np.quantile(values,.9), p95=np.quantile(values,.95),
        minimum=values.min(), maximum=values.max())


def analyze(root: Path, retained: Path) -> None:
    baseline = Quality.model_validate_json((retained / "metrics-unified.json").read_text())
    candidate = Quality.model_validate_json((root / "model/metrics-unified.json").read_text())
    quality = {"baseline": baseline.model_dump(), "canonicalized": candidate.model_dump(),
               "delta": {k: getattr(candidate,k)-getattr(baseline,k) for k in Quality.model_fields}}
    statistics, positions = {}, []
    for method, folder in (("baseline", root / "baseline"), ("canonicalized", root / "inputs")):
        fit = np.load(folder / "fit.npy").reshape(-1,196,96)
        cal = np.load(folder / "calibration.npy").reshape(-1,196,96)
        mean, std = fit.mean(axis=0), fit.std(axis=0,ddof=1)
        raw = np.square((cal-mean)/np.maximum(std,1e-6)).mean(axis=2)
        raw_std = raw.std(axis=0,ddof=1)
        if method == "canonicalized":
            np.testing.assert_allclose(std, np.load(root / "model/sample-std.npy"), atol=1e-12, rtol=1e-10)
            np.testing.assert_allclose(raw.mean(axis=0), np.load(root / "model/alpha.npy").ravel(), atol=1e-10, rtol=1e-10)
            np.testing.assert_allclose(np.maximum(raw_std,1e-6), np.load(root / "model/beta.npy").ravel(), atol=1e-10, rtol=1e-10)
        n = len(fit)
        mean_norm2 = np.square(mean).sum(axis=1)
        cosine = (n*mean_norm2-1)/(n-1)
        dispersion = 1-mean_norm2
        statistics[method] = {"sample_std": distribution(std).model_dump(),
            "std_fraction_at_or_below": {str(t): float(np.mean(std<=t)) for t in (1e-6,1e-5,1e-4,1e-3)},
            "calibration_raw_std": distribution(raw_std).model_dump(),
            "pairwise_cosine": distribution(cosine).model_dump(),
            "directional_dispersion": distribution(dispersion).model_dump()}
        np.save(root / (method + "-variance-map.npy"), np.square(std).mean(axis=1).reshape(14,14))
        for p in range(196):
            positions.append({"method": method, "row": p//14, "column": p%14,
                "mean_channel_variance": float(np.square(std[p]).mean()),
                "channels_std_le_1e6": int(np.sum(std[p]<=1e-6)), "channels_std_le_1e4": int(np.sum(std[p]<=1e-4)),
                "mean_pairwise_cosine": float(cosine[p]), "directional_dispersion": float(dispersion[p]),
                "calibration_raw_std": float(raw_std[p])})
    with (root / "position-statistics.csv").open("x", newline="") as stream:
        writer=csv.DictWriter(stream, fieldnames=list(positions[0])); writer.writeheader(); writer.writerows(positions)
    geometry = TypeAdapter(list[GeometryRecord]).validate_json((root / "geometry.json").read_text())
    reliability = {}
    for role in ("fit", "calibration", "test"):
        rows = [r for r in geometry if r.role == role]
        reliability[role] = {"n": len(rows), "failures": sum(r.status!="success" for r in rows),
            "canonical_check_failures": sum(r.canonical_check_status!="success" for r in rows),
            **{key: distribution(np.array([getattr(r,key) for r in rows])).model_dump()
               for key in ("angle", "cx", "cy", "area_fraction", "harmonic4", "canonical_angle_residual")}}
    rotations = TypeAdapter(list[RotationRecord]).validate_json((root / "rotation-geometry.json").read_text())
    normals = (root / "inputs/baseline-normal.txt").read_text().splitlines()
    tests = (root / "inputs/test.txt").read_text().splitlines()
    a0 = np.load(root / "baseline-normal-z.npy").max(axis=1)
    b0 = np.load(root / "model/native-z.npy").max(axis=1)
    at = np.load(root / "robust-a-z.npy").max(axis=1)
    bt = np.load(root / "robust-b-z.npy").max(axis=1)
    pairs, robustness = [], []
    for i,r in enumerate(rotations):
        pairs.append({"image":r.image,"rotation":r.rotation,"a0":float(a0[normals.index(r.image)]),"at":float(at[i]),
            "b0":float(b0[tests.index(r.image)]),"bt":float(bt[i]),
            "delta_a":float(at[i]-a0[normals.index(r.image)]),"delta_b":float(bt[i]-b0[tests.index(r.image)])})
    for angle in (-10,-5,5,10):
        group=[r for r in pairs if r["rotation"]==angle]
        robustness.append({"angle":angle,"n":len(group),**{method:distribution(np.array([r["delta_"+method] for r in group])).model_dump() for method in ("a","b")}})
    with (root / "rotation-scores.csv").open("x",newline="") as stream:
        writer=csv.DictWriter(stream,fieldnames=list(pairs[0])); writer.writeheader(); writer.writerows(pairs)
    error = np.abs([r.orientation_error for r in rotations])
    write_json(root / "summary.json", {"quality":quality,"statistics":statistics,"reliability":reliability,
        "robustness":robustness,"rotation_orientation_error":distribution(error).model_dump(),
        "rotation_failures":sum(r.status!="success" for r in rotations),
        "equivalent_branch_crossings":sum(r.equivalent_branch_crossing for r in rotations)})
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    maps = [np.load(root / (m + "-variance-map.npy")) for m in ("baseline","canonicalized")]
    figure, axes = plt.subplots(1,2,figsize=(9,4),layout="constrained")
    for axis, values, title in zip(axes,maps,("Baseline FIT variance","Canonicalized FIT variance"),strict=True):
        chart=axis.imshow(values,vmin=0,vmax=max(v.max() for v in maps),cmap="viridis")
        axis.set_title(title); axis.set_xlabel("Native column"); axis.set_ylabel("Native row")
    figure.colorbar(chart,ax=axes,label="Mean channel sample variance")
    figure.savefig(root / "variance-maps.png",dpi=150)
    plt.close(figure)
