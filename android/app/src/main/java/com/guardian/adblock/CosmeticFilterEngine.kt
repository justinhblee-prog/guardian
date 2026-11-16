package com.guardian.adblock

/**
 * Parses the CSS-only cosmetic subset of AdGuard/uBlock filter syntax.
 *
 * Supports generic rules, domain-scoped rules, excluded domains (~domain),
 * and domain exceptions. Scriptlet/procedural rules are intentionally ignored.
 */
object CosmeticFilterEngine {
    data class DomainHideRule(
        val includedDomains: Set<String>,
        val excludedDomains: Set<String>,
        val selector: String
    )

    data class RuleSet(
        val genericHide: Set<String>,
        val genericExclusions: Map<String, Set<String>>,
        val domainHideRules: List<DomainHideRule>,
        val domainExceptions: Map<String, Set<String>>,
        val ignored: Int
    )

    fun parse(lines: List<String>): RuleSet {
        val generic = linkedSetOf<String>()
        val genericExclusions = linkedMapOf<String, MutableSet<String>>()
        val domainRules = mutableListOf<DomainHideRule>()
        val exceptions = linkedMapOf<String, MutableSet<String>>()
        var ignored = 0

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

            if (marker == "#@#") {
                if (included.isEmpty() || excluded.isNotEmpty()) { ignored++; continue }
                included.forEach { domain -> exceptions.getOrPut(domain) { linkedSetOf() }.add(selector) }
            } else if (included.isEmpty() && excluded.isEmpty()) {
                generic.add(selector)
            } else if (included.isEmpty()) {
                generic.add(selector)
                genericExclusions.getOrPut(selector) { linkedSetOf() }.addAll(excluded)
            } else {
                domainRules.add(DomainHideRule(included, excluded, selector))
            }
        }

        return RuleSet(
            genericHide = generic,
            genericExclusions = genericExclusions.mapValues { it.value.toSet() },
            domainHideRules = domainRules,
            domainExceptions = exceptions.mapValues { it.value.toSet() },
            ignored = ignored
        )
    }

    /** Returns CSS selectors to hide for the current document hostname. */
    fun selectorsForHost(rules: RuleSet, hostname: String): Set<String> {
        val host = hostname.trimEnd('.').lowercase()
        if (!isValidDomain(host)) return emptySet()

        fun matches(domain: String): Boolean = host == domain || host.endsWith(".$domain")
        val hidden = linkedSetOf<String>()
        rules.genericHide.forEach { selector ->
            val excluded = rules.genericExclusions[selector].orEmpty()
            if (excluded.none(::matches)) hidden.add(selector)
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

    private fun isSafeCssSelector(value: String): Boolean {
        if (value.isBlank() || value.length > 512) return false
        if (value.any { it == '{' || it == '}' || it == '<' || it == '>' || it == '\u0000' }) return false
        if (value.contains("url(", ignoreCase = true) || value.contains("@import", ignoreCase = true)) return false
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
