package com.guardian.adblock

import java.util.Locale

/** Parser for DNS-compatible Adblock rules and hosts-file entries. */
object FilterRuleParser {
    data class Parsed(val blocked: Set<String>, val allowed: Set<String>, val importantBlocked: Set<String>, val ignored: Int)

    // Reuse this immutable set for every rule. Creating it inside adblockDomain()
    // allocates several objects per rule and can exhaust Android's small app heap
    // when parsing multi-megabyte filter lists.
    private val SUPPORTED_MODIFIERS = setOf("important", "all", "badfilter")

    fun parse(lines: List<String>): Parsed = parse(lines.asSequence())

    /** Parses lazily so callers decoding large downloaded lists do not allocate a second line array. */
    fun parse(lines: Sequence<String>): Parsed {
        val blocked = linkedSetOf<String>()
        val allowed = linkedSetOf<String>()
        val importantBlocked = linkedSetOf<String>()
        val disabledBlocked = linkedSetOf<String>()
        val disabledImportantBlocked = linkedSetOf<String>()
        val disabledAllowed = linkedSetOf<String>()
        var ignored = 0

        for (raw in lines) {
            val line = raw.removePrefix("\uFEFF").substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            val lower = line.lowercase(Locale.ROOT)
            when {
                lower.startsWith("block-important=") -> addDomain(line.substringAfter('='), importantBlocked).also { if (!it) ignored++ }
                lower.startsWith("block=") -> addDomain(line.substringAfter('='), blocked).also { if (!it) ignored++ }
                lower.startsWith("allow=") -> addDomain(line.substringAfter('='), allowed).also { if (!it) ignored++ }
                lower.startsWith("@@||") && hasModifier(line, "badfilter") -> {
                    val domain = adblockDomain(line.substring(4))
                    if (domain == null) ignored++ else disabledAllowed.add(domain)
                }
                lower.startsWith("||") && hasModifier(line, "badfilter") -> {
                    val domain = adblockDomain(line.substring(2))
                    if (domain == null) ignored++
                    else if (hasModifier(line, "important")) disabledImportantBlocked.add(domain)
                    else disabledBlocked.add(domain)
                }
                lower.startsWith("@@||") -> {
                    val domain = adblockDomain(line.substring(4))
                    if (domain == null || hasModifier(line, "important")) ignored++ else allowed.add(domain)
                }
                lower.startsWith("||") -> {
                    val domain = adblockDomain(line.substring(2))
                    if (domain == null) ignored++
                    else if (hasModifier(line, "important")) importantBlocked.add(domain)
                    else blocked.add(domain)
                }
                else -> {
                    val fields = line.split(Regex("\\s+"))
                    if (fields.size >= 2 && (fields[0] == "0.0.0.0" || fields[0] == "127.0.0.1" ||
                                fields[0] == "::" || fields[0] == "::1")) {
                        var added = false
                        fields.drop(1).forEach { host ->
                            if (host != "localhost" && addDomain(host, blocked)) added = true
                        }
                        if (!added) ignored++
                    } else ignored++
                }
            }
        }
        blocked.removeAll(disabledBlocked)
        importantBlocked.removeAll(disabledImportantBlocked)
        allowed.removeAll(disabledAllowed)
        return Parsed(blocked, allowed, importantBlocked, ignored)
    }

    private fun hasModifier(rule: String, modifier: String): Boolean {
        val marker = rule.indexOf(36.toChar())
        if (marker < 0 || marker == rule.lastIndex) return false
        var start = marker + 1
        while (start <= rule.length) {
            val end = rule.indexOf(',', start).let { if (it < 0) rule.length else it }
            if (rule.regionMatches(start, modifier, 0, modifier.length, ignoreCase = true) &&
                end - start == modifier.length) return true
            if (end == rule.length) break
            start = end + 1
        }
        return false
    }

    private fun adblockDomain(expression: String): String? {
        // Context-sensitive, cosmetic, regex, URL/path, and rewriting rules
        // are not guessed at DNS level; only safe hostname anchors are used.
        val marker = expression.indexOf(36.toChar())
        val hostEnd = if (marker < 0) expression.length else marker
        val hostExpression = expression.substring(0, hostEnd).trimEnd('.')
        // A DNS filter can safely apply a hostname anchor, but not an Adblock
        // rule constrained to a URL path or to text following the separator.
        val host = when {
            hostExpression.endsWith('^') -> hostExpression.dropLast(1)
            '^' in hostExpression -> return null
            else -> hostExpression
        }
        if (marker >= 0) {
            var start = marker + 1
            while (start <= expression.length) {
                val end = expression.indexOf(',', start).let { if (it < 0) expression.length else it }
                val modifier = expression.substring(start, end)
                if (modifier.isNotBlank() && modifier.lowercase(Locale.ROOT) !in SUPPORTED_MODIFIERS) return null
                if (end == expression.length) break
                start = end + 1
            }
        }
        if (host.isEmpty() || host.contains('*') || host.contains('/') || host.contains('|')) return null
        return normalize(host, requireDomainName = true)
    }

    private fun addDomain(raw: String, destination: MutableSet<String>): Boolean {
        val domain = normalize(raw.trim()) ?: return false
        destination.add(domain)
        return true
    }

    private fun normalize(raw: String, requireDomainName: Boolean = false): String? {
        val domain = raw.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (domain.length !in 1..253) return null
        val labels = domain.split('.')
        if (requireDomainName && labels.size < 2) return null
        if (!labels.all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    (label.first() in 'a'..'z' || label.first() in '0'..'9') &&
                    (label.last() in 'a'..'z' || label.last() in '0'..'9') &&
                    label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            }) return null
        return domain
    }
}
