package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterRuleParserTest {
    @Test fun parsesAdguardDnsAnchoredRulesAndExceptions() {
        val parsed = FilterRuleParser.parse(listOf(
            "||ads.example.com^",
            "@@||trusted.ads.example.com^",
            "||tracker.example.net^\$important",
            "! comment"
        ))
        assertEquals(setOf("ads.example.com", "tracker.example.net"), parsed.blocked)
        assertEquals(setOf("trusted.ads.example.com"), parsed.allowed)
    }

    @Test fun parsesHostsFileAndIgnoresLocalhost() {
        val parsed = FilterRuleParser.parse(listOf(
            "0.0.0.0 ads.example.com tracker.example.net",
            "127.0.0.1 telemetry.example.org",
            "0.0.0.0 localhost"
        ))
        assertEquals(setOf("ads.example.com", "tracker.example.net", "telemetry.example.org"), parsed.blocked)
    }

    @Test fun ignoresCosmeticWildcardAndMalformedRules() {
        val parsed = FilterRuleParser.parse(listOf(
            "##.ad-banner",
            "||*.wild.example^",
            "||bad_domain.example^",
            "||good.example^"
        ))
        assertEquals(setOf("good.example"), parsed.blocked)
        assertTrue(parsed.ignored >= 2)
    }

    @Test fun lowercasesAndDeduplicatesDomains() {
        val parsed = FilterRuleParser.parse(listOf(
            "||ADS.Example.COM^",
            "||ads.example.com^"
        ))
        assertEquals(setOf("ads.example.com"), parsed.blocked)
    }

    @Test fun badfilterDisablesMatchingBlockAndUnsupportedContextIsIgnored() {
        val parsed = FilterRuleParser.parse(listOf(
            "||disabled.example^",
            "||disabled.example^\$badfilter",
            "||context.example^\$third-party",
            "||safe.example^"
        ))
        assertEquals(setOf("safe.example"), parsed.blocked)
        assertTrue(parsed.ignored >= 1)
    }

    @Test fun acceptsSingleLabelOnlyForExplicitGuardianRules() {
        val parsed = FilterRuleParser.parse(listOf(
            "block=localhost",
            "||localhost^"
        ))
        assertEquals(setOf("localhost"), parsed.blocked)
        assertTrue(parsed.ignored >= 1)
    }

    @Test fun rejectsSingleLabelAndInvalidHostnameLabels() {
        val parsed = FilterRuleParser.parse(listOf(
            "||localhost^",
            "||-bad.example^",
            "||bad-.example^",
            "||good.example^"
        ))
        assertEquals(setOf("good.example"), parsed.blocked)
    }
}
