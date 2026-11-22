from pathlib import Path

from windows_engine import desktop


def test_frozen_desktop_resolves_bundled_dns_daemon(monkeypatch, tmp_path):
    app_exe = tmp_path / "GuardianAdBlock.exe"
    app_exe.touch()
    monkeypatch.setattr(desktop.sys, "frozen", True, raising=False)
    monkeypatch.setattr(desktop.sys, "executable", str(app_exe))

    assert desktop.daemon_executable_path() == (
        tmp_path / "GuardianDnsProxy" / "GuardianDnsProxy.exe"
    )


def test_source_desktop_resolves_dist_dns_daemon(monkeypatch, tmp_path):
    module_path = tmp_path / "windows_engine" / "desktop.py"
    module_path.parent.mkdir()
    module_path.touch()
    monkeypatch.setattr(desktop.sys, "frozen", False, raising=False)
    monkeypatch.setattr(desktop, "__file__", str(module_path))

    assert desktop.daemon_executable_path() == (
        tmp_path / "dist" / "GuardianDnsProxy" / "GuardianDnsProxy.exe"
    )
