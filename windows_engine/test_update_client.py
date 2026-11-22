import hashlib
import json

import pytest

from windows_engine.update_client import (
    MANIFEST_NAME,
    WindowsUpdateError,
    check_for_update,
    download_update,
)


def make_release(build=56, version="0.7.43-dev", installer=b"guardian-installer"):
    tag = f"guardian-windows-dev-v{build}"
    filename = f"Guardian-AdBlock-Windows-Setup-{version}-b{build}.exe"
    url = f"https://github.com/justinhblee-prog/guardian/releases/download/{tag}/{filename}"
    manifest = {
        "schemaVersion": 1,
        "product": "Guardian AdBlock",
        "platform": "windows-x64",
        "versionName": version,
        "buildNumber": build,
        "tagName": tag,
        "sourceCommit": "a" * 40,
        "installerName": filename,
        "installerUrl": url,
        "sha256": hashlib.sha256(installer).hexdigest(),
    }
    release = {
        "tag_name": tag,
        "draft": False,
        "prerelease": True,
        "html_url": f"https://github.com/justinhblee-prog/guardian/releases/tag/{tag}",
        "assets": [{"name": MANIFEST_NAME, "browser_download_url": f"https://github.com/justinhblee-prog/guardian/releases/download/{tag}/{MANIFEST_NAME}"}],
    }
    return release, json.dumps(manifest).encode(), installer


def test_check_for_update_discovers_only_newer_official_windows_release():
    release, manifest, installer = make_release()
    def fetcher(url, limit):
        if url.endswith("/releases?per_page=30"):
            return json.dumps([release]).encode()
        if url.endswith(MANIFEST_NAME):
            return manifest
        if url.endswith(".exe"):
            return installer
        raise AssertionError(url)
    update = check_for_update(55, fetcher)
    assert update is not None
    assert update.version_name == "0.7.43-dev"
    assert update.build_number == 56


def test_check_for_update_returns_none_when_current_is_latest():
    release, manifest, _ = make_release()
    def fetcher(url, limit):
        if url.endswith("/releases?per_page=30"):
            return json.dumps([release]).encode()
        raise AssertionError("A current build must not download a manifest.")
    assert check_for_update(56, fetcher) is None


def test_manifest_rejects_nonofficial_installer_url():
    release, manifest, _ = make_release()
    data = json.loads(manifest)
    data["installerUrl"] = "https://attacker.example/installer.exe"
    invalid = json.dumps(data).encode()
    def fetcher(url, limit):
        if url.endswith("/releases?per_page=30"):
            return json.dumps([release]).encode()
        return invalid
    with pytest.raises(WindowsUpdateError):
        check_for_update(55, fetcher)


def test_download_update_checks_sha256_before_writing(tmp_path):
    release, manifest, installer = make_release()
    def fetcher(url, limit):
        if url.endswith("/releases?per_page=30"):
            return json.dumps([release]).encode()
        if url.endswith(MANIFEST_NAME):
            return manifest
        return installer
    update = check_for_update(55, fetcher)
    path = download_update(update, tmp_path, fetcher)
    assert path.read_bytes() == installer
    assert path.name == update.installer_name


def test_download_update_rejects_checksum_mismatch(tmp_path):
    release, manifest, installer = make_release()
    def fetcher(url, limit):
        if url.endswith("/releases?per_page=30"):
            return json.dumps([release]).encode()
        if url.endswith(MANIFEST_NAME):
            return manifest
        return installer + b"tampered"
    update = check_for_update(55, fetcher)
    with pytest.raises(WindowsUpdateError, match="SHA-256"):
        download_update(update, tmp_path, fetcher)
    assert list(tmp_path.iterdir()) == []
