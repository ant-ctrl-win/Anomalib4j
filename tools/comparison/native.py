# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: imported by worker.py using the frozen release .venv.
from __future__ import annotations

import time
import importlib.metadata
import json
from dataclasses import dataclass
from pathlib import Path
from typing import assert_never, Literal

from common import Case, ContractError, PRERUN, phase, write_json
from offline_env import activate

activate()
frozen_versions = json.loads((PRERUN / "environment.json").read_text(encoding="utf-8"))["versions"]
for package, version in frozen_versions.items():
    if importlib.metadata.version(package) != version:
        raise ContractError(f"Frozen dependency mismatch: {package}")

import torch
from PIL import Image
from anomalib.models import Padim, Patchcore
from lightning import seed_everything
from torchvision.transforms.v2.functional import to_dtype, to_image

Method = Literal["padim", "patchcore"]


@dataclass(frozen=True, slots=True)
class Prediction:
    score: torch.Tensor
    anomaly_map: torch.Tensor


class NativeModel:
    def __init__(self, method: Method):
        seed_everything(42, workers=True)
        if torch.version.cuda is not None or torch.cuda.is_available():
            raise ContractError("The frozen competitor runtime must be CPU-only")
        match method:
            case "padim":
                self.module = Padim(backbone="resnet18", layers=["layer1", "layer2", "layer3"],
                                    pre_trained=True, n_features=None, post_processor=False, evaluator=False, visualizer=False)
            case "patchcore":
                self.module = Patchcore(backbone="wide_resnet50_2", layers=["layer2", "layer3"],
                                       pre_trained=True, coreset_sampling_ratio=.1, num_neighbors=9,
                                       post_processor=False, evaluator=False, visualizer=False)
            case unreachable:
                assert_never(unreachable)
        self.module.model.cpu()

    def preprocess(self, image: Image.Image) -> torch.Tensor:
        rgb = to_dtype(to_image(image), torch.float32, scale=True)
        return self.module.pre_processor.transform(rgb)

    def predict(self, image: Image.Image) -> Prediction:
        result = self.module.model(self.preprocess(image).unsqueeze(0))
        return Prediction(result.pred_score, result.anomaly_map)

    def load(self, path: Path) -> None:
        self.module.model.load_state_dict(torch.load(path, map_location="cpu", weights_only=True), strict=True)
        self.module.model.eval()

    def fit(self, case: Case) -> None:
        from common import DATASET
        names = case.names("competitor-fit.txt")
        self.module.model.train()
        phase(case.directory, "fitting")
        with torch.no_grad():
            for start in range(0, len(names), 32):
                batch = []
                for name in names[start:start + 32]:
                    with Image.open(DATASET / "train/img" / name) as source:
                        batch.append(self.preprocess(source.convert("RGB")))
                self.module.model(torch.stack(batch))
            self.module.model.eval()
            self.module.fit()
        self.module.model.eval()

    def save(self, directory: Path) -> None:
        state = self.module.model.state_dict()
        torch.save(state, directory / "native-state.pt")
        backbone = sum(t.numel() * t.element_size() for key, t in state.items() if key.startswith("feature_extractor."))
        detector = sum(t.numel() * t.element_size() for key, t in state.items() if not key.startswith("feature_extractor."))
        write_json(directory / "payload.json", {"backbone_logical_tensor_bytes": backbone,
                   "detector_logical_tensor_bytes": detector, "total_state_dict_bytes": backbone + detector,
                   "serialized_bytes": (directory / "native-state.pt").stat().st_size,
                   "logical_bytes_note": "state_dict tensor payload, excludes Python/framework/allocator memory"})


def fit_case(case: Case, method: Method) -> NativeModel:
    phase(case.directory, "initialization")
    start = time.perf_counter_ns()
    model = NativeModel(method)
    initialized = time.perf_counter_ns()
    model.fit(case)
    ready = time.perf_counter_ns()
    phase(case.directory, "ready")
    write_json(case.directory / "fitting.json", {"initialization_seconds": (initialized - start) / 1e9,
               "fitting_seconds": (ready - initialized) / 1e9, "ready_to_infer_seconds": (ready - start) / 1e9,
               "fit_count": len(case.names("competitor-fit.txt")), "fit_batch": 32,
               "training_decode_io": "included; one ordered manifest pass; synchronous loader (0 workers)",
               "process_startup_imports": "excluded", "smoke": case.smoke})
    model.save(case.directory)
    return model
