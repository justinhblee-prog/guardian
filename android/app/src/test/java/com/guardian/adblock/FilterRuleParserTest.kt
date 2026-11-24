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
        assertEquals(setOf("ads.example.com"), parsed.blocked)
        assertEquals(setOf("tracker.example.net"), parsed.importantBlocked)
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

    @Test fun hostsFileDoesNotTreatSinkAddressesAsBlockedHostnames() {
        val parsed = FilterRuleParser.parse(listOf(
            "0.0.0.0 0.0.0.0 ads.example.com",
            "127.0.0.1 127.0.0.1 tracker.example.net",
            "0.0.0.0 999.1.1.1 invalid.example.org"
        ))
        assertEquals(setOf("ads.example.com", "tracker.example.net", "invalid.example.org"), parsed.blocked)
    }

    @Test fun ignoresCosmeticWildcardAndMalformedRules() {
        val parsed = FilterRuleParser.parse(listOf(
            "##.ad-banner",
            "||*.wild.example^",
            "||bad_domain.example^",
            "||good.example^"
        ))
        assertEquals(setOf("good.example"), parsed.blocked)
        assertTrue(parsed.ignored >= 1)
    }

    @Test fun doesNotBroadenPathOrSuffixConstrainedRulesToWholeDomains() {
        val parsed = FilterRuleParser.parse(listOf(
            "||path.example^/ads/",
            "||suffix.example^tracker",
            "@@||trusted.example^/login",
            "||safe.example^"
        ))
        assertEquals(setOf("safe.example"), parsed.blocked)
        assertTrue(parsed.allowed.isEmpty())
        assertEquals(3, parsed.ignored)
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

    @Test fun parsesLargeFeedTextWithoutMaterializingLineList() {
        val text = (1..5000).joinToString("\n") { "||host$it.example^" }
        val parsed = FilterRuleParser.parse(text)
        assertEquals(5000, parsed.blocked.size)
        assertTrue(parsed.blocked.contains("host5000.example"))
    }

    @Test fun parsesReaderIncrementallyWithSameResultsAsText() {
        val text = "||ads.example^\n@@||trusted.ads.example^\n||priority.example^\$important"
        val fromText = FilterRuleParser.parse(text)
        val fromReader = FilterRuleParser.parse(text.reader())
        assertEquals(fromText, fromReader)
    }

    @Test fun acceptsUtf8BomBeforeFirstFilterRule() {
        val parsed = FilterRuleParser.parse(listOf("\uFEFF||ads.example.com^"))
        assertEquals(setOf("ads.example.com"), parsed.blocked)
        assertEquals(0, parsed.ignored)
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
    @Test fun safelyScansEmptyAndMultipleModifierTokens() {
        val parsed = FilterRuleParser.parse(listOf(
            "||empty-suffix.example^\$",
            "||multiple.example^\$third-party,important",
            "||cancelled.example^\$important,badfilter",
            "||survives.example^\$important"
        ))
        assertEquals(setOf("empty-suffix.example"), parsed.blocked)
        // URL-context modifiers cannot be represented by DNS filtering.
        // Reject constrained rules instead of broadening them to every request.
        assertEquals(setOf("survives.example"), parsed.importantBlocked)
        assertEquals(1, parsed.ignored)
    }

    @Test fun badfilterDisablesAnImportantBlockRule() {
        val parsed = FilterRuleParser.parse(listOf(
            "||priority.example^\$important",
            "||priority.example^\$important,badfilter"
        ))
        assertTrue(parsed.importantBlocked.isEmpty())
        assertTrue(parsed.blocked.isEmpty())
    }

    @Test fun importantModifierOnExceptionIsIgnoredRatherThanMisapplied() {
        val parsed = FilterRuleParser.parse(listOf("@@||trusted.example^\$important"))
        assertTrue(parsed.allowed.isEmpty())
        assertEquals(1, parsed.ignored)
    }

    @Test fun badfilterOnlyCancelsTheMatchingImportantPriorityVariant() {
        val ordinaryDisabled = FilterRuleParser.parse(listOf(
            "||same.example^",
            "||same.example^\$important",
            "||same.example^\$badfilter"
        ))
        assertTrue(ordinaryDisabled.blocked.isEmpty())
        assertEquals(setOf("same.example"), ordinaryDisabled.importantBlocked)

        val importantDisabled = FilterRuleParser.parse(listOf(
            "||same.example^",
            "||same.example^\$important",
            "||same.example^\$important,badfilter"
        ))
        assertEquals(setOf("same.example"), importantDisabled.blocked)
        assertTrue(importantDisabled.importantBlocked.isEmpty())
    }

}
