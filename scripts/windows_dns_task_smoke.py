"""Exercise the installed SYSTEM scheduled task without changing adapter DNS."""
import json
import os
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path
from windows_engine.daemon_health import daemon_process_is_running

APP_DIR = Path(os.environ.get("PROGRAMDATA", r"C:\ProgramData")) / "Guardian"
TASK_NAME = "GuardianDnsProxy"
HEALTH = APP_DIR / "daemon-health.json"
LOG_FILE = APP_DIR / "logs" / "proxy-daemon.log"


def query_packet():
    labels = b"".join(bytes((len(part),)) + part.encode("ascii") for part in "doubleclick.net".split(".")) + b"\0"
    return struct.pack("!HHHHHH", 0x6A31, 0x0100, 1, 0, 0, 0) + labels + struct.pack("!HH", 1, 1)


def valid_block_response(query, response):
    return (
        len(response) >= 12
        and response[:2] == query[:2]
        and bool(response[2] & 0x80)
        and response[3] & 0x0F == 3
        and struct.unpack("!H", response[4:6])[0] == 1
    )


def recv_exact(stream, size):
    chunks = []
    while size:
        chunk = stream.recv(size)
        if not chunk:
            raise OSError("DNS TCP response ended early")
        chunks.append(chunk)
        size -= len(chunk)
    return b"".join(chunks)


def probe_udp(query):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
        client.settimeout(0.5)
        client.sendto(query, ("127.0.0.1", 53))
        response, _ = client.recvfrom(512)
    return valid_block_response(query, response)


def probe_tcp(query):
    with socket.create_connection(("127.0.0.1", 53), timeout=0.5) as client:
        client.settimeout(0.5)
        client.sendall(struct.pack("!H", len(query)) + query)
        size = struct.unpack("!H", recv_exact(client, 2))[0]
        if size < 12 or size > 4096:
            return False
        return valid_block_response(query, recv_exact(client, size))


def diagnostics():
    lines = []
    task = subprocess.run(
        ["schtasks.exe", "/Query", "/TN", TASK_NAME, "/V", "/FO", "LIST"],
        capture_output=True,
        text=True,
        timeout=10,
    )
    lines.append("Task Scheduler:\n" + (task.stdout or task.stderr or f"exit={task.returncode}"))
    for protocol in ("udp", "tcp"):
        result = subprocess.run(
            ["netstat.exe", "-ano", "-p", protocol],
            capture_output=True,
            text=True,
            timeout=5,
        )
        matches = [line.strip() for line in (result.stdout or "").splitlines() if ":53" in line]
        lines.append(f"Port 53 {protocol.upper()}: " + (" | ".join(matches[:12]) if matches else "no endpoint"))
    if LOG_FILE.exists():
        lines.append("Daemon log:\n" + "\n".join(LOG_FILE.read_text(encoding="utf-8", errors="replace").splitlines()[-40:]))
    else:
        lines.append("Daemon log was not created: " + str(LOG_FILE))
    return "\n".join(lines)


def main():
    if os.name != "nt":
        raise SystemExit("This integration test requires Windows.")
    APP_DIR.mkdir(parents=True, exist_ok=True)
    (APP_DIR / "rules.txt").write_text(
        "||doubleclick.net^\n||googlesyndication.com^\n", encoding="utf-8"
    )
    (APP_DIR / "guardian.json").write_text(
        json.dumps(
            {
                "rules_file": "rules.txt",
                "listen_port": 53,
                "upstreams": ["1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "2001:4860:4860::8888"],
            }
        ),
        encoding="utf-8",
    )
    state_path = APP_DIR / "protection.json"
    state_path.write_text(json.dumps({"enabled": True, "updatedAt": time.time()}), encoding="utf-8")
    HEALTH.unlink(missing_ok=True)
    query = query_packet()
    failure = None
    try:
        started = subprocess.run(
            ["schtasks.exe", "/Run", "/TN", TASK_NAME],
            capture_output=True,
            text=True,
            timeout=15,
        )
        if started.returncode:
            raise RuntimeError(started.stderr.strip() or started.stdout.strip() or "schtasks /Run failed")
        deadline = time.monotonic() + 20
        last_error = "DNS listener did not answer yet"
        while time.monotonic() < deadline:
            try:
                if probe_udp(query) and probe_tcp(query):
                    print("Installed Guardian SYSTEM task answered blocked DNS over IPv4 UDP and TCP port 53.")
                    return 0
                last_error = "UDP or TCP probe did not return the expected NXDOMAIN response"
            except OSError as exc:
                last_error = str(exc)
            time.sleep(0.25)
        raise RuntimeError("Scheduled DNS task did not become ready: " + last_error + "\n\n" + diagnostics())
    except Exception as exc:
        failure = exc
    finally:
        state_path.write_text(json.dumps({"enabled": False, "updatedAt": time.time()}), encoding="utf-8")
        subprocess.run(
            ["schtasks.exe", "/End", "/TN", TASK_NAME],
            capture_output=True,
            text=True,
            timeout=15,
        )
        deadline = time.monotonic() + 5
        while daemon_process_is_running(HEALTH) and time.monotonic() < deadline:
            time.sleep(0.2)
        if daemon_process_is_running(HEALTH) and failure is None:
            failure = RuntimeError("Guardian DNS daemon process did not stop after schtasks /End")
        if not daemon_process_is_running(HEALTH):
            HEALTH.unlink(missing_ok=True)
    if failure:
        print(str(failure), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
