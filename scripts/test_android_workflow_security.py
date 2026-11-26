import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/android-apk.yml").read_text(encoding="utf-8")


def job_block(name: str) -> str:
    jobs = WORKFLOW.split("jobs:\n", 1)[1]
    match = re.search(rf"(?m)^  {re.escape(name)}:\s*$", jobs)
    if not match:
        raise AssertionError(f"Missing workflow job: {name}")
    next_job = re.search(r"(?m)^  [A-Za-z0-9_-]+:\s*$", jobs[match.end():])
    end = match.end() + next_job.start() if next_job else len(jobs)
    return jobs[match.start():end]


class AndroidWorkflowPermissionTests(unittest.TestCase):
    def test_workflow_default_token_is_read_only(self):
        match = re.search(r"(?m)^permissions:\n(?P<body>(?:^  [^\n]*\n)+)", WORKFLOW)
        self.assertIsNotNone(match, "top-level permissions must be explicit")
        self.assertIn("contents: read", match.group("body"))
        self.assertNotIn("contents: write", match.group("body"))

    def test_android_build_job_cannot_publish_releases(self):
        build = job_block("build-apk")
        self.assertNotIn("contents: write", build)
        self.assertNotIn("gh release create", build)
        self.assertNotIn("gh release upload", build)

    def test_release_write_permission_is_isolated_to_main_only_job(self):
        publish = job_block("publish-development-release")
        self.assertIn("needs: build-apk", publish)
        self.assertIn("if: github.ref == 'refs/heads/main' && (github.event_name == 'push' || github.event_name == 'workflow_dispatch')", publish)
        self.assertRegex(publish, r"(?m)^    permissions:\n      contents: write$")
        self.assertIn("actions/download-artifact@v4", publish)
        self.assertIn("gh release create", publish)
        self.assertIn("gh release upload", publish)


if __name__ == "__main__":
    unittest.main()
