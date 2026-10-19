# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy==1.16.0", "pillow==11.3.0", "pydantic>=2", "typer"]
# ///
# How to run: python tools/canonicalization/phase1_reference.py --output NEW_DIRECTORY
"""Export read-only pilot geometry and a small retained encoder reference bridge."""
from hashlib import sha256
from pathlib import Path
from typing import Final, Literal

import numpy as np
import PIL
from PIL import Image
from pydantic import BaseModel, ConfigDict, TypeAdapter
from scipy import ndimage
import scipy
import typer

from geometry import Pose, component, estimate, angle_difference

ROOT: Final = Path(__file__).resolve().parents[2]
PILOT: Final = ROOT / "target/experiments/m2-metal-nut-canonicalization-20261019"


class ReferenceError(RuntimeError):
    pass


class PilotPlan(BaseModel):
    model_config = ConfigDict(frozen=True)
    dataset: Path


class Original(Pose):
    role: Literal["fit", "calibration", "test"]
    image: str


class Rotated(Pose):
    perturbed_image: str


class GeometryReference(BaseModel):
    model_config = ConfigDict(frozen=True)
    source: str
    source_sha256: str
    prefix: str
    pose: Pose


class BridgeReference(BaseModel):
    model_config = ConfigDict(frozen=True)
    source: str
    source_sha256: str
    role: str
    image: str


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def export_geometry(source: Path, prefix: Path, retained: Pose) -> GeometryReference:
    """Verify current pinned Python geometry against retained poses before export."""
    with Image.open(source) as image:
        rgb = np.array(image.convert("RGB"))
    pose = estimate(rgb)
    if pose != retained:
        raise ReferenceError(f"Retained geometry changed: {source}")
    gray = np.array(Image.fromarray(rgb).convert("L").resize((175, 175), Image.Resampling.BOX))
    histogram = np.bincount(gray.ravel(), minlength=256).astype(np.float64)
    weights = histogram.cumsum()
    sums = (histogram*np.arange(256)).cumsum()
    denominator = weights*(weights[-1]-weights)
    numerator = np.square(sums[-1]*weights-sums*weights[-1])
    threshold = int(np.divide(numerator, denominator, out=np.zeros(256), where=denominator > 0).argmax())
    labels, count = ndimage.label(gray > threshold, structure=np.ones((3, 3)))
    selected = np.zeros_like(gray, dtype=bool)
    if count:
        sizes = np.bincount(labels.ravel())
        sizes[0] = 0
        selected = labels == sizes.argmax()
    filled = ndimage.binary_fill_holes(selected)
    np.testing.assert_array_equal(filled, component(rgb))
    for suffix, values in (("gray", gray), ("component", selected), ("filled", filled)):
        with prefix.with_suffix("."+suffix).open("xb") as stream:
            stream.write(values.astype(np.uint8).tobytes())
    return GeometryReference(source=str(source), source_sha256=digest(source), prefix=str(prefix), pose=pose)


def main(output: Path = typer.Option(...)) -> None:
    """Write fresh parity fixtures; never fit, evaluate metrics, or alter pilot files."""
    if PIL.__version__ != "11.3.0" or scipy.__version__ != "1.16.0":
        raise ReferenceError("Pinned Pillow11.3.0 and SciPy1.16.0 required")
    root = output.resolve()
    if root.exists() or PILOT == root or PILOT in root.parents:
        raise ReferenceError("Use a new output outside retained pilot evidence")
    plan = PilotPlan.model_validate_json((PILOT / "plan.json").read_text())
    if root == plan.dataset or plan.dataset in root.parents:
        raise ReferenceError("Output cannot be inside dataset")
    originals = TypeAdapter(list[Original]).validate_json((PILOT / "geometry.json").read_text())
    rotations = TypeAdapter(list[Rotated]).validate_json((PILOT / "rotation-geometry.json").read_text())
    if len(originals) != 335 or len(rotations) != 88:
        raise ReferenceError("Incomplete retained pilot geometry")
    root.mkdir(parents=True)
    (root / "geometry").mkdir()
    references = []
    for record in originals:
        folder = "test" if record.role == "test" else "train"
        source = plan.dataset / folder / "img" / record.image
        prefix = root / "geometry" / str(len(references))
        references.append(export_geometry(source, prefix, Pose.model_validate(record.model_dump())))
    for record in rotations:
        source = PILOT / "images/robust-a" / record.perturbed_image
        prefix = root / "geometry" / str(len(references))
        references.append(export_geometry(source, prefix, Pose.model_validate(record.model_dump())))
    adapter = TypeAdapter(list[GeometryReference])
    (root / "geometry-reference.json").write_bytes(adapter.dump_json(references, indent=2))
    bridge, units, scores = [], [], []
    for role in ("fit", "calibration", "test"):
        names = (PILOT / "inputs" / (role+".txt")).read_text().splitlines()
        indices = [0, len(names)-1]
        if role == "test":
            indices += [next(i for i, name in enumerate(names) if name.startswith("metal_nut_good_")),
                        names.index("metal_nut_bent_011.png")]
        features = np.load(PILOT / "inputs" / (role+".npy"), allow_pickle=False)
        for i in dict.fromkeys(indices):
            source = PILOT / "images" / role / names[i]
            bridge.append(BridgeReference(source=str(source), source_sha256=digest(source), role=role, image=names[i]))
            units.append(features[i])
            if role == "test":
                scores.append(np.load(PILOT / "model/native-z.npy", allow_pickle=False)[i])
            else:
                scores.append(np.full(196, np.nan))
    (root / "bridge-reference.json").write_bytes(TypeAdapter(list[BridgeReference]).dump_json(bridge, indent=2))
    np.save(root / "bridge-units.npy", np.array(units))
    np.save(root / "bridge-z.npy", np.array(scores))
    hashes = {str(path): digest(path) for path in PILOT.rglob("*") if path.is_file()}
    hashes[str(Path(__file__))] = digest(Path(__file__))
    hashes[str(Path(__file__).with_name("geometry.py"))] = digest(Path(__file__).with_name("geometry.py"))
    (root / "input-hashes.json").write_bytes(TypeAdapter(dict[str, str]).dump_json(hashes, indent=2))
    print(f"PHASE1 - PYTHON REFERENCE - originals={len(originals)} rotations={len(rotations)} bridge={len(bridge)}")


if __name__ == "__main__":
    typer.run(main)
