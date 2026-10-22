# /// script
# requires-python = ">=3.11"
# dependencies = ["typer", "pydantic>=2", "numpy"]
# ///
# How to run: python tools/ablation/benchmark_m2.py --run target/ablation/java-m2-20261018-validated
from pathlib import Path
import json
import typer
from inputs import ROOT, java_command, checked_command, sha256_file
from common import capture_hardware, write_json
from progress import phase
from models import ModelError


def main(run: Path = typer.Option(...), generic_only: bool = False) -> None:
    run = run.resolve()
    if not (run / "complete.json").is_file():
        raise ModelError("Completed parity/quality regression required before timing")
    directory = run / ("benchmark/final-generic" if generic_only else "benchmark/cpu-verified")
    if directory.exists() and any(directory.iterdir()):
        raise ModelError("Benchmark output must be absent or empty")
    directory.mkdir(parents=True, exist_ok=True)
    plan = json.loads((run / "plan.json").read_text())
    encoder = plan.get("encoder", "SMALL_SPATIAL_14")
    if not generic_only and encoder != "SMALL_SPATIAL_14":
        raise ModelError("Large requires --generic-only")
    capture_hardware(directory / "hardware.json")
    write_json(directory / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p)
        for pattern in ("src/**/*.java", "tools/ablation/*.py") for p in ROOT.glob(pattern)})
    scopes = r"M2Benchmark\.(cnnExtraction|m2Detection|m2Localization)$" if generic_only else "M2Benchmark.*"
    command = java_command("unused", [])[:4] + ["org.openjdk.jmh.Main", scopes,
        "-p", "run=" + str(run), "-p", "baseline=" + ("required" if generic_only else plan["baseline"]),
        "-p", "encoderSpec=" + encoder, "-p", "dataset=" + plan["dataset"],
        "-p", "category=bottle", "-p", "benchmarkName=" + directory.name,
        "-rf", "json", "-rff", str(directory / "jmh.json"), "-foe", "true"]
    write_json(directory / "command.json", {"command": command,
        "scope": "Bottle 900x900 predecoded normal; generic encoder/detection/localization" if generic_only else "Historical nine-stage comparison"})
    with phase(run / "progress.log", "[01/01] bottle", "JMH; 2 forks, 5x2s warmup, 10x2s measurement"):
        checked_command(command, directory / "jmh.log")


if __name__ == "__main__":
    typer.run(main)
