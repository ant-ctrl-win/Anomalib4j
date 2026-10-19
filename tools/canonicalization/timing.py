# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy", "pillow", "pydantic>=2", "typer"]
# ///
# How to run: python tools/canonicalization/timing.py ROOT DATASET FORK
import csv
from pathlib import Path
from time import perf_counter

import numpy as np
from PIL import Image
import typer

from geometry import estimate, canonical_matrix, warp


def main(root: Path, dataset: Path, fork: int) -> None:
    images = []
    for name in (root / "inputs/fit.txt").read_text().splitlines()[:8]:
        with Image.open(dataset / "train/img" / name) as image:
            images.append(np.array(image.convert("RGB")))
    matrices = [canonical_matrix(estimate(rgb)) for rgb in images]
    start = perf_counter()
    while perf_counter()-start < 2:
        for rgb in images:
            warp(rgb, canonical_matrix(estimate(rgb)))
    rows = []
    stages = ("estimate", "warp", "total")
    for round_index in range(9):
        for offset in range(3):
            stage = stages[(offset + round_index + fork) % 3]
            start = perf_counter()
            for i in range(16):
                match stage:
                    case "estimate":
                        estimate(images[i % 8])
                    case "warp":
                        warp(images[i % 8], matrices[i % 8])
                    case "total":
                        warp(images[i % 8], canonical_matrix(estimate(images[i % 8])))
                    case unreachable:
                        from typing import assert_never
                        assert_never(unreachable)
            rows.append({"fork": fork, "round": round_index, "stage": stage,
                         "batch": 16, "ms_per_image": (perf_counter()-start)*1000/16})
    with (root / f"timing-{fork}.csv").open("x", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    typer.run(main)
