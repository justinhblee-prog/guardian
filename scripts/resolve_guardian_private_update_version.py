#!/usr/bin/env python3
"""Resolve a monotonic version for Guardian's private Beta/Dev update channels."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

CHANNEL_VERSION = {
    "dev": re.compile(r"^([0-9]+)\.([0-9]+)\.([0-9]+)-dev$"),
    "beta": re.compile(r"^([0-9]+)\.([0-9]+)\.([0-9]+)-beta$")
}


def bump_patch(version: str, channel: str) -> str:
    match = CHANNEL_VERSION[channel].fullmatch(version)
    if not match:
        raise ValueError(f"Invalid {channel} versionName: {version}")
    major, minor, patch = map(int, match.groups())
    return f"{major}.{minor}.{patch + 1}-{channel}"


def resolve_next_version(
    updates: list[dict[str, Any]], channel: str, baseline_version: str, baseline_code: int
) -> dict[str, Any]:
    if channel not in CHANNEL_VERSION:
        raise ValueError("Update channel must be beta or dev.")
    if not CHANNEL_VERSION[channel].fullmatch(baseline_version) or baseline_code < 1:
        raise ValueError("Invalid Guardian channel baseline version.")
    latest_code = baseline_code
    latest_channel_code = baseline_code
    latest_channel_name = baseline_version
    for update in updates:
        if not isinstance(update, dict):
            raise ValueError("Private update list contains an invalid item.")
        code = update.get("version_code")
        if not isinstance(code, int) or code < 1:
            raise ValueError("Private update list contains an invalid versionCode.")
        latest_code = max(latest_code, code)
        if update.get("channel") != channel:
            continue
        name = update.get("version_name")
        if not isinstance(name, str) or not CHANNEL_VERSION[channel].fullmatch(name):
            raise ValueError(f"Private {channel} update has an invalid versionName.")
        if code > latest_channel_code:
            latest_channel_code = code
            latest_channel_name = name
    next_code = latest_code + 1
    if next_code >= 2_000_000:
        raise ValueError("Guardian private update versionCode reached the reserved production range.")
    return {
        "channel": channel,
        "version_name": bump_patch(latest_channel_name, channel),
        "version_code": next_code,
        "tag": f"guardian-{channel}-v{next_code}",
        "reused": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--updates-json", required=True)
    parser.add_argument("--channel", choices=("beta", "dev"), required=True)
    parser.add_argument("--baseline-version", required=True)
    parser.add_argument("--baseline-code", type=int, required=True)
    parser.add_argument("--github-env", required=True)
    parser.add_argument("--github-output", required=True)
    args = parser.parse_args()
    payload = json.loads(Path(args.updates_json).read_text(encoding="utf-8"))
    updates = payload.get("updates") if isinstance(payload, dict) else None
    if not isinstance(updates, list):
        raise ValueError("Guardian private update API response must contain an updates array.")
    resolved = resolve_next_version(updates, args.channel, args.baseline_version, args.baseline_code)
    values = {
        "GUARDIAN_VERSION_NAME": resolved["version_name"],
        "GUARDIAN_VERSION_CODE": str(resolved["version_code"]),
        "GUARDIAN_RELEASE_TAG": resolved["tag"],
        "GUARDIAN_UPDATE_CHANNEL": args.channel,
    }
    with Path(args.github_env).open("a", encoding="utf-8") as stream:
        for key, value in values.items():
            stream.write(f"{key}={value}\n")
    with Path(args.github_output).open("a", encoding="utf-8") as stream:
        for key, value in values.items():
            stream.write(f"{key.lower()}={value}\n")
        stream.write("reused=false\n")
    print(json.dumps(resolved, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
