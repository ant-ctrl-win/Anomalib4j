# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: imported by run.py in the frozen Anomalib 2.6.2 environment.
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Final, TypeAlias

ROOT: Final = Path(__file__).resolve().parents[2]
PRERUN: Final = ROOT / "docs/benchmark/benchmark-prerun"
UPSTREAM: Final = ROOT / "target/prerun/anomalib-2.6.2"
PYTHON: Final = UPSTREAM / ".venv/Scripts/python.exe"
DATASET: Final = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja"
Json: TypeAlias = str | int | float | bool | None | list["Json"] | dict[str, "Json"]
sys.path.insert(0, str(ROOT / "tools/prerun"))
from workspace_identity import capture
from offline_env import sha256_file


class ContractError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class Case:
    run: Path
    method: str
    category: str
    smoke: bool

    @property
    def directory(self) -> Path:
        return self.run / self.method / self.category

    @property
    def manifests(self) -> Path:
        return self.run / "data" / self.category

    @property
    def size(self) -> int:
        return {"bottle": 900, "metal_nut": 700}[self.category]

    def names(self, manifest: str) -> list[str]:
        names = (self.manifests / manifest).read_text(encoding="utf-8-sig").splitlines()
        return names[:2] if self.smoke else names


def write_json(path: Path, value: Json) -> None:
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write("\n")


def phase(directory: Path, name: str) -> None:
    path = directory / "phases.csv"
    if not path.exists():
        path.write_text("timestamp_utc,pid,phase\n", encoding="utf-8")
    with path.open("a", encoding="utf-8") as stream:
        stream.write(f"{datetime.now(timezone.utc).isoformat()},{os.getpid()},{name}\n")


def checked_command(command: list[str], log: Path) -> None:
    with (log.parent / "run.log").open("a", encoding="utf-8") as history:
        history.write(json.dumps({"at": datetime.now(timezone.utc).isoformat(), "command": command, "log": log.name}) + "\n")
    with log.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(command) + "\n")
        stream.flush()
        subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)


def java_command(main: str, arguments: list[str]) -> list[str]:
    dependency_cp = (ROOT / "target/comparison-classpath.txt").read_text(encoding="utf-8").strip()
    classpath = os.pathsep.join([str(ROOT / "target/test-classes"), str(ROOT / "target/classes"), dependency_cp])
    return ["java", "-Xmx2g", "-cp", classpath, "io.github.antctrlwin.anomalib4j.evaluation." + main, *arguments]


def initialize(run: Path, smoke: bool) -> None:
    if run.exists():
        raise ContractError(f"Use an absent run directory: {run}")
    run.mkdir(parents=True)
    capture(run / "workspace")
    shutil.copy2(ROOT / "docs/benchmark/BENCHMARK_CONTRACT.md", run / "contract.md")
    shutil.copytree(PRERUN / "data", run / "data")
    shutil.copytree(PRERUN, run / "environment/prerun")
    sources = sorted({*ROOT.glob("src/main/**/*.java"), *ROOT.glob("src/test/**/*.java"),
                      *ROOT.glob("src/jmh/**/*.java"), *ROOT.glob("tools/comparison/*.*"),
                      *ROOT.glob("tools/prerun/*.*"), *ROOT.glob("src/main/resources/models/*.onnx"),
                      ROOT / "pom.xml", ROOT / "docs/benchmark/BENCHMARK_CONTRACT.md", ROOT / "docs/benchmark/benchmark-prerun/configurations.json"})
    checksums = {str(path.relative_to(ROOT)): sha256_file(path) for path in sources}
    write_json(run / "source-hashes.json", checksums)
    write_json(run / "manifest.json", {"format": "comparison-run/v1", "smoke": smoke,
               "created_at": datetime.now(timezone.utc).isoformat(), "seed": 42,
               "dataset": str(DATASET), "method_order": ["anomalib4j", "padim", "patchcore"],
               "second_process_order": ["patchcore", "padim", "anomalib4j"],
               "execution": "serial; one method/category per command; operator follows frozen order"})
    capture_hardware(run / "hardware.json")
    validate_manifests(run)
    data_hashes = {}
    for category in ["bottle", "metal_nut"]:
        case = Case(run, "anomalib4j", category, False)
        for role, names in [("train", case.names("competitor-fit.txt")), ("test", case.names("test.txt"))]:
            for name in names:
                paths = [DATASET / role / "img" / name]
                if role == "test":
                    paths.append(DATASET / role / "ann" / (name + ".json"))
                for path in paths:
                    data_hashes[path.relative_to(DATASET).as_posix()] = sha256_file(path)
    write_json(run / "dataset-hashes.json", data_hashes)
    (run / "checksums.sha256").write_text("".join(f"{digest}  {relative}\n" for relative, digest in checksums.items()), encoding="utf-8")


