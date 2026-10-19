# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/canonicalization/phase2.py --output target/experiments/java-canonicalization-phase2-20261019
from pathlib import Path
import sys
from time import perf_counter

import numpy as np
from pydantic import TypeAdapter
import typer

from phase2_models import ROOT, PILOT, PHASE1, PROTOCOL, CANDIDATES, Phase2Error
from phase2_inputs import prepare, verify_hashes
from phase2_results import analyze, timing_summary
from inputs import java_command, sha256_file
from common import write_json
from progress import phase, stream_command


def main(output: Path = typer.Option(...)) -> None:
    root = output.resolve()
    started = perf_counter()
    print("PHASE2 - METAL_NUT - PREFLIGHT - frozen sources, inputs and protocol",flush=True)
    plan = prepare(root)
    progress = root / "progress.log"
    source_manifest = root / "source-inputs.json"
    for name in CANDIDATES:
        folder = root / name
        folder.mkdir()
        with phase(progress,f"PHASE2 {name}","EXTRACT FIT/CAL/TEST/ROTATION"):
            stream_command(java_command("CanonicalPhase2Bridge",["extract",name,str(source_manifest),str(folder / "inputs")]),
                           folder / "extract.log",progress)
        with phase(progress,f"PHASE2 {name}","INDEPENDENT FIT/CALIBRATE/RELOAD"):
            stream_command(java_command("M2Runner",[str(folder / "inputs"),str(folder / "model"),f"PHASE2 {name}"]),folder / "fit.log",progress)
            np.testing.assert_array_equal(np.load(folder / "model/native-z.npy",allow_pickle=False),np.load(folder / "model/reload-z.npy",allow_pickle=False))
        preprocessing = TypeAdapter(dict[str,str|int|bool]).validate_json((folder / "inputs/preprocessing.json").read_text())
        write_json(folder / "model/config.json",{"method":"M2","category":"metal_nut","candidate":name,
                   "preprocessing_id":preprocessing["id"],"experimental_only":True,"legacy_archive_requires_this_metadata":True,
                   "protocol_sha256":sha256_file(PROTOCOL),"source_snapshot_sha256":sha256_file(root / "source-hashes.json"),
                   "archive_sha256":sha256_file(folder / "model/model.bin"),"features_sha256":sha256_file(folder / "inputs/fit.npy")})
        with phase(progress,f"PHASE2 {name}","SCORE ROTATIONS"):
            stream_command(java_command("CanonicalPilotBridge",["score",str(folder / "model/model.bin"),str(folder / "inputs/rotation.npy"),
                           str(folder / "rotation-z.npy")]),folder / "rotation.log",progress)
        with phase(progress,f"PHASE2 {name}","QUALITY ORIGINAL FRAME"):
            command = java_command("CanonicalPilotBridge",["evaluate",str(plan.dataset),str(folder / "model"),str(folder / "inputs")])
            command[1] = "-Xmx4g"
            stream_command(command,folder / "evaluate.log",progress)
        with phase(progress,f"PHASE2 {name}","PER DEFECT DIAGNOSTIC"):
            command = java_command("CanonicalPhase2Bridge",["defects",str(folder / "model"),str(plan.dataset),str(folder / "per-defect.json")])
            command[1] = "-Xmx4g"
            stream_command(command,folder / "per-defect.log",progress)
    archive_hashes = {sha256_file(root / name / "model/model.bin") for name in CANDIDATES}
    if len(archive_hashes) != 3:
        raise Phase2Error("Candidate state archives unexpectedly identical; investigate before qualification")
    (root / "R").mkdir()
    with phase(progress,"PHASE2 R","REFERENCE PER DEFECT DIAGNOSTIC"):
        command = java_command("CanonicalPhase2Bridge",["defects",str(PILOT / "model"),str(plan.dataset),str(root / "R/per-defect.json")])
        command[1] = "-Xmx4g"
        stream_command(command,root / "R/per-defect.log",progress)
    with phase(progress,"PHASE2","COHERENCE/ROTATION/GATES"):
        analyze(root)
    scientific_seconds = perf_counter()-started
    with phase(progress,"PHASE2","MATCHED PREPROCESSING TIMING"):
        for fork in range(3):
            stream_command(java_command("CanonicalPhase2Timing",[str(source_manifest),str(root / f"timing-{fork}.csv"),str(fork)]),
                           root / f"timing-{fork}.log",progress)
        timing_summary(root)
    source_count = verify_hashes(root / "source-hashes.json")
    data_count = verify_hashes(root / "input-hashes.json")
    retained_count = verify_hashes(PHASE1 / "input-hashes.json")
    write_json(root / "complete.json",{"category":"metal_nut","candidates":["B","C","D"],"fit":176,"calibration":44,"test":115,
               "rotations_per_candidate":88,"scientific_seconds":scientific_seconds,"wall_seconds":perf_counter()-started,
               "unchanged_source_count":source_count,"unchanged_dataset_inputs":data_count,"unchanged_retained_inputs":retained_count,
               "command":sys.argv,"production_unchanged":True})


if __name__ == "__main__":
    typer.run(main)
