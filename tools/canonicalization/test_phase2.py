# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "numpy", "pydantic>=2"]
# ///
# How to run: python -m pytest tools/canonicalization/test_phase2.py
from pathlib import Path
import sys

import numpy as np
import pytest

from phase2_models import Coherence, GateEvidence, Phase2Error, Quality, RotationPair, RotationSummary, acceptance, distribution
from phase2_statistics import rotation_summary


def evidence() -> GateEvidence:
    stats = distribution(np.array([.01]))
    coherence = Coherence(sample_std=stats,low_variance_counts={},per_position_variance=stats,pairwise_cosine=distribution(np.array([.85])),
                         directional_dispersion=stats,calibration_raw_std=stats)
    return GateEvidence(quality=Quality(image_auroc=.96,pixel_auroc=.89,aupro030=.65),coherence=coherence,
                        rotation=RotationSummary(levels=(),average_absolute_level_median=.5),geometry_pass=True)


def test_gate_boundaries_use_all_six_conditions_without_rounding() -> None:
    reference = evidence()
    baseline = Quality(image_auroc=.84,pixel_auroc=.84,aupro030=.62)
    assert acceptance(reference,reference,baseline).qualifies
    boundary = reference.model_copy(update={"quality":reference.quality.model_copy(update={"image_auroc":.96-.01})})
    assert acceptance(boundary,reference,baseline).quality_loss
    worse = reference.model_copy(update={"quality":reference.quality.model_copy(update={"image_auroc":np.nextafter(.96-.01,-np.inf)})})
    assert not acceptance(worse,reference,baseline).qualifies
    tied = reference.model_copy(update={"quality":baseline})
    assert not acceptance(tied,reference,baseline).above_unaligned
    assert not acceptance(reference.model_copy(update={"geometry_pass":False}),reference,baseline).qualifies
    slower_rotation = reference.model_copy(update={"rotation":RotationSummary(levels=(),average_absolute_level_median=1.000001)})
    assert not acceptance(slower_rotation,reference,baseline).rotation
    poor = reference.coherence.model_copy(update={"sample_std":distribution(np.array([.0110001])),"pairwise_cosine":distribution(np.array([.829999]))})
    gates = acceptance(reference.model_copy(update={"coherence":poor}),reference,baseline)
    assert not gates.median_std and not gates.cosine


def test_rotation_uses_paired_signed_medians_then_absolute_level_average() -> None:
    pairs = [RotationPair(image=f"normal_{i}",rotation=angle,original=float(i),transformed=float(i+angle/10),delta=angle/10)
             for angle in (-10,-5,5,10) for i in range(22)]
    result = rotation_summary(pairs)
    assert result.average_absolute_level_median == .75
    assert [r.signed_delta.median for r in result.levels] == [-1,-.5,.5,1]
    with pytest.raises(Phase2Error,match="distinct"):
        rotation_summary([pairs[0],*pairs[:-1]])
    with pytest.raises(AssertionError):
        rotation_summary([pairs[0].model_copy(update={"delta":99}),*pairs[1:]])


def test_phase2_source_guards_reject_changed_bytes(tmp_path: Path) -> None:
    sys.path.insert(0,str(Path(__file__).resolve().parents[1] / "ablation"))
    from phase2_inputs import verify_hashes
    from inputs import sha256_file
    from pydantic import TypeAdapter
    source = tmp_path / "source.bin"
    source.write_bytes(b"frozen")
    manifest = tmp_path / "hashes.json"
    manifest.write_bytes(TypeAdapter(dict[str,str]).dump_json({str(source):sha256_file(source)}))
    assert verify_hashes(manifest) == 1
    source.write_bytes(b"changed")
    with pytest.raises(Phase2Error,match="identity mismatch"):
        verify_hashes(manifest)


def test_timing_csv_parses_batch_text_and_still_requires_exactly_sixteen() -> None:
    from phase2_results import TimingRow
    from pydantic import ValidationError
    row = {"fork":"0","round":"0","stage":"geometry","candidate":"shared", "batch":"16",
           "ns_per_image":"123.5","bytes_per_image":"96.0"}
    assert TimingRow.model_validate(row).batch == 16
    with pytest.raises(ValidationError):
        TimingRow.model_validate({**row,"batch":"17"})
