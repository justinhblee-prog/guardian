package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.StringReader
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
        assertTrue(rules.domainHideRules.any {
            it.includedDomains == setOf("news.example", "shop.example") && it.selector == ".ad-slot"
        })
        assertTrue(rules.domainHideRules.any {
            it.includedDomains == setOf("news.example") && it.selector == "div[data-ad]"
        })
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

    @Test fun excludedDomainsLimitGenericAndScopedRules() {
        val rules = CosmeticFilterEngine.parse(listOf(
            "##.generic-ad",
            "~shop.example##.generic-ad",
            "example.com,~news.example.com##.site-ad"
        ))

        assertEquals(setOf(".generic-ad", ".site-ad"),
            CosmeticFilterEngine.selectorsForHost(rules, "www.example.com"))
        assertEquals(setOf(".generic-ad"),
            CosmeticFilterEngine.selectorsForHost(rules, "news.example.com"))
        assertEquals(setOf(".generic-ad"),
            CosmeticFilterEngine.selectorsForHost(rules, "shop.example"))
    }

    @Test fun excludedOnlyGenericRuleAppliesOutsideItsExcludedDomains() {
        val rules = CosmeticFilterEngine.parse(listOf("~shop.example##.limited-ad"))

        assertEquals(setOf(".limited-ad"), CosmeticFilterEngine.selectorsForHost(rules, "news.example"))
        assertTrue(CosmeticFilterEngine.selectorsForHost(rules, "shop.example").isEmpty())
        assertTrue(CosmeticFilterEngine.selectorsForHost(rules, "sub.shop.example").isEmpty())
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
            "example.com#?#div:has(.ad)",
            "bad_domain##.invalid"
        ))

        assertTrue(rules.domainHideRules.any {
            it.includedDomains == setOf("example.com") && it.selector == ".valid-ad"
        })
        assertTrue(rules.ignored >= 3)
    }

    @Test fun ignoresScriptletsAndExtendedProceduralSelectors() {
        val rules = CosmeticFilterEngine.parse(listOf(
            "example.com##+js(abort-on-property-read, ad)",
            "example.com##div:contains(ad)",
            "example.com##div:has-text(ad)",
            "example.com##div:style(color:red)",
            "example.com##div:matches-css(display:block)",
            "example.com##div:has(.ad)"
        ))

        assertEquals(setOf("div:has(.ad)"),
            CosmeticFilterEngine.selectorsForHost(rules, "example.com"))
        assertEquals(5, rules.ignored)
    }

    @Test fun acceptsSafeChildCombinatorsUsedByBuiltInAdSlotRules() {
        val rules = CosmeticFilterEngine.parse(listOf("example.com##div > iframe"))

        assertEquals(0, rules.ignored)
        assertTrue(CosmeticFilterEngine.selectorsForHost(rules, "example.com").contains("div > iframe"))
    }

    @Test fun producesCssOnlyHideDeclarations() {
        val rules = CosmeticFilterEngine.parse(listOf("example.com##.ad-container"))
        val css = CosmeticFilterEngine.cssForHost(rules, "www.example.com")

        assertEquals(".ad-container { display: none !important; }", css)
        assertFalse(css.contains("<script", ignoreCase = true))
    }

    @Test fun bundledWebViewDefaultsCoverCommonAdSlotContainersAndFrames() {
        val rules = CosmeticFilterEngine.parse(CosmeticFilterEngine.DEFAULT_RULES)
        val selectors = CosmeticFilterEngine.selectorsForHost(rules, "shop.example")

        assertEquals(0, rules.ignored)
        assertTrue(selectors.contains(".ad-slot"))
        assertTrue(selectors.contains(".ad-wrapper"))
        assertTrue(selectors.contains("[data-ad-unit]"))
        assertTrue(selectors.contains("iframe[src*='doubleclick.net']"))
        assertTrue(selectors.contains("iframe[src*='googlesyndication.com']"))
    }

    @Test fun builtInAdSlotRulesRemainActiveWhenCachedFeedIsMerged() {
        val downloaded = CosmeticFilterEngine.parse(listOf("news.example##.site-sponsored"))
        val combined = CosmeticFilterEngine.withBuiltInDefaults(downloaded)
        val selectors = CosmeticFilterEngine.selectorsForHost(combined, "news.example")

        assertTrue(selectors.contains(".advertisement"))
        assertTrue(selectors.contains("#advertisement"))
        assertTrue(selectors.contains("[id*='advertisement']"))
        assertTrue(selectors.contains(".ad-box"))
        assertTrue(selectors.contains("[id*='ad-placeholder']"))
        assertTrue(selectors.contains("[class*='ad-placement']"))
        assertTrue(selectors.contains("[class*='adbox']"))
        assertTrue(selectors.contains("[aria-label*='advertisement' i]"))
        assertTrue(selectors.contains("div:has(iframe[src*='doubleclick.net'])"))
        assertTrue(selectors.contains("div:has(ins.adsbygoogle)"))
        assertTrue(selectors.contains(":where(div,section,aside):has(iframe[src*='doubleclick.net'])"))
        assertTrue(selectors.contains(":where(div,section,aside):has(iframe[id^='google_ads_iframe'])"))
        assertTrue(selectors.contains(":where(div,section,aside):has([data-ad-status='unfilled'])"))
        assertTrue(selectors.contains(":where(div,section,aside):has([data-ad-slot]:empty)"))
        assertTrue(selectors.contains(".google-auto-placed:empty"))
        assertTrue(selectors.contains(".site-sponsored"))
    }

    @Test fun builtInWebViewRulesCoverGoogleAndUnfilledAdPlaceholders() {
        val rules = CosmeticFilterEngine.withBuiltInDefaults(
            CosmeticFilterEngine.parse(listOf("news.example##.site-sponsored"))
        )
        val selectors = CosmeticFilterEngine.selectorsForHost(rules, "news.example")

        assertTrue(selectors.contains("iframe[id^='google_ads_iframe']"))
        assertTrue(selectors.contains("div[id^='google_ads_iframe']"))
        assertTrue(selectors.contains("[id^='aswift_']"))
        assertTrue(selectors.contains(".google-auto-placed"))
        assertTrue(selectors.contains("[data-google-query-id]"))
        assertTrue(selectors.contains("[data-ad-status='unfilled']"))
        assertTrue(selectors.contains("amp-ad"))
        assertTrue(selectors.contains("ins.adsbygoogle"))
        assertTrue(selectors.contains("[aria-label='Ads']"))
    }

    @Test fun parsesCachedFeedIncrementallyFromReader() {
        val rules = StringReader(
            "! generated filter\n##.generic-ad\nnews.example##.sponsored\n||ads.example^\n"
        ).use { CosmeticFilterEngine.parse(it) }

        assertEquals(setOf(".generic-ad"), rules.genericHide)
        assertEquals(setOf(".generic-ad", ".sponsored"),
            CosmeticFilterEngine.selectorsForHost(rules, "news.example"))
    }

    @Test fun mergesExceptionsAcrossIndependentFilterSources() {
        val base = CosmeticFilterEngine.parse(listOf(
            "##.generic-ad",
            "news.example##.sponsored"
        ))
        val optional = CosmeticFilterEngine.parse(listOf(
            "news.example#@#.generic-ad"
        ))
        val merged = CosmeticFilterEngine.merge(listOf(base, optional))

        assertEquals(setOf(".sponsored"),
            CosmeticFilterEngine.selectorsForHost(merged, "news.example"))
        assertEquals(setOf(".generic-ad"),
            CosmeticFilterEngine.selectorsForHost(merged, "shop.example"))
    }

}
