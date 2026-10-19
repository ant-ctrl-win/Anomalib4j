# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy", "pillow", "pydantic>=2", "typer", "matplotlib"]
# ///
# How to run: python tools/canonicalization/run.py --output target/experiments/m2-metal-nut-canonicalization-20261019
import csv
from pathlib import Path
import shutil
import sys
from time import perf_counter
from typing import Final

import numpy as np
from pydantic import TypeAdapter
import typer

ROOT: Final = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT / "tools/ablation"))
from inputs import baseline_contract, read_split, java_command, sha256_file
from models import normalize
from common import capture, capture_hardware, write_json
from progress import phase, stream_command
from geometry import GeometryError
from prepare import prepare
from diagnostics import analyze


def main(output: Path = typer.Option(...)) -> None:
    started = perf_counter()
    root = output.resolve()
    baseline = ROOT / "target/comparison/m0-all15-baseline-20261016"
    retained = ROOT / "target/ablation/local-models-20261017"
    robust = ROOT / "target/robustness/m2-controlled-20261018"
    if root.exists() or any(p==root or p in root.parents for p in (baseline,retained,robust)):
        raise GeometryError("Output must be new and outside retained evidence")
    print("METAL_NUT - PREFLIGHT - frozen sources/splits/dataset",flush=True)
    contract = baseline_contract(baseline,("metal_nut",),verify_data=True)
    if root==contract.dataset or contract.dataset in root.parents:
        raise GeometryError("Output cannot be in dataset")
    hashes = TypeAdapter(dict[str,str]).validate_json((robust / "source-hashes.json").read_text())
    frozen = {name:digest for name,digest in hashes.items() if name.startswith("src/main/")}
    for name,digest in frozen.items():
        if sha256_file(ROOT / name)!=digest:
            raise GeometryError(f"Production source changed: {name}")
    root.mkdir(parents=True)
    capture(root / "workspace")
    capture_hardware(root / "hardware.json")
    sources={p for pattern in ("src/**/*.java","tools/canonicalization/*.py","tools/ablation/*.py") for p in ROOT.glob(pattern)}
    sources.add(ROOT / "docs/experiments/M2_METAL_NUT_CANONICALIZATION_PLAN.md")
    write_json(root / "source-hashes.json",{p.relative_to(ROOT).as_posix():sha256_file(p) for p in sorted(sources)})
    write_json(root / "plan.json",{"category":"metal_nut","dataset":str(contract.dataset),"baseline":str(baseline),
        "retained":str(retained),"models":str(robust),"command":sys.argv,"python":sys.version,
        "numpy":np.__version__,"canonicalizer":"fourfold-otsu-center-source700-v1","scale":1,
        "production_sources_verified":frozen})
    shutil.copytree(baseline / "data/metal_nut",root / "data")
    split=read_split(baseline / "data","metal_nut")
    progress=root / "progress.log"
    with phase(progress,"METAL_NUT","GEOMETRY AND SOURCE CANONICALIZATION"):
        prepare(root,contract.dataset,split)
    base=root / "baseline"
    base.mkdir()
    prior=TypeAdapter(dict[str,str]).validate_json((robust / "metal_nut/input-hashes.json").read_text())
    input_hashes={}
    for role in ("fit","calibration"):
        directory=retained / "features/metal_nut" / role
        if (directory / "images.txt").read_text().splitlines()!=list(getattr(split,role)):
            raise GeometryError("Baseline feature order mismatch")
        path=directory / "features.npy"
        if sha256_file(path)!=prior[str(path)]:
            raise GeometryError("Baseline feature identity mismatch")
        values=np.load(path,allow_pickle=False)
        np.save(base / (role+".npy"),normalize(values.reshape(len(values),196,96)).reshape(len(values),-1))
        input_hashes[str(path)]=sha256_file(path)
    model=robust / "metal_nut/model.bin"
    if sha256_file(model)!=prior[str(model)]:
        raise GeometryError("Frozen M2 model changed")
    shutil.copyfile(model,base / "model.bin")
    shutil.copyfile(retained / "M2/metal_nut/metrics-unified.json",base / "metrics-unified.json")
    input_hashes[str(model)]=sha256_file(model)
    input_hashes[str(retained / "M2/metal_nut/metrics-unified.json")]=sha256_file(base / "metrics-unified.json")
    write_json(root / "input-hashes.json",input_hashes)
    for role in ("fit","calibration","test","baseline-normal","robust-a","robust-b"):
        with phase(progress,"METAL_NUT","ENCODE "+role):
            stream_command(java_command("CanonicalPilotBridge",["extract",str(root / "images" / role),
                str(root / "inputs" / (role+".txt")),str(root / "inputs" / (role+".npy"))]),root / (role+"-extract.log"),progress)
    with phase(progress,"METAL_NUT","FIT AND HELD-OUT CALIBRATION"):
        stream_command(java_command("M2Runner",[str(root / "inputs"),str(root / "model"),"METAL_NUT B"]),root / "model.log",progress)
        np.testing.assert_array_equal(np.load(root / "model/native-z.npy"),np.load(root / "model/reload-z.npy"))
    for role,archive in (("baseline-normal",base / "model.bin"),("robust-a",base / "model.bin"),("robust-b",root / "model/model.bin")):
        stream_command(java_command("CanonicalPilotBridge",["score",str(archive),str(root / "inputs" / (role+".npy")),
            str(root / (role+"-z.npy"))]),root / (role+"-score.log"),progress)
    with (robust / "metal_nut/per-image.csv").open(newline="") as stream:
        old={r["image"]:float(r["s0"]) for r in csv.DictReader(stream)}
    normals=(root / "inputs/baseline-normal.txt").read_text().splitlines()
    np.testing.assert_array_equal(np.load(root / "baseline-normal-z.npy").max(axis=1),[old[n] for n in normals])
    write_json(root / "baseline-parity.json",{"exact_original_normal_scores":len(normals),"candidate_reload_bit_exact":True})
    write_json(root / "model/config.json",{"method":"M2","category":"metal_nut","variant":"canonicalized-training-and-inference",
        "canonicalizer_sha256":sha256_file(ROOT / "tools/canonicalization/geometry.py"),
        "evaluation":"canonical native max; original-frame backprojected maps; untouched masks",
        "transforms_sha256":sha256_file(root / "inputs/test-transforms.npy")})
    with phase(progress,"METAL_NUT","QUALITY ORIGINAL COORDINATES"):
        command=java_command("CanonicalPilotBridge",["evaluate",str(contract.dataset),str(root / "model"),str(root / "inputs")])
        command[1]="-Xmx4g"
        stream_command(command,root / "evaluate.log",progress)
    with phase(progress,"METAL_NUT","COHERENCE AND ROBUSTNESS DIAGNOSTICS"):
        analyze(root,retained / "M2/metal_nut")
    measurement_seconds=perf_counter()-started
    with phase(progress,"METAL_NUT","CANONICALIZER TIMING"):
        for fork in range(3):
            stream_command([sys.executable,str(ROOT / "tools/canonicalization/timing.py"),str(root),str(contract.dataset),str(fork)],
                root / f"timing-{fork}.log",progress)
    for name,digest in frozen.items():
        if sha256_file(ROOT / name)!=digest:
            raise GeometryError("Production source changed during run")
    write_json(root / "complete.json",{"category":"metal_nut","fit":len(split.fit),"calibration":len(split.calibration),
        "test":len(split.test),"rotation_pairs":len(normals)*4,"measurement_seconds":measurement_seconds,
        "wall_seconds_including_timing":perf_counter()-started,"production_unchanged":True})


if __name__ == "__main__":
    typer.run(main)
