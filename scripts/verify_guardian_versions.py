#!/usr/bin/env python3
"""Fail CI when the shipped Guardian app versions drift across platforms."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def extract(pattern: str, content: str, label: str) -> str:
    match = re.search(pattern, content, re.MULTILINE)
    if not match:
        raise SystemExit(f"Version gate: could not read {label}.")
    return match.group(1)


def verify() -> str:
    gradle = read("android/app/build.gradle.kts")
    desktop = read("windows_engine/desktop.py")
    installer = read("installer/guardian.iss")
    android_name = extract(r'getOrElse\("([0-9]+\.[0-9]+\.[0-9]+)"\)', gradle, "Android versionName fallback")
    android_code = extract(r'\}\.getOrElse\(([0-9]+)\)', gradle, "Android versionCode fallback")
    windows_name = extract(r'app_version="([0-9]+\.[0-9]+\.[0-9]+)"', desktop, "Windows license client app_version")
    installer_name = extract(r'^#define MyAppVersion "([0-9]+\.[0-9]+\.[0-9]+)"$', installer, "Windows installer version")
    values = {"Android": android_name, "Windows license client": windows_name, "Windows installer": installer_name}
    if len(set(values.values())) != 1:
        details = ", ".join(f"{name}={value}" for name, value in values.items())
        raise SystemExit(f"Version gate: platform versions diverged: {details}")
    if int(android_code) < 1 or int(android_code) >= 2000000:
        raise SystemExit(f"Version gate: Android development versionCode is outside its allowed range: {android_code}")
    changelog = read("CHANGELOG.md")
    if "2026-11-23 — 개발 버전 기준값 단일화 및 회귀 방지" not in changelog:
        raise SystemExit("Version gate: current change set is missing its changelog entry.")
    return f"Guardian platform versions aligned: {android_name} (Android versionCode fallback {android_code})."


if __name__ == "__main__":
    print(verify())
