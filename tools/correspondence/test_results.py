# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "numpy", "pydantic>=2"]
# ///
# How to run: python -m pytest tools/correspondence -q
import csv
import importlib.util
from pathlib import Path
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))


def module():
    assert importlib.util.find_spec("correspondence_results") is not None, "correspondence results implementation missing"
    import correspondence_results
    return correspondence_results


def test_positional_pair_verification_uses_identity_not_row_order(tmp_path):
    m = module()
    # Given two images with different scores and reversed saved row order.
    rows = [dict(category="bottle", image=f"bottle_good_{i:03}.png", family="TRANSLATION_X", level=2,
        s0=i, st=i+2, delta=2, interior_s0=i, interior_st=i+1, interior_delta=1, reference=10, exceeds=False) for i in (1, 2)]
    paths = [tmp_path / "current.csv", tmp_path / "old.csv"]
    for path, values in zip(paths, (rows, rows[::-1])):
        with path.open("w", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(values)
    # When comparing by identity, then both pairs match exactly.
    assert m.verify_p_pairs(*paths) == 2


def test_positional_pair_verification_rejects_wrong_original(tmp_path):
    m = module()
    # Given one changed original score under the same image key.
    row = dict(category="bottle", image="bottle_good_001.png", family="SCALE", level=.9,
        s0=1, st=3, delta=2, interior_s0=1, interior_st=2, interior_delta=1, reference=10, exceeds=False)
    paths = [tmp_path / "current.csv", tmp_path / "old.csv"]
    for path, original in zip(paths, (1, 2)):
        with path.open("w", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(row)); writer.writeheader(); writer.writerow({**row, "s0": original})
    # When comparing, then the mismatched pair is rejected.
    with pytest.raises(m.RobustnessError):
        m.verify_p_pairs(*paths)


def test_macro_weights_categories_equally_despite_sample_counts():
    m = module()
    # Given categories of different sizes and known summary statistics.
    rows = []
    for category, n, value in (("bottle", 1, 1.), ("cable", 9, 9.)):
        row = m.Summary(category=category, family="ROTATION", level=5, n=n, reference=10,
            exceedance_fraction=.5, **{key: value for key in m.Summary.model_fields
                if key not in ("category", "family", "level", "n", "reference", "exceedance_fraction")})
        rows.append(("M2-N1", row))
    # When producing macro curves, then categories receive equal weight.
    result = next(r for r in m.macros(rows) if r.group == "all")
    assert result.median_delta == 5
    assert result.p95_delta == 5
