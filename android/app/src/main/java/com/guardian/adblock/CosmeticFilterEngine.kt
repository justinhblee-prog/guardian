package com.guardian.adblock

import java.io.Reader

/**
 * Parses the CSS-only cosmetic subset of AdGuard/uBlock filter syntax.
 *
 * Supports generic rules, domain-scoped rules, excluded domains (~domain),
 * and domain exceptions. Scriptlet/procedural rules are intentionally ignored.
 */
object CosmeticFilterEngine {
    private const val MAX_RULES = 5_000
    private const val MAX_SELECTOR_CHARS = 1_000_000
    /** Conservative, generic ad-slot selectors used only by Guardian's own WebView. */
    val DEFAULT_RULES = listOf(
        "##.adsbygoogle",
        "##[id^='google_ads']",
        "##[id^='div-gpt-ad']",
        "##[id^='ad-container']",
        "##[id^='ad-slot']",
        "##[id^='adunit']",
        "###advertisement",
        "###ad-banner",
        "##[id*='advertisement']",
        "##[class*='advertisement']",
        "##[id*='ad-banner']",
        "##[class*='ad-banner']",
        "##[class*='ad-container']",
        "##[class*='ad-slot']",
        "##[class*='ad-unit']",
        "##[id*='ad-placeholder']",
        "##[class*='ad-placeholder']",
        "##[id*='ad-placement']",
        "##[class*='ad-placement']",
        "##[id*='adbox']",
        "##[class*='adbox']",
        "##[id*='ad_box']",
        "##[class*='ad_box']",
        "##[id*='ad-box']",
        "##[class*='ad-box']",
        "##[aria-label*='advertisement' i]",
        "##[title*='advertisement' i]",
        "##div:has(iframe[src*='doubleclick.net'])",
        "##div:has(iframe[src*='googlesyndication.com'])",
        "##div:has(ins.adsbygoogle)",
        // Hide the immediate slot wrapper as well as its blocked child. This
        // collapses fixed-height blank ad boxes without hiding broad page regions.
        "##:where(div,section,aside):has(iframe[src*='doubleclick.net'])",
        "##:where(div,section,aside):has(iframe[src*='googlesyndication.com'])",
        "##:where(div,section,aside):has(iframe[src*='adservice.google'])",
        "##:where(div,section,aside):has(iframe[id^='google_ads_iframe'])",
        "##:where(div,section,aside):has(ins.adsbygoogle[data-ad-status='unfilled'])",
        "##:where(div,section,aside):has([data-ad-status='unfilled'])",
        "##:where(div,section,aside):has([data-ad-slot]:empty)",
        "##.google-auto-placed:empty",
        "##.adunit",
        "##.ad-unit",
        "##.ad-slot",
        "##.ad-wrapper",
        "##.ad-banner",
        "##.ad-box",
        "##.advertisement-box",
        "##.ad-placeholder",
        "##.advert",
        "##.advertisement",
        "##[data-ad]",
        "##[data-ad-slot]",
        "##[data-ad-client]",
        "##[data-ad-unit]",
        "##[aria-label='advertisement']",
        "##iframe[src*='doubleclick.net']",
        "##iframe[src*='googlesyndication.com']",
        "##iframe[src*='adservice.google']",
        "##iframe[id^='google_ads_iframe']",
        "##div[id^='google_ads_iframe']",
        "##[id^='aswift_']",
        "##.google-auto-placed",
        "##[data-google-query-id]",
        "##[data-ad-status='unfilled']",
        "##amp-ad",
        "##ins.adsbygoogle",
        "##[aria-label='Ads']"
    )

    /** Retain bundled ad-slot coverage when an optional downloaded cosmetic feed is present. */
    fun withBuiltInDefaults(cached: RuleSet): RuleSet =
        merge(listOf(parse(DEFAULT_RULES), cached))

    data class GenericHideRule(
        val selector: String,
        val excludedDomains: Set<String>
    )

    data class DomainHideRule(
        val includedDomains: Set<String>,
        val excludedDomains: Set<String>,
        val selector: String
    )

    data class RuleSet(
        val genericHide: Set<String>,
        val genericHideRules: List<GenericHideRule>,
        val domainHideRules: List<DomainHideRule>,
        val domainExceptions: Map<String, Set<String>>,
        val ignored: Int
    )

    fun parse(lines: List<String>): RuleSet = parse(lines.asSequence())

    /** Parses a cached feed incrementally without materializing every line. */
    fun parse(reader: Reader): RuleSet = parse(reader.buffered().lineSequence())

