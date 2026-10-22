# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pytest", "typer", "pydantic>=2"]
# ///
# How to run: python -m pytest tools/ablation/test_java_m2.py -q
import json
import subprocess
import sys
from pathlib import Path
import numpy as np
import pytest
from inputs import java_command
from models import normalize, ModelError
from java_m2 import validate_parity, raw_m2, EncoderChoice, main
from progress import phase, stream_command


@pytest.mark.parametrize("encoder", list(EncoderChoice))
@pytest.mark.parametrize("staged", [False, True])
def test_java_fit_scores_and_reload_match_python(tmp_path: Path, encoder: EncoderChoice, staged: bool) -> None:
    rng = np.random.default_rng(42)
    units = {role: normalize(rng.normal(size=(count, 196, encoder.channels)))
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
    command = [str(tmp_path), str(output), "synthetic", encoder.value]
    if staged:
        test_path = tmp_path / "test.npy"
        test_bytes = test_path.read_bytes()
        test_path.unlink()
        subprocess.run(java_command("M2Runner", command + ["fit"]), check=True)
        assert not (output / "native-z.npy").exists()
        assert (output / "model.bin").is_file()
        test_path.write_bytes(test_bytes)
        subprocess.run(java_command("M2Runner", command + ["infer"]), check=True)
    else:
        subprocess.run(java_command("M2Runner", command), check=True)
    validate_parity(output, units, tmp_path)
    errors = json.loads((output / "parity.json").read_text())
    assert errors["native-z"]["max_abs"] < 1e-10
    assert (output / "model.bin").stat().st_size > 300000


def test_large_refuses_retained_small_features(tmp_path: Path) -> None:
    with pytest.raises(ModelError, match="requires --reextract"):
        main(output=tmp_path / "new", encoder=EncoderChoice.LARGE_SPATIAL_14)
    assert not (tmp_path / "new").exists()


def test_complete_cached_workflow_records_adaptation_without_overwriting(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    import java_m2
    from inputs import ROLE_FILES

    baseline = tmp_path / "target/comparison/m0-all15-baseline-20261016"
    retained = tmp_path / "target/ablation/local-models-20261017"
    baseline.mkdir(parents=True)
    retained.mkdir(parents=True)
    (baseline / "manifest.json").write_text(json.dumps({"dataset": str(tmp_path / "dataset")}))
    (baseline / "dataset-hashes.json").write_text("{}")
    (retained / "source-hashes.json").write_text("{}")
    manifests = baseline / "data/bottle"
    manifests.mkdir(parents=True)
    rng = np.random.default_rng(491)
    units = {}
    for index, role in enumerate(ROLE_FILES):
        names = [f"bottle_good_{index*2+i:03}.png" for i in range(2)]
        (manifests / ROLE_FILES[role]).write_text("\n".join(names) + "\n")
        directory = retained / "features/bottle" / role
        directory.mkdir(parents=True)
        values = rng.normal(size=(2, 14, 14, 96)).astype(np.float32)
        np.save(directory / "features.npy", values)
        (directory / "images.txt").write_text("\n".join(names) + "\n")
        units[role] = normalize(values.reshape(2, 196, 96))
    mean = units["fit"].mean(axis=0)
    std = np.maximum(units["fit"].std(axis=0, ddof=1), 1e-6)
    cal = raw_m2(units["calibration"], mean, std)
    raw = raw_m2(units["test"], mean, std)
    reference = retained / "M2/bottle"
    reference.mkdir(parents=True)
    np.save(reference / "raw.npy", raw)
    np.save(reference / "native-z.npy", (raw-cal.mean(axis=0))/np.maximum(cal.std(axis=0, ddof=1), 1e-6))
    monkeypatch.setattr(java_m2, "ROOT", tmp_path)
    monkeypatch.setattr(java_m2, "validate_manifests", lambda path: None)
    output = tmp_path / "result"
    main(output=output, category=["bottle"], reextract=False, evaluate=False)
    assert (output / "complete.json").is_file()
    assert json.loads((output / "M2/bottle/adaptation.json").read_text())["total_seconds"] > 0
    normal_hashes = json.loads((output / "inputs/bottle/fit-input-hashes.json").read_text())
    all_hashes = json.loads((output / "inputs/bottle/input-hashes.json").read_text())
    assert len(normal_hashes) == 4
    assert len(all_hashes) == 6


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
