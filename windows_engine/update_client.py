"""Verified Windows development-update discovery and download."""
from __future__ import annotations

import hashlib
import json
import re
import tempfile
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

REPOSITORY = "justinhblee-prog/guardian"
RELEASES_URL = f"https://api.github.com/repos/{REPOSITORY}/releases?per_page=30"
MANIFEST_NAME = "guardian-windows-update.json"
MAX_MANIFEST_BYTES = 64 * 1024
MAX_INSTALLER_BYTES = 180 * 1024 * 1024
TAG_PATTERN = re.compile(r"^guardian-windows-dev-v([1-9][0-9]*)$")
VERSION_PATTERN = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+-dev$")


class WindowsUpdateError(RuntimeError):
    pass


@dataclass(frozen=True)
class WindowsUpdate:
    version_name: str
    build_number: int
    tag_name: str
    installer_url: str
    installer_name: str
    sha256: str
    release_url: str


def _https_bytes(url: str, limit: int) -> bytes:
    parsed = urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise WindowsUpdateError("Guardian updates require HTTPS URLs without embedded credentials.")
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json, application/octet-stream", "User-Agent": "GuardianWindowsUpdater/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            if response.status != 200:
                raise WindowsUpdateError(f"Update server returned HTTP {response.status}.")
            data = response.read(limit + 1)
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise WindowsUpdateError(f"Could not contact the Guardian update service: {exc}") from exc
    if len(data) > limit:
        raise WindowsUpdateError("The update file exceeds the configured size limit.")
    return data


def _validate_manifest(payload: bytes, tag: str, release_url: str) -> WindowsUpdate:
    try:
        data = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise WindowsUpdateError("Windows update manifest is not valid UTF-8 JSON.") from exc
    required = {
        "schemaVersion", "product", "platform", "versionName", "buildNumber",
        "tagName", "installerName", "installerUrl", "sha256",
    }
    if not isinstance(data, dict) or set(data) != required:
        raise WindowsUpdateError("Windows update manifest fields do not match the supported schema.")
    match = TAG_PATTERN.fullmatch(tag)
    if not match or data["tagName"] != tag:
        raise WindowsUpdateError("Windows update tag is invalid or inconsistent.")
    try:
        build = int(data["buildNumber"])
    except (TypeError, ValueError) as exc:
        raise WindowsUpdateError("Windows update build number is invalid.") from exc
    if (
        data["schemaVersion"] != 1
        or data["product"] != "Guardian AdBlock"
        or data["platform"] != "windows-x64"
        or not isinstance(data["versionName"], str)
        or not VERSION_PATTERN.fullmatch(data["versionName"])
        or build != int(match.group(1))
        or not isinstance(data["installerName"], str)
        or not re.fullmatch(r"Guardian-AdBlock-Windows-Setup-[A-Za-z0-9.-]+\.exe", data["installerName"])
        or not isinstance(data["sha256"], str)
        or not re.fullmatch(r"[0-9a-f]{64}", data["sha256"])
    ):
        raise WindowsUpdateError("Windows update manifest contains invalid product metadata.")
    expected_url = f"https://github.com/{REPOSITORY}/releases/download/{tag}/{data['installerName']}"
    if data["installerUrl"] != expected_url:
        raise WindowsUpdateError("Windows update installer URL does not match the official Guardian release.")
    return WindowsUpdate(
        version_name=data["versionName"],
        build_number=build,
        tag_name=tag,
        installer_url=expected_url,
        installer_name=data["installerName"],
        sha256=data["sha256"],
        release_url=release_url,
    )


def check_for_update(current_build: int, fetcher=_https_bytes) -> WindowsUpdate | None:
    if current_build < 1:
        raise WindowsUpdateError("Installed Guardian build number is invalid.")
    try:
        releases = json.loads(fetcher(RELEASES_URL, MAX_MANIFEST_BYTES * 30).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise WindowsUpdateError("Guardian release list is not valid JSON.") from exc
    if not isinstance(releases, list):
        raise WindowsUpdateError("Guardian release list has an unsupported format.")
    candidates = []
    for release in releases:
        if not isinstance(release, dict) or release.get("draft") or not release.get("prerelease"):
            continue
        tag = str(release.get("tag_name", ""))
        match = TAG_PATTERN.fullmatch(tag)
        if not match or int(match.group(1)) <= current_build:
            continue
        assets = release.get("assets")
        if not isinstance(assets, list):
            continue
        manifest_asset = next(
            (asset for asset in assets if isinstance(asset, dict) and asset.get("name") == MANIFEST_NAME),
            None,
        )
        if not manifest_asset:
            continue
        manifest_url = manifest_asset.get("browser_download_url")
        if not isinstance(manifest_url, str):
            continue
        manifest = _validate_manifest(
            fetcher(manifest_url, MAX_MANIFEST_BYTES),
            tag,
            str(release.get("html_url") or f"https://github.com/{REPOSITORY}/releases/tag/{tag}"),
        )
        candidates.append(manifest)
    if not candidates:
        return None
    return max(candidates, key=lambda item: item.build_number)


def download_update(update: WindowsUpdate, destination: Path | None = None, fetcher=_https_bytes) -> Path:
    data = fetcher(update.installer_url, MAX_INSTALLER_BYTES)
    digest = hashlib.sha256(data).hexdigest()
    if digest != update.sha256:
        raise WindowsUpdateError("Downloaded installer SHA-256 does not match the official update manifest.")
    target_dir = Path(destination) if destination else Path.home() / "Downloads" / "Guardian AdBlock Updates"
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / update.installer_name
    fd, temp_name = tempfile.mkstemp(prefix=target.name + ".", suffix=".tmp", dir=target_dir)
    try:
        with open(fd, "wb", closefd=True) as stream:
            stream.write(data)
            stream.flush()
        Path(temp_name).replace(target)
    finally:
        Path(temp_name).unlink(missing_ok=True)
    return target
