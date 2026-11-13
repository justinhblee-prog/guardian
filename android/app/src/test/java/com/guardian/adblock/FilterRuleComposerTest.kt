package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterRuleComposerTest {
    @Test fun badfilterOnlyDisablesRuleFromItsOwnSource() {
        val sourceA = listOf("||ads.example.com^", "||ads.example.com^\$badfilter")
        val sourceB = listOf("||ads.example.com^")
        val composed = FilterRuleComposer.compose(listOf(sourceA, sourceB))
        DomainFilter.loadRules(composed)
        assertTrue(DomainFilter.isBlocked("ads.example.com"))
    }

    @Test fun userAllowOverridesAllComposedSources() {
        val composed = FilterRuleComposer.compose(
            sources = listOf(listOf("||ads.example.com^"), listOf("||tracker.example.net^")),
            userAllowDomains = listOf("trusted.ads.example.com")
        )
        DomainFilter.loadRules(composed)
        assertTrue(DomainFilter.isBlocked("ads.example.com"))
        assertFalse(DomainFilter.isBlocked("trusted.ads.example.com"))
    }
    @Test fun importantBlockOverridesUserAllowException() {
        val composed = FilterRuleComposer.compose(
            sources = listOf(listOf("||critical.example^$important")),
            userAllowDomains = listOf("critical.example")
        )
        DomainFilter.loadRules(composed)
        assertTrue(DomainFilter.isBlocked("critical.example"))
    }

}
