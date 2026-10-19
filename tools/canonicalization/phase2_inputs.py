# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/canonicalization/phase2.py
from pathlib import Path
import shutil
import sys
from typing import Final

from pydantic import TypeAdapter

from phase2_models import ROOT, PILOT, PHASE1, PROTOCOL, Phase2Error, PilotPlan, SourceInput, OriginalReference, RotationReference, PoseReference, J0Checkpoint, J1Checkpoint

sys.path.insert(0, str(ROOT / "tools/ablation"))
from inputs import read_split, sha256_file
from common import write_json, capture, capture_hardware, validate_manifests

SOURCE_PATTERNS: Final = ("src/**/*.java", "src/main/resources/models/*.onnx", "tools/canonicalization/*.py", "tools/ablation/*.py")


def verify_hashes(path: Path) -> int:
    hashes = TypeAdapter(dict[str,str]).validate_json(path.read_text())
    for filename, expected in hashes.items():
        source = Path(filename)
        if not source.is_absolute():
            source = ROOT / source
        if sha256_file(source) != expected:
            raise Phase2Error(f"Protected input/source identity mismatch: {source}")
    return len(hashes)


def sources() -> list[Path]:
    return sorted({p for pattern in SOURCE_PATTERNS for p in ROOT.glob(pattern)} | {PROTOCOL})


def prepare(root: Path) -> PilotPlan:
    plan = PilotPlan.model_validate_json((PILOT / "plan.json").read_text())
    protected = (PILOT, PHASE1, plan.baseline, plan.dataset)
    if root.exists() or any(p == root or p in root.parents for p in protected):
        raise Phase2Error("Output must be new and outside protected evidence/dataset")
    verified = verify_hashes(PHASE1 / "input-hashes.json")
    J0Checkpoint.model_validate_json((PHASE1 / "j0-result.json").read_text())
    J1Checkpoint.model_validate_json((PHASE1 / "j1-result.json").read_text())
    for name,digest in plan.production_sources_verified.items():
        if name != "src/main/java/io/github/antctrlwin/anomalib4j/onnx/OnnxMobileNetV4Encoder.java" and sha256_file(ROOT / name) != digest:
            raise Phase2Error(f"Preserved M0/M2/preprocessing source changed: {name}")
    asset = "src/main/resources/models/mobilenetv4_spatial_14x14.onnx"
    asset_hashes = TypeAdapter(dict[str,str]).validate_json((plan.baseline / "source-hashes.json").read_text())
    if sha256_file(ROOT / asset) != asset_hashes[asset]:
        raise Phase2Error("Frozen encoder byte identity changed")
    validate_manifests(plan.baseline)
    split = read_split(plan.baseline / "data", "metal_nut")
    if tuple(map(len,(split.fit,split.calibration,split.test))) != (176,44,115):
        raise Phase2Error("Frozen176/44/115 manifests required")
    for role in ("fit", "calibration", "test"):
        if (PILOT / "inputs" / (role+".txt")).read_text().splitlines() != list(getattr(split,role)):
            raise Phase2Error("Pilot/baseline split ordering differs")
    data_hashes = TypeAdapter(dict[str,str]).validate_json((plan.baseline / "dataset-hashes.json").read_text())
    selected = ["meta.json"]
    for role, names in (("train", split.fit+split.calibration),("test",split.test)):
        selected += [f"{role}/{kind}/{name}{suffix}" for name in names for kind,suffix in (("img",""),("ann",".json"))]
    input_hashes = {}
    for name in selected:
        path = plan.dataset / name
        if sha256_file(path) != data_hashes[name]:
            raise Phase2Error(f"Dataset byte mismatch: {name}")
        input_hashes[str(path)] = data_hashes[name]
    originals = TypeAdapter(list[OriginalReference]).validate_json((PILOT / "geometry.json").read_text())
    rotations = TypeAdapter(list[RotationReference]).validate_json((PILOT / "rotation-geometry.json").read_text())
    if len(originals) != 335 or len(rotations) != 88 or {r.rotation for r in rotations} != {-10,-5,5,10}:
        raise Phase2Error("Frozen geometry/challenge coverage mismatch")
    for role in ("fit","calibration","test"):
        if [r.image for r in originals if r.role == role] != list(getattr(split,role)):
            raise Phase2Error("Geometry/manifests differ")
    records = []
    for row in originals:
        folder = "test" if row.role == "test" else "train"
        source = plan.dataset / folder / "img" / row.image
        records.append(SourceInput(role=row.role,image=row.image,source=source,sha256=sha256_file(source),
                                   expected=PoseReference.model_validate(row.model_dump())))
    for row in rotations:
        source = PILOT / "images/robust-a" / row.perturbed_image
        digest = sha256_file(source)
        input_hashes[str(source)] = digest
        records.append(SourceInput(role="rotation",image=row.perturbed_image,source=source,sha256=digest,
                                   expected=PoseReference.model_validate(row.model_dump())))
    root.mkdir(parents=True)
    capture(root / "workspace")
    capture_hardware(root / "hardware.json")
    shutil.copyfile(PROTOCOL, root / "protocol.md")
    write_json(root / "source-hashes.json", {p.relative_to(ROOT).as_posix():sha256_file(p) for p in sources()})
    (root / "source-inputs.json").write_bytes(TypeAdapter(list[SourceInput]).dump_json(records,indent=2))
    for path in (root / "source-inputs.json",root / "protocol.md"):
        input_hashes[str(path)] = sha256_file(path)
    write_json(root / "input-hashes.json", input_hashes)
    shutil.copytree(PILOT / "data", root / "data")
    write_json(root / "plan.json", {"dataset":str(plan.dataset),"pilot":str(PILOT),"phase1":str(PHASE1),
               "originals":335,"rotations":88,"candidates":["B","C","D"],"verified_phase1_inputs":verified,
               "encoder_sha256":sha256_file(ROOT / "src/main/resources/models/mobilenetv4_spatial_14x14.onnx"),
               "protocol_sha256":sha256_file(PROTOCOL),"command":sys.argv})
    return plan
