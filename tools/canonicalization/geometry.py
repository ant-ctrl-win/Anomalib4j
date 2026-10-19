# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy", "pillow", "pydantic>=2"]
# ///
# How to run: imported by tools/canonicalization/run.py
from typing import Literal

import numpy as np
from PIL import Image
from pydantic import BaseModel, ConfigDict
from scipy import ndimage


class GeometryError(RuntimeError):
    pass


class Pose(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)
    status: Literal["success", "failure"]
    reason: str
    cx: float = 349.5
    cy: float = 349.5
    angle: float = 0
    area_fraction: float = 0
    harmonic4: float = 0
    radius: float = 0
    scale: float = 1


def component(rgb: np.ndarray) -> np.ndarray:
    if rgb.shape != (700, 700, 3) or rgb.dtype != np.uint8:
        raise GeometryError("Expected 700x700 uint8 RGB")
    gray = np.array(Image.fromarray(rgb).convert("L").resize((175, 175), Image.Resampling.BOX))
    histogram = np.bincount(gray.ravel(), minlength=256).astype(np.float64)
    weights = histogram.cumsum()
    sums = (histogram * np.arange(256)).cumsum()
    denominator = weights * (weights[-1] - weights)
    numerator = np.square(sums[-1] * weights - sums * weights[-1])
    between = np.divide(numerator, denominator, out=np.zeros(256), where=denominator > 0)
    threshold = int(between.argmax())
    labels, count = ndimage.label(gray > threshold, structure=np.ones((3, 3)))
    if count == 0:
        return np.zeros_like(gray, dtype=bool)
    sizes = np.bincount(labels.ravel())
    sizes[0] = 0
    return ndimage.binary_fill_holes(labels == sizes.argmax())


def estimate(rgb: np.ndarray) -> Pose:
    mask = component(rgb)
    area = float(mask.mean())
    if not .2 <= area <= .8 or mask[0].any() or mask[-1].any() or mask[:, 0].any() or mask[:, -1].any():
        return Pose(status="failure", reason="area_or_border", area_fraction=area)
    y, x = np.nonzero(mask)
    cx, cy = x.mean(), y.mean()
    angle = np.arctan2(y - cy, x - cx)
    harmonic = np.exp(4j * angle).mean()
    pose = Pose(status="success", reason="ok", cx=(cx+.5)*4-.5, cy=(cy+.5)*4-.5,
                angle=float(np.angle(harmonic)*180/np.pi/4), area_fraction=area,
                harmonic4=float(abs(harmonic)), radius=float(np.sqrt(mask.sum()/np.pi)*4))
    if pose.harmonic4 < .03:
        return pose.model_copy(update={"status": "failure", "reason": "weak_fourfold_harmonic"})
    return pose


def angle_difference(degrees: float) -> float:
    return (degrees + 45) % 90 - 45


def rotation(degrees: float, center: tuple[float, float]) -> np.ndarray:
    angle = np.deg2rad(degrees)
    c, s = np.cos(angle), np.sin(angle)
    if degrees % 90 == 0:
        c, s = ((1, 0), (0, 1), (-1, 0), (0, -1))[int(degrees / 90) % 4]
    matrix = np.array([[c, -s, 0.], [s, c, 0.], [0., 0., 1.]])
    matrix[:2, 2] = np.asarray(center) - matrix[:2, :2] @ center
    return matrix


def canonical_matrix(pose: Pose) -> np.ndarray:
    if pose.status != "success":
        raise GeometryError(f"Canonicalization failed: {pose.reason}")
    matrix = rotation(-pose.angle, (pose.cx, pose.cy))
    matrix[:2, 2] += np.array([349.5-pose.cx, 349.5-pose.cy])
    return matrix


def warp(rgb: np.ndarray, forward: np.ndarray) -> np.ndarray:
    """Resample once at source resolution using a pixel-center forward transform."""
    inverse = np.linalg.inv(forward)
    corners = inverse @ np.array([[0, 699, 0, 699], [0, 0, 699, 699], [1, 1, 1, 1]])
    margin = max(0, int(np.ceil(max(-corners[:2].min(), corners[:2].max()-699)))) + 2
    padded = np.pad(rgb, ((margin, margin), (margin, margin), (0, 0)), mode="edge")
    # Pillow evaluates coordinates at pixel corners; convert the center-based matrix.
    inverse[:2, 2] += margin + .5 - inverse[:2, :2] @ np.array([.5, .5])
    result = Image.fromarray(padded).transform((700, 700), Image.Transform.AFFINE,
        tuple(inverse[:2].ravel()), resample=Image.Resampling.BILINEAR)
    return np.array(result)
