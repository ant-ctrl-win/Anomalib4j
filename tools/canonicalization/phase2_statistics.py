# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/canonicalization/phase2.py
import csv
from pathlib import Path

import numpy as np
from pydantic import BaseModel

from phase2_models import Array, Coherence, Phase2Error, RotationPair, RotationLevel, RotationSummary, distribution


def save(path: Path, value: BaseModel) -> None:
    with path.open("x", encoding="utf-8") as stream:
        stream.write(value.model_dump_json(indent=2)+"\n")


def load_units(path: Path) -> Array:
    values = np.load(path, allow_pickle=False)
    if values.ndim != 2 or values.shape[1] != 18816 or not np.isfinite(values).all():
        raise Phase2Error(f"Expected finite Nx18816 unit descriptors: {path}")
    units = values.reshape(-1,196,96)
    np.testing.assert_allclose(np.linalg.norm(units,axis=2),1,atol=1e-12,rtol=0)
    return units


def coherence(inputs: Path, model: Path, output: Path) -> Coherence:
    fit = load_units(inputs / "fit.npy")
    cal = load_units(inputs / "calibration.npy")
    mean, std = fit.mean(axis=0), fit.std(axis=0,ddof=1)
    raw = np.square((cal-mean)/np.maximum(std,1e-6)).mean(axis=2)
    raw_std = raw.std(axis=0,ddof=1)
    np.testing.assert_allclose(mean,np.load(model / "mean.npy"),atol=1e-12,rtol=1e-10)
    np.testing.assert_allclose(std,np.load(model / "sample-std.npy"),atol=1e-12,rtol=1e-10)
    np.testing.assert_allclose(raw.mean(axis=0),np.load(model / "alpha.npy").ravel(),atol=1e-10,rtol=1e-10)
    np.testing.assert_allclose(np.maximum(raw_std,1e-6),np.load(model / "beta.npy").ravel(),atol=1e-10,rtol=1e-10)
    norm2 = np.square(mean).sum(axis=1)
    cosine = (len(fit)*norm2-1)/(len(fit)-1)
    dispersion = 1-norm2
    variance = np.square(std).mean(axis=1)
    thresholds = (1e-6,1e-5,1e-4,1e-3)
    result = Coherence(sample_std=distribution(std),low_variance_counts={str(t):int(np.sum(std<=t)) for t in thresholds},
                       per_position_variance=distribution(variance),pairwise_cosine=distribution(cosine),
                       directional_dispersion=distribution(dispersion),calibration_raw_std=distribution(raw_std))
    np.save(output / "variance-map.npy",variance.reshape(14,14))
    with (output / "position-statistics.csv").open("x",newline="") as stream:
        columns = ["row","column","mean_channel_variance","pairwise_cosine","directional_dispersion","calibration_raw_std"]
        columns += [f"std_le_{t}" for t in thresholds]
        writer = csv.DictWriter(stream,fieldnames=columns)
        writer.writeheader()
        for p in range(196):
            row = {"row":p//14,"column":p%14,"mean_channel_variance":float(variance[p]),"pairwise_cosine":float(cosine[p]),
                   "directional_dispersion":float(dispersion[p]),"calibration_raw_std":float(raw_std[p])}
            row.update({f"std_le_{t}":int(np.sum(std[p]<=t)) for t in thresholds})
            writer.writerow(row)
    save(output / "coherence.json",result)
    return result


def rotation_summary(pairs: list[RotationPair]) -> RotationSummary:
    if len(pairs) != 88 or len({(p.image,p.rotation) for p in pairs}) != 88:
        raise Phase2Error("Expected88 distinct rotation pairs")
    levels = []
    for angle in (-10,-5,5,10):
        selected = [p for p in pairs if p.rotation == angle]
        if len(selected) != 22:
            raise Phase2Error("Expected22 normal images per rotation")
        values = np.array([p.delta for p in selected])
        np.testing.assert_allclose(values,[p.transformed-p.original for p in selected],atol=1e-12,rtol=1e-12)
        levels.append(RotationLevel(angle=angle,n=22,signed_delta=distribution(values),absolute_delta=distribution(np.abs(values))))
    return RotationSummary(levels=tuple(levels),average_absolute_level_median=float(np.mean([abs(r.signed_delta.median) for r in levels])))


def retain_rotation(output: Path, pairs: list[RotationPair]) -> RotationSummary:
    summary = rotation_summary(pairs)
    with (output / "rotation-scores.csv").open("x",newline="") as stream:
        writer = csv.DictWriter(stream,fieldnames=list(RotationPair.model_fields))
        writer.writeheader()
        writer.writerows([r.model_dump() for r in pairs])
    save(output / "rotation-summary.json",summary)
    return summary
