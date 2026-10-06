# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: invoked by worker.py; no separate environment is resolved.
from __future__ import annotations

import csv
import statistics
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Final, TypedDict

from common import Case, ContractError, DATASET, phase, write_json


@dataclass(frozen=True, slots=True)
class Schedule:
    warmup: int = 5
    measurement: int = 10
    seconds: float = 2


DEFAULT: Final = Schedule()
SMOKE: Final = Schedule(1, 1, .1)


def sample(operation: Callable[[], float], schedule: Schedule) -> list[tuple[int, int]]:
    for _ in range(schedule.warmup):
        until = time.perf_counter_ns() + int(schedule.seconds * 1e9)
        while time.perf_counter_ns() < until:
            operation()
    rows = []
    for iteration in range(schedule.measurement):
        until = time.perf_counter_ns() + int(schedule.seconds * 1e9)
        while time.perf_counter_ns() < until:
            start = time.perf_counter_ns()
            consumed = operation()
            elapsed = time.perf_counter_ns() - start
            if not isinstance(consumed, float):
                raise ContractError("Latency consumer must materialize a scalar")
            rows.append((iteration, elapsed))
    return rows


class LatencySummary(TypedDict):
    samples: int
    mean_us: float
    p50_us: float
    p95_us: float
    p99_us: float


def summarize(values: list[float]) -> LatencySummary:
    ordered = sorted(values)
    if not ordered:
        raise ContractError("No latency samples")
    def percentile(fraction: float) -> float:
        at = (len(ordered) - 1) * fraction
        low = int(at)
        return ordered[low] + (ordered[min(low + 1, len(ordered) - 1)] - ordered[low]) * (at - low)
    return {"samples": len(values), "mean_us": statistics.fmean(values), "p50_us": percentile(.5),
            "p95_us": percentile(.95), "p99_us": percentile(.99)}


def latency_process(case: Case, model, process: int) -> None:
    import os
    import torch
    from PIL import Image
    path = DATASET / ("train" if case.smoke else "test") / "img" / f"{case.category}_good_000.png"
    with Image.open(path) as source:
        image = source.convert("RGB")
    schedule = SMOKE if case.smoke else DEFAULT
    rows = []
    with torch.no_grad():
        for region in ["native_full", "full_resolution"]:
            def operation() -> float:
                result = model.predict(image)
                output = result.anomaly_map
                if region == "full_resolution":
                    output = torch.nn.functional.interpolate(output.double(), size=(case.size, case.size), mode="bilinear", align_corners=False)
                return float(result.score.item() + output[0, 0, output.shape[2] // 2, output.shape[3] // 2].item())
            rows.extend((process, os.getpid(), region, iteration, elapsed) for iteration, elapsed in sample(operation, schedule))
    with (case.directory / f"latency-process-{process}.csv").open("x", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["process", "pid", "region", "iteration", "elapsed_ns"])
        writer.writerows(rows)


def memory_workload(case: Case, model) -> None:
    import torch
    from PIL import Image
    with Image.open(DATASET / ("train" if case.smoke else "test") / "img" / f"{case.category}_good_000.png") as source:
        image = source.convert("RGB")
    with torch.no_grad():
        for name, seconds in [("warmup", .1 if case.smoke else 10), ("steady", .2 if case.smoke else 10)]:
            phase(case.directory, name)
            until = time.perf_counter_ns() + int(seconds * 1e9)
            while time.perf_counter_ns() < until:
                result = model.predict(image)
                full = torch.nn.functional.interpolate(result.anomaly_map.double(), size=(case.size, case.size), mode="bilinear", align_corners=False)
                float(result.score.item() + full[0, 0, 0, 0].item())
    phase(case.directory, "complete")
    write_json(case.directory / "memory-input.json", {"decoded_image": "one RGB PIL image in RAM",
               "logical_rgb_bytes": case.size * case.size * 3, "region": "full_resolution"})
