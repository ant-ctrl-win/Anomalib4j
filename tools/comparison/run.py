# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/comparison/run.py --help
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Literal, assert_never

from common import (Case, ContractError, DATASET, PRERUN, PYTHON, ROOT, UPSTREAM,
                    checked_command, initialize, java_command, prepare_case, require_model,
                    require_run, seal_model, write_json)
from offline_env import sha256_file, verify_weights


def worker(case: Case, action: str, extra: str = "") -> list[str]:
    return [str(PYTHON), str(ROOT / "tools/comparison/worker.py"), action, str(case.run), case.method, case.category,
            str(case.smoke).lower(), extra]


def adapter(case: Case, action: str) -> list[str]:
    return java_command("ComparisonRunner", [action, str(DATASET), str(case.directory), str(case.manifests),
                                           case.category, str(case.smoke).lower()])


def competitor_gate() -> None:
    verify_weights()
    source = subprocess.run(["git", "-C", str(UPSTREAM), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    if source != "cc5f400a4a4b1b14b5a3ee5078063c250a8f522e":
        raise ContractError("Unexpected Anomalib checkout")
    if subprocess.run(["git", "-C", str(UPSTREAM), "status", "--porcelain"], capture_output=True, text=True, check=True).stdout.strip():
        raise ContractError("Anomalib upstream checkout is modified")


def dispatch(case: Case, action: str, process: int | None) -> None:
    actions: dict[str, Literal["fit", "infer", "evaluate", "latency", "memory"]] = {"fit": "fit", "infer": "infer", "evaluate": "evaluate", "latency": "latency", "memory": "memory"}
    action = actions[action]
    require_run(case)
    competitor_gate()
    match action:
        case "fit":
            prepare_case(case)
            command = adapter(case, "fit") if case.method == "anomalib4j" else worker(case, "fit")
            checked_command(command, case.directory / "fit.log")
            seal_model(case)
            filenames = [path for role in ["anomalib4j-fit.txt", "anomalib4j-calibration.txt"] for path in case.names(role)] if case.method == "anomalib4j" else case.names("competitor-fit.txt")
            hashes = [{"role": "train", "filename": name, "sha256": sha256_file(DATASET / "train/img" / name)} for name in filenames]
            write_json(case.directory / "training-inputs.json", hashes)
        case "infer":
            require_model(case)
            checked_command(adapter(case, "infer") if case.method == "anomalib4j" else worker(case, "infer"), case.directory / "inference.log")
        case "evaluate":
            if case.smoke:
                raise ContractError("Smoke runs do not evaluate real comparative metrics; use synthetic evaluator tests")
            require_model(case)
            checked_command(java_command("ComparisonEvaluator", [str(case.directory), str(DATASET), str(case.manifests / "test.txt"), case.method, case.category]), case.directory / "evaluation.log")
        case "latency":
            require_model(case)
            from results import python_latency, jmh_latency
            if case.method == "anomalib4j":
                output = "jmh.json" if process is None else f"jmh-process-{process}.json"
                command = java_command("", [])[:4] + ["org.openjdk.jmh.Main", "ComparisonBenchmark.*Inference",
                           "-p", "caseDirectory=" + str(case.directory), "-p", "dataset=" + str(DATASET),
                           "-p", "category=" + case.category, "-p", "smoke=" + str(case.smoke).lower(),
                           "-rf", "json", "-rff", str(case.directory / output)]
                if process is not None:
                    command += ["-f", "1"]
                if case.smoke:
                    command += ["-wi", "1", "-i", "1", "-w", "100ms", "-r", "100ms", "-f", "1"]
                checked_command(command, case.directory / ("latency.log" if process is None else f"latency-{process}.log"))
                if process is None or process == 1:
                    jmh_latency(case)
            else:
                processes = [process] if process is not None else ([0] if case.smoke else [0, 1])
                for fork in processes:
                    checked_command(worker(case, "latency", str(fork)), case.directory / f"latency-{fork}.log")
                if process is None or process == 1:
                    python_latency(case)
        case "memory":
            from memory import monitor
            memory_case = Case(case.run / "memory-sessions", case.method, case.category, case.smoke)
            if not (memory_case.run / "data").exists():
                shutil.copytree(case.run / "data", memory_case.run / "data")
            memory_case.directory.mkdir(parents=True, exist_ok=False)
            command = adapter(memory_case, "memory") if case.method == "anomalib4j" else worker(memory_case, "memory")[:-1]
            monitor(memory_case, command)
            for name in ["memory.csv", "memory-summary.json"]:
                shutil.copy2(memory_case.directory / name, case.directory / name)
        case unreachable:
            assert_never(unreachable)


def main() -> None:
    parser = argparse.ArgumentParser(description="Frozen one-case benchmark; no all-cases execution")
    parser.add_argument("action", choices=["init", "fit", "infer", "evaluate", "latency", "memory", "summarize"])
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--method", choices=["anomalib4j", "padim", "patchcore"])
    parser.add_argument("--category", choices=["bottle", "metal_nut"])
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--process", type=int, choices=[0, 1])
    args = parser.parse_args()
    if Path(args.run_id).name != args.run_id or args.run_id in {".", ".."}:
        parser.error("run-id must be a single directory name")
    run = ROOT / "target/comparison" / args.run_id
    if args.action == "init":
        initialize(run, args.smoke)
        return
    if args.action == "summarize":
        from results import summarize_run
        summarize_run(run)
        return
    if args.method is None or args.category is None:
        parser.error("method and category are required for individual phases")
    dispatch(Case(run, args.method, args.category, args.smoke), args.action, args.process)


if __name__ == "__main__":
    main()
