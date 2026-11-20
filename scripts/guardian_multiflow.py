#!/usr/bin/env python3
"""Safe, local-only Ollama multi-flow worker for Guardian.

The model can propose unified diffs only. Git, file access, validation, commit,
push and Draft PR operations remain under this deterministic wrapper.
"""
from __future__ import annotations
import argparse, concurrent.futures, contextlib, datetime as dt, json, os, re, socket, subprocess, sys, threading, time, urllib.error, urllib.parse, urllib.request, uuid
from pathlib import Path, PurePosixPath
from typing import Any, Iterator

REPOSITORY = "justinhblee-prog/guardian"
DEFAULT_MODEL = "qwen2.5-coder:7b"
DEFAULT_OLLAMA_URL = "http://127.0.0.1:11434"
MAX_PATCH_BYTES = 256 * 1024
MAX_FILE_BYTES = 96 * 1024
MAX_PROMPT_BYTES = 240 * 1024
MAX_PATCH_FILES = 8
LEASE_TTL_SECONDS = 60 * 60
OLLAMA_SLOT_COUNT = 3
OLLAMA_SLOT_WAIT_SECONDS = 15 * 60

PROTECTED_PARTS = {".git", ".env", ".venv", "venv", "node_modules", "__pycache__", "secrets", "outputs", "logs", "build", "dist"}
PROTECTED_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".p8", ".mobileprovision", ".dpapi"}
PROTECTED_EXACT = {".guardian/automation/tasks.json", "shared/filter_sources.json"}
PROTECTED_PREFIXES = (".github/workflows/", "scripts/guardian_", "scripts/guardian-", "scripts/test_guardian_")

# Commands are code-owned allowlists. A task manifest cannot provide executable text.
TEST_PROFILES: dict[str, list[tuple[list[str], int]]] = {
    "android-unit-lint": [
        (["gradle", "-p", "android", "--no-daemon", "testDebugUnitTest", "lintDebug"], 1800),
    ],
    "filter-node": [
        (["npm", "--prefix", "server/filter-distribution", "test"], 900),
    ],
}

class AutomationError(RuntimeError):
    pass

