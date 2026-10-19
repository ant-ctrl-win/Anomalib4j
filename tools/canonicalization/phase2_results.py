# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pydantic>=2"]
# ///
# How to run: imported by tools/canonicalization/phase2.py
import csv
from pathlib import Path
from typing import Literal

import numpy as np
from pydantic import Field, TypeAdapter

from phase2_models import (PILOT, CANDIDATES, Quality, VerifiedQuality, FrozenModel, Coherence, GateEvidence, Gates,
                           RotationSummary, RotationPair, RotationReference, SavedPair, SourceInput, Phase2Error, acceptance, distribution)
from phase2_statistics import coherence, retain_rotation, save


class JavaPose(FrozenModel):
    status: Literal["SUCCESS"]
    reason: Literal["OK"]
    cx: float
    cy: float
    angle: float


class GeometryRecord(FrozenModel):
    role: str
    image: str
    geometry: JavaPose


class GeometrySummary(FrozenModel):
    records: int
    max_center_error: float
    max_angle_error: float
    equivalent_branch_crossings: int
    source_failures: int = 0
    unexplained_branch_changes: int = 0
    drops: int = 0
    fallbacks: int = 0


class CandidateResult(FrozenModel):
    quality: Quality
    delta_unaligned: Quality
    delta_reference: Quality
    coherence: Coherence
    rotation: RotationSummary
    geometry: GeometrySummary
    gates: Gates
    qualifies: bool


class TimingRow(FrozenModel):
    fork: int
    round: int
    stage: Literal["geometry","tensor","total"]
    candidate: Literal["shared","B","C","D"]
    batch: int = Field(ge=16,le=16)
    ns_per_image: float
    bytes_per_image: float


def reliability(root: Path, candidate: Path) -> GeometrySummary:
    expected = TypeAdapter(list[SourceInput]).validate_json((root / "source-inputs.json").read_text())
    actual = TypeAdapter(list[GeometryRecord]).validate_json((candidate / "inputs/geometry.json").read_text())
    if len(actual) != len(expected) or len(actual) != 423:
        raise Phase2Error("Missing candidate source geometry")
    center, angles = [], []
    for a,e in zip(actual,expected,strict=True):
        if (a.role,a.image) != (e.role,e.image):
            raise Phase2Error("Candidate source geometry order mismatch")
        center.append(max(abs(a.geometry.cx-e.expected.cx),abs(a.geometry.cy-e.expected.cy)))
        angles.append(abs(a.geometry.angle-e.expected.angle))
    if max(center)>1e-6 or max(angles)>1e-6:
        raise Phase2Error("Candidate geometry semantics changed")
    originals = {r.image:r.geometry.angle for r in actual if r.role == "test"}
    rotated = {r.image:r.geometry.angle for r in actual if r.role == "rotation"}
    challenge = TypeAdapter(list[RotationReference]).validate_json((PILOT / "rotation-geometry.json").read_text())
    crossings = 0
    for row in challenge:
        expected_branch = int(np.floor((originals[row.image]+row.rotation+45)/90))
        observed_branch = int(round((originals[row.image]+row.rotation-rotated[row.perturbed_image])/90))
        if expected_branch != observed_branch:
            raise Phase2Error("Unexplained modulo90 branch change")
        crossings += observed_branch != 0
    return GeometrySummary(records=423,max_center_error=max(center),max_angle_error=max(angles),equivalent_branch_crossings=crossings)


