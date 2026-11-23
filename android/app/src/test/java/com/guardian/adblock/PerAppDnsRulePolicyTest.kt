package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class PerAppDnsRulePolicyTest {
    @Test fun normalizesAndDeduplicatesAdditionalDomains() {
        val parsed = PerAppDnsRulePolicy.parse(
            "# custom app rules\n Ads.Example.com \ntracker.example.com.\nads.example.com\n\n"
        )
        assertEquals(setOf("ads.example.com", "tracker.example.com"), parsed.normalizedRules)
        assertTrue(parsed.invalidLines.isEmpty())
    }

    @Test fun returnsInvalidLinesWithoutSilentlySavingThem() {
        val parsed = PerAppDnsRulePolicy.parse("valid.example.com\nnot a domain\n")
        assertEquals(setOf("valid.example.com"), parsed.normalizedRules)
        assertEquals(listOf("not a domain"), parsed.invalidLines)
    }

    @Test fun rejectsMoreThanTwoHundredRules() {
        try {
            PerAppDnsRulePolicy.parse((1..201).joinToString("\n") { "host$it.example.com" })
            fail("too many rules must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun validatesPackageNamesBeforeBuildingPreferenceKeys() {
        assertTrue(PerAppDnsRulePolicy.isValidPackageName("com.example.mail"))
        assertFalse(PerAppDnsRulePolicy.isValidPackageName("../private"))
        assertFalse(PerAppDnsRulePolicy.isValidPackageName("com..example"))
    }
}
