# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
# How to run: imported by campaign runners.
from contextlib import contextmanager
from pathlib import Path
from time import perf_counter
from collections.abc import Iterator
import json
import subprocess
import sys


class ProgressError(RuntimeError):
    """The subprocess did not provide the requested output stream."""


@contextmanager
def phase(log: Path, category_progress: str, name: str) -> Iterator[None]:
    started = perf_counter()

    def emit(state: str) -> None:
        seconds = int(perf_counter() - started)
        line = f"{category_progress} - {name} - {state} - {seconds // 3600:02}:{seconds // 60 % 60:02}:{seconds % 60:02}"
        print(line, flush=True)
        with log.open("a", encoding="utf-8") as stream:
            stream.write(line + "\n")

    emit("started")
    try:
        yield
    finally:
        emit("complete" if sys.exc_info()[0] is None else "failed")


def stream_command(command: list[str], log: Path, progress_log: Path) -> None:
    with (log.parent / "run.log").open("a", encoding="utf-8") as history:
        history.write(json.dumps({"command": command, "log": log.name}) + "\n")
    with log.open("x", encoding="utf-8") as stream, progress_log.open("a", encoding="utf-8") as progress:
        stream.write(json.dumps(command) + "\n")
        with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True) as process:
            if process.stdout is None:
                raise ProgressError("Subprocess output pipe unavailable")
            for line in process.stdout:
                print(line, end="", flush=True)
                stream.write(line)
                progress.write(line)
                stream.flush()
                progress.flush()
            status = process.wait()
            if status:
                raise subprocess.CalledProcessError(status, command)
