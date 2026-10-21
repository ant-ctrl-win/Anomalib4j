#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.2.5", "pillow==11.3.0", "matplotlib==3.10.6", "pydantic>=2,<3", "typer>=0.12,<1"]
# ///
# --- How to run ---
# Install uv if needed: https://docs.astral.sh/uv/getting-started/installation/
# From the repository root: uv run tools/readme/render_examples.py
# Optional source override: --dataset PATH (same retained image hashes required).
# Restore the retained campaign roots first. No model execution is performed.
# ------------------
"""Render fixed TEST examples from saved M2 maps; never infer or evaluate a model."""

from __future__ import annotations

import csv
import hashlib
import io
import json
import platform
from dataclasses import dataclass
from importlib.metadata import version
from pathlib import Path
from typing import Final

import matplotlib
import numpy as np
import typer
from numpy.typing import NDArray
from PIL import Image, ImageDraw, ImageFont
from pydantic import BaseModel, ConfigDict

ROOT: Final = Path(__file__).resolve().parents[2]
BASE: Final = Path("target/ablation/local-models-20261017")
CANONICAL: Final = Path("target/experiments/java-canonical-m2-integration-20261019-validated")
BUNDLE: Final = "8771287ad2777083c8a14f3e862142b9f36f4ef56dcbdec46cf156f30ebf9bb0"
OUTPUT: Final = ROOT / "docs/assets/readme"
Z_MAX: Final = 12.0


@dataclass(frozen=True, slots=True)
class Example:
    category: str
    defect: str
    registered: bool = False


EXAMPLES: Final = (
    Example("bottle", "broken_large"), Example("leather", "cut"),
    Example("tile", "crack"), Example("grid", "broken"),
    Example("zipper", "broken_teeth"), Example("metal_nut", "bent", True),
)


class FrozenModel(BaseModel):
    model_config = ConfigDict(frozen=True, extra="ignore")


class Prediction(FrozenModel):
    filename: str
    category: str
    defect: str
    label: str
    method: str
    score_kind: str
    image_score: float
    image_sha256: str
    native_map_path: str
    eval_map_path: str
    geometry_id: str


class Metrics(FrozenModel):
    category: str
    method: str
    status: str
    image_auroc: float
    pixel_auroc: float
    aupro030: float


class Sidecar(FrozenModel):
    filename: str
    geometry_id: str
    sha256: str
    shape: tuple[int, int]
    dtype: str


class FileRef(FrozenModel):
    path: str
    sha256: str


class Rendered(FrozenModel):
    category: str
    defect: str
    model: str
    evidence_root: str
    image: FileRef
    native_z: FileRef
    original_coordinate_z: FileRef
    references: tuple[FileRef, ...]
    metrics: Metrics
    asset: FileRef
    evaluated_z_min: float
    evaluated_z_max: float


@dataclass(frozen=True, slots=True)
class EvidenceError(RuntimeError):
    path: Path
    reason: str

    def __str__(self) -> str:
        return f"{self.path}: {self.reason}"


