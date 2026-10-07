# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: run.py launches this process with the frozen release .venv.
from __future__ import annotations

import csv
import os
import sys
import time
from pathlib import Path
from typing import Literal, assert_never

from common import Case, ContractError, DATASET, phase, process_affinity, write_json


def main() -> None:
    action, run, method, category, smoke, extra = sys.argv[1:]
    actions: dict[str, Literal["fit", "memory", "infer", "latency"]] = {"fit": "fit", "memory": "memory", "infer": "infer", "latency": "latency"}
    action = actions[action]
    case = Case(Path(run), method, category, smoke == "true")
    if action == "memory":
        while not Path(extra).exists():
            time.sleep(.02)
    from native import NativeModel, fit_case
    from timing import latency_process, memory_workload
    import torch
    from PIL import Image
    from map_io import save_map, sha256_file

    write_json(case.directory / f"runtime-{action}-{os.getpid()}.json", {
               "pid": os.getpid(), "python": sys.version, "torch": torch.__version__,
               "intra_op": torch.get_num_threads(), "inter_op": torch.get_num_interop_threads(),
               "device": "cpu", "caller_threads": 1, "process_affinity": process_affinity(),
               "thread_environment": {key: os.environ.get(key) for key in ("OMP_NUM_THREADS", "MKL_NUM_THREADS", "OPENBLAS_NUM_THREADS", "OMP_DYNAMIC", "MKL_DYNAMIC", "KMP_AFFINITY", "OMP_PROC_BIND")}})
    match action:
        case "fit":
            fit_case(case, method)
        case "memory":
            model = fit_case(case, method)
            memory_workload(case, model)
        case "infer" | "latency":
            model = NativeModel(method)
            model.load(case.directory / "native-state.pt")
            if action == "latency":
                latency_process(case, model, int(extra))
                return
            names = case.names("competitor-fit.txt" if case.smoke else "test.txt")
            role = "train" if case.smoke else "test"
            with (case.directory / "predictions.csv").open("x", newline="", encoding="utf-8") as stream:
                writer = csv.writer(stream)
                writer.writerow(["run_id", "method", "category", "filename", "label", "defect", "image_score", "score_kind",
                                 "native_map_path", "eval_map_path", "dtype", "shape", "geometry_id", "image_sha256"])
                with torch.no_grad():
                    for name in names:
                        path = DATASET / role / "img" / name
                        with Image.open(path) as source:
                            image = source.convert("RGB")
                        if image.size != (case.size, case.size):
                            raise ContractError(f"Unexpected input geometry: {name}")
                        result = model.predict(image)
                        if result.anomaly_map.shape != (1, 1, 256, 256) or not torch.isfinite(result.anomaly_map).all() or not torch.isfinite(result.score).all():
                            raise ContractError(f"Invalid native output: {name}")
                        mapped = torch.nn.functional.interpolate(result.anomaly_map.double(), size=(case.size, case.size), mode="bilinear", align_corners=False)
                        stem = Path(name).stem
                        native_path = Path("maps") / (stem + ".native.npy")
                        evaluated_path = Path("maps") / (stem + ".evaluated.npy")
                        geometry = f"{category}-{case.size}x{case.size}-256x256"
                        save_map(case.directory / native_path, result.anomaly_map[0, 0].numpy(), filename=name, geometry_id=geometry, score_kind="native", dtype="float32")
                        save_map(case.directory / evaluated_path, mapped[0, 0].numpy(), filename=name, geometry_id=geometry, score_kind="evaluated", dtype="float64")
                        defect = stem.removeprefix(category + "_").rsplit("_", 1)[0]
                        writer.writerow([case.run.name, method, category, name, "good" if defect == "good" else "anomaly", defect,
                                         result.score.item(), "native_raw", native_path.as_posix(), evaluated_path.as_posix(),
                                         "float32", "256x256", geometry, sha256_file(path)])
        case unreachable:
            assert_never(unreachable)


if __name__ == "__main__":
    main()
