#!/usr/bin/env python3
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from resolve_guardian_dev_version import DEFAULT_BASELINE_CODE, DEFAULT_BASELINE_VERSION, bump_patch, resolve_next_version


class GuardianDevVersionTests(unittest.TestCase):
    def setUp(self):
        self.releases = [{
            "tag_name": "guardian-dev-v55",
            "name": "Guardian AdBlock 0.7.42-dev (개발 업데이트)",
            "draft": False,
            "prerelease": True,
            "body": "Legacy release without source marker."
        }]

    def test_default_baseline_matches_android_fallback_version(self):
        gradle = (Path(__file__).resolve().parents[1] / "android/app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('getOrElse("' + bump_patch(DEFAULT_BASELINE_VERSION).removesuffix("-dev") + '")', gradle)
        self.assertIn("getOrElse(" + str(DEFAULT_BASELINE_CODE + 1) + ")", gradle)

    def test_next_release_increments_patch_and_version_code(self):
        result = resolve_next_version(self.releases, "a" * 40, "0.7.42-dev", 55)
        self.assertEqual((result["version_name"], result["version_code"], result["tag"]),
                         ("0.7.43-dev", 56, "guardian-dev-v56"))
        self.assertFalse(result["reused"])

    def test_same_commit_rerun_reuses_existing_release(self):
        self.releases.append({
            "tag_name": "guardian-dev-v55",
            "name": "Guardian AdBlock 0.7.42-dev (개발 업데이트)",
            "draft": False, "prerelease": True,
            "body": "Guardian-Source-Commit: " + "b" * 40
        })
        result = resolve_next_version(self.releases, "b" * 40, "0.7.42-dev", 55)
        self.assertEqual((result["version_name"], result["version_code"], result["tag"]),
                         ("0.7.42-dev", 55, "guardian-dev-v55"))
        self.assertTrue(result["reused"])

    def test_older_commit_cannot_republish_after_newer_release(self):
        self.releases.extend([
            {"tag_name": "guardian-dev-v56", "name": "Guardian AdBlock 0.7.43-dev",
             "draft": False, "prerelease": True, "body": "Guardian-Source-Commit: " + "c" * 40},
            {"tag_name": "guardian-dev-v57", "name": "Guardian AdBlock 0.7.44-dev",
             "draft": False, "prerelease": True, "body": "Guardian-Source-Commit: " + "d" * 40}
        ])
        with self.assertRaisesRegex(ValueError, "older release"):
            resolve_next_version(self.releases, "c" * 40, "0.7.42-dev", 55)

    def test_ignores_drafts_and_stable_releases(self):
        self.releases.extend([
            {"tag_name": "guardian-dev-v55", "name": "Guardian AdBlock 0.7.42-dev",
             "draft": True, "prerelease": True},
            {"tag_name": "guardian-dev-v56", "name": "Guardian AdBlock 0.7.43",
             "draft": False, "prerelease": False}
        ])
        result = resolve_next_version(self.releases, "e" * 40, "0.7.42-dev", 55)
        self.assertEqual((result["version_name"], result["version_code"]),
                         ("0.7.43-dev", 56))

    def test_development_version_code_cannot_enter_production_range(self):
        releases = [{"tag_name": "guardian-dev-v1999999", "name": "Guardian AdBlock 0.7.99-dev",
                     "draft": False, "prerelease": True, "body": ""}]
        with self.assertRaisesRegex(ValueError, "reserved production range"):
            resolve_next_version(releases, "9" * 40, "0.7.42-dev", 55)

    def test_patch_increments_past_99_without_resetting_minor(self):
        releases = [{"tag_name": "guardian-dev-v99", "name": "Guardian AdBlock 0.7.99-dev",
                     "draft": False, "prerelease": True, "body": ""}]
        result = resolve_next_version(releases, "f" * 40, "0.7.42-dev", 55)
        self.assertEqual((result["version_name"], result["version_code"]),
                         ("0.7.100-dev", 100))


if __name__ == "__main__":
    unittest.main()