class FileLease:
    """Cross-process lease based on atomic file creation."""
    def __init__(self, path: Path, ttl_seconds: int = LEASE_TTL_SECONDS):
        self.path, self.ttl_seconds = path, ttl_seconds
        self.acquired = False
        self.token = uuid.uuid4().hex

    @staticmethod
    def _pid_alive(pid: int) -> bool:
        if pid <= 0:
            return False
        if os.name == "nt":
            # os.kill(pid, 0) and OpenProcess can report platform-specific
            # errors for vanished PIDs. Windows tasklist emits stable CSV
            # process IDs; only an explicit matching row counts as alive.
            # If tasklist itself fails, preserve the lease rather than steal it.
            try:
                result = subprocess.run(
                    ["tasklist.exe", "/FO", "CSV", "/NH"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    timeout=8,
                    check=False,
                )
                if result.returncode != 0:
                    return True
                return any(
                    re.search(rf'^"[^"]+","{pid}"(?:,|$)', line) is not None
                    for line in result.stdout.splitlines()
                )
            except (OSError, subprocess.TimeoutExpired):
                return True
        try:
            os.kill(pid, 0)
            return True
        except ProcessLookupError:
            return False
        except (PermissionError, OSError):
            return True

    def _try_reap_stale(self) -> bool:
        try:
            record = json.loads(self.path.read_text(encoding="utf-8"))
            if float(record.get("expires_unix", 0)) >= time.time():
                return False
            if record.get("host") == socket.gethostname() and self._pid_alive(int(record.get("pid", -1))):
                return False
            self.path.unlink(missing_ok=True)
            return True
        except FileNotFoundError:
            return True
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            return False

    def acquire(self, wait_seconds: float = 0) -> bool:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        deadline = time.monotonic() + max(0, wait_seconds)
        while True:
            record = {"token": self.token, "pid": os.getpid(), "host": socket.gethostname(),
                      "created_unix": time.time(), "expires_unix": time.time() + self.ttl_seconds}
            try:
                fd = os.open(str(self.path), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                with os.fdopen(fd, "w", encoding="utf-8") as stream:
                    json.dump(record, stream)
                    stream.flush()
                    os.fsync(stream.fileno())
                self.acquired = True
                return True
            except FileExistsError:
                if self._try_reap_stale():
                    # The stale file was removed; retry exclusive creation
                    # immediately instead of returning on a zero wait timeout.
                    continue
            if time.monotonic() >= deadline:
                return False
            time.sleep(1)

    def release(self) -> None:
        if not self.acquired:
            return
        try:
            record = json.loads(self.path.read_text(encoding="utf-8"))
            if record.get("token") == self.token:
                self.path.unlink(missing_ok=True)
        except (OSError, ValueError, json.JSONDecodeError):
            pass
        self.acquired = False

    def __enter__(self) -> "FileLease":
        if not self.acquire():
            raise AutomationError(f"Lease already held: {self.path.name}")
        return self

    def __exit__(self, *_: object) -> None:
        self.release()

@contextlib.contextmanager
def ollama_slot(slot_root: Path, wait_seconds: int = OLLAMA_SLOT_WAIT_SECONDS) -> Iterator[int]:
    slot_root.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + wait_seconds
    while True:
        for number in range(1, OLLAMA_SLOT_COUNT + 1):
            lease = FileLease(slot_root / f"slot-{number}.lock", ttl_seconds=20 * 60)
            if lease.acquire():
                try:
                    yield number
                finally:
                    lease.release()
                return
        if time.monotonic() >= deadline:
            raise AutomationError("Timed out waiting for a local Ollama concurrency slot.")
        time.sleep(2)

def validate_local_ollama_url(value: str) -> str:
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"} or parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment:
        raise AutomationError("Ollama must use a plain HTTP loopback URL; remote AI endpoints are blocked.")
    return value.rstrip("/")

def safe_relative_path(value: str) -> str:
    if not isinstance(value, str) or not value or "\\" in value:
        raise AutomationError(f"Invalid repository-relative path: {value!r}")
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise AutomationError(f"Path traversal or absolute path is not allowed: {value!r}")
    normalized = path.as_posix()
    if ":" in normalized or normalized.startswith("/"):
        raise AutomationError(f"Drive/absolute path is not allowed: {value!r}")
    parts = {part.lower() for part in path.parts}
    suffix = Path(normalized).suffix.lower()
    if parts & PROTECTED_PARTS or any(part.startswith(".env") for part in parts) or suffix in PROTECTED_SUFFIXES:
        raise AutomationError(f"Protected path is not accessible to the agent: {normalized}")
    if normalized in PROTECTED_EXACT or any(normalized.lower().startswith(p.lower()) for p in PROTECTED_PREFIXES):
        raise AutomationError(f"Automation control path is protected: {normalized}")
    return normalized

def validate_manifest(manifest: Any) -> list[dict[str, Any]]:
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise AutomationError("Task manifest must be a schema_version 1 object.")
    tasks = manifest.get("tasks")
    if not isinstance(tasks, list) or not tasks:
        raise AutomationError("Task manifest must contain a non-empty tasks array.")
    ids: set[str] = set()
    all_ids = {str(item.get("id", "")) for item in tasks if isinstance(item, dict)}
    validated: list[dict[str, Any]] = []
    ready_paths: dict[str, str] = {}
    for task in tasks:
        if not isinstance(task, dict):
            raise AutomationError("Every task must be an object.")
        task_id = task.get("id")
        if not isinstance(task_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]{2,47}", task_id):
            raise AutomationError(f"Invalid task id: {task_id!r}")
        if task_id in ids:
            raise AutomationError(f"Duplicate task id: {task_id}")
        ids.add(task_id)
        if task.get("status") not in {"ready", "blocked", "disabled"}:
            raise AutomationError(f"Unsupported task status for {task_id}.")
        if task.get("test_profile") not in TEST_PROFILES:
            raise AutomationError(f"Unknown fixed test profile for {task_id}.")
        allowed = task.get("allowed_files")
        if not isinstance(allowed, list) or not 1 <= len(allowed) <= MAX_PATCH_FILES:
            raise AutomationError(f"{task_id} must declare 1-{MAX_PATCH_FILES} exact allowed_files.")
        clean_paths: list[str] = []
        for raw_path in allowed:
            clean = safe_relative_path(raw_path)
            if clean in clean_paths:
                raise AutomationError(f"{task_id} declares a duplicate allowed file: {clean}")
            clean_paths.append(clean)
            if task.get("status") == "ready":
                previous = ready_paths.get(clean)
                if previous:
                    raise AutomationError(f"Ready tasks {previous} and {task_id} overlap on {clean}.")
                ready_paths[clean] = task_id
        criteria = task.get("acceptance_criteria")
        if not isinstance(criteria, list) or not criteria or not all(isinstance(x, str) and x.strip() for x in criteria):
            raise AutomationError(f"{task_id} must include non-empty acceptance_criteria.")
        deps = task.get("dependencies", [])
        if not isinstance(deps, list) or any(dep not in all_ids or dep == task_id for dep in deps):
            raise AutomationError(f"{task_id} has an invalid dependency list.")
        validated.append({**task, "allowed_files": clean_paths})
    return validated

def validate_unified_patch(patch: str, allowed_files: set[str]) -> set[str]:
    if not isinstance(patch, str):
        raise AutomationError("Ollama diff is not a string.")
    if not patch.strip():
        return set()
    if len(patch.encode("utf-8")) > MAX_PATCH_BYTES:
        raise AutomationError("Proposed diff exceeds the 256 KiB safety limit.")
    if "GIT binary patch" in patch or "Binary files " in patch:
        raise AutomationError("Binary patches are not accepted.")
    for forbidden in ("new file mode ", "deleted file mode ", "rename from ", "rename to ", "copy from ", "old mode ", "new mode "):
        if forbidden in patch:
            raise AutomationError(f"File creation/deletion/rename/mode changes are not accepted ({forbidden.strip()}).")
    sections = re.split(r"(?=^diff --git )", patch, flags=re.MULTILINE)
    sections = [section for section in sections if section.strip()]
    if not sections or len(sections) > MAX_PATCH_FILES:
        raise AutomationError("Diff must contain 1-8 ordinary git diff file sections.")
    changed: set[str] = set()
    for section in sections:
        lines = section.splitlines()
        if not lines:
            raise AutomationError("Empty diff file section.")
        header = re.fullmatch(r"diff --git a/([^\r\n]+) b/([^\r\n]+)", lines[0])
        if not header:
            raise AutomationError("Quoted, combined or malformed git diff headers are not accepted.")
        old, new = header.groups()
        if old != new:
            raise AutomationError("Renames and copies are not accepted.")
        header_path = safe_relative_path(old)
        if header_path not in allowed_files:
            raise AutomationError(f"Model attempted to modify a file outside its exact allowlist: {header_path}")
        old_markers = [line for line in lines if line.startswith("--- ")]
        new_markers = [line for line in lines if line.startswith("+++ ")]
        if len(old_markers) != 1 or len(new_markers) != 1:
            raise AutomationError(f"Diff for {header_path} must contain exactly one old and new file marker.")
        old_marker, new_marker = old_markers[0], new_markers[0]
        if old_marker == "--- /dev/null" or new_marker == "+++ /dev/null":
            raise AutomationError("Creating or deleting files is not permitted for model patches.")
        if not old_marker.startswith("--- a/") or not new_marker.startswith("+++ b/"):
            raise AutomationError("Diff markers must use ordinary a/ and b/ repository paths.")
        old_path = safe_relative_path(old_marker[6:])
        new_path = safe_relative_path(new_marker[6:])
        if old_path != header_path or new_path != header_path:
            raise AutomationError(
                f"Diff header/marker path mismatch: header={header_path}, old={old_path}, new={new_path}."
            )
        if header_path in changed:
            raise AutomationError("Diff repeats a file section.")
        changed.add(header_path)
    return changed

def process_environment(*, strip_credentials: bool = False) -> dict[str, str]:
    env = os.environ.copy()
    if strip_credentials:
        sensitive_markers = ("TOKEN", "SECRET", "PASSWORD", "PRIVATE_KEY", "ACCESS_KEY")
        for name in tuple(env):
            if any(marker in name.upper() for marker in sensitive_markers):
                env.pop(name, None)
        # Do not let model-authored test code inherit Actions artifact upload credentials.
        env.pop("ACTIONS_RUNTIME_URL", None)
        env.pop("ACTIONS_RESULTS_URL", None)
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["GH_PROMPT_DISABLED"] = "1"
    return env

def run_process(args: list[str], cwd: Path, timeout: int, input_text: str | None = None,
                *, strip_credentials: bool = False) -> subprocess.CompletedProcess[str]:
    env = process_environment(strip_credentials=strip_credentials)
    try:
        return subprocess.run(args, cwd=str(cwd), input=input_text, text=True, encoding="utf-8", errors="replace",
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, env=env, check=False)
    except FileNotFoundError as error:
        raise AutomationError(f"Required executable is not installed or not in PATH: {args[0]}") from error
    except subprocess.TimeoutExpired as error:
        raise AutomationError(f"Command timed out after {timeout}s: {args[0]}") from error

def git(repo: Path, args: list[str], *, timeout: int = 120, check: bool = True) -> str:
    result = run_process(["git", "-C", str(repo), *args], repo, timeout)
    if check and result.returncode != 0:
        raise AutomationError(f"git {' '.join(args)} failed ({result.returncode}):\n{result.stdout[-4000:]}")
    return result.stdout.strip()

def verify_repository(repo: Path) -> None:
    root = Path(git(repo, ["rev-parse", "--show-toplevel"])).resolve()
    if root != repo.resolve():
        raise AutomationError("RepoRoot must be the root of the Guardian checkout.")
    remote = git(repo, ["remote", "get-url", "origin"])
    normalized = remote.lower().replace(".git", "").rstrip("/")
    if "github.com/justinhblee-prog/guardian" not in normalized and "justinhblee-prog/guardian" not in normalized:
        raise AutomationError(f"origin is not the authorized Guardian repository: {remote}")
    git(repo, ["fetch", "--no-tags", "origin", "main"], timeout=180)
    git(repo, ["rev-parse", "--verify", "origin/main"])

def ollama_json(base_url: str, payload: dict[str, Any], timeout: int = 600) -> dict[str, Any]:
    request = urllib.request.Request(base_url + "/api/chat", data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                                     headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            result = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, UnicodeDecodeError, OSError) as error:
        raise AutomationError(f"Local Ollama inference failed: {error}") from error
    if not isinstance(result, dict) or not isinstance(result.get("message"), dict):
        raise AutomationError("Ollama response has no message object.")
    return result

def verify_ollama_model(base_url: str, model: str) -> None:
    request = urllib.request.Request(base_url + "/api/tags", method="GET")
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            result = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, UnicodeDecodeError, OSError) as error:
        raise AutomationError(f"Ollama tags endpoint is unavailable: {error}") from error
    if not isinstance(result, dict) or not isinstance(result.get("models"), list):
        raise AutomationError("Ollama tags response must contain a models array.")
    names = {str(item.get("name", "")) for item in result["models"] if isinstance(item, dict)}
    if model not in names:
        raise AutomationError(f"Required local model is not installed: {model}. No model download was started.")

def smoke_test(base_url: str, model: str, slot_root: Path) -> str:
    verify_ollama_model(base_url, model)
    with ollama_slot(slot_root):
        result = ollama_json(base_url, {
            "model": model, "stream": False, "format": "json",
            "options": {"temperature": 0, "num_predict": 64},
            "messages": [{"role": "user", "content": 'Return JSON only: {"token":"GUARDIAN_LOCAL_OLLAMA_OK"}'}],
        }, timeout=180)
    try:
        parsed = json.loads(result["message"].get("content", ""))
    except (TypeError, json.JSONDecodeError) as error:
        raise AutomationError("Ollama smoke test did not return valid JSON.") from error
    if not isinstance(parsed, dict) or parsed.get("token") != "GUARDIAN_LOCAL_OLLAMA_OK":
        raise AutomationError("Ollama smoke test returned an unexpected token.")
    return "PASS: real /api/chat inference executed against the installed loopback model."

def load_allowed_sources(worktree: Path, allowed_files: list[str]) -> str:
    parts: list[str] = []
    total = 0
    for relative in allowed_files:
        safe = safe_relative_path(relative)
        candidate = worktree.joinpath(*PurePosixPath(safe).parts)
        current = worktree
        for piece in PurePosixPath(safe).parts:
            current = current / piece
            if current.is_symlink():
                raise AutomationError(f"Symbolic link in an allowed path is blocked: {safe}")
        resolved = candidate.resolve(strict=True)
        if worktree.resolve() not in resolved.parents or not resolved.is_file():
            raise AutomationError(f"Allowed file is not a regular file inside worktree: {safe}")
        raw = resolved.read_bytes()
        if len(raw) > MAX_FILE_BYTES or b"\x00" in raw:
            raise AutomationError(f"Allowed file is binary or exceeds 96 KiB: {safe}")
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as error:
            raise AutomationError(f"Allowed file is not UTF-8: {safe}") from error
        total += len(raw)
        if total > MAX_PROMPT_BYTES:
            raise AutomationError("Task source exceeds the 240 KiB prompt size limit.")
        parts.append(f"===== FILE: {safe} =====\n{text}")
    return "\n\n".join(parts)

def propose_patch(base_url: str, model: str, slot_root: Path, task: dict[str, Any], source: str) -> str:
    prompt = f"""You are the local code-change proposer for Guardian AdBlock.
Task: {task['id']} — {task['title']}

Goal:
{task['scope']}

Acceptance criteria:
{chr(10).join('- ' + item for item in task['acceptance_criteria'])}

Exact editable files:
{chr(10).join(task['allowed_files'])}

Source:
{source}

Rules:
- Return JSON with exactly one string property named diff.
- diff must be a complete ordinary unified git diff with diff --git headers.
- Only modify existing files in the exact editable file list.
- Do not create, delete, rename, chmod, or binary-edit files.
- Do not add credentials, secrets, endpoints, license approvals, filter redistribution approvals or signing material.
- Preserve fail-closed security gates. Never mark legal/redistribution approval as granted.
- Do not execute commands or claim tests have run. The wrapper runs fixed allowlisted tests.
- Keep changes minimal and include regression coverage inside listed test files when appropriate.
- If no safe complete change is justified by the source, return an empty diff.
"""
    with ollama_slot(slot_root):
        result = ollama_json(base_url, {
            "model": model, "stream": False, "format": "json",
            "options": {"temperature": 0.1, "num_predict": 8192},
            "messages": [
                {"role": "system", "content": "Return only a JSON object with a diff string. Never use tools or shell commands."},
                {"role": "user", "content": prompt},
            ],
        }, timeout=900)
    try:
        payload = json.loads(result["message"].get("content", ""))
    except (TypeError, json.JSONDecodeError) as error:
        raise AutomationError("Ollama did not return the required JSON diff envelope.") from error
    if not isinstance(payload, dict) or set(payload) != {"diff"} or not isinstance(payload["diff"], str):
        raise AutomationError("Ollama diff response must have exactly one string property: diff.")
    return payload["diff"]

def run_profile(profile: str, worktree: Path, log_dir: Path, task_id: str) -> list[str]:
    outcomes = []
    for index, (command, timeout) in enumerate(TEST_PROFILES[profile], start=1):
        # Test/build code executes model-authored source. It must not inherit
        # the workflow's short-lived GitHub token or artifact-upload credentials.
        result = run_process(command, worktree, timeout, strip_credentials=True)
        log_dir.mkdir(parents=True, exist_ok=True)
        (log_dir / f"{task_id}-test-{index}.log").write_text(result.stdout, encoding="utf-8")
        if result.returncode != 0:
            raise AutomationError(f"Fixed test profile {profile} failed ({result.returncode}): {' '.join(command)}\n{result.stdout[-5000:]}")
        outcomes.append(f"PASS: {' '.join(command)}")
    return outcomes

def read_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"schema_version": 1, "tasks": {}}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if value.get("schema_version") != 1 or not isinstance(value.get("tasks"), dict):
            raise ValueError("unsupported state schema")
        return value
    except (OSError, ValueError, json.JSONDecodeError) as error:
        raise AutomationError(f"Automation state is unreadable; preserving it and stopping: {error}") from error

