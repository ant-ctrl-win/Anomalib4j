# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: python tools/comparison/test_campaign.py
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import common
import run
from unittest.mock import patch
import base64
import struct
import zlib


def test_all_category_plan_without_dataset() -> None:
    # Given canonical categories and a new output identity.
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory) / "campaign"
        # When planning, no training or dataset access occurs.
        plan = run.campaign_plan(target, common.CATEGORIES, ("anomalib4j",))
        # Then every category has the existing five phases in serial order.
        assert len(plan) == 75
        assert plan[0].category == "bottle"
        assert plan[-1].category == "zipper"
        assert [step.action for step in plan[:5]] == ["fit", "infer", "evaluate", "latency", "memory"]
        assert not target.exists()


def test_missing_dataset_cli_fails_before_creating_run() -> None:
    # Given an absent dataset and output directory.
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        target = root / "outputs"
        # When initialization crosses the CLI boundary.
        result = subprocess.run([sys.executable, str(Path(run.__file__)), "init",
            "--run-id", "missing", "--output-root", str(target),
            "--dataset", str(root / "absent"), "--category", "wood"],
            capture_output=True, text=True)
        # Then input failure is explicit and no partial run is created.
        assert result.returncode != 0
        assert "train/img" in result.stderr
        assert not (target / "missing").exists()


def test_campaign_stops_and_records_first_failure() -> None:
    with tempfile.TemporaryDirectory() as directory:
        target = Path(directory)
        plan = run.campaign_plan(target, common.CATEGORIES, ("anomalib4j",))
        with patch.object(run, "dispatch", side_effect=common.ContractError("fixture failure")) as dispatch:
            try:
                run.execute_campaign(target, plan, False)
            except common.ContractError:
                pass
            else:
                raise AssertionError("Campaign swallowed failure")
            assert dispatch.call_count == 1
        records = [json.loads(line) for line in (target / "campaign.jsonl").read_text().splitlines()]
        assert [row["status"] for row in records] == ["started", "failed"]


def test_m0_dispatch_does_not_require_competitor_environment() -> None:
    with tempfile.TemporaryDirectory() as directory:
        case = common.Case(Path(directory), "anomalib4j", "wood", False)
        with patch.object(run, "require_run"), patch.object(run, "require_model"), \
                patch.object(run, "adapter", return_value=["java"]), \
                patch.object(run, "checked_command") as command, \
                patch.object(run, "competitor_gate", side_effect=AssertionError("Competitor gate invoked")):
            run.dispatch(case, "infer", None)
            command.assert_called_once()


def test_all_category_initialization_with_synthetic_metadata() -> None:
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(b"\0\xff\xff\xff\xff")) + chunk(b"IEND", b"")
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        dataset = root / "synthetic"
        dataset.mkdir()
        tags = [{"id": i + 1, "name": name} for i, name in enumerate(common.CATEGORIES)]
        (dataset / "meta.json").write_text(json.dumps({"classes": [{"id": 100, "title": "custom_defect"}],
            "tags": tags + [{"id": 99, "name": "good"}]}), encoding="utf-8")
        for tag in tags:
            category = tag["name"]
            for role, defect, index in [("train", "good", n) for n in range(7, 10)] + [("test", "good", 12), ("test", "custom_defect", 14)]:
                name = f"{category}_{defect}_{index:03d}.png"
                (dataset / role / "img").mkdir(parents=True, exist_ok=True)
                (dataset / role / "ann").mkdir(parents=True, exist_ok=True)
                (dataset / role / "img" / name).write_bytes(png)
                annotation = {"size": {"width": 1, "height": 1},
                    "tags": [{"tagId": tag["id"], "name": category}] + ([{"tagId": 99, "name": "good"}] if defect == "good" else []),
                    "objects": [] if defect == "good" else [{"classId": 100, "classTitle": defect, "geometryType": "bitmap",
                        "bitmap": {"origin": [0, 0], "data": base64.b64encode(png).decode()}}]}
                (dataset / role / "ann" / (name + ".json")).write_text(json.dumps(annotation), encoding="utf-8")
        result = subprocess.run([sys.executable, str(Path(run.__file__)), "init", "--run-id", "synthetic",
            "--output-root", str(root / "output"), "--dataset", str(dataset), "--all-categories",
            "--method", "anomalib4j", "--seed", "42"], capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        initialized = root / "output/synthetic"
        for category in common.CATEGORIES:
            case = common.Case(initialized, "anomalib4j", category, False)
            common.require_run(case)
            assert len(case.names("anomalib4j-fit.txt")) == 1
            assert len(case.names("anomalib4j-calibration.txt")) == 2
            assert case.fixture().name == f"{category}_good_012.png"


if __name__ == "__main__":
    test_all_category_plan_without_dataset()
    test_missing_dataset_cli_fails_before_creating_run()
    test_campaign_stops_and_records_first_failure()
    test_m0_dispatch_does_not_require_competitor_environment()
    test_all_category_initialization_with_synthetic_metadata()
    print(json.dumps({"tests": 5, "status": "passed"}))
