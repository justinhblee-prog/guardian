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
            sources = listOf(listOf("||critical.example^\$important")),
            userAllowDomains = listOf("critical.example")
        )
        DomainFilter.loadRules(composed)
        assertTrue(DomainFilter.isBlocked("critical.example"))
    }

    @Test fun parsedMergeKeepsBadfilterScopedToItsSourceWithoutRuleTextCopies() {
        val parsed = FilterRuleComposer.composeParsed(
            sequenceOf(
                FilterRuleParser.parse(sequenceOf("||shared.example^", "||shared.example^\$badfilter")),
                FilterRuleParser.parse(sequenceOf("||shared.example^", "||second.example^"))
            )
        )
        DomainFilter.loadParsedRules(parsed)
        assertTrue(DomainFilter.isBlocked("shared.example"))
        assertTrue(DomainFilter.isBlocked("second.example"))
    }

    @Test fun parserAcceptsLazySequenceForLargeSourcePipelines() {
        val parsed = FilterRuleParser.parse(sequence {
            yield("||one.example^")
            yield("||two.example^")
            yield("||one.example^")
        })
        assertEquals(setOf("one.example", "two.example"), parsed.blocked)
    }

    @Test fun parserHandlesLargeAdblockSourcesAndSupportedModifiers() {
        val parsed = FilterRuleParser.parse(sequence {
            repeat(50_000) { index -> yield("||host$index.example^") }
            yield("||important.example^\$important")
            yield("||disabled.example^\$badfilter")
            yield("@@||allowed.example^\$important")
        })
        assertEquals(50_000, parsed.blocked.size)
        assertTrue("important.example" in parsed.importantBlocked)
        assertFalse("disabled.example" in parsed.blocked)
        assertEquals(1, parsed.ignored)
    }
}
