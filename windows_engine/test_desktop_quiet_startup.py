import ast
from pathlib import Path

from windows_engine import desktop, network_config


def test_helper_processes_are_started_without_console_windows(monkeypatch):
    class StartupInfo:
        dwFlags = 0
        wShowWindow = 1

    captured = {}
    monkeypatch.setattr(desktop.os, "name", "nt")
    monkeypatch.setattr(desktop.subprocess, "STARTUPINFO", StartupInfo, raising=False)
    monkeypatch.setattr(desktop.subprocess, "STARTF_USESHOWWINDOW", 1, raising=False)
    monkeypatch.setattr(desktop.subprocess, "CREATE_NO_WINDOW", 0x08000000, raising=False)

    def fake_run(command, **kwargs):
        captured["command"] = command
        captured["kwargs"] = kwargs
        return "ok"

    monkeypatch.setattr(desktop.subprocess, "run", fake_run)
    assert desktop.run_hidden(["schtasks.exe", "/Query"]) == "ok"
    assert captured["kwargs"]["creationflags"] & 0x08000000
    assert captured["kwargs"]["startupinfo"].wShowWindow == 0


def test_protection_start_does_not_open_modal_error_windows():
    source = Path(desktop.__file__).read_text(encoding="utf-8")
    module = ast.parse(source)
    cls = next(node for node in module.body if isinstance(node, ast.ClassDef) and node.name == "GuardianDesktop")
    method = next(node for node in cls.body if isinstance(node, ast.FunctionDef) and node.name == "start_protection")
    calls = [node.func.attr for node in ast.walk(method) if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)]
    assert "show_error_dialog" not in calls


def test_diagnostic_port_filter_does_not_match_mdns_5353():
    import re

    exact_port_53 = re.compile(r":53(?:\s|$)")
    assert exact_port_53.search("UDP    0.0.0.0:53             *:*                                    4812")
    assert not exact_port_53.search("UDP    0.0.0.0:5353           *:*                                    34420")


def test_dns_adapter_powershell_runs_without_console_window(monkeypatch):
    class StartupInfo:
        dwFlags = 0
        wShowWindow = 1

    captured = {}
    monkeypatch.setattr(network_config.os, "name", "nt")
    monkeypatch.setattr(network_config.subprocess, "STARTUPINFO", StartupInfo, raising=False)
    monkeypatch.setattr(network_config.subprocess, "STARTF_USESHOWWINDOW", 1, raising=False)
    monkeypatch.setattr(network_config.subprocess, "CREATE_NO_WINDOW", 0x08000000, raising=False)

    def fake_run(command, **kwargs):
        captured["command"] = command
        captured["kwargs"] = kwargs
        return type("Result", (), {"returncode": 0, "stdout": "ok", "stderr": ""})()

    monkeypatch.setattr(network_config.subprocess, "run", fake_run)
    assert network_config.run_powershell("Get-DnsClientServerAddress") == "ok"
    assert captured["kwargs"]["creationflags"] & 0x08000000
    assert captured["kwargs"]["startupinfo"].wShowWindow == 0
