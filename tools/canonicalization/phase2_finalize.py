# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2", "typer"]
# ///
# How to run: python tools/canonicalization/phase2_finalize.py --root target/experiments/java-canonicalization-phase2-20261019
from pathlib import Path
import re
from typing import Final

from pydantic import TypeAdapter
import typer

from phase2_models import ROOT, PHASE1, CANDIDATES, Phase2Error
from phase2_inputs import verify_hashes
from phase2_results import timing_summary, CandidateResult
from inputs import sha256_file
from common import write_json

REPAIRED: Final = {"tools/canonicalization/phase2_results.py", "tools/canonicalization/test_phase2.py"}


def main(root: Path = typer.Option(...)) -> None:
    root = root.resolve()
    if (root / "complete.json").exists():
        raise Phase2Error("Completed campaign must not be overwritten")
    captured = TypeAdapter(dict[str,str]).validate_json((root / "source-hashes.json").read_text())
    changed = []
    for name,digest in captured.items():
        current = sha256_file(ROOT / name)
        if current != digest:
            backup = root / "source-at-measurement" / name
            if name not in REPAIRED or not backup.exists() or sha256_file(backup) != digest:
                raise Phase2Error(f"Unexpected measured-source change: {name}")
            changed.append({"path":name,"measured_sha256":digest,"current_sha256":current,
                            "measured_source":str(backup),"scope":"CSV boundary parsing/regression only; no scientific or timing body change"})
    if {r["path"] for r in changed} != REPAIRED:
        raise Phase2Error("Expected documented CSV parser and regression repair only")
    for name in CANDIDATES:
        result = CandidateResult.model_validate_json((root / name / "result.json").read_text())
        if result.geometry.records != 423 or result.geometry.source_failures:
            raise Phase2Error("Incomplete scientific results")
    input_count = verify_hashes(root / "input-hashes.json")
    retained_count = verify_hashes(PHASE1 / "input-hashes.json")
    timing_summary(root)
    write_json(root / "postprocessing-source-status.json",{"changed_after_measurement":changed,
               "unchanged_captured_sources":len(captured)-len(changed),"captured_sources":len(captured),
               "postprocessor":str(Path(__file__)),"postprocessor_sha256":sha256_file(Path(__file__))})
    log = (ROOT / "target/canonical-phase2-campaign.log").read_text()
    duration = re.search(r"scientific_seconds\s*=\s*([0-9.]+)",log)
    write_json(root / "complete.json",{"category":"metal_nut","candidates":["B","C","D"],"fit":176,"calibration":44,"test":115,
               "rotations_per_candidate":88,"scientific_seconds":float(duration.group(1)) if duration else None,
               "scientific_duration_source":"retained failure trace local variable; whole-campaign wall duration unavailable",
               "timing_records":189,"summary_recovered_without_measurement_rerun":True,
               "unchanged_dataset_inputs":input_count,"unchanged_retained_inputs":retained_count,"production_unchanged":True})
    print("PHASE2 - FINALIZE - complete - saved scientific and timing records; no rerun",flush=True)


if __name__ == "__main__":
    typer.run(main)
