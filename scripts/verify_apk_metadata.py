#!/usr/bin/env python3
"""Verify package identity and version metadata for sequential APK upgrade tests."""
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path


def main() -> int:
    args = sys.argv[1:]
    if not args or len(args) % 3:
        print("Expected APK path, versionCode, versionName triplets", file=sys.stderr)
        return 2

    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not android_home:
        print("ANDROID_HOME or ANDROID_SDK_ROOT must be configured", file=sys.stderr)
        return 2

    build_tools = Path(android_home) / "build-tools"
    versions = sorted(
        (p for p in build_tools.iterdir() if p.is_dir() and (p / "aapt").is_file()),
        key=lambda p: tuple(int(part) if part.isdigit() else part for part in re.split(r"[.-]", p.name)),
    )
    if not versions:
        print(f"No Android build-tools aapt found under {build_tools}", file=sys.stderr)
        return 2
    aapt = str(versions[-1] / "aapt")

    for offset in range(0, len(args), 3):
        apk, expected_code, expected_name = args[offset : offset + 3]
        if not Path(apk).is_file():
            print(f"APK does not exist: {apk}", file=sys.stderr)
            return 1
        output = subprocess.check_output([aapt, "dump", "badging", apk], text=True)
        package_line = next(
            (line for line in output.splitlines() if line.startswith("package:")), ""
        )
        print(f"APK metadata: {package_line or '(package metadata missing)'}")
        # Match the three primary fields immediately after "package:". Do not
        # use a global name= search: aapt appends platformBuildVersionName='15'
        # on the same line, which otherwise overwrites the real package name.
        match = re.match(
            r"^package:\s+name='([^']*)'\s+versionCode='([^']*)'\s+versionName='([^']*)'",
            package_line,
        )
        fields = (
            dict(zip(("name", "versionCode", "versionName"), match.groups()))
            if match
            else {}
        )
        expected = {
            "name": "com.guardian.adblock",
            "versionCode": expected_code,
            "versionName": expected_name,
        }
        if fields != expected:
            print(
                f"APK metadata mismatch for {apk}: expected {expected}, got {fields}",
                file=sys.stderr,
            )
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
