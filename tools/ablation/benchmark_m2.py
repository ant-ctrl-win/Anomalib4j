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


def main(run: Path = typer.Option(...)) -> None:
    run = run.resolve()
    if not (run / "complete.json").is_file():
        raise ModelError("Completed parity/quality regression required before timing")
    directory = run / "benchmark/cpu-verified"
    if directory.exists() and any(directory.iterdir()):
        raise ModelError("Benchmark output must be absent or empty")
    directory.mkdir(parents=True, exist_ok=True)
    plan = json.loads((run / "plan.json").read_text())
    capture_hardware(directory / "hardware.json")
    write_json(directory / "source-hashes.json", {p.relative_to(ROOT).as_posix(): sha256_file(p)
        for pattern in ("src/**/*.java", "tools/ablation/*.py") for p in ROOT.glob(pattern)})
    command = java_command("unused", [])[:4] + ["org.openjdk.jmh.Main", "M2Benchmark.*",
        "-p", "run=" + str(run), "-p", "baseline=" + plan["baseline"], "-p", "dataset=" + plan["dataset"],
        "-p", "category=bottle", "-rf", "json", "-rff", str(directory / "jmh.json"), "-foe", "true"]
    write_json(directory / "command.json", {"command": command, "scope": "Bottle predecoded normal; matched Java M0/M2"})
    with phase(run / "progress.log", "[01/01] bottle", "JMH 9 stages, 2 forks, 5x2s warmup, 10x2s measurement"):
        checked_command(command, directory / "jmh.log")


if __name__ == "__main__":
    typer.run(main)
