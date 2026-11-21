#!/usr/bin/env python3
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from resolve_guardian_dev_version import resolve_next_version


class GuardianDevVersionTests(unittest.TestCase):
    def setUp(self):
        self.releases = [{
            "tag_name": "guardian-dev-v1001804",
            "name": "Guardian AdBlock 0.7.34-dev (개발 업데이트)",
            "draft": False,
            "prerelease": True,
            "body": "Legacy release without source marker."
        }]

    def test_next_release_increments_patch_and_version_code(self):
        result = resolve_next_version(self.releases, "a" * 40, "0.7.34-dev", 47)
        self.assertEqual((result["version_name"], result["version_code"], result["tag"]),
                         ("0.7.35-dev", 1001805, "guardian-dev-v1001805"))
        self.assertFalse(result["reused"])

    def test_same_commit_rerun_reuses_existing_release(self):
        self.releases.append({
            "tag_name": "guardian-dev-v1001805",
            "name": "Guardian AdBlock 0.7.35-dev (개발 업데이트)",
            "draft": False, "prerelease": True,
            "body": "Guardian-Source-Commit: " + "b" * 40
        })
        result = resolve_next_version(self.releases, "b" * 40, "0.7.34-dev", 47)
        self.assertEqual(result["version_code"], 1001805)
        self.assertTrue(result["reused"])

    def test_older_commit_cannot_republish_after_newer_release(self):
        self.releases.extend([
            {"tag_name": "guardian-dev-v1001805", "name": "Guardian AdBlock 0.7.35-dev",
             "draft": False, "prerelease": True, "body": "Guardian-Source-Commit: " + "c" * 40},
            {"tag_name": "guardian-dev-v1001806", "name": "Guardian AdBlock 0.7.36-dev",
             "draft": False, "prerelease": True, "body": "Guardian-Source-Commit: " + "d" * 40}
        ])
        with self.assertRaisesRegex(ValueError, "older release"):
            resolve_next_version(self.releases, "c" * 40, "0.7.34-dev", 47)

    def test_ignores_drafts_and_stable_releases(self):
        self.releases.extend([
            {"tag_name": "guardian-dev-v1001899", "name": "Guardian AdBlock 0.7.99-dev",
             "draft": True, "prerelease": True},
            {"tag_name": "guardian-dev-v1001900", "name": "Guardian AdBlock 0.7.100",
             "draft": False, "prerelease": False}
        ])
        result = resolve_next_version(self.releases, "e" * 40, "0.7.34-dev", 47)
        self.assertEqual(result["version_code"], 1001805)

    def test_rolls_patch_into_minor(self):
        releases = [{"tag_name": "guardian-dev-v1001880", "name": "Guardian AdBlock 0.7.99-dev",
                     "draft": False, "prerelease": True, "body": ""}]
        result = resolve_next_version(releases, "f" * 40, "0.7.34-dev", 47)
        self.assertEqual(result["version_name"], "0.8.0-dev")
        self.assertEqual(result["version_code"], 1001881)


if __name__ == "__main__":
    unittest.main()
