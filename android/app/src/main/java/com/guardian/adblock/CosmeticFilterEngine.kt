package com.guardian.adblock

/**
 * Parses the CSS-only cosmetic subset of AdGuard/uBlock filter syntax.
 *
 * This is deliberately separate from DNS parsing: cosmetic rules must only be
 * applied by a document renderer that can inspect the page DOM (for example,
 * a WebView with an explicitly enabled cosmetic-filter feature).
 */
object CosmeticFilterEngine {
    data class RuleSet(
        val genericHide: Set<String>,
        val domainHide: Map<String, Set<String>>,
        val domainExceptions: Map<String, Set<String>>,
        val ignored: Int
    )

    fun parse(lines: List<String>): RuleSet {
        val generic = linkedSetOf<String>()
        val byDomain = linkedMapOf<String, MutableSet<String>>()
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

            val domains = parts[0].split(",").map { it.trim().lowercase() }
            if (parts[0].isBlank()) {
                if (marker == "##") generic.add(selector) else ignored++
                continue
            }
            if (domains.isEmpty() || domains.any { !isValidDomain(it) }) {
                ignored++
                continue
            }
            val target = if (marker == "#@#") exceptions else byDomain
            domains.forEach { domain -> target.getOrPut(domain) { linkedSetOf() }.add(selector) }
        }

        return RuleSet(
            genericHide = generic,
            domainHide = byDomain.mapValues { it.value.toSet() },
            domainExceptions = exceptions.mapValues { it.value.toSet() },
            ignored = ignored
        )
    }

    /** Returns CSS selectors to hide for the current document hostname. */
    fun selectorsForHost(rules: RuleSet, hostname: String): Set<String> {
        val host = hostname.trimEnd('.').lowercase()
        if (!isValidDomain(host)) return emptySet()
        val matchingDomains = (rules.domainHide.keys + rules.domainExceptions.keys)
            .filter { host == it || host.endsWith(".$it") }
        val hidden = linkedSetOf<String>()
        hidden.addAll(rules.genericHide)
        matchingDomains.forEach { hidden.addAll(rules.domainHide[it].orEmpty()) }
        val exceptions = matchingDomains.flatMapTo(linkedSetOf()) { rules.domainExceptions[it].orEmpty() }
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
