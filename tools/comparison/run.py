# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/comparison/run.py --help
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Literal, assert_never
from dataclasses import dataclass

from common import (Case, ContractError, DATASET, CATEGORIES, METHODS, PRERUN, PYTHON, ROOT, UPSTREAM,
                    checked_command, java_command, prepare_case, require_model,
                    require_run, seal_model, write_json)
from offline_env import sha256_file, verify_weights
from preparation import RunSettings, initialize


def worker(case: Case, action: str, extra: str = "") -> list[str]:
    return [str(PYTHON), str(ROOT / "tools/comparison/worker.py"), action, str(case.run), case.method, case.category,
            str(case.smoke).lower(), extra]


def adapter(case: Case, action: str) -> list[str]:
    return java_command("ComparisonRunner", [action, str(case.dataset), str(case.directory), str(case.manifests),
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
    if case.method != "anomalib4j":
        competitor_gate()
    match action:
        case "fit":
            prepare_case(case)
            command = adapter(case, "fit") if case.method == "anomalib4j" else worker(case, "fit")
            checked_command(command, case.directory / "fit.log")
            seal_model(case)
            filenames = [path for role in ["anomalib4j-fit.txt", "anomalib4j-calibration.txt"] for path in case.names(role)] if case.method == "anomalib4j" else case.names("competitor-fit.txt")
            hashes = [{"role": "train", "filename": name, "sha256": sha256_file(case.dataset / "train/img" / name)} for name in filenames]
            write_json(case.directory / "training-inputs.json", hashes)
        case "infer":
            require_model(case)
            checked_command(adapter(case, "infer") if case.method == "anomalib4j" else worker(case, "infer"), case.directory / "inference.log")
        case "evaluate":
            if case.smoke:
                raise ContractError("Smoke runs do not evaluate real comparative metrics; use synthetic evaluator tests")
            require_model(case)
            command = java_command("ComparisonEvaluator", [str(case.directory), str(case.dataset), str(case.manifests / "test.txt"), case.method, case.category])
            command[1] = "-Xmx" + os.environ.get("ANOMALIB_EVALUATION_HEAP", "4g")
            checked_command(command, case.directory / "evaluation.log")
        case "latency":
            require_model(case)
            from results import python_latency, jmh_latency
            if case.method == "anomalib4j":
                output = "jmh.json" if process is None else f"jmh-process-{process}.json"
                command = java_command("", [])[:4] + ["org.openjdk.jmh.Main", "ComparisonBenchmark.*Inference",
                           "-p", "caseDirectory=" + str(case.directory), "-p", "dataset=" + str(case.dataset),
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
            if not (memory_case.run / "manifest.json").exists():
                shutil.copy2(case.run / "manifest.json", memory_case.run / "manifest.json")
            memory_case.directory.mkdir(parents=True, exist_ok=False)
            command = adapter(memory_case, "memory") if case.method == "anomalib4j" else worker(memory_case, "memory")[:-1]
            monitor(memory_case, command)
            for name in ["memory.csv", "memory-summary.json"]:
                shutil.copy2(memory_case.directory / name, case.directory / name)
        case unreachable:
            assert_never(unreachable)


@dataclass(frozen=True, slots=True)
class CampaignStep:
    category: str
    method: str
    action: str


def campaign_plan(run: Path, categories: tuple[str, ...], methods: tuple[str, ...]) -> tuple[CampaignStep, ...]:
    if not categories or not set(categories) <= set(CATEGORIES) or not methods or not set(methods) <= set(METHODS):
        raise ContractError(f"Invalid campaign selection for {run}")
    return tuple(CampaignStep(category, method, action)
                 for category in categories for method in methods
                 for action in ("fit", "infer", "evaluate", "latency", "memory"))


def execute_campaign(run: Path, steps: tuple[CampaignStep, ...], smoke: bool) -> None:
    for step in steps:
        if smoke and step.action == "evaluate":
            continue
        with (run / "campaign.jsonl").open("a", encoding="utf-8") as log:
            identity = {"category": step.category, "method": step.method, "action": step.action}
            log.write(json.dumps({**identity, "status": "started"}) + "\n")
            log.flush()
            try:
                dispatch(Case(run, step.method, step.category, smoke), step.action, None)
            except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as failure:
                log.write(json.dumps({**identity, "status": "failed", "reason": str(failure)}) + "\n")
                raise
            log.write(json.dumps({**identity, "status": "complete"}) + "\n")
    from results import summarize_run
    summarize_run(run)


def main() -> None:
    parser = argparse.ArgumentParser(description="MVTec category evaluation; serial, fail-fast campaigns")
    parser.add_argument("action", choices=["init", "run", "fit", "infer", "evaluate", "latency", "memory", "summarize"])
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output-root", type=Path, default=ROOT / "target/comparison")
    parser.add_argument("--dataset", type=Path, default=DATASET)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--method", choices=METHODS)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--category", choices=CATEGORIES)
    group.add_argument("--all-categories", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--process", type=int, choices=[0, 1])
    args = parser.parse_args()
    if Path(args.run_id).name != args.run_id or args.run_id in {".", ".."}:
        parser.error("run-id must be a single directory name")
    run = args.output_root.resolve() / args.run_id
    if args.action in {"init", "run"}:
        categories = CATEGORIES if args.all_categories else ((args.category,) if args.category else
                     tuple(sorted(path.name for path in (PRERUN / "data").iterdir() if path.is_dir())))
        methods = (args.method,) if args.method else (("anomalib4j",) if args.action == "run" else METHODS)
        settings = RunSettings(args.dataset.resolve(), categories, methods, args.seed)
        steps = campaign_plan(run, categories, methods)
        if args.dry_run:
            print(json.dumps({"run": str(run), "dataset": str(settings.dataset), "seed": settings.seed,
                  "steps": [{"category": step.category, "method": step.method, "action": step.action} for step in steps]}, indent=2))
            return
        initialize(run, args.smoke, settings)
        if args.action == "run":
            execute_campaign(run, steps, args.smoke)
        return
    if args.dry_run or args.all_categories:
        parser.error("--dry-run/--all-categories apply to init or run")
    if args.action == "summarize":
        from results import summarize_run
        summarize_run(run)
        return
    if args.method is None or args.category is None:
        parser.error("method and category are required for individual phases")
    dispatch(Case(run, args.method, args.category, args.smoke), args.action, args.process)


if __name__ == "__main__":
    main()
