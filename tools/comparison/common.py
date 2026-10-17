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
CATEGORIES: Final = tuple((ROOT / "src/test/resources/mvtec-categories.txt").read_text(encoding="utf-8").split())
METHODS: Final = ("anomalib4j", "padim", "patchcore")
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
    def dataset(self) -> Path:
        manifest = json.loads((self.run / "manifest.json").read_text(encoding="utf-8"))
        return Path(manifest["dataset"])

    def fixture(self) -> Path:
        names = self.names("competitor-fit.txt" if self.smoke else "test.txt")
        normal = sorted(name for name in names if name.startswith(self.category + "_good_"))
        if not normal:
            raise ContractError(f"No normal timing fixture: {self.category}")
        return self.dataset / ("train" if self.smoke else "test") / "img" / normal[0]

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


def capture_hardware(output: Path) -> None:
    script = "$c=Get-CimInstance Win32_Processor; $s=Get-CimInstance Win32_ComputerSystem; "
    script += "$o=Get-CimInstance Win32_OperatingSystem; "
    script += "@{cpu=$c.Name;physical_cores=$c.NumberOfCores;logical_cores=$c.NumberOfLogicalProcessors;ram_bytes=$s.TotalPhysicalMemory;"
    script += "os=$o.Caption;build=$o.BuildNumber;power=(powercfg /getactivescheme);background_cpu=(Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor | Where-Object Name -eq '_Total').PercentProcessorTime;"
    script += "captured_at=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json"
    result = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True, check=True)
    output.write_text(result.stdout, encoding="utf-8")


def validate_manifests(run: Path) -> None:
    generated = run / "manifest-hashes.json"
    if generated.exists():
        declared = json.loads(generated.read_text(encoding="utf-8"))
        for relative, digest in declared.items():
            if sha256_file(run / relative) != digest:
                raise ContractError(f"Manifest hash mismatch: {relative}")
        manifest = json.loads((run / "manifest.json").read_text(encoding="utf-8"))
        categories = manifest["categories"]
    else:
        declared = json.loads((PRERUN / "data-sha256.json").read_text(encoding="utf-8-sig"))
        for entry in declared:
            suffix = entry["Path"].replace("\\", "/").split("/data/", 1)[1]
            if sha256_file(run / "data" / suffix).upper() != entry["Hash"]:
                raise ContractError(f"Manifest hash mismatch: {suffix}")
        categories = sorted(path.name for path in (run / "data").iterdir() if path.is_dir())
    for category in categories:
        if category not in CATEGORIES:
            raise ContractError(f"Unknown category: {category}")
        case = Case(run, "anomalib4j", category, False)
        fit = case.names("anomalib4j-fit.txt")
        calibration = case.names("anomalib4j-calibration.txt")
        competitor = case.names("competitor-fit.txt")
        test = case.names("test.txt")
        if not fit or len(calibration) < 2 or not test:
            raise ContractError(f"Insufficient split cardinality: {category}")
        if set(fit) & set(calibration) or set(fit + calibration) != set(competitor):
            raise ContractError(f"Wrong split membership: {category}")
        if any(not name.startswith(category + "_good_") for name in competitor):
            raise ContractError(f"Anomalous fit input: {category}")
        for names in (fit, calibration, competitor, test):
            if len(names) != len(set(names)):
                raise ContractError(f"Duplicate names: {category}")
            for name in names:
                if Path(name).name != name or "/" in name or "\\" in name:
                    raise ContractError(f"Invalid image name: {name}")


def require_run(case: Case) -> None:
    manifest = json.loads((case.run / "manifest.json").read_text(encoding="utf-8"))
    if manifest["smoke"] != case.smoke:
        raise ContractError("Smoke and final artifacts must use distinct run directories")
    if case.category not in manifest.get("categories", CATEGORIES) or case.method not in manifest["method_order"]:
        raise ContractError("Case not selected at run initialization")
    hashes = json.loads((case.run / "source-hashes.json").read_text(encoding="utf-8"))
    for relative, digest in hashes.items():
        if sha256_file(ROOT / relative) != digest:
            raise ContractError(f"Source changed after run initialization: {relative}")
    validate_manifests(case.run)
    data_hashes = json.loads((case.run / "dataset-hashes.json").read_text(encoding="utf-8"))
    for relative, digest in data_hashes.items():
        if (relative == "meta.json" or Path(relative).name.startswith(case.category + "_")) and sha256_file(case.dataset / relative) != digest:
            raise ContractError(f"Dataset changed after initialization: {relative}")


def prepare_case(case: Case) -> None:
    case.directory.mkdir(parents=True, exist_ok=False)
    configuration = json.loads((PRERUN / "configurations.json").read_text(encoding="utf-8"))
    write_json(case.directory / "config.json", {"method": case.method, "category": case.category,
               "run_settings": json.loads((case.run / "manifest.json").read_text(encoding="utf-8")),
               "split_counts": {name: len(case.names(name)) for name in ("anomalib4j-fit.txt", "anomalib4j-calibration.txt", "competitor-fit.txt", "test.txt")},
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