def capture_hardware(output: Path) -> None:
    script = "$c=Get-CimInstance Win32_Processor; $s=Get-CimInstance Win32_ComputerSystem; "
    script += "$o=Get-CimInstance Win32_OperatingSystem; "
    script += "@{cpu=$c.Name;physical_cores=$c.NumberOfCores;logical_cores=$c.NumberOfLogicalProcessors;ram_bytes=$s.TotalPhysicalMemory;"
    script += "os=$o.Caption;build=$o.BuildNumber;power=(powercfg /getactivescheme);background_cpu=(Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor | Where-Object Name -eq '_Total').PercentProcessorTime;"
    script += "captured_at=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json"
    result = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True, check=True)
    output.write_text(result.stdout, encoding="utf-8")


def validate_manifests(run: Path) -> None:
    declared = json.loads((PRERUN / "data-sha256.json").read_text(encoding="utf-8-sig"))
    for entry in declared:
        suffix = entry["Path"].replace("\\", "/").split("/data/", 1)[1]
        path = run / "data" / suffix
        if sha256_file(path).upper() != entry["Hash"]:
            raise ContractError(f"Manifest hash mismatch: {path}")
    for category, fit_count, calibration_count, test_count in [("bottle", 167, 42, 83), ("metal_nut", 176, 44, 115)]:
        case = Case(run, "anomalib4j", category, False)
        fit = case.names("anomalib4j-fit.txt")
        calibration = case.names("anomalib4j-calibration.txt")
        competitor = case.names("competitor-fit.txt")
        test = case.names("test.txt")
        if (len(fit), len(calibration), len(test)) != (fit_count, calibration_count, test_count):
            raise ContractError(f"Wrong cardinality: {category}")
        if set(fit) & set(calibration) or set(fit + calibration) != set(competitor):
            raise ContractError(f"Wrong split membership: {category}")
        if any(not name.startswith(category + "_good_") for name in competitor):
            raise ContractError(f"Anomalous fit input: {category}")
        for names, role in [(competitor, "train"), (test, "test")]:
            if len(names) != len(set(names)):
                raise ContractError(f"Duplicate names: {category}/{role}")
            for name in names:
                if Path(name).name != name or not (DATASET / role / "img" / name).is_file():
                    raise ContractError(f"Invalid image name: {name}")


def require_run(case: Case) -> None:
    manifest = json.loads((case.run / "manifest.json").read_text(encoding="utf-8"))
    if manifest["smoke"] != case.smoke:
        raise ContractError("Smoke and final artifacts must use distinct run directories")
    hashes = json.loads((case.run / "source-hashes.json").read_text(encoding="utf-8"))
    for relative, digest in hashes.items():
        if sha256_file(ROOT / relative) != digest:
            raise ContractError(f"Source changed after run initialization: {relative}")
    validate_manifests(case.run)
    data_hashes = json.loads((case.run / "dataset-hashes.json").read_text(encoding="utf-8"))
    for relative, digest in data_hashes.items():
        if Path(relative).name.startswith(case.category + "_") and sha256_file(DATASET / relative) != digest:
            raise ContractError(f"Dataset changed after initialization: {relative}")


def prepare_case(case: Case) -> None:
    case.directory.mkdir(parents=True, exist_ok=False)
    configuration = json.loads((PRERUN / "configurations.json").read_text(encoding="utf-8"))
    write_json(case.directory / "config.json", {"method": case.method, "category": case.category,
               "smoke": case.smoke, "frozen_configuration": configuration, "fit_batch": 32 if case.method != "anomalib4j" else 1,
               "fit_loader": "ordered manifests; synchronous decode (0 worker processes)", "map_format": "npy+json/v1"})
    (case.directory / "validation.md").write_text("# Validation\n\nStatus: implementation smoke only. Final metrics and final latency are separate opt-in commands.\n" if case.smoke else "# Validation\n\nConfiguration frozen. Inspect phase logs and status artifacts; absence of an output is not a zero result.\n", encoding="utf-8")


def seal_model(case: Case) -> None:
    paths = [path for path in case.directory.iterdir() if path.suffix in {".pt", ".bin"} or path.name == "descriptor.json"]
    write_json(case.directory / "model-state.json", {"status": "implemented_smoke" if case.smoke else "fitted",
               "method": case.method, "category": case.category, "config_sha256": sha256_file(case.directory / "config.json"),
               "files": [{"path": path.name, "bytes": path.stat().st_size, "sha256": sha256_file(path)} for path in paths],
               "java_archive": "learning state; recompiled on load outside timers; not runtime payload"})


def require_model(case: Case) -> None:
    state = json.loads((case.directory / "model-state.json").read_text(encoding="utf-8"))
    if state["method"] != case.method or state["category"] != case.category or state["config_sha256"] != sha256_file(case.directory / "config.json"):
        raise ContractError("Model/configuration identity mismatch")
    for entry in state["files"]:
        if sha256_file(case.directory / entry["path"]) != entry["sha256"]:
            raise ContractError(f"Model hash mismatch: {entry['path']}")


def process_affinity() -> int:
    result = subprocess.run(["powershell", "-NoProfile", "-Command", f"(Get-Process -Id {os.getpid()}).ProcessorAffinity.ToInt64()"], capture_output=True, text=True, check=True)
    return int(result.stdout.strip())
