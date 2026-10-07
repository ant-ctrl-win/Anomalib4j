# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: run.py memory; the monitor is separate from latency measurement.
from __future__ import annotations

import csv
import json
import subprocess
import time
from collections import Counter
from datetime import datetime
from pathlib import Path

from common import Case, ContractError, ROOT, write_json


def monitor(case: Case, command: list[str]) -> None:
    directory = case.directory
    gate = directory / "start.signal"
    ready = directory / "monitor-ready.signal"
    stop = directory / "stop.signal"
    command.append(str(gate))
    with (directory / "workload.log").open("x", encoding="utf-8") as workload_log, (directory / "monitor.log").open("x", encoding="utf-8") as monitor_log:
        with subprocess.Popen(command, cwd=ROOT, stdout=workload_log, stderr=subprocess.STDOUT) as workload:
            monitor_command = ["powershell", "-NoProfile", "-File", str(ROOT / "tools/prerun/Measure-ProcessMemory.ps1"),
                               "-RootPid", str(workload.pid), "-OutputPath", str(directory / "memory.csv"),
                               "-Seconds", "86400", "-IntervalMs", "100", "-StopFile", str(stop), "-ReadyFile", str(ready)]
            with subprocess.Popen(monitor_command, cwd=ROOT, stdout=monitor_log, stderr=subprocess.STDOUT) as observer:
                try:
                    until = time.monotonic() + 60
                    while not ready.exists():
                        if observer.poll() is not None or workload.poll() is not None or time.monotonic() > until:
                            workload.terminate()
                            raise ContractError("Memory monitor failed before initialization; inspect monitor.log")
                        time.sleep(.05)
                    gate.touch(exist_ok=False)
                    code = workload.wait()
                    if code:
                        raise subprocess.CalledProcessError(code, command)
                finally:
                    stop.touch(exist_ok=False)
                    if observer.wait(timeout=60):
                        raise ContractError("Memory monitor failed; inspect monitor.log")
    summarize_memory(directory)


COUNTER_FIELDS = ("working_set_bytes", "private_bytes", "lifetime_peak_working_set_bytes")


def parse_counter(value: str | None) -> int | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return int(text)
    except ValueError:
        try:
            return int(float(text))
        except ValueError:
            return None


def summarize_memory(directory: Path) -> None:
    with (directory / "phases.csv").open(encoding="utf-8-sig") as stream:
        phases = [(datetime.fromisoformat(row["timestamp_utc"].replace("Z", "+00:00")), row["phase"]) for row in csv.DictReader(stream)]
    with (directory / "memory.csv").open(encoding="utf-8-sig") as stream:
        observed = list(csv.DictReader(stream))
    complete: list[dict[str, str]] = []
    missing: list[dict[str, str]] = []
    for row in observed:
        if all(parse_counter(row.get(field)) is not None for field in COUNTER_FIELDS):
            complete.append(row)
        else:
            missing.append(row)
    status_counts: Counter[str] = Counter((row.get("status") or "").strip() or "unknown" for row in observed)
    missing_reasons: Counter[str] = Counter()
    missing_counters: Counter[str] = Counter()
    for row in missing:
        tokens = [token.strip() for token in (row.get("reason") or "").split(";") if token.strip()]
        if tokens:
            for token in tokens:
                missing_reasons[token] += 1
        else:
            missing_reasons["unreported_missing"] += 1
        for field in COUNTER_FIELDS:
            if parse_counter(row.get(field)) is None:
                missing_counters[field] += 1
    polls: dict[str, list[dict[str, str]]] = {}
    for row in complete:
        polls.setdefault(row["sample_id"], []).append(row)
    grouped: dict[str, list[tuple[int, int]]] = {"baseline": [], "fitting": [], "steady": []}
    intervals = []
    previous = None
    for rows in polls.values():
        at = datetime.fromisoformat(rows[0]["timestamp_utc"])
        current = "baseline"
        for instant, name in phases:
            if instant <= at:
                current = name
        bucket = "baseline" if current == "baseline" else "steady" if current == "steady" else "fitting" if current in {"initialization", "fitting", "calibration"} else "other"
        if bucket in grouped:
            grouped[bucket].append((sum(parse_counter(row["working_set_bytes"]) for row in rows), sum(parse_counter(row["private_bytes"]) for row in rows)))
        if previous is not None:
            intervals.append((at - previous).total_seconds() * 1000)
        previous = at
    summary = {}
    summary["observed_process_rows"] = len(observed)
    summary["complete_process_rows"] = len(complete)
    summary["missing_process_rows"] = len(missing)
    summary["incomplete_process_rows"] = len(missing)
    summary["status_counts"] = dict(status_counts)
    summary["missing_reasons"] = dict(missing_reasons)
    summary["missing_counters"] = dict(missing_counters)
    for name, values in grouped.items():
        summary[name] = {"samples": len(values), "max_working_set_bytes": max((v[0] for v in values), default=None),
                         "max_private_bytes": max((v[1] for v in values), default=None),
                         "mean_working_set_bytes": sum(v[0] for v in values) / len(values) if values else None,
                         "mean_private_bytes": sum(v[1] for v in values) / len(values) if values else None}
    summary["interval_ms"] = {"requested": 100, "observed_min": min(intervals, default=None), "observed_max": max(intervals, default=None)}
    to_ready = grouped["baseline"] + grouped["fitting"]
    summary["peak_initialization_to_ready"] = {"max_working_set_bytes": max((v[0] for v in to_ready), default=None),
                                              "max_private_bytes": max((v[1] for v in to_ready), default=None)}
    lifetime: dict[str, int] = {}
    for row in complete:
        key = row["pid"] + "/" + row["start_time_utc"]
        lifetime[key] = max(lifetime.get(key, 0), parse_counter(row["lifetime_peak_working_set_bytes"]))
    summary["process_lifetime_peak_working_set_bytes"] = lifetime
    summary["limits"] = "Sampled process tree. Missing counters are preserved as null and never coerced to 0: rows are recorded with status ok|partial|missing and a reason, excluded from maxima/means/peaks, and counted in missing_process_rows/missing_reasons. Brief/orphan processes and transient peaks can be missed. Lifetime peaks are separate, never summed. Baseline precedes model initialization."
    write_json(directory / "memory-summary.json", summary)
