#!/usr/bin/env python3
"""Keep responsive navigation assertions aligned with Guardian's real primary tabs."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ResponsiveNavigationContractTest(unittest.TestCase):
    def test_responsive_emulator_covers_exact_primary_destinations(self):
        navigation = (ROOT / "android/app/src/main/java/com/guardian/adblock/GuardianNavigation.kt").read_text(
            encoding="utf-8"
        )
        responsive = (ROOT / "scripts/verify_android_responsive_emulator.sh").read_text(
            encoding="utf-8"
        )

        items_match = re.search(
            r"val primaryItems = listOf\((.*?)\n\s*\)",
            navigation,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(items_match, "Guardian primary navigation contract was not found.")

        source_labels = tuple(
            re.findall(
                r'GuardianNavigationItem\("[^"]+",\s*"[^"]+",\s*"([^"]+)"\)',
                items_match.group(1),
            )
        )
        labels_match = re.search(r"labels=\(([^)]*)\)", responsive)
        self.assertIsNotNone(labels_match, "Responsive emulator navigation labels were not found.")
        test_labels = tuple(re.findall(r'"([^"]+)"', labels_match.group(1)))

        self.assertEqual(
            source_labels,
            test_labels,
            "Responsive emulator navigation labels must match GuardianNavigation.primaryItems.",
        )


if __name__ == "__main__":
    unittest.main()
