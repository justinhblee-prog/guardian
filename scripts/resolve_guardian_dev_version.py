#!/usr/bin/env python3
"""Resolve a monotonic Guardian development release version from published releases."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

TAG_PATTERN = re.compile(r"^guardian-dev-v([0-9]+)$")
VERSION_PATTERN = re.compile(r"([0-9]+)\.([0-9]+)\.([0-9]+)-dev")


def bump_patch(version: str) -> str:
    match = VERSION_PATTERN.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid development versionName: {version}")
    major, minor, patch = map(int, match.groups())
    patch += 1
    return f"{major}.{minor}.{patch}-dev"


def resolve_next_version(
    releases: list[dict[str, Any]], commit: str, baseline_version: str, baseline_code: int
) -> dict[str, Any]:
    if not VERSION_PATTERN.fullmatch(baseline_version) or baseline_code < 1:
        raise ValueError("Invalid Guardian baseline version.")
    latest_code = baseline_code
    latest_version = tuple(map(int, VERSION_PATTERN.fullmatch(baseline_version).groups()))
    latest_name = baseline_version
    same_commit: list[tuple[int, str, str]] = []
    names_by_code: dict[int, str] = {}

    for release in releases:
        if release.get("draft") or not release.get("prerelease"):
            continue
        tag_match = TAG_PATTERN.fullmatch(str(release.get("tag_name", "")))
        if not tag_match:
            continue
        code = int(tag_match.group(1))
        title = str(release.get("name") or "")
        version_match = VERSION_PATTERN.search(title)
        if not version_match:
            continue
        version_name = version_match.group(0)
        version_tuple = tuple(map(int, version_match.groups()))
        previous_name = names_by_code.setdefault(code, version_name)
        if previous_name != version_name:
            raise ValueError(f"Conflicting version names for release versionCode {code}.")
        if code < baseline_code:
            continue
        body = str(release.get("body") or "")
        source_match = re.search(r"(?m)^Guardian-Source-Commit:\s*([0-9a-f]{40})\s*$", body)
        if source_match and source_match.group(1).lower() == commit.lower():
            same_commit.append((code, version_name, str(release.get("tag_name"))))
        latest_code = max(latest_code, code)
        if version_tuple > latest_version:
            latest_version, latest_name = version_tuple, version_name

    if same_commit:
        code, name, tag = max(same_commit)
        if code != latest_code:
            raise ValueError("This source commit was already published as an older release; refusing a version rollback.")
        return {"version_name": name, "version_code": code, "tag": tag, "reused": True}

    next_code = latest_code + 1
    if next_code >= 2000000:
        raise ValueError("Development versionCode reached the reserved production range.")
    return {
        "version_name": bump_patch(latest_name),
        "version_code": next_code,
        "tag": f"guardian-dev-v{next_code}",
        "reused": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--releases-json", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--baseline-version", default="0.7.59-dev")
    parser.add_argument("--baseline-code", type=int, default=72)
    parser.add_argument("--github-env", required=True)
    parser.add_argument("--github-output", required=True)
    args = parser.parse_args()
    releases = json.loads(Path(args.releases_json).read_text(encoding="utf-8"))
    if not isinstance(releases, list):
        raise ValueError("GitHub releases response must be a JSON array.")
    resolved = resolve_next_version(releases, args.commit, args.baseline_version, args.baseline_code)
    values = {
        "GUARDIAN_VERSION_NAME": resolved["version_name"],
        "GUARDIAN_VERSION_CODE": str(resolved["version_code"]),
        "GUARDIAN_RELEASE_TAG": resolved["tag"],
    }
    with Path(args.github_env).open("a", encoding="utf-8") as stream:
        for key, value in values.items():
            stream.write(f"{key}={value}\n")
    with Path(args.github_output).open("a", encoding="utf-8") as stream:
        stream.write(f"version_name={resolved['version_name']}\n")
        stream.write(f"version_code={resolved['version_code']}\n")
        stream.write(f"release_tag={resolved['tag']}\n")
        stream.write(f"reused={'true' if resolved['reused'] else 'false'}\n")
    print(json.dumps(resolved, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
