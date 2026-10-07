# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Capture the workspace identity for a Phase 2 run without touching the repository.

Records what exactly produced a result set so it can be reconstructed and audited:
HEAD, branch, porcelain status, the uncommitted diff, the untracked file list and
the SHA-256 of the files the benchmark contract treats as critical.

Run by the future runner right before the final metrics are produced::

    python tools/prerun/workspace_identity.py --output target/comparison/<run-id>/workspace

Rules: this helper only reads the repository. It never commits, never resets,
never cleans and never edits existing files. It refuses to overwrite an output
directory that already contains files.
"""

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Final

ROOT: Final = Path(__file__).resolve().parents[2]

CRITICAL_FILES: Final = (
    "AGENTS.md",
    "docs/architecture/ONNX_MODEL_CONTRACT.md",
    "docs/benchmark/BENCHMARK_CONTRACT.md",
    "docs/PROJECT_STATE.md",
    "docs/benchmark/benchmark-prerun/source.json",
    "docs/benchmark/benchmark-prerun/configurations.json",
    "docs/benchmark/benchmark-prerun/weights.json",
    "docs/benchmark/benchmark-prerun/data-sha256.json",
    "docs/benchmark/benchmark-prerun/resize-equivalence.json",
    "docs/benchmark/benchmark-prerun/map-format/fixtures.json",
    "pom.xml",
    "src/main/java/io/github/antctrlwin/anomalib4j/onnx/ImageNetPreprocessor.java",
    "src/main/resources/models/mobilenetv4_spatial_14x14.onnx",
    "src/main/resources/models/mobilenetv4_spatial_28x28.onnx",
    "src/test/java/io/github/antctrlwin/anomalib4j/evaluation/EvaluationLabels.java",
    "src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java",
    "src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMetrics.java",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed ({result.returncode}): {result.stderr.strip()}")
    return result.stdout


def hashes(files: tuple[str, ...]) -> list[dict]:
    entries = []
    for relative in files:
        path = ROOT / relative
        if path.is_file():
            entries.append({"path": relative, "bytes": path.stat().st_size, "sha256": sha256_file(path)})
        else:
            entries.append({"path": relative, "missing": True})
    return entries


def capture(output: Path) -> dict:
    if output.exists() and any(output.iterdir()):
        raise FileExistsError(f"Refusing to overwrite non-empty output directory: {output}")
    output.mkdir(parents=True, exist_ok=True)

    status = git("status", "--porcelain=v1")
    diff = git("diff", "--no-ext-diff", "HEAD")
    untracked = git("ls-files", "--others", "--exclude-standard")

    (output / "git-status.txt").write_text(status, encoding="utf-8")
    (output / "git-diff.patch").write_text(diff, encoding="utf-8")
    (output / "untracked.txt").write_text(untracked, encoding="utf-8")

    document = {
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "git_version": git("--version").strip(),
        "head": git("rev-parse", "HEAD").strip(),
        "branch": git("rev-parse", "--abbrev-ref", "HEAD").strip(),
        "describe": git("describe", "--tags", "--always", "--dirty").strip(),
        "status_porcelain": status.splitlines(),
        "untracked_count": len([line for line in untracked.splitlines() if line.strip()]),
        "modified_count": len([line for line in status.splitlines() if line.strip()]),
        "critical_files": hashes(CRITICAL_FILES),
        "artifacts": {
            "status": "git-status.txt",
            "diff": "git-diff.patch",
            "diff_base": "HEAD",
            "untracked": "untracked.txt",
        },
    }
    (output / "workspace.json").write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    return document


def main() -> None:
    parser = argparse.ArgumentParser(description="Capture read-only workspace identity")
    parser.add_argument("--output", required=True, help="output directory (must be empty or absent)")
    arguments = parser.parse_args()
    document = capture(Path(arguments.output))
    print(json.dumps({
        "output": arguments.output,
        "head": document["head"],
        "branch": document["branch"],
        "modified": document["modified_count"],
        "untracked": document["untracked_count"],
        "critical_missing": [entry["path"] for entry in document["critical_files"] if entry.get("missing")],
    }))


if __name__ == "__main__":
    sys.exit(main())
