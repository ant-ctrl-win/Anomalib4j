# /// script
# requires-python = ">=3.11"
# dependencies = ["torch"]
# ///
"""Deterministic fixture generator for the evaluation-space resize equivalence gate.

The evaluation-space convention is
``F.interpolate(..., mode="bilinear", align_corners=False)`` (PyTorch / Anomalib)
and must match ``LocalizationMaps.upsample`` (Java) numerically. This script only
produces the synthetic oracle: small, non-trivial fixtures plus the exact
PyTorch float64 output. It performs no fitting, no dataset access and no network
access. Run it from the repository root with the release-locked .venv Python:

    target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/prerun/resize_equivalence.py

The generated file ``docs/benchmark/benchmark-prerun/resize-equivalence.json`` is read by
``ResizeEquivalenceTest``. Regenerating it is an explicit choice, not a test
side effect.
"""

import json
from pathlib import Path
from typing import Final

import torch
import torch.nn.functional as functional

ROOT: Final = Path(__file__).resolve().parents[2]
OUT: Final = ROOT / "docs/benchmark/benchmark-prerun/resize-equivalence.json"
TOLERANCE: Final = 1e-9


def _grid(source_width: int, source_height: int, fn) -> list[float]:
    return [float(fn(row, col)) for row in range(source_height) for col in range(source_width)]


def _polynomial(a: float, b: float, c: float, d: float):
    return lambda row, col: a * row + b * col + c * row * col + d


def _cases() -> list[tuple[str, int, int, int, int, list[float]]]:
    cases: list[tuple[str, int, int, int, int, list[float]]] = [
        ("gradient_3x2_to_7x5", 3, 2, 7, 5, _grid(3, 2, _polynomial(1.3, -2.7, 0.4, 5.0))),
        ("asymmetric_2x3_to_6x6", 2, 3, 6, 6, _grid(2, 3, _polynomial(-3.5, 2.25, -0.75, -1.0))),
        ("ramp_4x4_to_9x9", 4, 4, 9, 9, _grid(4, 4, _polynomial(0.5, 1.5, 0.25, -6.0))),
        ("single_pixel_1x1_to_4x4", 1, 1, 4, 4, _grid(1, 1, _polynomial(0.0, 0.0, 0.0, -8.0))),
        ("downsample_5x3_to_3x5", 5, 3, 3, 5, _grid(5, 3, _polynomial(2.0, -1.0, 0.5, 3.0))),
        ("downsample_4x4_to_2x2", 4, 4, 2, 2, _grid(4, 4, _polynomial(1.0, 1.0, -0.125, 0.0))),
        ("tall_1x5_to_4x7", 1, 5, 4, 7, _grid(1, 5, _polynomial(0.0, 2.0, 0.0, -4.0))),
        ("wide_5x1_to_3x6", 5, 1, 3, 6, _grid(5, 1, _polynomial(0.0, 1.75, 0.0, 2.0))),
        (
            "border_impulse_4x4_to_9x9",
            4,
            4,
            9,
            9,
            _grid(
                4,
                4,
                lambda row, col: {
                    (0, 0): 12.5,
                    (0, 3): -7.25,
                    (3, 0): -3.5,
                    (3, 3): 9.0,
                    (1, 1): -15.0,
                }.get((row, col), 0.0),
            ),
        ),
    ]
    return cases


def main() -> dict:
    cases = []
    for case_id, source_width, source_height, width, height, source in _cases():
        tensor = torch.tensor(source, dtype=torch.float64).reshape(1, 1, source_height, source_width)
        resized = functional.interpolate(tensor, size=(height, width), mode="bilinear", align_corners=False)
        expected = [float(value) for value in resized.reshape(-1).tolist()]
        assert tensor.shape == (1, 1, source_height, source_width)
        assert len(expected) == width * height
        cases.append({
            "id": case_id,
            "sourceWidth": source_width,
            "sourceHeight": source_height,
            "width": width,
            "height": height,
            "source": source,
            "expected": expected,
        })

    payload = {
        "generated_by": "tools/prerun/resize_equivalence.py",
        "torch_version": torch.__version__,
        "dtype": "float64",
        "mode": "bilinear",
        "align_corners": False,
        "tolerance": TOLERANCE,
        "cases": cases,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(OUT), "cases": len(cases), "torch": torch.__version__}))
    return payload


if __name__ == "__main__":
    main()
