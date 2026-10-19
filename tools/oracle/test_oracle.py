# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "pytest"]
# ///
# How to run: python -m pytest tools/oracle
from importlib.util import find_spec

import numpy as np
import pytest


def test_recovery_excludes_small_denominators() -> None:
    assert find_spec("oracle_results") is not None
    from oracle_results import score_summary
    result = score_summary(np.array([0., .05, 10., -10.]), np.array([1., 1., 2., -2.]))
    assert result.recovery_count == 2
    assert result.median_recovery == pytest.approx(.8)
    assert result.within_1 == .5
    assert result.within_2 == 1
    empty = score_summary(np.zeros(2), np.ones(2))
    assert empty.recovery_count == 0
    assert empty.median_recovery is None


def test_pairing_checks_image_identity_not_row_order() -> None:
    assert find_spec("oracle_results") is not None
    from oracle_results import Pair, validate_pairs, OracleError
    first = Pair(category="bottle", image="a", family="ROTATION", level=5, s0=1, sp=3, sc=2,
                 interior_s0=0, interior_sp=2, interior_sc=1)
    second = first.model_copy(update={"level": -5})
    validate_pairs([second, first])
    with pytest.raises(OracleError):
        validate_pairs([first, first])
    with pytest.raises(OracleError):
        validate_pairs([first, second.model_copy(update={"s0": 2})])
