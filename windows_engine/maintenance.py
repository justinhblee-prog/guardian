#!/usr/bin/env python3
"""Small console entry point for safe Windows DNS restoration during uninstall."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

from windows_engine.daemon_health import daemon_process_is_running, remove_daemon_health
from windows_engine.network_config import DnsAdapterManager, NetworkConfigError, is_admin

APP_DIR = Path(os.environ.get("PROGRAMDATA", r"C:\ProgramData")) / "Guardian"
BACKUP = APP_DIR / "dns-backup.json"
STATE = APP_DIR / "protection.json"
HEALTH = APP_DIR / "daemon-health.json"
TASK_NAME = "GuardianDnsProxy"


def run_task(*args):
    return subprocess.run(
        ["schtasks.exe", *args],
        capture_output=True,
        text=True,
        timeout=15,
    )


def write_state(enabled):
    APP_DIR.mkdir(parents=True, exist_ok=True)
    temporary = STATE.with_suffix(".tmp")
    temporary.write_text(
        json.dumps({"enabled": bool(enabled), "updatedAt": time.time()}),
        encoding="utf-8",
    )
    os.replace(temporary, STATE)


def stop_daemon(delete=False):
    run_task("/End", "/TN", TASK_NAME)
    if daemon_process_is_running(HEALTH):
        deadline = time.monotonic() + 5
        while daemon_process_is_running(HEALTH) and time.monotonic() < deadline:
            time.sleep(0.2)
        if daemon_process_is_running(HEALTH):
            raise NetworkConfigError(
                "Guardian DNS background process did not stop; network protection state was not marked stopped"
            )
    if not daemon_process_is_running(HEALTH):
        remove_daemon_health(HEALTH)
    if delete:
        run_task("/Delete", "/TN", TASK_NAME, "/F")
        for _ in range(5):
            query = run_task("/Query", "/TN", TASK_NAME)
            if query.returncode != 0:
                return
            time.sleep(0.3)
        subprocess.run(
            [
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                "Unregister-ScheduledTask -TaskName 'GuardianDnsProxy' -Confirm:$false -ErrorAction SilentlyContinue",
            ],
            capture_output=True,
            text=True,
            timeout=20,
        )
        for _ in range(10):
            query = run_task("/Query", "/TN", TASK_NAME)
            if query.returncode != 0:
                return
            time.sleep(0.3)
        raise NetworkConfigError(
            "Could not remove the boot-start Guardian DNS task; uninstall must be retried"
        )


def restore_and_remove_task():
    manager = DnsAdapterManager(BACKUP)
    manager.restore()
    write_state(False)
    stop_daemon(delete=True)


def main():
    if "--restore-dns" not in sys.argv:
        print("GuardianMaintenance requires --restore-dns", file=sys.stderr, flush=True)
        return 2
    if not is_admin():
        print(
            "Guardian DNS restoration requires an elevated administrator process.",
            file=sys.stderr,
            flush=True,
        )
        return 1
    restore_and_remove_task()
    print("Guardian DNS settings restored and background task removed.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
