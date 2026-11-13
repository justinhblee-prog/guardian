package com.guardian.adblock

import java.util.Locale

/** Parser for DNS-compatible Adblock rules and hosts-file entries. */
object FilterRuleParser {
    data class Parsed(val blocked: Set<String>, val allowed: Set<String>, val ignored: Int)

    fun parse(lines: List<String>): Parsed {
        val blocked = linkedSetOf<String>()
        val allowed = linkedSetOf<String>()
        var ignored = 0
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            val lower = line.lowercase(Locale.ROOT)
            when {
                lower.startsWith("block=") -> addDomain(line.substringAfter('='), blocked).also { if (!it) ignored++ }
                lower.startsWith("allow=") -> addDomain(line.substringAfter('='), allowed).also { if (!it) ignored++ }
                lower.startsWith("@@||") -> {
                    val domain = adblockDomain(line.substring(4))
                    if (domain == null) ignored++ else allowed.add(domain)
                }
                lower.startsWith("||") -> {
                    val domain = adblockDomain(line.substring(2))
                    if (domain == null) ignored++ else blocked.add(domain)
                }
                else -> {
                    val fields = line.split(Regex("\\s+"), limit = 3)
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
        return Parsed(blocked, allowed, ignored)
    }

    private fun adblockDomain(expression: String): String? {
        // DNS-compatible domain anchors only. Cosmetic, regex, URL/path and
        // wildcard rules are intentionally ignored rather than misapplied.
        val host = expression.substringBefore('^').substringBefore('$').trimEnd('.')
        if (host.isEmpty() || host.contains('*') || host.contains('/') || host.contains('|')) return null
        return normalize(host)
    }

    private fun addDomain(raw: String, destination: MutableSet<String>): Boolean {
        val domain = normalize(raw.trim())
        if (domain == null) return false
        destination.add(domain)
        return true
    }

    private fun normalize(raw: String): String? {
        val domain = raw.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (domain.length !in 1..253) return null
        val labels = domain.split('.')
        if (labels.size < 2) return null
        if (!labels.all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    (label.first() in 'a'..'z' || label.first() in '0'..'9') &&
                    (label.last() in 'a'..'z' || label.last() in '0'..'9') &&
                    label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            }) return null
        return domain
    }
}
