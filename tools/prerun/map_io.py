# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy"]
# ///
"""Lossless anomaly-map format (frozen for the Phase 2 comparative runner).

Decision
--------
Array payload: NumPy ``.npy`` (v1.0), C-order, little-endian, dtype ``float32``
or ``float64``. This is a documented, self-describing standard already available
in the pinned environment: it preserves the exact float values, the shape and the
dtype, and needs no custom binary parser on the Python side (``numpy.save`` /
``numpy.load``). No colored PNG is ever a metric input; the only PNG in the
pipeline stays a human visualization.

Metadata sidecar: a JSON file with the same stem as the ``.npy``. Metadata that
does not belong to the array (which sample, which geometry, which score) is kept
next to it rather than pushed into the array. Fields::

    {
      "format": "npy+json/v1",
      "filename": "bottle_broken_large_012.png",
      "geometry_id": "bottle-900x900-256x256",
      "score_kind": "native" | "evaluated",
      "dtype": "float32" | "float64",
      "shape": [rows, columns],
      "array": "bottle_broken_large_012.native.npy",
      "sha256": "<sha256 of the .npy bytes>"
    }

The layout inside a run is::

    maps/<filename-stem>.native.npy   +  .json
    maps/<filename-stem>.evaluated.npy + .json

This script both (a) provides the reusable writer/reader and (b) regenerates the
committed fixtures under ``docs/benchmark/benchmark-prerun/map-format/`` that
``NpyMapTest`` reads. Regenerating is an explicit choice, not a test side effect.
"""

import hashlib
import json
import math
from pathlib import Path
from typing import Final

import numpy as np

ROOT: Final = Path(__file__).resolve().parents[2]
FIXTURE_DIR: Final = ROOT / "docs/benchmark/benchmark-prerun/map-format"
FORMAT: Final = "npy+json/v1"
ALLOWED_DTYPES: Final = {"float32", "float64"}


def sidecar_path(array_path: Path) -> Path:
    return array_path.with_suffix(".json")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def save_map(array_path: Path, values, *, filename: str, geometry_id: str,
             score_kind: str, dtype: str | None = None) -> dict:
    """Write an array as ``.npy`` plus a JSON metadata sidecar. Returns the metadata."""
    array = np.asarray(values)
    if array.ndim != 2:
        raise ValueError(f"Anomaly map must be 2-D, got shape {array.shape}")
    resolved = dtype or ("float32" if array.dtype.itemsize <= 4 else "float64")
    if resolved not in ALLOWED_DTYPES:
        raise ValueError(f"Unsupported dtype {resolved!r}")
    array = np.ascontiguousarray(array, dtype=resolved)

    array_path = Path(array_path)
    array_path.parent.mkdir(parents=True, exist_ok=True)
    with array_path.open("wb") as stream:
        np.save(stream, array, allow_pickle=False)

    metadata = {
        "format": FORMAT,
        "filename": filename,
        "geometry_id": geometry_id,
        "score_kind": score_kind,
        "dtype": resolved,
        "shape": list(array.shape),
        "array": array_path.name,
        "sha256": sha256_file(array_path),
    }
    sidecar_path(array_path).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


def load_map(array_path: Path) -> tuple[np.ndarray, dict]:
    """Read a ``.npy`` plus sidecar and verify that array and metadata still agree."""
    array_path = Path(array_path)
    metadata = json.loads(sidecar_path(array_path).read_text(encoding="utf-8"))
    array = np.load(array_path, allow_pickle=False)
    if metadata.get("format") != FORMAT:
        raise ValueError(f"Unexpected map format {metadata.get('format')!r}")
    if list(array.shape) != metadata["shape"]:
        raise ValueError(f"Shape mismatch for {array_path}: {list(array.shape)} != {metadata['shape']}")
    if array.dtype.name != metadata["dtype"]:
        raise ValueError(f"Dtype mismatch for {array_path}: {array.dtype.name} != {metadata['dtype']}")
    if sha256_file(array_path) != metadata["sha256"]:
        raise ValueError(f"SHA-256 mismatch for {array_path}")
    return array, metadata


def _native_fixture() -> np.ndarray:
    rows = [
        [0.0, 0.125, -0.25, 1.0 / 3.0, 2.5],
        [-1.75, 3.5, -0.0625, 7.0, -2.0],
        [0.5, -8.25, 4.0 / 7.0, 0.0, 12.25],
        [-0.5, 1.0, -3.0, 6.5, -11.75],
    ]
    return np.array(rows, dtype=np.float32)


def _evaluated_fixture() -> np.ndarray:
    rows = [
        [0.0, 1.0 / 3.0, -2.0 / 7.0],
        [math.pi, -math.sqrt(2.0), 1e-9],
    ]
    return np.array(rows, dtype=np.float64)


def _write_fixtures() -> dict:
    fixtures = [
        {
            "id": "native_4x5",
            "array": "native_4x5.npy",
            "filename": "bottle_broken_large_012.png",
            "geometry_id": "bottle-900x900-256x256",
            "score_kind": "native",
            "values": _native_fixture(),
        },
        {
            "id": "evaluated_2x3",
            "array": "evaluated_2x3.npy",
            "filename": "metal_nut_flip_003.png",
            "geometry_id": "metal_nut-700x700-256x256",
            "score_kind": "evaluated",
            "values": _evaluated_fixture(),
        },
    ]
    manifest = {"format": FORMAT, "fixtures": []}
    for fixture in fixtures:
        array: np.ndarray = fixture["values"]
        metadata = save_map(
            FIXTURE_DIR / fixture["array"],
            array,
            filename=fixture["filename"],
            geometry_id=fixture["geometry_id"],
            score_kind=fixture["score_kind"],
            dtype=array.dtype.name,
        )
        manifest["fixtures"].append({
            "id": fixture["id"],
            **metadata,
            "values": [float(value) for value in array.reshape(-1)],
        })
    FIXTURE_DIR.mkdir(parents=True, exist_ok=True)
    (FIXTURE_DIR / "fixtures.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    manifest = _write_fixtures()
    for fixture in manifest["fixtures"]:
        array, metadata = load_map(FIXTURE_DIR / fixture["array"])
        assert metadata["filename"] == fixture["filename"]
        assert metadata["score_kind"] == fixture["score_kind"]
        assert metadata["geometry_id"] == fixture["geometry_id"]
        assert metadata["sha256"] == fixture["sha256"]
        assert array.dtype.name == fixture["dtype"], fixture["id"]
    print(json.dumps({
        "output": str(FIXTURE_DIR / "fixtures.json"),
        "fixtures": len(manifest["fixtures"]),
        "format": FORMAT,
    }))


if __name__ == "__main__":
    main()
