# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pytest", "typer", "pydantic>=2"]
# ///
# How to run: python -m pytest tools/ablation/test_java_m2.py -q
import json
import subprocess
import sys
import numpy as np
import pytest
from inputs import java_command
from models import normalize, ModelError
from java_m2 import validate_parity, raw_m2
from progress import phase, stream_command


def test_java_fit_scores_and_reload_match_python(tmp_path):
    rng = np.random.default_rng(42)
    units = {role: normalize(rng.normal(size=(count, 196, 96)))
             for role, count in (("fit", 5), ("calibration", 4), ("test", 3))}
    for role, values in units.items():
        np.save(tmp_path / (role + ".npy"), values.reshape(len(values), -1))
    mean = units["fit"].mean(axis=0)
    std = np.maximum(units["fit"].std(axis=0, ddof=1), 1e-6)
    calibration = raw_m2(units["calibration"], mean, std)
    raw = raw_m2(units["test"], mean, std)
    np.save(tmp_path / "raw.npy", raw)
    np.save(tmp_path / "native-z.npy", (raw - calibration.mean(axis=0)) / np.maximum(calibration.std(axis=0, ddof=1), 1e-6))
    output = tmp_path / "java"
    subprocess.run(java_command("M2Runner", [str(tmp_path), str(output)]), check=True)
    validate_parity(output, units, tmp_path)
    errors = json.loads((output / "parity.json").read_text())
    assert errors["native-z"]["max_abs"] < 1e-10
    assert (output / "model.bin").stat().st_size > 300000


def test_progress_records_completion_and_failure(tmp_path, capsys):
    log = tmp_path / "progress.log"
    with phase(log, "[01/15] bottle", "FIT"):
        count = 1
    with pytest.raises(ModelError):
        with phase(log, "[01/15] bottle", "INFER"):
            raise ModelError("fixture")
    text = log.read_text()
    assert count == 1
    assert "FIT - complete" in text
    assert "INFER - failed" in text
    assert capsys.readouterr().out == text


def test_streamed_phase_output_and_nonzero_exit(tmp_path, capsys):
    log = tmp_path / "java.log"
    progress = tmp_path / "progress.log"
    stream_command([sys.executable, "-c", "print('[01/15] bottle - FIT - complete')"], log, progress)
    assert "FIT - complete" in capsys.readouterr().out
    assert "FIT - complete" in log.read_text()
    assert "FIT - complete" in progress.read_text()
    with pytest.raises(subprocess.CalledProcessError):
        stream_command([sys.executable, "-c", "raise SystemExit(3)"], tmp_path / "failure.log", progress)
