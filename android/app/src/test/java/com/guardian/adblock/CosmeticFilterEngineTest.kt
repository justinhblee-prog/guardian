package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticFilterEngineTest {
    @Test fun parsesGenericAndDomainScopedHideRules() {
        val rules = CosmeticFilterEngine.parse(listOf(
            "##.generic-ad",
            "news.example,shop.example##.ad-slot",
            "news.example##div[data-ad]",
            "! comment",
            "||ads.example^"
        ))

        assertEquals(setOf(".generic-ad"), rules.genericHide)
        assertEquals(setOf(".ad-slot", "div[data-ad]"), rules.domainHide["news.example"])
        assertEquals(setOf(".ad-slot"), rules.domainHide["shop.example"])
    }

    @Test fun appliesRulesToExactHostAndSubdomainsOnly() {
        val rules = CosmeticFilterEngine.parse(listOf(
            "example.com##.ad",
            "other.example##.banner"
        ))

        assertEquals(setOf(".ad"), CosmeticFilterEngine.selectorsForHost(rules, "example.com"))
        assertEquals(setOf(".ad"), CosmeticFilterEngine.selectorsForHost(rules, "www.example.com"))
        assertTrue(CosmeticFilterEngine.selectorsForHost(rules, "notexample.com").isEmpty())
    }

    @Test fun domainExceptionCancelsMatchingGenericOrScopedSelectorForThatHost() {
        val rules = CosmeticFilterEngine.parse(listOf(
            "##.ad",
            "news.example##.sponsor",
            "news.example#@#.ad",
            "news.example#@#.sponsor"
        ))

        assertTrue(CosmeticFilterEngine.selectorsForHost(rules, "news.example").isEmpty())
        assertEquals(setOf(".ad"), CosmeticFilterEngine.selectorsForHost(rules, "shop.example"))
    }

    @Test fun rejectsUnsafeOrMalformedSelectorsAndKeepsDnsRulesOut() {
        val rules = CosmeticFilterEngine.parse(listOf(
            "example.com##.valid-ad",
            "example.com##.bad { display:block }",
            "example.com##div:url(https://example.com)",
            "||ads.example^",
            "example.com#?#div:has(.ad)"
        ))

        assertEquals(setOf(".valid-ad"), rules.domainHide["example.com"])
        assertTrue(rules.ignored >= 2)
    }

    @Test fun producesCssOnlyHideDeclarations() {
        val rules = CosmeticFilterEngine.parse(listOf("example.com##.ad-container"))
        val css = CosmeticFilterEngine.cssForHost(rules, "www.example.com")

        assertEquals(".ad-container { display: none !important; }", css)
        assertFalse(css.contains("<script", ignoreCase = true))
    }
}
