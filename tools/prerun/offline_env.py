# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Offline / weight-integrity gate for the Phase 2 comparative runner.

The future runner must (a) point the framework caches at the already-verified
local snapshot, (b) force offline mode, (c) re-check every declared pretrained
weight SHA-256 before doing any work, and (d) fail loudly when a weight is
missing or the hash does not match. This module performs only those checks: it
never downloads, installs or updates anything.

Import from the runner::

    from offline_env import activate
    summary = activate()          # configure env + install network guard + verify
    ...

or run it directly as a gate::

    python tools/prerun/offline_env.py            # activate() + print JSON
    python tools/prerun/offline_env.py --self-check
"""

import hashlib
import json
import os
import socket
import sys
from pathlib import Path
from typing import Final

ROOT: Final = Path(__file__).resolve().parents[2]
CACHE: Final = ROOT / "target/prerun/weights"
WEIGHTS: Final = ROOT / "docs/benchmark/benchmark-prerun/weights.json"


class OfflineError(RuntimeError):
    """Raised when the offline contract cannot be satisfied."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def configure_environment(cache: Path = CACHE) -> dict[str, str]:
    """Force the verified local cache and offline variables, overriding any ambient value."""
    settings = {
        "HF_HOME": str(cache / "huggingface"),
        "TORCH_HOME": str(cache / "torch"),
        "HF_HUB_OFFLINE": "1",
        "TRANSFORMERS_OFFLINE": "1",
        "HF_HUB_DISABLE_TELEMETRY": "1",
    }
    os.environ.update(settings)
    return settings


def enforce_no_network() -> None:
    """Install a process-wide audit hook that aborts on any socket/DNS activity.

    This is a process-level guard, not a system firewall: it covers the Python
    process that installs it (and its in-process imports). A failure here means
    the interpreter cannot even block an outbound probe, so it is raised.
    """

    def reject(event: str, args: tuple) -> None:
        if event in {"socket.connect", "socket.getaddrinfo", "socket.sendto"}:
            raise PermissionError(event, "Phase 2 runner forbids network access")

    sys.addaudithook(reject)
    try:
        socket.getaddrinfo("example.com", 443)
    except PermissionError:
        return
    raise OfflineError("Network guard could not be verified: getaddrinfo was not blocked")


def verify_weights(weights_path: Path = WEIGHTS, root: Path = ROOT) -> list[dict]:
    """Verify size and SHA-256 of every declared weight file before the run."""
    if not weights_path.is_file():
        raise OfflineError(f"Missing declared weight manifest: {weights_path}")
    document = json.loads(weights_path.read_text(encoding="utf-8"))
    files = document.get("files") or []
    if not files:
        raise OfflineError(f"No weight files declared in {weights_path}")

    verified = []
    for entry in files:
        path = root / entry["path"]
        if not path.is_file():
            raise OfflineError(f"Missing pretrained weight: {path}")
        size = path.stat().st_size
        if size != entry["bytes"]:
            raise OfflineError(f"Size mismatch for {path}: {size} != {entry['bytes']}")
        digest = sha256_file(path)
        if digest.lower() != entry["sha256"].lower():
            raise OfflineError(f"SHA-256 mismatch for {path}: {digest} != {entry['sha256']}")
        verified.append({"path": entry["path"], "bytes": size, "sha256": digest})
    return verified


def activate(cache: Path = CACHE) -> dict:
    """Configure the environment, install the network guard and verify all weights."""
    settings = configure_environment(cache)
    enforce_no_network()
    weights = verify_weights(root=ROOT)
    return {"cache": str(cache), "settings": settings, "weights": len(weights), "network_blocked": True}


def _self_check() -> dict:
    import tempfile

    verified = verify_weights()
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)

        missing = directory / "missing.json"
        missing.write_text(json.dumps({"files": [
            {"path": "target/prerun/weights/does-not-exist.bin", "bytes": 1, "sha256": "00"},
        ]}), encoding="utf-8")
        _expect_offline_error(lambda: verify_weights(missing, root=ROOT), "missing weight")

        existing = WEIGHTS
        wrong = directory / "wrong.json"
        wrong.write_text(json.dumps({"files": [{
            "path": "docs/benchmark/benchmark-prerun/weights.json",
            "bytes": existing.stat().st_size,
            "sha256": "0" * 64,
        }]}), encoding="utf-8")
        _expect_offline_error(lambda: verify_weights(wrong), "wrong hash")

    return {"weights": len(verified), "negative_cases": 2}


def _expect_offline_error(action, label: str) -> None:
    try:
        action()
    except OfflineError:
        return
    raise OfflineError(f"Self-check failed: {label} was not rejected")


def main() -> None:
    if "--self-check" in sys.argv[1:]:
        print(json.dumps({"mode": "self-check", **_self_check()}))
        return
    print(json.dumps({"mode": "gate", **activate()}))


if __name__ == "__main__":
    main()
