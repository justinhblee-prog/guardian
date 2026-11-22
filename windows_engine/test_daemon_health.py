import json
import os
from pathlib import Path

from windows_engine.daemon_health import (
    daemon_health_is_fresh,
    daemon_process_is_running,
    process_is_running,
    read_daemon_pid,
    remove_daemon_health,
    write_daemon_health,
)


def test_health_marker_is_atomic_and_fresh_for_live_process(tmp_path: Path):
    path = tmp_path / "daemon-health.json"
    write_daemon_health(path, pid=1234, now=100.0)
    assert json.loads(path.read_text(encoding="utf-8")) == {"pid": 1234, "heartbeatAt": 100.0}
    assert daemon_health_is_fresh(path, now=103.0, process_check=lambda pid: pid == 1234)


def test_stale_or_future_heartbeat_is_not_ready(tmp_path: Path):
    path = tmp_path / "daemon-health.json"
    write_daemon_health(path, pid=1234, now=100.0)
    check = lambda pid: True
    assert not daemon_health_is_fresh(path, now=111.0, process_check=check)
    assert not daemon_health_is_fresh(path, now=90.0, process_check=check)


def test_dead_process_is_not_ready_even_with_fresh_heartbeat(tmp_path: Path):
    path = tmp_path / "daemon-health.json"
    write_daemon_health(path, pid=1234, now=100.0)
    assert not daemon_health_is_fresh(path, now=100.5, process_check=lambda pid: False)
    assert not daemon_process_is_running(path, process_check=lambda pid: False)


def test_invalid_or_missing_health_marker_fails_closed(tmp_path: Path):
    path = tmp_path / "daemon-health.json"
    assert read_daemon_pid(path) is None
    path.write_text('{"pid": true, "heartbeatAt": 100}', encoding="utf-8")
    assert not daemon_health_is_fresh(path, now=100.5, process_check=lambda pid: True)
    path.write_text('{broken', encoding="utf-8")
    assert not daemon_health_is_fresh(path, now=100.5, process_check=lambda pid: True)
    path.write_text('[1, 2, 3]', encoding="utf-8")
    assert not daemon_health_is_fresh(path, now=100.5, process_check=lambda pid: True)
    path.write_text('{"pid": 1234, "heartbeatAt": NaN}', encoding="utf-8")
    assert not daemon_health_is_fresh(path, now=100.5, process_check=lambda pid: True)


def test_remove_health_only_removes_matching_process_marker(tmp_path: Path):
    path = tmp_path / "daemon-health.json"
    write_daemon_health(path, pid=1234, now=100.0)
    remove_daemon_health(path, pid=9999)
    assert path.exists()
    remove_daemon_health(path, pid=1234)
    assert not path.exists()


def test_process_is_running_detects_current_process():
    assert process_is_running(os.getpid())
    assert not process_is_running(2**30)
    assert not process_is_running(2**40)


def test_windows_process_check_falls_back_to_tasklist_when_openprocess_is_denied(monkeypatch):
    import ctypes
    import subprocess
    import windows_engine.daemon_health as health

    class Api:
        def __init__(self, result=None):
            self.result = result
        def __call__(self, *_args):
            return self.result

    class Kernel:
        OpenProcess = Api(0)
        GetExitCodeProcess = Api(0)
        CloseHandle = Api(1)

    monkeypatch.setattr(health.os, "name", "nt")
    monkeypatch.setattr(ctypes, "WinDLL", lambda *_args, **_kwargs: Kernel(), raising=False)
    monkeypatch.setattr(
        health.subprocess, "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0], 0,
            stdout='"GuardianDnsProxy.exe","39716","Services","0","3,000 K"\\n',
            stderr="",
        ),
    )

    assert health.process_is_running(39716)


def test_windows_process_check_does_not_accept_a_different_tasklist_pid(monkeypatch):
    import ctypes
    import subprocess
    import windows_engine.daemon_health as health

    class Api:
        def __init__(self, result=None):
            self.result = result
        def __call__(self, *_args):
            return self.result

    class Kernel:
        OpenProcess = Api(0)
        GetExitCodeProcess = Api(0)
        CloseHandle = Api(1)

    monkeypatch.setattr(health.os, "name", "nt")
    monkeypatch.setattr(ctypes, "WinDLL", lambda *_args, **_kwargs: Kernel(), raising=False)
    monkeypatch.setattr(
        health.subprocess, "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0], 0, stdout='"other.exe","1234","Console","1","1,000 K"\\n', stderr="",
        ),
    )

    assert not health.process_is_running(39716)