    fun parse(lines: Sequence<String>): RuleSet {
        val generic = linkedSetOf<String>()
        val genericRules = mutableListOf<GenericHideRule>()
        val domainRules = mutableListOf<DomainHideRule>()
        val exceptions = linkedMapOf<String, MutableSet<String>>()
        var ignored = 0
        var acceptedRules = 0
        var selectorChars = 0

        for (raw in lines) {
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue

            val marker = when {
                "#@#" in line -> "#@#"
                "##" in line -> "##"
                else -> null
            } ?: continue

            val parts = line.split(marker, limit = 2)
            if (parts.size != 2) { ignored++; continue }
            val selector = parts[1].trim()
            if (!isSafeCssSelector(selector)) { ignored++; continue }

            val domainTokens = if (parts[0].isBlank()) emptyList() else parts[0].split(",").map { it.trim().lowercase() }
            val included = domainTokens.filterNot { it.startsWith("~") }.toSet()
            val excluded = domainTokens.filter { it.startsWith("~") }.map { it.removePrefix("~") }.toSet()
            if (domainTokens.any { token ->
                    val domain = token.removePrefix("~")
                    !isValidDomain(domain)
                }) {
                ignored++
                continue
            }
            if (acceptedRules >= MAX_RULES || selectorChars + selector.length > MAX_SELECTOR_CHARS) {
                ignored++
                continue
            }

            if (marker == "#@#") {
                if (included.isEmpty() || excluded.isNotEmpty()) { ignored++; continue }
                included.forEach { domain -> exceptions.getOrPut(domain) { linkedSetOf() }.add(selector) }
                acceptedRules++
                selectorChars += selector.length
            } else if (included.isEmpty()) {
                generic.add(selector)
                genericRules.add(GenericHideRule(selector, excluded))
                acceptedRules++
                selectorChars += selector.length
            } else {
                domainRules.add(DomainHideRule(included, excluded, selector))
                acceptedRules++
                selectorChars += selector.length
            }
        }

        return RuleSet(
            genericHide = generic,
            genericHideRules = genericRules,
            domainHideRules = domainRules,
            domainExceptions = exceptions.mapValues { it.value.toSet() },
            ignored = ignored
        )
    }

    /** Combines independently parsed filter sources while keeping exceptions global. */
    fun merge(ruleSets: Iterable<RuleSet>): RuleSet {
        val generic = linkedSetOf<String>()
        val genericRules = mutableListOf<GenericHideRule>()
        val domainRules = mutableListOf<DomainHideRule>()
        val exceptions = linkedMapOf<String, MutableSet<String>>()
        var ignored = 0
        for (rules in ruleSets) {
            generic.addAll(rules.genericHide)
            genericRules.addAll(rules.genericHideRules)
            domainRules.addAll(rules.domainHideRules)
            rules.domainExceptions.forEach { (domain, selectors) ->
                exceptions.getOrPut(domain) { linkedSetOf() }.addAll(selectors)
            }
            ignored += rules.ignored
        }
        return RuleSet(generic, genericRules, domainRules, exceptions.mapValues { it.value.toSet() }, ignored)
    }

    /** Returns CSS selectors to hide for the current document hostname. */
    fun selectorsForHost(rules: RuleSet, hostname: String): Set<String> {
        val host = hostname.trimEnd('.').lowercase()
        if (!isValidDomain(host)) return emptySet()

        fun matches(domain: String): Boolean = host == domain || host.endsWith(".$domain")
        val hidden = linkedSetOf<String>()
        rules.genericHideRules.forEach { rule ->
            if (rule.excludedDomains.none(::matches)) hidden.add(rule.selector)
        }
        rules.domainHideRules.forEach { rule ->
            if (rule.includedDomains.any(::matches) && rule.excludedDomains.none(::matches)) {
                hidden.add(rule.selector)
            }
        }
        val exceptions = rules.domainExceptions
            .filterKeys(::matches)
            .values
            .flatten()
            .toSet()
        hidden.removeAll(exceptions)
        return hidden
    }

    /** CSS payload for a document renderer; no JavaScript or HTML is accepted. */
    fun cssForHost(rules: RuleSet, hostname: String): String =
        selectorsForHost(rules, hostname).joinToString("\n") { "$it { display: none !important; }" }

    private val unsupportedCosmeticSyntax = listOf(
        ":contains(",
        ":has-text(",
        ":matches-css",
        ":matches-attr(",
        ":matches-path(",
        ":matches-prop(",
        ":matches-url(",
        ":xpath(",
        ":upward(",
        ":remove(",
        ":style(",
        ":watch-attr(",
        ":min-text-length(",
        ":max-text-length("
    )

    private fun isSafeCssSelector(value: String): Boolean {
        if (value.isBlank() || value.length > 512) return false
        if (value.any { it == '{' || it == '}' || it == '<' || it == '>' || it == '\u0000' }) return false
        val normalized = value.lowercase()
        if (normalized.startsWith("+js(") || unsupportedCosmeticSyntax.any(normalized::contains)) return false
        if (normalized.contains("url(") || normalized.contains("@import")) return false
        return true
    }

    private fun isValidDomain(value: String): Boolean {
        if (value.isBlank() || value.length > 253 || value.startsWith("~")) return false
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
