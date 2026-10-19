# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/canonicalization/phase2.py
from pathlib import Path
from typing import Final, Literal

import numpy as np
from numpy.typing import NDArray
from pydantic import BaseModel, ConfigDict, Field

ROOT: Final = Path(__file__).resolve().parents[2]
PILOT: Final = ROOT / "target/experiments/m2-metal-nut-canonicalization-20261019"
PHASE1: Final = ROOT / "target/experiments/java-canonicalization-phase1-20261019"
PROTOCOL: Final = ROOT / "docs/experiments/JAVA_CANONICALIZATION_PHASE2_PROTOCOL.md"
CANDIDATES: Final = ("B", "C", "D")
Candidate = Literal["B", "C", "D"]
Array = NDArray[np.float64]


class Phase2Error(RuntimeError):
    pass


class FrozenModel(BaseModel):
    model_config = ConfigDict(frozen=True, allow_inf_nan=False)


class PoseReference(FrozenModel):
    status: Literal["success", "failure"]
    reason: str
    cx: float
    cy: float
    angle: float
    forward: tuple[float, float, float, float, float, float]


class OriginalReference(PoseReference):
    role: Literal["fit", "calibration", "test"]
    image: str


class RotationReference(PoseReference):
    image: str
    perturbed_image: str
    rotation: Literal[-10, -5, 5, 10]
    equivalent_branch_crossing: bool


class SourceInput(FrozenModel):
    role: Literal["fit", "calibration", "test", "rotation"]
    image: str
    source: Path
    sha256: str
    expected: PoseReference


class PilotPlan(FrozenModel):
    dataset: Path
    baseline: Path
    production_sources_verified: dict[str,str]


class J0Checkpoint(FrozenModel):
    cases: Literal[423]
    exact_gray_component_filled: Literal[True]
    max_center_error: float = Field(ge=0,le=1e-6)
    max_angle_error_degrees: float = Field(ge=0,le=1e-6)
    max_affine_coefficient_error: float = Field(ge=0,le=1e-6)


class J1Checkpoint(FrozenModel):
    canonical_images: Literal[8]
    test_maps: Literal[4]
    max_unit_error: Literal[0.0]
    max_z_error: Literal[0.0]


class Quality(FrozenModel):
    image_auroc: float
    pixel_auroc: float
    aupro030: float


class VerifiedQuality(Quality):
    status: Literal["verified"]
    n_good: Literal[22]
    n_anomaly: Literal[93]
    regions: int
    foreground_pixels: int
    background_pixels: int


class Distribution(FrozenModel):
    mean: float
    median: float
    p25: float
    p75: float
    p90: float
    p95: float
    minimum: float
    maximum: float


def distribution(values: Array) -> Distribution:
    if values.size == 0 or not np.isfinite(values).all():
        raise Phase2Error("Finite nonempty observations required")
    return Distribution(mean=float(values.mean()), median=float(np.median(values)), p25=float(np.quantile(values,.25)),
                        p75=float(np.quantile(values,.75)), p90=float(np.quantile(values,.9)), p95=float(np.quantile(values,.95)),
                        minimum=float(values.min()), maximum=float(values.max()))


class Coherence(FrozenModel):
    sample_std: Distribution
    low_variance_counts: dict[str, int]
    per_position_variance: Distribution
    pairwise_cosine: Distribution
    directional_dispersion: Distribution
    calibration_raw_std: Distribution


class RotationPair(FrozenModel):
    image: str
    rotation: int
    original: float
    transformed: float
    delta: float


class SavedPair(FrozenModel):
    image: str
    rotation: int
    a0: float
    at: float
    b0: float
    bt: float
    delta_a: float
    delta_b: float


class RotationLevel(FrozenModel):
    angle: int
    n: int
    signed_delta: Distribution
    absolute_delta: Distribution


class RotationSummary(FrozenModel):
    levels: tuple[RotationLevel, ...]
    average_absolute_level_median: float


class Gates(FrozenModel):
    quality_loss: bool
    above_unaligned: bool
    median_std: bool
    cosine: bool
    rotation: bool
    geometry: bool

    @property
    def qualifies(self) -> bool:
        return all(self.model_dump().values())


class GateEvidence(FrozenModel):
    quality: Quality
    coherence: Coherence
    rotation: RotationSummary
    geometry_pass: bool


def acceptance(candidate: GateEvidence, reference: GateEvidence, baseline: Quality) -> Gates:
    return Gates(quality_loss=all(getattr(candidate.quality,k) >= getattr(reference.quality,k)-.01 for k in Quality.model_fields),
                 above_unaligned=all(getattr(candidate.quality,k) > getattr(baseline,k) for k in Quality.model_fields),
                 median_std=candidate.coherence.sample_std.median <= 1.10*reference.coherence.sample_std.median,
                 cosine=candidate.coherence.pairwise_cosine.mean >= reference.coherence.pairwise_cosine.mean-.02,
                 rotation=candidate.rotation.average_absolute_level_median <= 1.0,
                 geometry=candidate.geometry_pass)
