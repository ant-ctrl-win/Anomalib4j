# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "pytest"]
# ///
# How to run: python -m pytest tools/robustness -q
import importlib.util
from pathlib import Path
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))


def summary_module():
    # Given the experiment summary module is required.
    spec = importlib.util.find_spec("summary")
    # When discovering the implementation.
    assert spec is not None, "summary implementation does not exist"
    # Then load its public data boundary.
    import summary
    return summary


def test_paired_summary_uses_image_deltas_not_difference_of_medians():
    module = summary_module()
    # Given image scores whose paired median differs from the difference of medians.
    rows = [module.Observation(category="bottle", image=f"bottle_good_{i:03}.png", family="TRANSLATION_X",
        level=2, s0=a, st=b, delta=b-a, interior_s0=a, interior_st=b, interior_delta=b-a,
        reference=50, exceeds=b > 50) for i, (a, b) in enumerate(((0, 10), (100, 101), (101, 200)))]
    # When summarizing the paired observations.
    result = module.summarize(rows)
    # Then the median is from paired changes, and strict reference exceedance is retained.
    assert result.median_delta == 10
    assert result.median_st - result.median_s0 == 1
    assert result.exceedance_fraction == pytest.approx(2 / 3)
    assert result.p90_delta == pytest.approx(81.2)


def test_pairing_rejects_changed_original_for_same_image():
    module = summary_module()
    # Given the same identity with an inconsistent original score.
    common = dict(category="bottle", image="bottle_good_001.png", family="TRANSLATION_X",
                  interior_s0=1, interior_st=2, interior_delta=1, reference=10, exceeds=False)
    rows = [module.Observation(**common, level=2, s0=1, st=2, delta=1),
            module.Observation(**common, level=4, s0=3, st=4, delta=1)]
    # When checking pairing, then the inconsistent original is rejected.
    with pytest.raises(module.RobustnessError, match="original"):
        module.validate_pairing(rows)


def test_pairing_rejects_duplicate_image_level():
    module = summary_module()
    # Given duplicate image/level observations.
    row = module.Observation(category="bottle", image="bottle_good_001.png", family="GAMMA", level=1,
        s0=1, st=1, delta=0, interior_s0=1, interior_st=1, interior_delta=0, reference=10, exceeds=False)
    # When checking pairing, then duplicate observations are rejected.
    with pytest.raises(module.RobustnessError, match="Duplicate"):
        module.validate_pairing([row, row])
