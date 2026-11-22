from pathlib import Path
from types import SimpleNamespace

import pytest

from windows_engine import desktop


def test_existing_live_dns_daemon_is_not_started_twice(monkeypatch, tmp_path):
    executable = tmp_path / "GuardianDnsProxy.exe"
    executable.write_bytes(b"test")
    run_calls = []
    clock = iter((0.0, 13.0, 14.0))

    monkeypatch.setattr(desktop, "proxy_ready", lambda: False)
    monkeypatch.setattr(desktop, "daemon_executable_path", lambda: executable)
    monkeypatch.setattr(desktop, "daemon_process_is_running", lambda _path: True)
    monkeypatch.setattr(desktop, "daemon_startup_diagnostics", lambda: "daemon diagnostic details")
    monkeypatch.setattr(desktop, "run_task", lambda *args: run_calls.append(args) or SimpleNamespace(returncode=0, stdout="", stderr=""))
    monkeypatch.setattr(desktop.time, "monotonic", lambda: next(clock))
    monkeypatch.setattr(desktop.time, "sleep", lambda _seconds: None)

    with pytest.raises(desktop.NetworkConfigError, match="already running") as error:
        desktop.ensure_daemon_running()

    assert "\\n\\n" in str(error.value)
    assert run_calls == []


def test_dead_dns_daemon_starts_scheduled_task(monkeypatch, tmp_path):
    executable = tmp_path / "GuardianDnsProxy.exe"
    executable.write_bytes(b"test")
    run_calls = []
    readiness = iter((False, True))

    monkeypatch.setattr(desktop, "proxy_ready", lambda: next(readiness))
    monkeypatch.setattr(desktop, "daemon_executable_path", lambda: executable)
    monkeypatch.setattr(desktop, "daemon_process_is_running", lambda _path: False)
    monkeypatch.setattr(desktop, "run_task", lambda *args: run_calls.append(args) or SimpleNamespace(returncode=0, stdout="", stderr=""))
    monkeypatch.setattr(desktop.time, "sleep", lambda _seconds: None)

    desktop.ensure_daemon_running()

    assert run_calls == [("/Run", "/TN", desktop.TASK_NAME)]
