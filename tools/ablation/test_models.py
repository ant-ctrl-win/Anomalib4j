# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pytest"]
# ///
# How to run: python -m pytest tools/ablation/test_models.py
import sys
from pathlib import Path

import numpy as np
import pytest
import models

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "prerun"))
import local_memory_ablation as retained


def test_centroid_cosine_when_fit_vectors_are_known():
    # Given two orthogonal fit directions at one position.
    fit = np.array([[[1., 0.]], [[0., 1.]]])
    # When constructing the shared local model.
    model = models.fit(fit, np.eye(2))
    # Then C1 is cosine to the normalized arithmetic mean.
    np.testing.assert_allclose(model.centroid, [[2**-.5, 2**-.5]])
    np.testing.assert_allclose(models.score(model, fit).c1, 1 - 2**-.5)


def test_degeneracy_when_fit_mean_is_zero():
    # Given opposing unit directions.
    fit = np.array([[[1., 0.]], [[-1., 0.]]])
    # When fitting, then undefined cosine is rejected.
    with pytest.raises(models.ModelError, match="centroid"):
        models.fit(fit, np.eye(2))


def test_normalization_when_descriptor_guard_activates():
    # Given a descriptor below the established guard.
    features = np.zeros((1, 1, 2))
    # When normalizing, then no observation is silently redefined or dropped.
    with pytest.raises(models.ModelError, match="norm"):
        models.normalize(features)


def test_v1_when_projection_is_not_isometric():
    # Given a non-isometric projection and independent fit/query directions.
    projection = np.array([[2., 1.], [0., 1.], [1., -1.]])
    fit = models.normalize(np.array([[[2., 1.]], [[1., 2.]]]))
    query = models.normalize(np.array([[[3., -2.]], [[-1., 2.]]]))
    model = models.fit(fit, projection.T @ projection)
    # When scoring by compiled and explicit cosine.
    actual = models.score(model, query).v1
    expected = models.explicit_v1(model, query, projection)
    # Then they agree, including the varying projected norm.
    np.testing.assert_allclose(actual, expected, atol=1e-14, rtol=0)
    assert not np.allclose(actual, 1 - np.einsum("ipc,pc->ip", query, model.weights))


def test_m2_when_compared_to_retained_formula():
    # Given normalized data including zero-variance channels.
    fit = models.normalize(np.array([[[1., 2., 0.]], [[2., 1., 0.]], [[3., 1., 0.]]]))
    query = models.normalize(np.array([[[1., 1., .1]]]))
    model = models.fit(fit, np.eye(3))
    # When evaluating M2.
    actual = models.score(model, query).m2
    # Then the retained Python formula is unchanged.
    expected = retained.raw_m2(query, fit.mean(axis=0), np.maximum(fit.std(axis=0, ddof=1), 1e-6))
    np.testing.assert_array_equal(actual, expected)


def test_calibration_when_sample_variance_and_floor_are_needed():
    # Given held-out raw scores with one constant position.
    raw = np.array([[1., 2.], [3., 2.]])
    # When calibration is fitted only to those scores.
    calibration = models.calibrate_fit(raw)
    # Then sample standard deviation and the established floor are used.
    np.testing.assert_allclose(calibration.beta, [2**.5, 1e-6])
    np.testing.assert_array_equal(calibration.alpha, [2., 2.])


def test_single_fit_observation_is_rejected():
    # Given too few observations for sample variance.
    fit = np.array([[[1., 0.]]])
    # When fitting, then M2 cannot silently acquire invalid statistics.
    with pytest.raises(models.ModelError, match="two"):
        models.fit(fit, np.eye(2))
