# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "numpy", "scipy==1.16.0", "pillow==11.3.0", "pydantic>=2", "typer"]
# ///
# How to run: python -m pytest tools/canonicalization/test_phase1_reference.py
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
import pytest

from geometry import estimate
from phase1_reference import export_geometry, ReferenceError, digest


def test_export_preserves_reference_pixels_and_exact_component(tmp_path: Path) -> None:
    image = Image.new("RGB", (700, 700))
    draw = ImageDraw.Draw(image)
    draw.rectangle((260, 100, 440, 600), fill="white")
    draw.rectangle((100, 260, 600, 440), fill="white")
    rgb = np.array(image)
    source = tmp_path / "source.png"
    image.save(source)
    before = digest(source)
    expected = estimate(rgb)
    prefix = tmp_path / "reference"
    record = export_geometry(source, prefix, expected)
    assert record.source_sha256 == before == digest(source)
    assert record.pose == expected
    assert len(prefix.with_suffix(".gray").read_bytes()) == 175*175
    np.testing.assert_array_equal(np.frombuffer(prefix.with_suffix(".filled").read_bytes(), dtype=np.uint8),
                                  np.frombuffer(prefix.with_suffix(".component").read_bytes(), dtype=np.uint8))
    with pytest.raises(FileExistsError):
        export_geometry(source, prefix, expected)


def test_reference_drift_stops_instead_of_exporting(tmp_path: Path) -> None:
    rgb = np.zeros((700,700,3), dtype=np.uint8)
    source = tmp_path / "source.png"
    Image.fromarray(rgb).save(source)
    pose = estimate(rgb).model_copy(update={"cx": 300})
    with pytest.raises(ReferenceError, match="Retained geometry changed"):
        export_geometry(source, tmp_path / "reference", pose)
    assert not (tmp_path / "reference.gray").exists()
