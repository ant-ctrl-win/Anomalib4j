# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/ablation/final_encoder_campaign.py --output NEW_PATH --onnx-python PATH_TO_ONNX_PYTHON
from pathlib import Path
from datetime import datetime, timezone
import json
import subprocess
import sys
from time import perf_counter, time_ns

import typer

from inputs import ROOT, CATEGORIES, sha256_file
from common import capture, capture_hardware, write_json
from java_m2 import EncoderChoice
from models import ModelError


def execute(command: list[str], log: Path, output: Path) -> None:
    with log.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps({"command": command}) + "\n")
        stream.flush()
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=False)
    if result.returncode:
        write_json(output / "failed.json", {"returncode": result.returncode, "log": str(log), "command": command})
        raise ModelError(f"Campaign command failed; retained log: {log}")


def record_status(output: Path, phase: str, encoder: str | None = None) -> None:
    directory = output / "events"
    directory.mkdir(exist_ok=True)
    write_json(directory / (str(time_ns()) + ".json"), {"phase": phase, "encoder": encoder,
               "at_utc": datetime.now(timezone.utc).isoformat()})


def main(output: Path = typer.Option(...), onnx_python: Path = typer.Option(...), resume: bool = False) -> None:
    output = output.resolve()
    started = perf_counter()
    if resume:
        frozen = json.loads((output / "source-hashes.json").read_text())
        helper = Path(__file__).resolve().relative_to(ROOT).as_posix()
        changes = {name: {"previous": digest, "current": sha256_file(ROOT / name)}
                   for name, digest in frozen.items() if sha256_file(ROOT / name) != digest}
        if set(changes) - {helper}:
            raise ModelError(f"Scientific/benchmark source changed: {list(changes)}")
        record_status(output, "resume")
        write_json(output / ("orchestration-amendment-" + str(time_ns()) + ".json"), {"changes": changes,
            "reason": "Only controller status/resume handling changed after Small completion; completed Small retained"})
        frozen[helper] = sha256_file(ROOT / helper)
        run(output, frozen, started, resume=True)
        return
    output.mkdir(parents=True, exist_ok=False)
    capture(output / "workspace")
    capture_hardware(output / "hardware.json")
    execute(["java", "-version"], output / "java-version.log", output)
    inspection = """
from pathlib import Path
import hashlib,json,math,onnx,sys
root=Path(sys.argv[1]); rows=[]
for name in ('mobilenetv4_spatial_14x14.onnx','mobilenetv4_large_spatial_14x14.onnx'):
 p=root/'src/main/resources/models'/name; m=onnx.load(str(p)); onnx.checker.check_model(m)
 rows.append(dict(file=name,bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),
  initializer_values=sum(math.prod(t.dims) for t in m.graph.initializer),nodes=len(m.graph.node),initializers=len(m.graph.initializer),
  inputs=[dict(name=t.name,type=t.type.tensor_type.elem_type,shape=[d.dim_value for d in t.type.tensor_type.shape.dim]) for t in m.graph.input],
  outputs=[dict(name=t.name,type=t.type.tensor_type.elem_type,shape=[d.dim_value for d in t.type.tensor_type.shape.dim]) for t in m.graph.output]))
print(json.dumps(dict(onnx_version=onnx.__version__,artifacts=rows)))
"""
    inspected = subprocess.run([str(onnx_python), "-c", inspection, str(ROOT)], cwd=ROOT,
                               capture_output=True, text=True, check=True)
    write_json(output / "encoder-artifacts.json", json.loads(inspected.stdout))
    frozen = {p.relative_to(ROOT).as_posix(): sha256_file(p)
              for pattern in ("src/**/*.java", "tools/ablation/*.py", "src/main/resources/models/*.onnx")
              for p in ROOT.glob(pattern)}
    write_json(output / "source-hashes.json", frozen)
    write_json(output / "protocol.json", {
        "frozen_at_utc": datetime.now(timezone.utc).isoformat(), "categories": list(CATEGORIES),
        "order": [e.value for e in EncoderChoice], "fresh_features_both": True,
        "preprocessing": "imagenet-rgb-resize224-bicubic-v1", "detector": "M2-P; unchanged",
        "canonicalization": False, "fine_tuning": False,
        "quality_delta": "Large minus Small; strict sign, exact ties; no significance threshold",
        "fit_scope": "Direct normal-only wall time to serialized/reloaded ready state; excludes TEST; includes current orchestration/validation/normal diagnostic I/O",
        "jmh": "Bottle 900x900; SampleTime us; one caller; two forks; 5x2s warmup; 10x2s measurement; -Xmx2g; default ORT",
        "readme_sha256": sha256_file(ROOT / "README.md"), "onnx_python": str(onnx_python),
    })
    run(output, frozen, started, resume=False)


def run(output: Path, frozen: dict[str, str], started: float, resume: bool) -> None:
    for encoder in EncoderChoice:
        case = output / encoder.value
        if resume and (case / "complete.json").is_file():
            if json.loads((case / "complete.json").read_text())["categories"] != list(CATEGORIES):
                raise ModelError("Incomplete category set cannot be resumed as complete")
            record_status(output, "retained_complete_quality", encoder.value)
            continue
        record_status(output, "quality_and_adaptation", encoder.value)
        execute([sys.executable, str(ROOT / "tools/ablation/java_m2.py"), "--output", str(case),
                 "--all-categories", "--reextract", "--encoder", encoder.value],
                output / (encoder.value + "-quality.log"), output)
    for encoder in EncoderChoice:
        for name, digest in frozen.items():
            if sha256_file(ROOT / name) != digest:
                raise ModelError(f"Frozen source changed before timing: {name}")
        if (output / encoder.value / "benchmark/final-generic").exists():
            raise ModelError("Existing benchmark evidence requires reconciliation, not automatic rerun")
        record_status(output, "jmh", encoder.value)
        execute([sys.executable, str(ROOT / "tools/ablation/benchmark_m2.py"), "--run", str(output / encoder.value), "--generic-only"],
                output / (encoder.value + "-jmh.log"), output)
    write_json(output / "complete.json", {"categories": list(CATEGORIES), "encoders": [e.value for e in EncoderChoice],
               "controller_elapsed_seconds": perf_counter() - started, "resumed": resume,
               "completed_at_utc": datetime.now(timezone.utc).isoformat()})
    record_status(output, "complete")


if __name__ == "__main__":
    typer.run(main)
