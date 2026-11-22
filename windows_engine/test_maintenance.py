import json
import subprocess

import pytest

from windows_engine import maintenance
from windows_engine.network_config import NetworkConfigError


def completed(returncode=0):
    return subprocess.CompletedProcess([], returncode, "", "")


def test_write_state_atomically_uses_expected_shape(tmp_path, monkeypatch):
    monkeypatch.setattr(maintenance, "APP_DIR", tmp_path)
    monkeypatch.setattr(maintenance, "STATE", tmp_path / "protection.json")
    maintenance.write_state(False)
    data = json.loads((tmp_path / "protection.json").read_text(encoding="utf-8"))
    assert data["enabled"] is False
    assert isinstance(data["updatedAt"], (int, float))
    assert not (tmp_path / "protection.tmp").exists()


def test_stop_daemon_removes_task_only_after_process_is_stopped(monkeypatch):
    calls = []
    monkeypatch.setattr(maintenance, "daemon_process_is_running", lambda _path: False)
    monkeypatch.setattr(maintenance, "remove_daemon_health", lambda _path: calls.append("remove-health"))

    def fake_run_task(*args):
        calls.append(args)
        return completed(1 if args[0] == "/Query" else 0)

    monkeypatch.setattr(maintenance, "run_task", fake_run_task)
    maintenance.stop_daemon(delete=True)

    assert calls[0] == ("/End", "/TN", maintenance.TASK_NAME)
    assert "remove-health" in calls
    assert ("/Delete", "/TN", maintenance.TASK_NAME, "/F") in calls


def test_stop_daemon_refuses_task_deletion_when_process_remains(monkeypatch):
    calls = []
    monkeypatch.setattr(maintenance, "daemon_process_is_running", lambda _path: True)
    ticks = iter((0.0, 6.0))
    monkeypatch.setattr(maintenance.time, "monotonic", lambda: next(ticks))
    monkeypatch.setattr(maintenance, "run_task", lambda *args: calls.append(args) or completed())

    with pytest.raises(NetworkConfigError, match="did not stop"):
        maintenance.stop_daemon(delete=True)

    assert calls == [("/End", "/TN", maintenance.TASK_NAME)]
    assert not any(args[0] == "/Delete" for args in calls)
