import unittest
from verify_guardian_versions import verify


class GuardianVersionConsistencyTests(unittest.TestCase):
    def test_android_windows_installer_and_changelog_are_aligned(self):
        self.assertIn("0.7.49", verify())


if __name__ == "__main__":
    unittest.main()