def analyze(root: Path) -> None:
    baseline = VerifiedQuality.model_validate_json((PILOT / "baseline/metrics-unified.json").read_text())
    reference = VerifiedQuality.model_validate_json((PILOT / "model/metrics-unified.json").read_text())
    rdir = root / "R"
    ref_coherence = coherence(PILOT / "inputs",PILOT / "model",rdir)
    with (PILOT / "rotation-scores.csv").open(newline="") as stream:
        old = [SavedPair.model_validate(row) for row in csv.DictReader(stream)]
    rpairs = [RotationPair(image=r.image,rotation=r.rotation,original=r.b0,transformed=r.bt,delta=r.delta_b) for r in old]
    apairs = [RotationPair(image=r.image,rotation=r.rotation,original=r.a0,transformed=r.at,delta=r.delta_a) for r in old]
    rrot = retain_rotation(rdir,rpairs)
    adir = root / "A"
    adir.mkdir()
    arot = retain_rotation(adir,apairs)
    reference_evidence = GateEvidence(quality=reference,coherence=ref_coherence,rotation=rrot,geometry_pass=True)
    tests = (PILOT / "inputs/test.txt").read_text().splitlines()
    rotations = TypeAdapter(list[RotationReference]).validate_json((PILOT / "rotation-geometry.json").read_text())
    results = {}
    for name in CANDIDATES:
        folder = root / name
        quality = VerifiedQuality.model_validate_json((folder / "model/metrics-unified.json").read_text())
        if (quality.regions,quality.foreground_pixels,quality.background_pixels) != (reference.regions,reference.foreground_pixels,reference.background_pixels):
            raise Phase2Error("Evaluation pixel/region population changed")
        stats = coherence(folder / "inputs",folder / "model",folder)
        original = np.load(folder / "model/native-z.npy",allow_pickle=False).max(axis=1)
        transformed = np.load(folder / "rotation-z.npy",allow_pickle=False).max(axis=1)
        if original.shape != (115,) or transformed.shape != (88,) or not np.isfinite(original).all() or not np.isfinite(transformed).all():
            raise Phase2Error("Invalid candidate native maps")
        pairs = [RotationPair(image=r.image,rotation=r.rotation,original=float(original[tests.index(r.image)]),transformed=float(transformed[i]),
                              delta=float(transformed[i]-original[tests.index(r.image)])) for i,r in enumerate(rotations)]
        rotation = retain_rotation(folder,pairs)
        geometry = reliability(root,folder)
        evidence = GateEvidence(quality=quality,coherence=stats,rotation=rotation,geometry_pass=True)
        gates = acceptance(evidence,reference_evidence,baseline)
        result = CandidateResult(quality=quality,
                 delta_unaligned=Quality(**{k:getattr(quality,k)-getattr(baseline,k) for k in Quality.model_fields}),
                 delta_reference=Quality(**{k:getattr(quality,k)-getattr(reference,k) for k in Quality.model_fields}),
                 coherence=stats,rotation=rotation,geometry=geometry,gates=gates,qualifies=gates.qualifies)
        save(folder / "result.json",result)
        results[name] = result
    for name in CANDIDATES[1:]:
        if (root / name / "inputs/geometry.json").read_bytes() != (root / "B/inputs/geometry.json").read_bytes():
            raise Phase2Error("Candidate geometry records differ")
    with (root / "quality-comparison.csv").open("x",newline="") as stream:
        writer = csv.DictWriter(stream,fieldnames=["method",*Quality.model_fields])
        writer.writeheader()
        for name,quality in (("A",baseline),("R",reference),*((name,r.quality) for name,r in results.items())):
            writer.writerow({"method":name,**Quality.model_validate(quality.model_dump()).model_dump()})
    with (root / "gates.csv").open("x",newline="") as stream:
        writer = csv.DictWriter(stream,fieldnames=["candidate",*Gates.model_fields,"qualifies"])
        writer.writeheader()
        writer.writerows({"candidate":name,**result.gates.model_dump(),"qualifies":result.qualifies} for name,result in results.items())
    with (root / "rotation-summary.csv").open("x",newline="") as stream:
        fields = ["method","angle","n","median","mean","p90","p95","max","average_absolute_level_median"]
        writer = csv.DictWriter(stream,fieldnames=fields)
        writer.writeheader()
        for name,result in (("A",arot),("R",rrot),*((n,r.rotation) for n,r in results.items())):
            for level in result.levels:
                d=level.signed_delta
                writer.writerow(dict(zip(fields,(name,level.angle,level.n,d.median,d.mean,d.p90,d.p95,d.maximum,result.average_absolute_level_median),strict=True)))


def timing_summary(root: Path) -> None:
    rows = []
    for fork in range(3):
        with (root / f"timing-{fork}.csv").open(newline="") as stream:
            records = [TimingRow.model_validate(row) for row in csv.DictReader(stream)]
        if len(records) != 63 or any(r.fork != fork for r in records):
            raise Phase2Error("Timing coverage mismatch")
        rows.extend(records)
    with (root / "timing-summary.csv").open("x",newline="") as stream:
        writer = csv.DictWriter(stream,fieldnames=["fork","stage","candidate","n","median_ms","mean_ms","p95_ms","min_ms","max_ms","mean_bytes"])
        writer.writeheader()
        keys = [("geometry","shared"),*((stage,name) for stage in ("tensor","total") for name in CANDIDATES)]
        for fork in (0,1,2,"pooled"):
            for stage,name in keys:
                group = [r for r in rows if r.stage == stage and r.candidate == name and (fork == "pooled" or r.fork == fork)]
                if len(group) != (27 if fork == "pooled" else 9):
                    raise Phase2Error("Timing stage/fork coverage mismatch")
                d = distribution(np.array([r.ns_per_image/1e6 for r in group]))
                allocations = [r.bytes_per_image for r in group if r.bytes_per_image >= 0]
                writer.writerow({"fork":fork,"stage":stage,"candidate":name,"n":len(group),"median_ms":d.median,"mean_ms":d.mean,
                                 "p95_ms":d.p95,"min_ms":d.minimum,"max_ms":d.maximum,"mean_bytes":float(np.mean(allocations)) if allocations else "unavailable"})
