"""Unit tests for the Guardian local multi-flow safety boundary."""
from __future__ import annotations
import json
import subprocess
import sys
import tempfile
import socket
import time
from contextlib import nullcontext, redirect_stderr, redirect_stdout
from unittest.mock import mock_open, patch
from io import StringIO
import unittest
from pathlib import Path

# Direct execution (python scripts/test_guardian_multiflow.py) places only the
# scripts directory on sys.path. Add the repository root so the same import
# works both directly on Windows and via python -m unittest.
REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.guardian_multiflow import (
    AutomationError,
    FileLease,
    main,
    ollama_json,
    process_environment,
    propose_patch,
    safe_relative_path,
    smoke_test,
    validate_local_ollama_url,
    validate_manifest,
    verify_ollama_model,
    validate_unified_patch,
)

def task(task_id="sample-task", allowed=None, profile="android-unit-lint"):
    return {
        "id": task_id,
        "title": "Sample task",
        "priority": 1,
        "status": "ready",
        "dependencies": [],
        "scope": "Test scope only.",
        "allowed_files": allowed or ["android/app/src/test/java/com/guardian/adblock/ExampleTest.kt"],
        "test_profile": profile,
        "acceptance_criteria": ["A test verifies the behavior."],
    }

class GuardianMultiFlowValidationTests(unittest.TestCase):
    def test_validate_only_cli_reports_success_and_zero_exit(self):
        from unittest.mock import patch
        root = Path(__file__).resolve().parents[1]
        stdout = StringIO()
        stderr = StringIO()
        argv = ["guardian_multiflow.py", "--validate-only", "--repo-root", str(root)]
        with patch("sys.argv", argv), redirect_stdout(stdout), redirect_stderr(stderr):
            exit_code = main()
        self.assertEqual(exit_code, 0, stdout.getvalue() + stderr.getvalue())
        self.assertIn("PASS: manifest schema", stdout.getvalue())
        print("PASS: validate-only CLI returned exit code 0.")

    def test_real_manifest_has_three_disjoint_ready_tracks(self):
        root = Path(__file__).resolve().parents[1]
        manifest = json.loads((root / ".guardian/automation/tasks.json").read_text(encoding="utf-8"))
        tasks = validate_manifest(manifest)
        ready = [item for item in tasks if item["status"] == "ready"]
        self.assertGreaterEqual(len(ready), 3)
        all_paths = [path for item in ready for path in item["allowed_files"]]
        self.assertEqual(len(all_paths), len(set(all_paths)))

    def test_manifest_rejects_unknown_executable_test_profile(self):
        with self.assertRaisesRegex(AutomationError, "Unknown fixed test profile"):
            validate_manifest({"schema_version": 1, "tasks": [task(profile="powershell -Command whoami")]})

    def test_manifest_rejects_overlapping_ready_task_scopes(self):
        same = "android/app/src/test/java/com/guardian/adblock/ExampleTest.kt"
        with self.assertRaisesRegex(AutomationError, "overlap"):
            validate_manifest({"schema_version": 1, "tasks": [task("first-task", [same]), task("second-task", [same])]})

    def test_manifest_rejects_duplicate_task_ids(self):
        with self.assertRaisesRegex(AutomationError, "Duplicate task id"):
            validate_manifest({"schema_version": 1, "tasks": [task("same-task"), task("same-task")]})

    def test_manifest_rejects_invalid_dependency(self):
        entry = task()
        entry["dependencies"] = ["missing-task"]
        with self.assertRaisesRegex(AutomationError, "invalid dependency"):
            validate_manifest({"schema_version": 1, "tasks": [entry]})

    def test_manifest_rejects_empty_acceptance_criteria(self):
        entry = task()
        entry["acceptance_criteria"] = []
        with self.assertRaisesRegex(AutomationError, "acceptance_criteria"):
            validate_manifest({"schema_version": 1, "tasks": [entry]})

    def test_repository_paths_reject_traversal_and_absolute_paths(self):
        for path in ("../README.md", "/etc/passwd", "C:/Windows/win.ini", "scripts\\agent.ps1"):
            with self.subTest(path=path), self.assertRaises(AutomationError):
                safe_relative_path(path)

    def test_repository_paths_reject_dotenv_variants_and_secrets(self):
        for path in (".env.production", "server/license-api/secrets/private.pem", "android/app/build/outputs/app.apk"):
            with self.subTest(path=path), self.assertRaises(AutomationError):
                safe_relative_path(path)

    def test_repository_paths_reject_automation_control_plane(self):
        for path in (".github/workflows/new.yml", "scripts/guardian-git-agent.ps1", ".guardian/automation/tasks.json"):
            with self.subTest(path=path), self.assertRaises(AutomationError):
                safe_relative_path(path)

    def test_test_process_environment_strips_credentials_and_artifact_tokens(self):
        from unittest.mock import patch
        with patch.dict("os.environ", {
            "GH_TOKEN": "temporary-token",
            "GITHUB_TOKEN": "temporary-token",
            "CUSTOM_CLIENT_SECRET": "temporary-secret",
            "ACTIONS_RUNTIME_TOKEN": "runtime-token",
            "PATH": "C:\\Windows\\System32",
            "GUARDIAN_OLLAMA_MODEL": "qwen2.5-coder:7b",
        }, clear=True):
            env = process_environment(strip_credentials=True)
        self.assertNotIn("GH_TOKEN", env)
        self.assertNotIn("GITHUB_TOKEN", env)
        self.assertNotIn("CUSTOM_CLIENT_SECRET", env)
        self.assertNotIn("ACTIONS_RUNTIME_TOKEN", env)
        self.assertNotIn("ACTIONS_RUNTIME_URL", env)
        self.assertEqual(env["PATH"], "C:\\Windows\\System32")
        self.assertEqual(env["GUARDIAN_OLLAMA_MODEL"], "qwen2.5-coder:7b")

    def test_ollama_tags_accepts_installed_exact_model_name(self):
        payload = b'{"models":[{"name":"qwen2.5-coder:7b"}]}'
        with patch("scripts.guardian_multiflow.urllib.request.urlopen", mock_open(read_data=payload)):
            verify_ollama_model("http://127.0.0.1:11434", "qwen2.5-coder:7b")

    def test_ollama_tags_rejects_missing_model_without_download(self):
        payload = b'{"models":[{"name":"qwen2.5-coder:3b"}]}'
        with patch("scripts.guardian_multiflow.urllib.request.urlopen", mock_open(read_data=payload)) as urlopen:
            with self.assertRaisesRegex(AutomationError, "not installed"):
                verify_ollama_model("http://127.0.0.1:11434", "qwen2.5-coder:7b")
        self.assertEqual(urlopen.call_count, 1)

    def test_ollama_tags_rejects_non_object_or_missing_models_array(self):
        for payload in (b'[]', b'{"models":{}}', b'{"model":[] }'):
            with self.subTest(payload=payload):
                with patch("scripts.guardian_multiflow.urllib.request.urlopen", mock_open(read_data=payload)):
                    with self.assertRaisesRegex(AutomationError, "models array"):
                        verify_ollama_model("http://127.0.0.1:11434", "qwen2.5-coder:7b")

    def test_ollama_json_rejects_invalid_utf8_and_missing_message(self):
        for payload in (b'\\xff', b'{"response":"not-chat"}'):
            with self.subTest(payload=payload):
                with patch("scripts.guardian_multiflow.urllib.request.urlopen", mock_open(read_data=payload)):
                    with self.assertRaises(AutomationError):
                        ollama_json("http://127.0.0.1:11434", {"model": "qwen2.5-coder:7b"})

    def test_smoke_test_requires_the_exact_json_token(self):
        good = {"message": {"content": '{"token":"GUARDIAN_LOCAL_OLLAMA_OK"}'}}
        with (
            patch("scripts.guardian_multiflow.verify_ollama_model"),
            patch("scripts.guardian_multiflow.ollama_slot", return_value=nullcontext()),
            patch("scripts.guardian_multiflow.ollama_json", return_value=good),
        ):
            result = smoke_test("http://127.0.0.1:11434", "qwen2.5-coder:7b", Path("slots"))
        self.assertIn("PASS:", result)

    def test_smoke_test_rejects_non_object_json_and_wrong_token(self):
        for content in ('[]', '{"token":"WRONG"}'):
            with self.subTest(content=content):
                with (
                    patch("scripts.guardian_multiflow.verify_ollama_model"),
                    patch("scripts.guardian_multiflow.ollama_slot", return_value=nullcontext()),
                    patch("scripts.guardian_multiflow.ollama_json", return_value={"message": {"content": content}}),
                ):
                    with self.assertRaisesRegex(AutomationError, "unexpected token"):
                        smoke_test("http://127.0.0.1:11434", "qwen2.5-coder:7b", Path("slots"))

    def test_propose_patch_accepts_only_the_exact_diff_envelope(self):
        task_data = task()
        with (
            patch("scripts.guardian_multiflow.ollama_slot", return_value=nullcontext()),
            patch("scripts.guardian_multiflow.ollama_json", return_value={"message": {"content": '{"diff":""}'}}),
        ):
            self.assertEqual(
                propose_patch("http://127.0.0.1:11434", "qwen2.5-coder:7b", Path("slots"), task_data, "source"),
                "",
            )

        invalid = (
            '{"diff":"","extra":"not allowed"}',
            '{"diff":null}',
            '[]',
            'not-json',
        )
        for content in invalid:
            with self.subTest(content=content):
                with (
                    patch("scripts.guardian_multiflow.ollama_slot", return_value=nullcontext()),
                    patch("scripts.guardian_multiflow.ollama_json", return_value={"message": {"content": content}}),
                ):
                    with self.assertRaises(AutomationError):
                        propose_patch("http://127.0.0.1:11434", "qwen2.5-coder:7b", Path("slots"), task_data, "source")

    def test_ollama_url_must_be_loopback(self):
        self.assertEqual(validate_local_ollama_url("http://127.0.0.1:11434/"), "http://127.0.0.1:11434")
        self.assertEqual(validate_local_ollama_url("http://localhost:11434"), "http://localhost:11434")
        for url in ("https://127.0.0.1:11434", "http://example.com:11434", "http://10.0.0.4:11434", "http://user:pass@localhost:11434"):
            with self.subTest(url=url), self.assertRaises(AutomationError):
                validate_local_ollama_url(url)

    def test_empty_diff_is_a_safe_no_change_result(self):
        self.assertEqual(validate_unified_patch("", {"README.md"}), set())
        self.assertEqual(validate_unified_patch("  \n", {"README.md"}), set())

    def test_patch_accepts_only_exactly_allowed_existing_paths(self):
        patch = (
            "diff --git a/android/app/src/test/java/com/guardian/adblock/ExampleTest.kt b/android/app/src/test/java/com/guardian/adblock/ExampleTest.kt\n"
            "index 1111111..2222222 100644\n"
            "--- a/android/app/src/test/java/com/guardian/adblock/ExampleTest.kt\n"
            "+++ b/android/app/src/test/java/com/guardian/adblock/ExampleTest.kt\n"
            "@@ -1 +1 @@\n-old\n+new\n"
        )
        allowed = {"android/app/src/test/java/com/guardian/adblock/ExampleTest.kt"}
        self.assertEqual(validate_unified_patch(patch, allowed), allowed)

    def test_patch_rejects_path_outside_task_allowlist(self):
        patch = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-old\n+new\n"
        with self.assertRaisesRegex(AutomationError, "outside its exact allowlist"):
            validate_unified_patch(patch, {"docs/README.md"})

    def test_patch_rejects_file_markers_that_escape_an_allowed_header(self):
        patch = (
            "diff --git a/README.md b/README.md\n"
            "--- a/README.md\n"
            "+++ b/docs/README-extra.md\n"
            "@@ -1 +1 @@\n-old\n+secret\n"
        )
        with self.assertRaisesRegex(AutomationError, "header/marker path mismatch"):
            validate_unified_patch(patch, {"README.md"})

    def test_patch_rejects_second_file_marker_even_when_header_is_allowed(self):
        patch = (
            "diff --git a/README.md b/README.md\n"
            "--- a/README.md\n"
            "+++ b/README.md\n"
            "@@ -1 +1 @@\n-old\n+new\n"
            "+++ b/.github/workflows/evil.yml\n"
        )
        with self.assertRaises(AutomationError):
            validate_unified_patch(patch, {"README.md"})

    def test_patch_rejects_binary_creation_deletion_and_mode_changes(self):
        allowed = {"README.md"}
        examples = (
            "diff --git a/README.md b/README.md\nGIT binary patch\n",
            "diff --git a/README.md b/README.md\nnew file mode 100644\n",
            "diff --git a/README.md b/README.md\ndeleted file mode 100644\n",
            "diff --git a/README.md b/README.md\nold mode 100644\nnew mode 100755\n",
            "diff --git a/README.md b/README.md\n--- a/README.md\n+++ /dev/null\n",
        )
        for patch in examples:
            with self.subTest(patch=patch), self.assertRaises(AutomationError):
                validate_unified_patch(patch, allowed)

    def test_patch_rejects_duplicate_file_sections(self):
        section = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-old\n+new\n"
        with self.assertRaisesRegex(AutomationError, "repeats a file"):
            validate_unified_patch(section + section, {"README.md"})

    def test_file_lease_prevents_duplicate_process_ownership_and_releases(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "task.lock"
            first = FileLease(path)
            second = FileLease(path)
            self.assertTrue(first.acquire())
            self.assertFalse(second.acquire())
            first.release()
            self.assertTrue(second.acquire())
            second.release()
            self.assertFalse(path.exists())

    def test_file_lease_does_not_steal_live_owner_even_after_ttl_expiry(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "task.lock"
            first = FileLease(path, ttl_seconds=0)
            self.assertTrue(first.acquire())
            second = FileLease(path)
            self.assertFalse(second.acquire())
            first.release()

    def test_file_lease_reclaims_expired_lock_when_owner_is_confirmed_dead(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "task.lock"
            path.write_text(json.dumps({
                "token": "expired-owner",
                "pid": 2147483647,
                "host": socket.gethostname(),
                "created_unix": time.time() - 120,
                "expires_unix": time.time() - 60,
            }), encoding="utf-8")
            lease = FileLease(path)
            with patch.object(FileLease, "_pid_alive", return_value=False):
                self.assertTrue(lease.acquire())
            self.assertEqual(json.loads(path.read_text(encoding="utf-8"))["token"], lease.token)
            lease.release()
            self.assertFalse(path.exists())

    def test_windows_pid_probe_detects_a_terminated_child_process(self):
        child = subprocess.Popen([sys.executable, "-c", "pass"])
        child.wait(timeout=15)
        self.assertFalse(FileLease._pid_alive(child.pid))

if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(GuardianMultiFlowValidationTests)
    result = unittest.TextTestRunner(stream=sys.stdout, verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
