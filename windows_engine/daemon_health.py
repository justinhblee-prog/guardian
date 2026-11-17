"""Heartbeat and process-identity checks for the Windows DNS daemon."""
import json
import math
import os
import tempfile
import time
from pathlib import Path

HEALTH_MAX_AGE_SECONDS = 4.0
FUTURE_CLOCK_SKEW_SECONDS = 2.0


def process_is_running(pid: int) -> bool:
    if pid <= 0 or pid > 0xFFFFFFFF:
        return False
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except PermissionError:
            return True
        except OSError:
            return False

    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
    kernel32.OpenProcess.restype = wintypes.HANDLE
    kernel32.GetExitCodeProcess.argtypes = (wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD))
    kernel32.GetExitCodeProcess.restype = wintypes.BOOL
    kernel32.CloseHandle.argtypes = (wintypes.HANDLE,)
    kernel32.CloseHandle.restype = wintypes.BOOL

    handle = kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
    if not handle:
        return False
    try:
        exit_code = wintypes.DWORD()
        return bool(kernel32.GetExitCodeProcess(handle, ctypes.byref(exit_code))) and exit_code.value == 259
    finally:
        kernel32.CloseHandle(handle)


def read_daemon_pid(path: Path):
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            return None
        pid = data.get("pid")
        if isinstance(pid, bool) or not isinstance(pid, int) or pid <= 0:
            return None
        return pid
    except (OSError, ValueError, TypeError, AttributeError, json.JSONDecodeError):
        return None


def daemon_process_is_running(path: Path, process_check=process_is_running) -> bool:
    pid = read_daemon_pid(path)
    return pid is not None and process_check(pid)


def daemon_health_is_fresh(
    path: Path,
    now: float | None = None,
    max_age: float = HEALTH_MAX_AGE_SECONDS,
    process_check=process_is_running,
) -> bool:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            return False
        pid = data.get("pid")
        heartbeat = data.get("heartbeatAt")
        if isinstance(pid, bool) or not isinstance(pid, int) or pid <= 0:
            return False
        if isinstance(heartbeat, bool) or not isinstance(heartbeat, (int, float)):
            return False
        heartbeat = float(heartbeat)
        if not math.isfinite(heartbeat):
            return False
        age = (time.time() if now is None else now) - heartbeat
        if age < -FUTURE_CLOCK_SKEW_SECONDS or age > max_age:
            return False
        return process_check(pid)
    except (OSError, ValueError, TypeError, AttributeError, json.JSONDecodeError, OverflowError):
        return False


def write_daemon_health(path: Path, pid: int | None = None, now: float | None = None) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = {"pid": os.getpid() if pid is None else int(pid), "heartbeatAt": time.time() if now is None else float(now)}
    fd, temporary = tempfile.mkstemp(prefix=target.name + ".", suffix=".tmp", dir=target.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(payload, stream, separators=(",", ":"))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def remove_daemon_health(path: Path, pid: int | None = None) -> None:
    target = Path(path)
    current_pid = read_daemon_pid(target)
    if current_pid is not None and (pid is None or current_pid == pid):
        target.unlink(missing_ok=True)