def file_ref(path: Path) -> FileRef:
    """Record portable repository paths and absolute external source paths."""
    name = path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else path.as_posix()
    return FileRef(path=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def load_map(path: Path, prediction: Prediction) -> NDArray[np.float64]:
    """Check saved-array bytes/identity before consuming the evaluated map."""
    sidecar = Sidecar.model_validate_json(path.with_suffix(".json").read_text())
    if (sidecar.sha256 != file_ref(path).sha256 or sidecar.filename != prediction.filename
            or sidecar.geometry_id != prediction.geometry_id or sidecar.dtype != "float64"):
        raise EvidenceError(path, "map sidecar hash/identity/dtype mismatch")
    with path.open("rb") as stream:
        values = np.asarray(np.load(stream, allow_pickle=False), dtype=np.float64)
    if values.shape != sidecar.shape or not np.isfinite(values).all():
        raise EvidenceError(path, "invalid saved-map shape or nonfinite values")
    return values


def write_stable(path: Path, content: bytes) -> None:
    """Reruns verify identical bytes; differing existing outputs require review."""
    if path.exists():
        if path.read_bytes() != content:
            raise EvidenceError(path, "refusing to replace a different existing public artifact")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as stream:
        stream.write(content)


def composite(image: Image.Image, values: NDArray[np.float64], title: str) -> bytes:
    """Full-frame input/overlay; common absolute Z range with increasing opacity."""
    original = image.convert("RGB").resize((360, 360), Image.Resampling.LANCZOS)
    display_z = Image.fromarray(values.astype(np.float32)).resize((360, 360), Image.Resampling.BILINEAR)
    scaled = np.clip(np.asarray(display_z, dtype=np.float64) / Z_MAX, 0.0, 1.0)
    color = matplotlib.colormaps["magma"](scaled)[..., :3] * 255.0
    opacity = (0.65 * scaled)[..., None]
    blended = np.rint(np.asarray(original) * (1.0 - opacity) + color * opacity).astype(np.uint8)
    card = Image.new("RGB", (752, 484), "#fafafa")
    draw = ImageDraw.Draw(card)
    draw.text((12, 9), title, fill="#18202c", font=ImageFont.load_default(size=22))
    font = ImageFont.load_default(size=17)
    draw.text((12, 43), "Original TEST", fill="#374151", font=font)
    draw.text((380, 43), "Retained Z overlay", fill="#374151", font=font)
    card.paste(original, (12, 69))
    card.paste(Image.fromarray(blended), (380, 69))
    ramp = np.linspace(0.0, 1.0, 240)[None, :].repeat(10, axis=0)
    bar = np.rint(matplotlib.colormaps["magma"](ramp)[..., :3] * 255).astype(np.uint8)
    card.paste(Image.fromarray(bar), (12, 441))
    small = ImageFont.load_default(size=14)
    for x, label in ((12, "0"), (127, "6"), (204, ">=12 Z")):
        draw.text((x, 458), label, fill="#374151", font=small)
    draw.text((280, 441), "Common Z display [0, 12]; no per-image normalization", fill="#374151", font=small)
    draw.text((280, 461), "Display clipping only; scientific maps remain unchanged", fill="#374151", font=small)
    buffer = io.BytesIO()
    card.save(buffer, format="PNG", compress_level=9, optimize=False)
    return buffer.getvalue()


def render(example: Example, dataset: Path) -> Rendered:
    """Choose the first lexical anomaly in the predeclared defect family, not by score."""
    root = ROOT / CANONICAL / "evaluation" if example.registered else ROOT / BASE / "M2" / example.category
    with (root / "predictions.csv").open(newline="", encoding="utf-8") as stream:
        rows = [Prediction.model_validate(row) for row in csv.DictReader(stream)
                if row["category"] == example.category and row["defect"] == example.defect]
    prediction = min(rows, key=lambda row: row.filename)
    if prediction.method != "M2" or prediction.label != "anomaly" or prediction.score_kind != "positional_z":
        raise EvidenceError(root, "selected prediction is not retained anomalous M2 Z")
    references = [root / "predictions.csv", root / "metrics-unified.json", root / "config.json"]
    if example.registered:
        config = json.loads((root / "config.json").read_text())
        if config["model_identity"] != BUNDLE or "bilinear" not in config["preprocessing"]:
            raise EvidenceError(root, "canonical evidence is not the qualified B bundle")
        references.append(ROOT / CANONICAL / "metal-nut.canonical-m2")
        if file_ref(references[-1]).sha256 != BUNDLE:
            raise EvidenceError(references[-1], "canonical bundle hash mismatch")
    else:
        references.append(ROOT / BASE / "source-hashes.json")
    image_path = dataset / "test/img" / prediction.filename
    if file_ref(image_path).sha256 != prediction.image_sha256:
        raise EvidenceError(image_path, "original TEST image differs from retained prediction")
    native_path, evaluated_path = root / prediction.native_map_path, root / prediction.eval_map_path
    native, values = load_map(native_path, prediction), load_map(evaluated_path, prediction)
    if native.shape != (14, 14) or not np.isclose(native.max(), prediction.image_score, atol=1e-9, rtol=1e-12):
        raise EvidenceError(native_path, "native map/max disagrees with retained prediction")
    metrics = Metrics.model_validate_json((root / "metrics-unified.json").read_text())
    if metrics.method != "M2" or metrics.status != "verified" or metrics.category != example.category:
        raise EvidenceError(root, "metric identity mismatch")
    label = "M2 + semantic registration (B)" if example.registered else "M2-P"
    suffix = "_canonical" if example.registered else ""
    output = OUTPUT / f"{example.category}{suffix}_example.png"
    with Image.open(image_path) as image:
        if values.shape != (image.height, image.width):
            raise EvidenceError(evaluated_path, "original-coordinate map and image geometry differ")
        write_stable(output, composite(image, values, f"{example.category} / {example.defect} / {label}"))
    references.extend((native_path.with_suffix(".json"), evaluated_path.with_suffix(".json")))
    return Rendered(category=example.category, defect=example.defect, model=label,
                    evidence_root=root.relative_to(ROOT).as_posix(), image=file_ref(image_path),
                    native_z=file_ref(native_path), original_coordinate_z=file_ref(evaluated_path),
                    references=tuple(file_ref(path) for path in references), metrics=metrics,
                    asset=file_ref(output), evaluated_z_min=float(values.min()), evaluated_z_max=float(values.max()))


def main(dataset: Path | None = None) -> None:
    """Restore evidence first; optionally override the external DatasetNinja root."""
    baseline = ROOT / "target/comparison/m0-all15-baseline-20261016/manifest.json"
    source = dataset or Path(json.loads(baseline.read_text())["dataset"])
    rendered = [render(example, source.resolve()) for example in EXAMPLES]
    manifest = {
        "format": "anomalib4j-readme-gallery/v1", "dataset_root": source.resolve().as_posix(),
        "dataset_attribution": {"name": "MVTec AD, MVTec Software GmbH",
                                "source": "https://www.mvtec.com/company/research/datasets/mvtec-ad",
                                "license": "CC BY-NC-SA 4.0",
                                "license_evidence": file_ref(source.resolve() / "LICENSE.md").model_dump()},
        "selection": "Five required generic M2-P categories; Carpet excluded before image inspection. First lexical TEST anomaly in each fixed defect family; no score ranking.",
        "rendering": {"size": [752, 484], "source_resize": "full-frame 360x360 LANCZOS; no crop",
                      "map_source": "retained original-coordinate evaluated Z; no regeneration/backprojection",
                      "map_display_resize": "float32 BILINEAR 360x360", "z_range": [0, Z_MAX],
                      "colormap": "matplotlib magma", "opacity": "0.65 * clip(Z/12,0,1)",
                      "rgb": "round(input*(1-opacity)+magma(clip(Z/12,0,1))*255*opacity)",
                      "font": "Pillow bundled default; sizes22/17/14", "png": "compress_level9; optimizeFalse; no timestamp",
                      "disclosure": "Same absolute range on all six cards; Z<0 transparent, Z>=12 saturated. No per-image min/max scaling. Display only, not probabilities/thresholds."},
        "runtime": {name: version(name) for name in ("numpy", "pillow", "matplotlib", "pydantic", "typer")},
        "python": platform.python_version(), "renderer": file_ref(Path(__file__)).model_dump(),
        "examples": [item.model_dump() for item in rendered],
    }
    write_stable(OUTPUT / "provenance.json", (json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode())
    print(f"Rendered/verified {len(rendered)} retained-evidence cards; no inference or evaluation.")


if __name__ == "__main__":
    typer.run(main)
