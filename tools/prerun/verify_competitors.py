# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# Run from the repository root with the release-locked .venv Python:
# target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe tools/prerun/verify_competitors.py setup
# Repeat with 'offline' after setup; no benchmark or accuracy metrics are computed.

import gc
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import socket
import subprocess
import sys
from typing import Final

ROOT: Final = Path(__file__).resolve().parents[2]
OUT: Final = ROOT / "docs/benchmark/benchmark-prerun"
CACHE: Final = ROOT / "target/prerun/weights"
DATA: Final = ROOT.parent / "Anomalib4j_md/mvtec-ad-DatasetNinja/train/img"
OFFLINE: Final = sys.argv[1:] == ["offline"]
assert sys.argv[1:] in (["setup"], ["offline"])
OUT.mkdir(parents=True, exist_ok=True)
os.environ["HF_HOME"] = str(CACHE / "huggingface")
os.environ["TORCH_HOME"] = str(CACHE / "torch")
if OFFLINE:
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"

    def reject_network(event: str, args: tuple) -> None:
        """Fail the verification on any attempted socket connection or DNS lookup."""
        if event in {"socket.connect", "socket.getaddrinfo", "socket.sendto"}:
            raise PermissionError(event, "Offline smoke forbids network access")

    sys.addaudithook(reject_network)
    try:
        socket.getaddrinfo("example.com", 443)
    except PermissionError:
        network_guard_verified = True
    else:
        network_guard_verified = False
    assert network_guard_verified

from PIL import Image
import torch
from torchvision.transforms.v2.functional import to_dtype, to_image
from anomalib.data import InferenceBatch
from anomalib.models import Padim, Patchcore
from lightning import seed_everything

assert torch.version.cuda is None
assert not torch.cuda.is_available()
versions = {name: importlib.metadata.version(name) for name in (
    "anomalib", "torch", "torchvision", "timm", "lightning", "pytorch-lightning",
    "numpy", "scipy", "scikit-learn", "Pillow", "kornia", "torchmetrics",
    "huggingface-hub", "safetensors", "opencv-python-headless",
)}
environment = {
    "python": sys.version, "executable": sys.executable, "platform": platform.platform(),
    "versions": versions, "device": "cpu", "cuda_build": torch.version.cuda,
    "intra_op": torch.get_num_threads(), "inter_op": torch.get_num_interop_threads(),
    "environment": {k: os.environ.get(k) for k in (
        "OMP_NUM_THREADS", "MKL_NUM_THREADS", "OPENBLAS_NUM_THREADS",
        "OMP_DYNAMIC", "MKL_DYNAMIC", "KMP_AFFINITY", "OMP_PROC_BIND",
    )},
    "torch_config": torch.__config__.show(),
    "mkldnn_enabled": torch.backends.mkldnn.enabled,
}
(OUT / "environment.json").write_text(json.dumps(environment, indent=2), encoding="utf-8")
freeze = subprocess.run(
    ["uv", "pip", "freeze", "--python", sys.executable], check=True, capture_output=True, text=True,
)
(OUT / "environment.freeze.txt").write_text(freeze.stdout, encoding="utf-8")

weight_configs = []
smoke = []
for model_type in (Padim, Patchcore):
    seed_everything(42, workers=True)
    module = model_type(post_processor=False, evaluator=False, visualizer=False).cpu()
    native = module.model
    transform = module.pre_processor.transform
    assert transform is not None
    weight_configs.append({
        "method": model_type.__name__, "backbone": native.backbone,
        "pretrained_cfg": native.feature_extractor.feature_extractor.pretrained_cfg,
        "transform": str(transform), "export_transform_not_used": str(module.pre_processor.export_transform),
        "parameter_dtypes": sorted({str(p.dtype) for p in native.parameters()}),
    })
    if OFFLINE:
        # Given: two normal TRAIN images, one per category, for a technical-only fit.
        fit_names = ["bottle_good_000.png", "metal_nut_good_000.png"]
        native.train()
        with torch.no_grad():
            for name in fit_names:
                with Image.open(DATA / name) as source:
                    image = to_dtype(to_image(source.convert("RGB")), torch.float32, scale=True)
                native(transform(image).unsqueeze(0))
            # When: the exact release fitting API is used with unchanged defaults.
            module.fit()
        native.eval()
        checks = []
        with torch.no_grad():
            for name, original_size in (("bottle_good_001.png", 900), ("metal_nut_good_001.png", 700)):
                with Image.open(DATA / name) as source:
                    image = to_dtype(to_image(source.convert("RGB")), torch.float32, scale=True)
                resized = transform(image).unsqueeze(0)
                result = native(resized)
                assert isinstance(result, InferenceBatch)
                # Then: native raw outputs are finite CPU float32, before PostProcessor.
                assert result.anomaly_map.shape == (1, 1, 256, 256)
                assert result.pred_score.numel() == 1
                assert result.anomaly_map.dtype == result.pred_score.dtype == torch.float32
                assert result.anomaly_map.device.type == result.pred_score.device.type == "cpu"
                assert torch.isfinite(result.anomaly_map).all() and torch.isfinite(result.pred_score).all()
                mapped = torch.nn.functional.interpolate(
                    result.anomaly_map.double(), size=(original_size, original_size),
                    mode="bilinear", align_corners=False,
                )
                assert mapped.shape == (1, 1, original_size, original_size)
                assert torch.isfinite(mapped).all()
                if model_type is Padim:
                    assert torch.equal(result.pred_score, result.anomaly_map.amax(dim=(-2, -1)))
                checks.append({
                    "image": name, "source": "train/img", "input_shape": list(resized.shape),
                    "score_shape": list(result.pred_score.shape), "map_shape": list(result.anomaly_map.shape),
                    "dtype": str(result.anomaly_map.dtype), "finite": True,
                    "evaluation_shape": list(mapped.shape),
                })
            rectangular = transform(torch.zeros(3, 300, 500))
            assert rectangular.shape == (3, 256, 256)
        smoke.append({
            "method": model_type.__name__, "fit_images": fit_names,
            "fit_completed": True, "outputs": checks, "rectangular_output": list(rectangular.shape),
            "native_feature_count": getattr(native, "n_features", None),
            "memory_bank_shape": list(native.memory_bank.shape) if model_type is Patchcore else None,
            "native_preprocessing": str(transform), "network_blocked": True,
        })
    del native, module
    gc.collect()

weights = []
for path in sorted(CACHE.rglob("*")):
    if path.is_file() and (path.stat().st_size > 1_000_000):
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        weights.append({"path": str(path.relative_to(ROOT)), "bytes": path.stat().st_size, "sha256": digest})
(OUT / "weights.json").write_text(json.dumps({"models": weight_configs, "files": weights}, indent=2), encoding="utf-8")
if OFFLINE:
    (OUT / "smoke.json").write_text(json.dumps(smoke, indent=2), encoding="utf-8")
print(json.dumps({"mode": sys.argv[1], "models": [c["method"] for c in weight_configs], "smoke_passed": OFFLINE, "weight_files": len(weights)}))
