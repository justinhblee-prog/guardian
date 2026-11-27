import unittest
from pathlib import Path

from scripts.resolve_guardian_private_update_version import resolve_next_version


class PrivateUpdateVersionTests(unittest.TestCase):
    def test_dev_version_increments_from_private_dev_channel(self):
        result = resolve_next_version(
            [
                {"channel": "dev", "version_code": 74, "version_name": "0.7.61-dev"},
                {"channel": "beta", "version_code": 79, "version_name": "0.7.62-beta"},
            ],
            "dev",
            "0.7.61-dev",
            74,
        )
        self.assertEqual(result["version_name"], "0.7.62-dev")
        self.assertEqual(result["version_code"], 80)
        self.assertEqual(result["tag"], "guardian-dev-v80")

    def test_beta_version_increments_independently_but_code_remains_global(self):
        result = resolve_next_version(
            [
                {"channel": "dev", "version_code": 83, "version_name": "0.7.64-dev"},
                {"channel": "beta", "version_code": 80, "version_name": "0.7.63-beta"},
            ],
            "beta",
            "0.7.61-beta",
            74,
        )
        self.assertEqual(result["version_name"], "0.7.64-beta")
        self.assertEqual(result["version_code"], 84)
        self.assertEqual(result["tag"], "guardian-beta-v84")

    def test_empty_private_feed_uses_channel_baseline(self):
        result = resolve_next_version([], "beta", "0.7.61-beta", 74)
        self.assertEqual(result["version_name"], "0.7.62-beta")
        self.assertEqual(result["version_code"], 75)

    def test_rejects_invalid_channel_and_channel_version_mismatch(self):
        with self.assertRaises(ValueError):
            resolve_next_version([], "stable", "1.0.0", 1)
        with self.assertRaises(ValueError):
            resolve_next_version(
                [{"channel": "beta", "version_code": 80, "version_name": "0.7.63-dev"}],
                "beta",
                "0.7.61-beta",
                74,
            )


    def test_build_and_publish_jobs_use_literal_dot_channel_version_pattern(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github/workflows/android-apk.yml").read_text(encoding="utf-8")
        expected = r'assert re.fullmatch(rf"[0-9]+\.[0-9]+\.[0-9]+-{channel}", manifest["versionName"])'
        self.assertEqual(workflow.count(expected), 2)


if __name__ == "__main__":
    unittest.main()
