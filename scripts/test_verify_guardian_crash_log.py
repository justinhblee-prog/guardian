import unittest

from scripts.verify_guardian_crash_log import guardian_process_crashed


class GuardianCrashLogTests(unittest.TestCase):
    def test_unrelated_google_wallpaper_crash_does_not_fail_guardian_check(self):
        log = """--------- beginning of crash
E AndroidRuntime: FATAL EXCEPTION: DefaultDispatcher-worker-1
E AndroidRuntime: Process: com.google.android.apps.wallpaper, PID: 2033
E AndroidRuntime: java.util.ConcurrentModificationException
"""
        self.assertFalse(guardian_process_crashed(log))

    def test_guardian_fatal_exception_is_detected(self):
        log = """E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.guardian.adblock, PID: 2507
E AndroidRuntime: java.lang.IllegalStateException
"""
        self.assertTrue(guardian_process_crashed(log))

    def test_guardian_process_death_is_detected(self):
        log = "ActivityManager: Process com.guardian.adblock (pid 2507) has died: fg TOP"
        self.assertTrue(guardian_process_crashed(log))

    def test_unrelated_crash_does_not_match_later_guardian_mentions(self):
        log = """E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.google.android.apps.wallpaper, PID: 2033
I ActivityManager: Start proc 2507:com.guardian.adblock/u0a209 for activity
"""
        self.assertFalse(guardian_process_crashed(log))

    def test_normal_log_is_clean(self):
        self.assertFalse(guardian_process_crashed("I Guardian: protection startup completed"))


if __name__ == "__main__":
    unittest.main()
