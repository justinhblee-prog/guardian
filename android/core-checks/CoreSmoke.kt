package com.guardian.adblock

/** Standalone smoke checks for the pure Kotlin DNS core; does not replace Android/JUnit tests. */
fun main() {
    var assertions = 0
    fun verify(condition: Boolean, message: String) {
        check(condition) { message }
        assertions++
    }

    DomainFilter.loadRules(listOf("block=doubleclick.net", "allow=trusted.doubleclick.net"))
    verify(DomainFilter.isBlocked("ads.doubleclick.net"), "block suffix")
    verify(!DomainFilter.isBlocked("trusted.doubleclick.net"), "allow exception")
    verify(!DomainFilter.isBlocked("notdoubleclick.net"), "label boundary")
    verify(DomainFilter.normalizeRuleDomain(" ADS.Example.COM. ") == "ads.example.com", "normalize custom rule")
    verify(DomainFilter.normalizeRuleDomain("-bad.example.com") == null, "reject invalid hostname label")
    DomainFilter.loadRules(listOf("block=ads.customer.example", "allow=trusted.ads.customer.example"))
    verify(DomainFilter.isBlocked("banner.ads.customer.example"), "custom block suffix")
    verify(!DomainFilter.isBlocked("trusted.ads.customer.example"), "custom allow exception")
    val rejectedEmptyRules = runCatching { DomainFilter.loadRules(listOf("allow=only.example")) }.isFailure
    verify(rejectedEmptyRules && DomainFilter.isBlocked("banner.ads.customer.example"), "failed rule reload keeps prior rules")

    val query = intArrayOf(
        0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
        0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
        0x03,0x63,0x6f,0x6d,0,0,1,0,1
    ).map { it.toByte() }.toByteArray()
    verify(DomainFilter.readQuestionName(query) == "example.com", "question decode")
    val ambiguousName = intArrayOf(0,1,0x01,0,0,1,0,0,0,0,0,0,3,0x61,0x2e,0x62,0,0,1,0,1).map { it.toByte() }.toByteArray()
    verify(DomainFilter.readQuestionName(ambiguousName) == null, "reject ambiguous dot inside DNS label")
    val reply = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
    verify(DomainFilter.isMatchingResponse(query, reply), "matching reply")
    verify(!DomainFilter.isMatchingResponse(query, reply.copyOf().also { it[1] = 0x35 }), "transaction mismatch")
    verify(!DomainFilter.isMatchingResponse(query, reply.copyOf().also { it[2] = 0x83.toByte() }), "truncated reply")
    val nxdomain = DomainFilter.makeNxdomain(query) ?: error("NXDOMAIN generation failed")
    verify((nxdomain[2].toInt() and 0xff) == 0x81 && (nxdomain[3].toInt() and 0xff) == 0x83, "NXDOMAIN flags")
    val servfail = DomainFilter.makeServfail(query) ?: error("SERVFAIL generation failed")
    verify((servfail[2].toInt() and 0xff) == 0x81 && (servfail[3].toInt() and 0xff) == 0x82, "SERVFAIL flags")
    verify((servfail[5].toInt() and 0xff) == 1 && (servfail[7].toInt() and 0xff) == 0, "SERVFAIL section counts")


    // IPv6 UDP/53 request with a valid mandatory UDP checksum.
    val v6Query = ByteArray(40 + 8 + query.size)
    v6Query[0] = 0x60
    v6Query[4] = ((8 + query.size) ushr 8).toByte()
    v6Query[5] = (8 + query.size).toByte()
    v6Query[6] = 17
    v6Query[7] = 55
    v6Query[8] = 0x20; v6Query[23] = 1
    v6Query[24] = 0xfd.toByte(); v6Query[25] = 0x13; v6Query[26] = 0; v6Query[27] = 0x37; v6Query[39] = 2
    v6Query[40] = 0xc0.toByte(); v6Query[41] = 0x01
    v6Query[42] = 0; v6Query[43] = 53
    v6Query[44] = ((8 + query.size) ushr 8).toByte(); v6Query[45] = (8 + query.size).toByte()
    System.arraycopy(query, 0, v6Query, 48, query.size)
    fun checksum16(bytes: ByteArray, start: Int, size: Int): Int {
        var sum = 0L; var i = start
        while (i + 1 < start + size) { sum += (((bytes[i].toInt() and 255) shl 8) or (bytes[i+1].toInt() and 255)).toLong(); i += 2 }
        if (i < start + size) sum += ((bytes[i].toInt() and 255) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 65535) + (sum ushr 16)
        return sum.toInt() and 65535
    }
    var pseudo = 0L
    for (i in 8 until 40 step 2) pseudo += (((v6Query[i].toInt() and 255) shl 8) or (v6Query[i+1].toInt() and 255)).toLong()
    pseudo += (8 + query.size).toLong() + 17L + checksum16(v6Query, 40, 8 + query.size).toLong()
    while ((pseudo ushr 16) != 0L) pseudo = (pseudo and 65535) + (pseudo ushr 16)
    var csum = pseudo.toInt().inv() and 65535
    if (csum == 0) csum = 65535
    v6Query[46] = (csum ushr 8).toByte(); v6Query[47] = csum.toByte()
    val parsedV6 = Ipv6Udp.parseDns(v6Query, v6Query.size)
    verify(parsedV6 != null && parsedV6.payload.contentEquals(query), "IPv6 DNS query parse/checksum")
    val v6Reply = Ipv6Udp.response(parsedV6!!, DomainFilter.makeServfail(query)!!)
    verify(((v6Reply[0].toInt() ushr 4) and 15) == 6 && (v6Reply[6].toInt() and 255) == 17, "IPv6 response header")
    verify(Ipv6Udp.parseDns(v6Query.copyOf().also { it[47] = (it[47].toInt() xor 1).toByte() }, v6Query.size) == null, "IPv6 bad checksum rejected")


    // Adblock/hosts parser regressions: sink IPs are not DNS rules, badfilter
    // disables only the matching rule, and important blocks outrank exceptions.
    val parsedFeeds = FilterRuleParser.parse(listOf(
        "0.0.0.0 ads.example.test 127.0.0.1",
        "127.0.0.1 tracker.example.test ::1",
        "0.0.0.0 127.0.0.1",
        "||disabled.example.test^",
        "||disabled.example.test^$badfilter",
        "||ads.example.test^",
        "@@||trusted.ads.example.test^",
        "||critical.example.test^$important",
        "@@||critical.example.test^",
        "||unsupported.example.test^$script"
    ))
    verify("ads.example.test" in parsedFeeds.blocked, "hosts and Adblock block rules parsed")
    verify("tracker.example.test" in parsedFeeds.blocked, "loopback hosts aliases parsed")
    verify("127.0.0.1" !in parsedFeeds.blocked && "::1" !in parsedFeeds.blocked, "sink IP aliases ignored")
    verify("disabled.example.test" !in parsedFeeds.blocked, "badfilter disables matching block rule")
    verify("trusted.ads.example.test" in parsedFeeds.allowed, "Adblock exception parsed")
    verify("critical.example.test" in parsedFeeds.importantBlocked, "important block parsed")
    verify("unsupported.example.test" !in parsedFeeds.blocked, "unsupported network modifier ignored")
    DomainFilter.loadRuleSources(listOf({ parsedFeeds }), listOf(1))
    verify(DomainFilter.isBlocked("sub.ads.example.test"), "parsed hosts/adblock suffix blocks")
    verify(!DomainFilter.isBlocked("trusted.ads.example.test"), "ordinary exception overrides ordinary block")
    verify(DomainFilter.isBlocked("critical.example.test"), "important block cannot be overridden")
    verify(parsedFeeds.ignored >= 3, "invalid, sink-only and unsupported rules counted as ignored")

    println("Guardian DNS core Kotlin smoke checks: PASS ($assertions assertions)")
}