def write_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + "." + uuid.uuid4().hex + ".tmp")
    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(temp, path)

def check_previous_submission(repo: Path, task_id: str, previous: dict[str, Any], current_main: str) -> str:
    if previous.get("status") == "no_change" and previous.get("base_sha") == current_main:
        print(f"[{task_id}] previous model pass found no justified diff on this exact main revision; skipping.")
        return "no_change"
    branch, commit, pr_url = previous.get("branch"), previous.get("commit"), previous.get("pr_url")
    if not branch or not commit:
        return ""
    try:
        ancestor = run_process(["git", "-C", str(repo), "merge-base", "--is-ancestor", str(commit), "origin/main"], repo, 30)
        if ancestor.returncode == 0:
            print(f"[{task_id}] already integrated into origin/main at {commit[:12]}; skipping.")
            return "integrated"
    except AutomationError:
        pass
    if pr_url:
        result = run_process(["gh", "pr", "view", str(pr_url), "--repo", REPOSITORY, "--json", "state"], repo, 45)
        if result.returncode == 0:
            try:
                if json.loads(result.stdout).get("state") == "OPEN":
                    print(f"[{task_id}] previous Draft PR remains open: {pr_url}; skipping duplicate.")
                    return "pr_open"
            except json.JSONDecodeError:
                pass
    return ""

