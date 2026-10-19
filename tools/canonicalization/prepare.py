# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "scipy", "pillow", "pydantic>=2"]
# ///
# How to run: imported by tools/canonicalization/run.py
from pathlib import Path
import shutil

import numpy as np
from PIL import Image

from geometry import estimate, component, rotation, canonical_matrix, warp, angle_difference, GeometryError
from inputs import Split
from common import write_json


def prepare(root: Path, dataset: Path, split: Split) -> None:
    inputs = root / "inputs"
    inputs.mkdir()
    records, harmonic_orders, audit_errors = [], [], []
    failures = 0
    sources = {"fit": "train", "calibration": "train", "test": "test"}
    for role in ("fit", "calibration", "test"):
        names = getattr(split, role)
        (inputs / (role + ".txt")).write_text("\n".join(names)+"\n")
        destination = root / "images" / role
        destination.mkdir(parents=True)
        transforms = []
        for index, name in enumerate(names):
            with Image.open(dataset / sources[role] / "img" / name) as image:
                rgb = np.array(image.convert("RGB"))
            pose = estimate(rgb)
            if pose != estimate(rgb):
                raise GeometryError("Nondeterministic pose")
            record = {"role": role, "image": name, **pose.model_dump()}
            if pose.status == "success":
                matrix = canonical_matrix(pose)
                record["forward"] = matrix[:2].ravel().tolist()
                transforms.append(matrix[:2].ravel())
                canonical = warp(rgb, matrix)
                check = estimate(canonical)
                record["canonical_angle_residual"] = check.angle
                record["canonical_check_status"] = check.status
                Image.fromarray(canonical).save(destination / name)
            else:
                failures += 1
            records.append(record)
            if role == "fit":
                mask = component(rgb)
                y, x = np.nonzero(mask)
                theta = np.arctan2(y-y.mean(), x-x.mean())
                amplitudes = [float(abs(np.exp(k*1j*theta).mean())) for k in range(2, 13)]
                harmonic_orders.append({"image": name, "dominant": int(np.argmax(amplitudes))+2, "amplitudes_2_to_12": amplitudes})
                if index < 16:
                    for angle in (-10, -5, 5, 10):
                        observed = estimate(warp(rgb, rotation(angle, (349.5, 349.5))))
                        audit_errors.append(abs(angle_difference(observed.angle-pose.angle-angle)))
                        if observed.status != "success":
                            failures += 1
        write_json(root / ("geometry-" + role + ".json"), [r for r in records if r["role"] == role])
        np.save(inputs / (role + "-transforms.npy"), np.array(transforms))
        print(f"METAL_NUT - CANONICALIZE {role} - {len(names)} images - failures {failures}", flush=True)
        if role == "fit":
            write_json(root / "fit-symmetry.json", {"harmonics": harmonic_orders, "rotation_errors_degrees": audit_errors})
            if failures or any(r["dominant"] != 4 for r in harmonic_orders) or max(audit_errors) > 2 or np.quantile(audit_errors, .95) > 1:
                raise GeometryError("FIT-only orientation gate failed; stop before calibration/test")
    write_json(root / "geometry.json", records)
    if failures:
        raise GeometryError(f"Geometry failed for {failures} images; all statuses retained")
    normals = [n for n in split.test if n.startswith("metal_nut_good_")]
    (inputs / "baseline-normal.txt").write_text("\n".join(normals)+"\n")
    for role in ("baseline-normal", "robust-a", "robust-b"):
        (root / "images" / role).mkdir()
    perturbations = []
    perturbation_names = []
    for name in normals:
        shutil.copyfile(dataset / "test/img" / name, root / "images/baseline-normal" / name)
        with Image.open(dataset / "test/img" / name) as image:
            rgb = np.array(image.convert("RGB"))
        original = estimate(rgb)
        for angle in (-10, -5, 5, 10):
            perturbed = warp(rgb, rotation(angle, (349.5, 349.5)))
            pose = estimate(perturbed)
            identity = name.removesuffix(".png") + f"__rotation_{angle}.png"
            perturbation_names.append(identity)
            record = {"image": name, "perturbed_image": identity, "rotation": angle, **pose.model_dump(),
                      "orientation_error": angle_difference(pose.angle-original.angle-angle),
                      "equivalent_branch_crossing": abs(pose.angle-original.angle-angle) > 45}
            if pose.status == "success":
                matrix = canonical_matrix(pose)
                record["forward"] = matrix[:2].ravel().tolist()
                Image.fromarray(perturbed).save(root / "images/robust-a" / identity)
                Image.fromarray(warp(perturbed, matrix)).save(root / "images/robust-b" / identity)
            else:
                failures += 1
            perturbations.append(record)
    write_json(root / "rotation-geometry.json", perturbations)
    (inputs / "robust-a.txt").write_text("\n".join(perturbation_names)+"\n")
    shutil.copyfile(inputs / "robust-a.txt", inputs / "robust-b.txt")
    if failures:
        raise GeometryError(f"Rotation canonicalization failed {failures} times")
