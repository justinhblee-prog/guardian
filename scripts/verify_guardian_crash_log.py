#!/usr/bin/env python3
"""Fail Android emulator smoke tests only for crashes of the Guardian package."""

from __future__ import annotations

import re
import sys
from pathlib import Path

GUARDIAN_PROCESS = re.compile(r"\bProcess:\s+com\.guardian\.adblock(?:\s|,|$)")
GUARDIAN_DIED = re.compile(r"\bcom\.guardian\.adblock\b.*\bhas died\b", re.IGNORECASE)


def guardian_process_crashed(log_text: str) -> bool:
    lines = log_text.splitlines()
    for index, line in enumerate(lines):
        if "FATAL EXCEPTION" in line:
            # AndroidRuntime prints the owning package immediately after the
            # fatal header. Restrict the match to this exception block so a
            # later, unrelated package crash cannot taint Guardian's result.
            exception_block = lines[index : min(len(lines), index + 6)]
            if any(GUARDIAN_PROCESS.search(candidate) for candidate in exception_block):
                return True
        if GUARDIAN_DIED.search(line):
            return True
    return False


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: verify_guardian_crash_log.py <logcat-file>", file=sys.stderr)
        return 2
    log_path = Path(sys.argv[1])
    try:
        log_text = log_path.read_text(encoding="utf-8", errors="replace")
    except OSError as error:
        print(f"Could not read Android logcat file: {error}", file=sys.stderr)
        return 2
    if guardian_process_crashed(log_text):
        print("Guardian Android process crash detected.")
        return 1
    print("No Guardian Android process crash detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
