package com.guardian.adblock

object DomainFilter {
    // Bootstrap defaults are replaced only after a valid shared rule asset is loaded.
    // Keep this emergency set aligned with shared/filter_rules.txt. It is used
    // when the packaged baseline cannot be read or a first-run filter download
    // is unavailable, so it must provide meaningful coverage rather than only
    // the original ten demonstration domains.
    private val bootstrapBlocked = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "adform.net", "taboola.com", "outbrain.com",
        "criteo.com", "scorecardresearch.com", "quantserve.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "pagead2.googleadservices.com", "securepubads.g.doubleclick.net",
        "partner.googleadservices.com", "ads.yahoo.com", "advertising.yahoo.com",
        "adserver.yahoo.com", "adsystem.com", "adroll.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "casalemedia.com", "appnexus.com",
        "bidswitch.net", "smartadserver.com", "serving-sys.com", "exoclick.com",
        "popads.net", "popcash.net", "propellerads.com", "trafficjunky.net",
        "mgid.com", "revcontent.com", "analytics.google.com",
        "google-analytics.com", "googletagmanager.com", "hotjar.com",
        "mixpanel.com", "amplitude.com", "segment.io", "segment.com",
        "fullstory.com", "mouseflow.com", "clarity.ms", "newrelic.com",
        "nr-data.net", "sentry.io", "bugsnag.com", "branch.io",
        "adjust.com", "appsflyer.com", "kochava.com"
    )
    private val adMarkers = arrayOf(
        "doubleclick", "googlesyndication", "googleadservices", "adservice",
        "adsystem", "adserver", "adnxs", "adform", "taboola", "outbrain",
        "criteo", "popads", "adroll", "advert"
    )
    private val trackerMarkers = arrayOf(
        "analytics", "tracking", "track.", "telemetry", "metrics", "pixel.",
        "scorecardresearch", "quantserve", "mixpanel", "segment.", "amplitude",
        "hotjar", "matomo", "clarity.ms"
    )
    private data class RuleSet(val blocked: Set<String>, val allowed: Set<String>, val importantBlocked: Set<String>)
    @Volatile private var activeRules = RuleSet(bootstrapBlocked, emptySet(), emptySet())

    /** Loads list-backed sources for callers that already hold a small collection. */
    fun loadRuleSources(
        sources: List<() -> List<String>>,
        minimumRuleCounts: List<Int>,
        userBlockDomains: List<String> = emptyList(),
        userAllowDomains: List<String> = emptyList()
    ) {
        loadParsedRuleSources(
            sources.map { load -> { FilterRuleParser.parse(load()) } },
            minimumRuleCounts,
            userBlockDomains,
            userAllowDomains
        )
    }

    /** Merges reader-parsed sources so large disk feeds never become line-sized String lists. */
    fun loadParsedRuleSources(
        sources: List<() -> FilterRuleParser.Parsed>,
        minimumRuleCounts: List<Int>,
        userBlockDomains: List<String> = emptyList(),
        userAllowDomains: List<String> = emptyList()
    ) {
        require(sources.isNotEmpty() && sources.size == minimumRuleCounts.size) {
            "Filter source validation configuration is incomplete"
        }
        val blocked = HashSet<String>()
        val allowed = HashSet<String>()
        val importantBlocked = HashSet<String>()
        sources.forEachIndexed { index, load ->
            val parsed = load()
            val count = parsed.blocked.size + parsed.importantBlocked.size
            require(count >= minimumRuleCounts[index]) {
                "Filter source ${index + 1} has too few valid DNS rules: $count"
            }
            blocked.addAll(parsed.blocked)
            allowed.addAll(parsed.allowed)
            importantBlocked.addAll(parsed.importantBlocked)
        }
        userBlockDomains.forEach { DomainFilter.normalizeRuleDomain(it)?.let(blocked::add) }
        userAllowDomains.forEach { DomainFilter.normalizeRuleDomain(it)?.let(allowed::add) }
        require(blocked.isNotEmpty() || importantBlocked.isNotEmpty()) {
            "Filter sources contain no valid DNS block rules"
        }
        // One volatile swap keeps allow/block precedence consistent during live updates.
        activeRules = RuleSet(blocked, allowed, importantBlocked)
    }

    /** Loads the canonical cross-platform rules shipped as an Android asset. */
    fun loadRules(lines: List<String>) {
        loadRuleSources(listOf({ lines }), listOf(1))
    }

    enum class BlockCategory { AD, TRACKER, OTHER }

    /**
     * Best-effort reporting category only. The imported DNS lists do not carry
     * consistent per-rule taxonomy, so this must never be used to decide blocking.
     */
    fun classifyBlockedDomain(domain: String): BlockCategory {
        val value = domain.lowercase(java.util.Locale.ROOT)
        return when {
            adMarkers.any(value::contains) -> BlockCategory.AD
            trackerMarkers.any(value::contains) -> BlockCategory.TRACKER
            else -> BlockCategory.OTHER
        }
    }

    /**
     * Restore the small built-in emergency list when the bundled shared asset cannot be read.
     * This deliberately drops optional/downloaded rules and returns the active baseline size.
     */
    fun resetToBootstrapRules(): Int {
        activeRules = RuleSet(bootstrapBlocked, emptySet(), emptySet())
        return bootstrapBlocked.size
    }

    fun bootstrapRuleCount(): Int = bootstrapBlocked.size
    fun bootstrapRuleLines(): List<String> = bootstrapBlocked.map { "block=$it" }

    fun blockedRuleCount(): Int = activeRules.blocked.size + activeRules.importantBlocked.size
    fun allowedRuleCount(): Int = activeRules.allowed.size

    /** Normalize a user-entered hostname. Only ASCII DNS labels are accepted. */
    fun normalizeRuleDomain(raw: String): String? {
        val domain = raw.trim().trimEnd('.').lowercase(java.util.Locale.ROOT)
        if (domain.length !in 1..253) return null
        val labels = domain.split('.')
        val valid = labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                (label.first() in 'a'..'z' || label.first() in '0'..'9') &&
                (label.last() in 'a'..'z' || label.last() in '0'..'9') &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
        return if (valid) domain else null
    }

    private fun isValidRuleDomain(domain: String): Boolean = normalizeRuleDomain(domain) == domain

    fun isBlocked(domain: String): Boolean {
        val d = domain.trim().trimEnd('.').lowercase(java.util.Locale.ROOT)
        if (d.isEmpty() || d.length > 253) return false
        val snapshot = activeRules
        // Hash lookups by DNS label avoid scanning every downloaded rule for
        // every query. Work is bounded by the number of labels in the domain.
        if (matchesDomainOrParent(d, snapshot.importantBlocked)) return true
        if (matchesDomainOrParent(d, snapshot.allowed)) return false
        return matchesDomainOrParent(d, snapshot.blocked)
    }

    private fun matchesDomainOrParent(domain: String, rules: Set<String>): Boolean {
        var candidate = domain
        while (true) {
            if (candidate in rules) return true
            val separator = candidate.indexOf('.')
            if (separator < 0) return false
            candidate = candidate.substring(separator + 1)
        }
    }

    /**
     * Accept only a standard, single-question DNS query. Compression pointers are
     * deliberately rejected in QNAME because a query's first question is uncompressed.
     */
    private fun questionEnd(message: ByteArray): Int? {
        if (message.size < 17) return null
        val flags = u16(message, 2)
        val qdCount = u16(message, 4)
        if ((flags and 0x8000) != 0) return null // response, not query
        if ((flags and 0x7800) != 0) return null // standard QUERY opcode only
        // AA, TC, RA and the reserved Z bit are response-only/reserved in a
        // standard client query. RD, AD and CD remain valid query flags.
        if ((flags and 0x06c0) != 0) return null
        if (qdCount != 1) return null
        var p = 12
        var labels = 0
        var nameLength = 0
        while (p < message.size && labels++ < 128) {
            val n = message[p].toInt() and 0xff
            p++
            if (n == 0) {
                if (p + 4 > message.size) return null // QTYPE + QCLASS
                // QTYPE=0 (reserved) and QCLASS=0 (reserved) are not valid
                // questions. Keep other assigned and extensible values intact.
                if (u16(message, p) == 0 || u16(message, p + 2) == 0) return null
                return p + 4
            }
            if ((n and 0xc0) != 0 || n > 63 || p + n > message.size) return null
            nameLength += n + 1
            if (nameLength > 254) return null
            for (i in p until p + n) {
                val c = message[i].toInt() and 0xff
                // A literal dot inside one wire label would be ambiguous after
                // joining labels into the hostname used for blocklist matching.
                if (c <= 32 || c >= 127 || c == '.'.code) return null
            }
            p += n
        }
        return null
    }

    fun readQuestionName(message: ByteArray): String? {
        if (questionEnd(message) == null) return null
        var p = 12
        val labels = ArrayList<String>()
        while (p < message.size) {
            val n = message[p++].toInt() and 0xff
            if (n == 0) break
            labels.add(String(message, p, n, Charsets.US_ASCII))
            p += n
        }
        return labels.joinToString(".").lowercase(java.util.Locale.ROOT)
    }

    /** Reject spoofed, mismatched, truncated, or malformed upstream DNS replies. */
    fun isMatchingResponse(query: ByteArray, response: ByteArray, allowTruncated: Boolean = false): Boolean {
        if (query.size < 17 || response.size < 17) return false
        val queryFlags = u16(query, 2)
        val responseFlags = u16(response, 2)
        if ((queryFlags and 0x8000) != 0 || (queryFlags and 0x7800) != 0) return false
        if ((responseFlags and 0x8000) == 0 || (responseFlags and 0x7800) != 0) return false
        if ((responseFlags and 0x0040) != 0) return false // The reserved DNS Z bit must be zero in responses.
        if (!allowTruncated && (responseFlags and 0x0200) != 0) return false // The service retries TC replies over TCP.
        if (u16(query, 0) != u16(response, 0)) return false
        if (u16(query, 4) != 1 || u16(response, 4) != 1) return false
        val queryEnd = questionEnd(query) ?: return false
        val responseEnd = rawQuestionEnd(response) ?: return false
        if (canonicalQuestion(query, queryEnd)?.contentEquals(
                canonicalQuestion(response, responseEnd) ?: return false
            ) != true) return false
        return hasCompleteResourceRecordSections(response, responseEnd)
    }

    /**
     * Validate all answer/authority/additional record boundaries before forwarding
     * an upstream packet. The RDATA remains type-opaque, but owner names, fixed
     * RR headers, RDLENGTH and the complete message boundary must be well formed.
     */
    private fun hasCompleteResourceRecordSections(message: ByteArray, questionEnd: Int): Boolean {
        val totalRecords = u16(message, 6).toLong() + u16(message, 8) + u16(message, 10)
        if (totalRecords > message.size.toLong()) return false
        var offset = questionEnd
        repeat(totalRecords.toInt()) {
            offset = skipCompressedName(message, offset) ?: return false
            if (offset > message.size - 10) return false
            val dataLength = u16(message, offset + 8)
            offset += 10
            if (dataLength > message.size - offset) return false
            offset += dataLength
        }
        return offset == message.size
    }

    /** Returns the encoded-name end while validating bounded, backward-only compression pointers. */
    private fun skipCompressedName(message: ByteArray, start: Int): Int? {
        var offset = start
        var encodedEnd = -1
        var expandedLength = 0
        var steps = 0
        val visited = HashSet<Int>()
        while (offset < message.size && steps++ < 128) {
            val length = message[offset].toInt() and 0xff
            when {
                length == 0 -> {
                    expandedLength++
                    if (expandedLength > 255) return null
                    return if (encodedEnd >= 0) encodedEnd else offset + 1
                }
                (length and 0xc0) == 0xc0 -> {
                    if (offset + 1 >= message.size) return null
                    val target = ((length and 0x3f) shl 8) or (message[offset + 1].toInt() and 0xff)
                    if (target < 12 || target >= offset || !visited.add(target)) return null
                    if (encodedEnd < 0) encodedEnd = offset + 2
                    offset = target
                }
                (length and 0xc0) != 0 || length > 63 || offset + 1 + length > message.size -> return null
                else -> {
                    expandedLength += length + 1
                    if (expandedLength > 255) return null
                    offset += length + 1
                }
            }
        }
        return null
    }


    /** DNS names are case-insensitive; preserve the binary QTYPE/QCLASS bytes. */
    private fun canonicalQuestion(message: ByteArray, end: Int): ByteArray? {
        if (end < 17 || end > message.size) return null
        val question = message.copyOfRange(12, end)
        var p = 0
        var labels = 0
        while (p < question.size && labels++ < 128) {
            val size = question[p++].toInt() and 0xff
            if (size == 0) return if (p + 4 == question.size) question else null
            if ((size and 0xc0) != 0 || size > 63 || p + size > question.size) return null
            for (i in p until p + size) {
                val c = question[i].toInt() and 0xff
                if (c in 65..90) question[i] = (c + 32).toByte()
            }
            p += size
        }
        return null
    }

    private fun rawQuestionEnd(message: ByteArray): Int? {
        var p = 12
        var labels = 0
        var nameLength = 0
        while (p < message.size && labels++ < 128) {
            val n = message[p++].toInt() and 0xff
            if (n == 0) return if (p + 4 <= message.size) p + 4 else null
            if ((n and 0xc0) != 0 || n > 63 || p + n > message.size) return null
            nameLength += n + 1
            if (nameLength > 254) return null
            p += n
        }
        return null
    }

    fun makeNxdomain(query: ByteArray): ByteArray? = makeErrorResponse(query, 3)

    /** Return a standards-shaped SERVFAIL for a valid query when upstream DNS is unavailable. */
    fun makeServfail(query: ByteArray): ByteArray? = makeErrorResponse(query, 2)

    /**
     * Build a standards-shaped UDP truncation response when an upstream answer
     * exceeds the safe IPv6-minimum-MTU DNS payload (1232 bytes). The TC bit
     * signals truncation; the VPN TCP/53 path can accept the resolver retry.
     */
    fun makeTruncatedResponse(query: ByteArray): ByteArray? {
        val end = questionEnd(query) ?: return null
        val out = query.copyOfRange(0, end)
        val flags = u16(query, 2)
        val responseFlags = (flags and 0x7910) or 0x8280 // QR + TC + RA; preserve RD/CD
        out[2] = (responseFlags ushr 8).toByte()
        out[3] = responseFlags.toByte()
        out[4] = 0; out[5] = 1 // QDCOUNT
        out[6] = 0; out[7] = 0 // ANCOUNT
        out[8] = 0; out[9] = 0 // NSCOUNT
        out[10] = 0; out[11] = 0 // ARCOUNT
        return out
    }

    private fun makeErrorResponse(query: ByteArray, rcode: Int): ByteArray? {
        if (rcode !in 0..15) return null
        val end = questionEnd(query) ?: return null
        // Return a valid response containing exactly the validated question. Preserve
        // opcode, RD and CD; set QR and RA, clear answer/authority/additional sections.
        val out = query.copyOfRange(0, end)
        val flags = u16(query, 2)
        val responseFlags = (flags and 0x7910) or 0x8080 or rcode
        out[2] = (responseFlags ushr 8).toByte()
        out[3] = responseFlags.toByte()
        out[4] = 0; out[5] = 1 // QDCOUNT
        out[6] = 0; out[7] = 0 // ANCOUNT
        out[8] = 0; out[9] = 0 // NSCOUNT
        out[10] = 0; out[11] = 0 // ARCOUNT (OPT is omitted)
        return out
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}