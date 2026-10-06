# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: frozen .venv Python tools/comparison/test_runner.py (synthetic tests only).
import json
import tempfile
from pathlib import Path

from common import Case, ContractError, PRERUN, validate_manifests
from memory import summarize_memory
from results import summarize_run
from timing import Schedule, sample, summarize


def test_percentiles_and_timer_consumption() -> None:
    summary = summarize([1., 2., 3., 4.])
    assert summary["p50_us"] == 2.5
    assert abs(summary["p95_us"] - 3.85) < 1e-12
    assert summary["mean_us"] == 2.5
    called = []
    def operation() -> float:
        called.append(1)
        return 2.
    rows = sample(operation, Schedule(1, 2, .001))
    assert {iteration for iteration, elapsed in rows} == {0, 1}
    assert all(elapsed >= 0 for iteration, elapsed in rows)
    assert any(elapsed > 0 for iteration, elapsed in rows)
    assert len(called) > len(rows)


def test_frozen_split_cardinality_and_tamper_gate() -> None:
    import shutil
    with tempfile.TemporaryDirectory() as directory:
        run = Path(directory)
        shutil.copytree(PRERUN / "data", run / "data")
        validate_manifests(run)
        assert len(Case(run, "padim", "metal_nut", False).names("competitor-fit.txt")) == 220
        assert len(Case(run, "anomalib4j", "metal_nut", False).names("anomalib4j-fit.txt")) == 176
        path = run / "data/metal_nut/anomalib4j-fit.txt"
        path.write_text("metal_nut_good_000.png\n", encoding="utf-8")
        try:
            validate_manifests(run)
        except ContractError:
            return
        raise AssertionError("Manifest tampering was accepted")


def test_memory_missing_is_not_zero() -> None:
    import csv
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory)
        with (path / "phases.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["timestamp_utc", "pid", "phase"])
            writer.writerow(["2026-10-03T13:45:05+00:00", "100", "initialization"])
            writer.writerow(["2026-10-03T13:45:10+00:00", "100", "fitting"])
            writer.writerow(["2026-10-03T13:45:20+00:00", "100", "steady"])
        with (path / "memory.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["timestamp_utc", "sample_id", "elapsed_ms", "pid", "start_time_utc", "root_pid",
                             "working_set_bytes", "private_bytes", "lifetime_peak_working_set_bytes", "status", "reason"])
            writer.writerow(["2026-10-03T13:45:02+00:00", "1", "1.0", "100", "2026-10-03T13:44:00+00:00", "100", "100", "200", "150", "ok", ""])
            writer.writerow(["2026-10-03T13:45:12+00:00", "2", "2.0", "100", "2026-10-03T13:44:00+00:00", "100", "300", "400", "350", "ok", ""])
            writer.writerow(["2026-10-03T13:45:22+00:00", "3", "3.0", "100", "2026-10-03T13:44:00+00:00", "100", "500", "600", "550", "ok", ""])
            writer.writerow(["2026-10-03T13:45:23+00:00", "4", "4.0", "200", "", "100", "", "", "", "missing",
                             "process_not_found; process_terminated_between_samples"])
        summarize_memory(path)
        summary = json.loads((path / "memory-summary.json").read_text(encoding="utf-8"))
        assert summary["observed_process_rows"] == 4
        assert summary["complete_process_rows"] == 3
        assert summary["missing_process_rows"] == 1
        assert summary["incomplete_process_rows"] == 1
        assert summary["status_counts"] == {"ok": 3, "missing": 1}
        assert summary["missing_reasons"]["process_not_found"] == 1
        assert summary["missing_reasons"]["process_terminated_between_samples"] == 1
        assert summary["missing_counters"]["working_set_bytes"] == 1
        assert summary["baseline"]["max_working_set_bytes"] == 100
        assert summary["fitting"]["max_working_set_bytes"] == 300
        assert summary["steady"]["max_working_set_bytes"] == 500
        assert summary["baseline"]["samples"] == 1
        assert summary["fitting"]["samples"] == 1
        assert summary["steady"]["samples"] == 1


def test_summarize_run_null_and_macro() -> None:
    import csv
    with tempfile.TemporaryDirectory() as directory:
        run = Path(directory)
        (run / "manifest.json").write_text("{}", encoding="utf-8")
        fixtures = [("anomalib4j", "bottle", {"status": "verified", "n_good": 83, "n_anomaly": 33,
                                              "image_auroc": 1.0, "pixel_auroc": 0.9, "aupro030": 0.8,
                                              "regions": 5, "foreground_pixels": 10, "background_pixels": 20}),
                    ("anomalib4j", "metal_nut", {"status": "verified", "n_good": 100, "n_anomaly": 15,
                                                 "image_auroc": 0.8, "pixel_auroc": 0.7, "aupro030": 0.6,
                                                 "regions": 3, "foreground_pixels": 11, "background_pixels": 21})]
        for method, category, payload in fixtures:
            target = run / method / category
            target.mkdir(parents=True)
            (target / "metrics-unified.json").write_text(json.dumps(payload), encoding="utf-8")
        summarize_run(run)
        rows = list(csv.DictReader((run / "summary-by-category.csv").open(encoding="utf-8")))
        assert len(rows) == 6
        by_key = {(row["method"], row["category"]): row for row in rows}
        assert by_key[("anomalib4j", "bottle")]["image_auroc"] == "1.0"
        assert by_key[("padim", "bottle")]["status"] == ""
        assert by_key[("padim", "bottle")]["reason"] == "metrics-unified.json missing; evaluation not run"
        macro = list(csv.DictReader((run / "macro-summary.csv").open(encoding="utf-8")))
        anomalib4j = next(row for row in macro if row["method"] == "anomalib4j")
        assert anomalib4j["verified_categories"] == "2"
        assert abs(float(anomalib4j["image_auroc_macro"]) - 0.9) < 1e-12
        padim = next(row for row in macro if row["method"] == "padim")
        assert padim["image_auroc_macro"] == ""
        assert padim["reason"] == "no verified metrics for this method"
        result = (run / "RESULT.md").read_text(encoding="utf-8")
        assert "null" in result and "metrics-unified.json missing" in result


if __name__ == "__main__":
    test_percentiles_and_timer_consumption()
    test_frozen_split_cardinality_and_tamper_gate()
    test_memory_missing_is_not_zero()
    test_summarize_run_null_and_macro()
    print(json.dumps({"tests": 4, "status": "passed"}))
