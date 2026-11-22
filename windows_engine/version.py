"""Single source of product version metadata for Windows builds and UI."""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

_VERSION_PATTERN = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?$")


def _version_file() -> Path:
    candidates = []
    if getattr(sys, "frozen", False):
        candidates.append(Path(sys.executable).resolve().parent / "VERSION")
    candidates.append(Path(__file__).resolve().parents[1] / "VERSION")
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return candidates[0]


def get_product_build_number() -> int:
    try:
        lines = _version_file().read_text(encoding="utf-8").splitlines()
        number = int(lines[1].strip())
    except (OSError, IndexError, ValueError):
        return 55
    if number < 1:
        raise ValueError("VERSION file contains an invalid build number.")
    return number


def get_product_version() -> str:
    override = os.environ.get("GUARDIAN_VERSION_NAME", "").strip()
    if override:
        if not _VERSION_PATTERN.fullmatch(override):
            raise ValueError("GUARDIAN_VERSION_NAME is not a valid semantic version.")
        return override
    try:
        version = _version_file().read_text(encoding="utf-8").splitlines()[0].strip()
    except (OSError, IndexError):
        return "0.7.42-dev"
    if not _VERSION_PATTERN.fullmatch(version):
        raise ValueError("VERSION file contains an invalid product version.")
    return version
