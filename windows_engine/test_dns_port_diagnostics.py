from types import SimpleNamespace

from windows_engine import desktop


def test_startup_diagnostics_report_dns_port_53_process_owners(monkeypatch):
    calls = []

    def fake_run(command, **kwargs):
        calls.append(command)
        if command[:3] == ["netstat.exe", "-ano", "-p"]:
            protocol = command[-1]
            if protocol == "udp":
                return SimpleNamespace(
                    returncode=0,
                    stdout="  UDP    0.0.0.0:53       *:*       4321\n",
                    stderr="",
                )
            return SimpleNamespace(returncode=0, stdout="", stderr="")
        if command[0] == "tasklist.exe":
            return SimpleNamespace(
                returncode=0,
                stdout='"dnsproxy.exe","4321","Services","0","12,000 K"\n',
                stderr="",
            )
        raise AssertionError(command)

    monkeypatch.setattr(desktop.subprocess, "run", fake_run)
    result = desktop.dns_port_53_diagnostics()

    assert any("UDP 0.0.0.0:53 PID 4321" in line for line in result)
    assert any("dnsproxy.exe" in line for line in result)
    assert any(command[0] == "tasklist.exe" for command in calls)
