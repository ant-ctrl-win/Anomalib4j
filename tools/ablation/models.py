# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy"]
# ///
# How to run: imported by tools/ablation/run.py.
from dataclasses import dataclass
from typing import Final

import numpy as np
from numpy.typing import NDArray

Array = NDArray[np.float64]
NORM_GUARD: Final = 1e-6
CENTROID_GUARD: Final = 1e-12
FLOOR: Final = 1e-6


class ModelError(RuntimeError):
    """Invalid experimental inputs; traceback attributes remain writable by Python."""


@dataclass(frozen=True, slots=True)
class LocalModel:
    mean: Array
    centroid: Array
    weights: Array
    gram: Array
    std: Array
    effective_std: Array
    centroid_norm: Array
    projected_centroid_norm: Array


@dataclass(frozen=True, slots=True)
class Scores:
    c1: Array
    v1: Array
    m2: Array

    def items(self) -> tuple[tuple[str, Array], ...]:
        return (("C1", self.c1), ("V1", self.v1), ("M2", self.m2))


@dataclass(frozen=True, slots=True)
class Calibration:
    alpha: Array
    beta: Array
    sample_std: Array

    def apply(self, raw: Array) -> Array:
        result = (raw - self.alpha) / self.beta
        if not np.isfinite(result).all():
            raise ModelError("Nonfinite calibrated scores")
        return result


def normalize(features: Array) -> Array:
    values = np.asarray(features, dtype=np.float64)
    if values.ndim != 3 or not np.isfinite(values).all():
        raise ModelError("Expected finite N,P,C descriptors")
    norms = np.linalg.norm(values, axis=2, keepdims=True)
    bad = np.argwhere((norms[..., 0] < NORM_GUARD) | ~np.isfinite(norms[..., 0]))
    if len(bad):
        raise ModelError(f"Descriptor norm guard: count={len(bad)}, first image/position={bad[0].tolist()}")
    return values / norms


def require_unit(unit: Array) -> None:
    if unit.ndim != 3 or not np.isfinite(unit).all() or not np.allclose(
            np.linalg.norm(unit, axis=2), 1., atol=1e-12, rtol=0):
        raise ModelError("Expected finite unit descriptors")


def fit(unit: Array, gram: Array) -> LocalModel:
    require_unit(unit)
    if len(unit) < 2:
        raise ModelError("At least two fit observations required")
    channels = unit.shape[2]
    if gram.shape != (channels, channels) or not np.isfinite(gram).all() or not np.allclose(gram, gram.T, atol=1e-14, rtol=0):
        raise ModelError("Invalid Gram matrix")
    if np.linalg.eigvalsh(gram).min() <= 0:
        raise ModelError("Gram matrix must be positive definite")
    mean = unit.mean(axis=0)
    norms = np.linalg.norm(mean, axis=1)
    projected = np.sqrt(np.einsum("pc,cd,pd->p", mean, gram, mean))
    bad = np.flatnonzero((norms < CENTROID_GUARD) | (projected < CENTROID_GUARD))
    if len(bad):
        raise ModelError(f"Degenerate centroid positions: {bad.tolist()}")
    std = unit.std(axis=0, ddof=1)
    return LocalModel(mean, mean / norms[:, None], (mean @ gram) / projected[:, None],
                      gram.copy(), std, np.maximum(std, FLOOR), norms, projected)


def score(model: LocalModel, unit: Array) -> Scores:
    require_unit(unit)
    if unit.shape[1:] != model.mean.shape:
        raise ModelError("Model/descriptor shape mismatch")
    norm2 = np.einsum("ipc,cd,ipd->ip", unit, model.gram, unit, optimize=True)
    if not np.isfinite(norm2).all() or np.any(norm2 <= 0):
        raise ModelError("Invalid projected query norm")
    residual = (unit - model.mean[None]) / model.effective_std[None]
    result = Scores(1 - np.einsum("pc,ipc->ip", model.centroid, unit),
                    1 - np.einsum("pc,ipc->ip", model.weights, unit) / np.sqrt(norm2),
                    (residual * residual).mean(axis=2))
    if any(not np.isfinite(values).all() for _, values in result.items()):
        raise ModelError("Nonfinite raw scores")
    return result


def explicit_v1(model: LocalModel, unit: Array, projection: Array) -> Array:
    require_unit(unit)
    archetype = model.mean @ projection.T
    archetype = archetype / np.linalg.norm(archetype, axis=1, keepdims=True)
    output = []
    for image in unit:
        projected = image @ projection.T
        output.append(1 - np.einsum("pd,pd->p", projected, archetype) / np.linalg.norm(projected, axis=1))
    return np.asarray(output)


def calibrate_fit(raw: Array) -> Calibration:
    if raw.ndim != 2 or len(raw) < 2 or not np.isfinite(raw).all():
        raise ModelError("Calibration requires at least two finite raw maps")
    std = raw.std(axis=0, ddof=1)
    return Calibration(raw.mean(axis=0), np.maximum(std, FLOOR), std)
