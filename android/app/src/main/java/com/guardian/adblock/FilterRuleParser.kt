package com.guardian.adblock

import java.util.Locale
import java.io.Reader

/** Parser for DNS-compatible Adblock rules and hosts-file entries. */
object FilterRuleParser {
    data class Parsed(
        val blocked: Set<String>,
        val allowed: Set<String>,
        val importantBlocked: Set<String>,
        val ignored: Int
    )

    fun parse(lines: List<String>): Parsed = parseSequence(lines.asSequence())

    /** Parses lazy rule pipelines without materializing their source lines. */
    fun parse(lines: Sequence<String>): Parsed = parseSequence(lines)

    /** Parses a decoded feed without first allocating a second list of every line. */
    fun parse(text: String): Parsed = parseSequence(text.lineSequence())

    /** Parses a cached feed directly from disk without retaining a million-line String/List. */
    fun parse(reader: Reader): Parsed = parseSequence(reader.buffered().lineSequence())

    private fun parseSequence(lines: Sequence<String>): Parsed {
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        val importantBlocked = HashSet<String>()
        val disabledBlocked = HashSet<String>()
        val disabledImportantBlocked = HashSet<String>()
        val disabledAllowed = HashSet<String>()
        var ignored = 0

        for (raw in lines) {
            val line = raw.removePrefix("\uFEFF").substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            val lower = line.lowercase(Locale.ROOT)
            when {
                lower.startsWith("block-important=") ->
                    addDomain(line.substringAfter('='), importantBlocked).also { if (!it) ignored++ }
                lower.startsWith("block=") ->
                    addDomain(line.substringAfter('='), blocked).also { if (!it) ignored++ }
                lower.startsWith("allow=") ->
                    addDomain(line.substringAfter('='), allowed).also { if (!it) ignored++ }
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
                    if (fields.size >= 2 && fields[0] in setOf("0.0.0.0", "127.0.0.1", "::", "::1")) {
                        var added = false
                        fields.drop(1).forEach { host ->
                            // Some generated hosts files accidentally repeat the sink
                            // address as an alias. It is not a DNS hostname and must not
                            // become a synthetic rule for a numeric reverse lookup.
                            if (host != "localhost" && !isIpv4Address(host) &&
                                addDomain(host, blocked)
                            ) added = true
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

    /** Scans a dollar-prefixed modifier suffix without String.split(). */
    private fun modifierTokens(rule: String): List<String> {
        val separator = rule.indexOf('$')
        if (separator < 0 || separator == rule.lastIndex) return emptyList()
        val tokens = ArrayList<String>()
        var tokenStart = separator + 1
        for (index in tokenStart..rule.length) {
            if (index == rule.length || rule[index] == ',') {
                tokens.add(rule.substring(tokenStart, index))
                tokenStart = index + 1
            }
        }
        return tokens
    }

    private fun hasModifier(rule: String, modifier: String): Boolean =
        modifierTokens(rule).any { it.equals(modifier, ignoreCase = true) }

    private fun adblockDomain(expression: String): String? {
        // Only hostname anchors are safe to represent at DNS level.
        val separator = expression.indexOf('$')
        val hostExpression = (if (separator < 0) expression else expression.substring(0, separator)).trimEnd('.')
        val host = when {
            hostExpression.endsWith('^') -> hostExpression.dropLast(1)
            '^' in hostExpression -> return null
            else -> hostExpression
        }
        val modifiers = modifierTokens(expression)
        val supportedModifiers = setOf("important", "all", "badfilter")
        if (modifiers.any { it.isNotBlank() && it.lowercase(Locale.ROOT) !in supportedModifiers }) return null
        if (host.isEmpty() || host.contains('*') || host.contains('/') || host.contains('|')) return null
        return normalize(host, requireDomainName = true)
    }

    private fun isIpv4Address(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { part ->
            part.isNotEmpty() && part.length <= 3 &&
                part.all { it in '0'..'9' } && part.toIntOrNull() in 0..255
        }
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