def task_prompt_body(task: dict[str, Any], outcomes: list[str], commit: str, model: str, branch: str) -> str:
    lines = [
        "## Guardian local Ollama implementation", "",
        f"- Task ID: {task['id']}",
        f"- Model: {model} (local loopback Ollama)",
        "- Base branch: main",
        f"- Source commit: {commit}",
        f"- Work branch: {branch}",
        "- Wrapper performed Git operations; model supplied a constrained diff.",
        "- Review policy: Draft PR only; no auto-merge or deployment.", "",
        "### Scope", task["scope"], "",
        "### Acceptance criteria",
    ]
    lines.extend(f"- [ ] {criterion}" for criterion in task["acceptance_criteria"])
    lines.extend(["", "### Automated checks actually run"])
    lines.extend(f"- {outcome}" for outcome in outcomes)
    lines.extend(["", "Human review is required. This PR does not establish physical-device, production, legal, or live-service acceptance."])
    return "\n".join(lines)

def execute_task(repo: Path, task: dict[str, Any], model: str, base_url: str, roots: dict[str, Path],
                 state: dict[str, Any], state_guard: threading.Lock) -> dict[str, Any]:
    task_id = task["id"]
    lease = FileLease(roots["leases"] / f"task-{task_id}.lock")
    if not lease.acquire():
        return {"task": task_id, "status": "skipped", "reason": "task lease already held"}
    worktree: Path | None = None
    branch = ""
    keep_worktree = True
    try:
        with state_guard:
            previous = dict(state["tasks"].get(task_id, {}))
        base_sha = git(repo, ["rev-parse", "origin/main"])
        previous_status = check_previous_submission(repo, task_id, previous, base_sha)
        if previous_status:
            if previous_status == "integrated":
                with state_guard:
                    state["tasks"][task_id]["status"] = "integrated"
                    state["tasks"][task_id]["integrated_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
                    write_state(roots["state"], state)
            return {"task": task_id, "status": "skipped", "reason": f"previous state: {previous_status}"}
        suffix = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + uuid.uuid4().hex[:6]
        branch = f"automation/ollama/{task_id}-{suffix}"
        worktree = roots["worktrees"] / f"{task_id}-{suffix}"
        git(repo, ["worktree", "add", "-b", branch, str(worktree), "origin/main"], timeout=180)
        source = load_allowed_sources(worktree, task["allowed_files"])
        patch = propose_patch(base_url, model, roots["slots"], task, source)
        changed = validate_unified_patch(patch, set(task["allowed_files"]))
        if not changed:
            with state_guard:
                state["tasks"][task_id] = {"status": "no_change", "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(), "base_sha": base_sha, "model": model}
                write_state(roots["state"], state)
            keep_worktree = False
            return {"task": task_id, "status": "no_change", "reason": "model found no justified source-only patch"}
        for relative in changed:
            target = worktree.joinpath(*PurePosixPath(relative).parts)
            if target.is_symlink() or not target.resolve(strict=True).is_relative_to(worktree.resolve()):
                raise AutomationError(f"Patch target is not a regular in-worktree file: {relative}")
        check = run_process(["git", "-C", str(worktree), "apply", "--check", "--whitespace=error", "-"], worktree, 60, patch)
        if check.returncode != 0:
            raise AutomationError(f"Git rejected proposed patch during --check:\n{check.stdout[-5000:]}")
        applied = run_process(["git", "-C", str(worktree), "apply", "--whitespace=error", "-"], worktree, 60, patch)
        if applied.returncode != 0:
            raise AutomationError(f"Could not apply proposed patch:\n{applied.stdout[-5000:]}")
        actual = set(filter(None, git(worktree, ["diff", "--name-only"]).splitlines()))
        untracked = git(worktree, ["ls-files", "--others", "--exclude-standard"])
        if actual != changed or untracked:
            raise AutomationError(f"Post-apply scope mismatch (actual={sorted(actual)}, untracked={untracked!r}).")
        diff_check = run_process(["git", "-C", str(worktree), "diff", "--check"], worktree, 60)
        if diff_check.returncode != 0:
            raise AutomationError(f"git diff --check failed:\n{diff_check.stdout[-4000:]}")
        outcomes = run_profile(task["test_profile"], worktree, roots["logs"], task_id)
        staged = run_process(["git", "-C", str(worktree), "add", "--", *sorted(changed)], worktree, 60)
        if staged.returncode != 0:
            raise AutomationError(f"Git staging failed:\n{staged.stdout[-3000:]}")
        staged_check = run_process(["git", "-C", str(worktree), "diff", "--cached", "--check"], worktree, 60)
        if staged_check.returncode != 0:
            raise AutomationError(f"Staged diff check failed:\n{staged_check.stdout[-3000:]}")
        staged_names = set(filter(None, git(worktree, ["diff", "--cached", "--name-only"]).splitlines()))
        if staged_names != changed:
            raise AutomationError("Staged file set differs from the exact allowlist.")
        committed = run_process(["git", "-C", str(worktree), "-c", "user.name=Guardian Local Automation",
                                 "-c", "user.email=guardian-local-automation@users.noreply.github.com",
                                 "commit", "-m", f"feat(guardian): implement {task_id}"], worktree, 180)
        if committed.returncode != 0:
            raise AutomationError(f"Git commit failed:\n{committed.stdout[-4000:]}")
        commit_sha = git(worktree, ["rev-parse", "HEAD"])
        pushed = run_process(["git", "-C", str(worktree), "push", "origin", f"HEAD:refs/heads/{branch}"], worktree, 180)
        if pushed.returncode != 0:
            raise AutomationError(f"Git push failed:\n{pushed.stdout[-4000:]}")
        pr = run_process(["gh", "pr", "create", "--draft", "--repo", REPOSITORY, "--base", "main",
                          "--head", branch, "--title", f"[Guardian Local AI] {task['title']}",
                          "--body", task_prompt_body(task, outcomes, base_sha, model, branch)], worktree, 120)
        if pr.returncode != 0:
            raise AutomationError(f"Branch pushed but Draft PR creation failed; branch={branch}\n{pr.stdout[-4000:]}")
        pr_url = next((line.strip() for line in pr.stdout.splitlines() if line.strip().startswith("https://github.com/")), "")
        if not pr_url:
            raise AutomationError(f"gh pr create returned no verifiable PR URL:\n{pr.stdout[-2000:]}")
        with state_guard:
            state["tasks"][task_id] = {"status": "pr_open", "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "base_sha": base_sha, "commit": commit_sha, "branch": branch, "pr_url": pr_url, "model": model,
                "changed_files": sorted(changed), "tests": outcomes}
            write_state(roots["state"], state)
        keep_worktree = False
        return {"task": task_id, "status": "draft_pr_created", "branch": branch, "commit": commit_sha, "pr_url": pr_url, "tests": outcomes}
    except Exception as error:
        with state_guard:
            state["tasks"][task_id] = {"status": "failed", "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "branch": branch or None, "worktree": str(worktree) if worktree else None, "error": str(error)[:4000]}
            write_state(roots["state"], state)
        return {"task": task_id, "status": "failed", "error": str(error), "worktree_preserved": bool(worktree and worktree.exists())}
    finally:
        if worktree and worktree.exists() and not keep_worktree:
            try:
                git(repo, ["worktree", "remove", str(worktree)], timeout=120)
            except AutomationError as error:
                print(f"[{task_id}] owned worktree cleanup warning: {error}", file=sys.stderr)
        lease.release()

def default_roots() -> dict[str, Path]:
    local = os.environ.get("LOCALAPPDATA")
    root = Path(local) / "GuardianAutomation" if local else Path.home() / ".guardian-automation"
    return {"root": root, "leases": root / "leases", "slots": root / "ollama-slots",
            "worktrees": root / "worktrees", "logs": root / "logs", "state": root / "multiflow-state.json"}

def run(repo: Path, args: argparse.Namespace) -> int:
    repo = repo.resolve(strict=True)
    manifest_path = repo / args.manifest
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise AutomationError(f"Could not read task manifest {manifest_path}: {error}") from error
    tasks = validate_manifest(manifest)
    roots = default_roots()
    if args.validate_only:
        for task in tasks:
            if task["status"] == "ready":
                for path in task["allowed_files"]:
                    target = repo / Path(*PurePosixPath(path).parts)
                    if not target.is_file() or target.is_symlink():
                        raise AutomationError(f"Manifest file is missing or not a regular file: {path}")
        print(f"PASS: manifest schema, task IDs, test profiles and non-overlapping scopes ({len(tasks)} tasks).", flush=True)
        return 0
    for key in ("leases", "slots", "worktrees", "logs"):
        roots[key].mkdir(parents=True, exist_ok=True)
    base_url = validate_local_ollama_url(args.ollama_url)
    verify_ollama_model(base_url, args.model)
    if args.smoke_test:
        print(smoke_test(base_url, args.model, roots["slots"]))
        return 0
    verify_repository(repo)
    auth = run_process(["gh", "auth", "status", "--hostname", "github.com"], repo, 30)
    if auth.returncode != 0:
        raise AutomationError("GitHub CLI is not authenticated for this Windows runner account.")
    tasks_by_id = {task["id"]: task for task in tasks}
    if args.task_id and args.task_id not in tasks_by_id:
        raise AutomationError(f"Unknown task_id: {args.task_id}")
    selected = [task for task in tasks if task["status"] == "ready" and (not args.task_id or task["id"] == args.task_id)]
    if not selected:
        print("No ready Guardian tasks match the current selection.")
        return 0
    if args.max_tasks < 1 or args.max_tasks > 3:
        raise AutomationError("--max-tasks must be between 1 and 3.")
    state = read_state(roots["state"])
    completed = {task_id for task_id, entry in state["tasks"].items() if isinstance(entry, dict) and entry.get("status") == "integrated"}
    eligible = [task for task in selected if all(dep in completed for dep in task.get("dependencies", []))][:args.max_tasks]
    if not eligible:
        print("No tasks are eligible; dependency gates remain closed.")
        return 0
    scheduler_lease = FileLease(roots["leases"] / "scheduler.lock")
    if not scheduler_lease.acquire():
        print("Another Windows/GitHub multi-flow scheduler is active; no duplicate cycle was started.")
        return 0
    try:
        state_guard = threading.Lock()
        print(f"Guardian local multi-flow: model={args.model}, tasks={len(eligible)}, max_parallel={args.max_tasks}")
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.max_tasks) as pool:
            futures = [pool.submit(execute_task, repo, task, args.model, base_url, roots, state, state_guard) for task in eligible]
            results = [future.result() for future in futures]
        for result in results:
            print(json.dumps(result, ensure_ascii=False))
        return 0 if all(item["status"] in {"draft_pr_created", "skipped", "no_change"} for item in results) else 1
    finally:
        scheduler_lease.release()

def main() -> int:
    parser = argparse.ArgumentParser(description="Guardian local-only Ollama multi-flow worker.")
    parser.add_argument("--repo-root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--manifest", default=".guardian/automation/tasks.json")
    parser.add_argument("--model", default=os.environ.get("GUARDIAN_OLLAMA_MODEL", DEFAULT_MODEL))
    parser.add_argument("--ollama-url", default=os.environ.get("GUARDIAN_OLLAMA_URL", DEFAULT_OLLAMA_URL))
    parser.add_argument("--max-tasks", type=int, default=1)
    parser.add_argument("--task-id")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--smoke-test", action="store_true")
    args = parser.parse_args()
    try:
        return run(Path(args.repo_root), args)
    except AutomationError as error:
        print(f"Guardian multi-flow BLOCKED: {error}", file=sys.stderr, flush=True)
        return 2
    except Exception as error:
        print(f"Guardian multi-flow UNEXPECTED ERROR: {type(error).__name__}: {error}", file=sys.stderr, flush=True)
        return 2

if __name__ == "__main__":
    raise SystemExit(main())
