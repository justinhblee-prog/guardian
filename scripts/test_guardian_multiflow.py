"""Unit tests for the Guardian local multi-flow safety boundary."""
from __future__ import annotations
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.guardian_multiflow import (
    AutomationError,
    FileLease,
    process_environment,
    safe_relative_path,
    validate_local_ollama_url,
    validate_manifest,
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
        root = Path(__file__).resolve().parents[1]
        script = root / "scripts" / "guardian_multiflow.py"
        result = subprocess.run(
            [sys.executable, str(script), "--validate-only", "--repo-root", str(root)],
            cwd=str(root),
            text=True,
            encoding="utf-8",
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("PASS: manifest schema", result.stdout)

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

if __name__ == "__main__":
    unittest.main()
