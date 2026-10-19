# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy", "pillow", "pydantic>=2", "pytest"]
# ///
# How to run: python -m pytest tools/canonicalization
from importlib.util import find_spec
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
import pytest


def test_identity_warp_and_input_preservation() -> None:
    assert find_spec("geometry") is not None
    from geometry import warp
    image = np.random.default_rng(42).integers(0, 256, (700, 700, 3), dtype=np.uint8)
    original = image.copy()
    actual = warp(image, np.eye(3))
    np.testing.assert_array_equal(actual, original)
    np.testing.assert_array_equal(image, original)


def test_fourfold_orientation_equivariance_and_determinism() -> None:
    assert find_spec("geometry") is not None
    from geometry import estimate, warp, rotation, canonical_matrix, angle_difference
    image = Image.new("RGB", (700, 700))
    draw = ImageDraw.Draw(image)
    draw.rectangle((260, 100, 440, 600), fill="white")
    draw.rectangle((100, 260, 600, 440), fill="white")
    source = np.array(image)
    pose = estimate(source)
    assert pose.status == "success"
    for angle in (-10, -5, 5, 10, 90):
        perturbed = warp(source, rotation(angle, (349.5, 349.5)))
        observed = estimate(perturbed)
        assert observed == estimate(perturbed)
        assert abs(angle_difference(observed.angle - pose.angle - angle)) < .5
        point = canonical_matrix(observed) @ np.array([observed.cx, observed.cy, 1])
        np.testing.assert_allclose(point, [349.5, 349.5, 1], atol=1e-12)


def test_textureless_input_is_explicit_failure() -> None:
    assert find_spec("geometry") is not None
    from geometry import estimate, canonical_matrix, GeometryError
    pose = estimate(np.zeros((700, 700, 3), dtype=np.uint8))
    assert pose.status == "failure"
    with pytest.raises(GeometryError):
        canonical_matrix(pose)


def test_warp_uses_the_same_pixel_coordinates_as_map_backprojection() -> None:
    from geometry import warp, rotation
    image = np.zeros((700, 700, 3), dtype=np.uint8)
    image[20, 10] = 255
    rotated = warp(image, rotation(90, (349.5, 349.5)))
    np.testing.assert_array_equal(rotated[10, 679], [255, 255, 255])
    translated = np.eye(3)
    translated[0, 2] = 1
    shifted = warp(image, translated)
    np.testing.assert_array_equal(shifted[20, 11], [255, 255, 255])


def test_preparation_retains_all_split_records_without_overwriting(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.syspath_prepend(str(Path(__file__).resolve().parents[1] / "ablation"))
    from inputs import Split
    from prepare import prepare
    dataset = tmp_path / "dataset"
    for role in ("train", "test"):
        (dataset / role / "img").mkdir(parents=True)
    image = Image.new("RGB", (700, 700))
    draw = ImageDraw.Draw(image)
    draw.rectangle((260, 100, 440, 600), fill="white")
    draw.rectangle((100, 260, 600, 440), fill="white")
    split = Split(fit=("metal_nut_good_f1.png", "metal_nut_good_f2.png"),
                  calibration=("metal_nut_good_c1.png", "metal_nut_good_c2.png"),
                  test=("metal_nut_good_t1.png", "metal_nut_good_t2.png"))
    for name in split.fit + split.calibration:
        image.save(dataset / "train/img" / name)
    for name in split.test:
        image.save(dataset / "test/img" / name)
    output = tmp_path / "output"
    output.mkdir()
    prepare(output, dataset, split)
    records = json.loads((output / "geometry.json").read_text())
    assert len(records) == 6
    assert [r["role"] for r in records] == ["fit", "fit", "calibration", "calibration", "test", "test"]
    assert np.load(output / "inputs/test-transforms.npy").shape == (2, 6)
