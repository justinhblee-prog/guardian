package com.guardian.adblock

/**
 * A real DNS resolution probe used before Guardian installs DNS-only VPN routes.
 * A matching SERVFAIL is not sufficient: it proves packet exchange, not usable DNS.
 */
object DnsUpstreamProbe {
    fun query(): ByteArray = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
        0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x01, 0x00, 0x01
    )

    fun isValidResponse(query: ByteArray, response: ByteArray?): Boolean {
        if (response == null || !DomainFilter.isMatchingResponse(query, response, allowTruncated = true)) {
            return false
        }
        // A matching transaction/question is not a healthy resolver response when
        // the resolver reports SERVFAIL or REFUSED; allow fallback to another server.
        return responseCode(response) !in setOf(2, 5)
    }

    fun responseCode(response: ByteArray): Int? {
        if (response.size < 4) return null
        val flags = ((response[2].toInt() and 0xff) shl 8) or (response[3].toInt() and 0xff)
        return flags and 0x000f
    }

    /** SERVFAIL and REFUSED are resolver failures; try the next configured fallback. */
    fun isRetryableResolverError(response: ByteArray): Boolean =
        responseCode(response) == 2 || responseCode(response) == 5

    /**
     * A positive answer count alone is not proof of usable DNS: a truncated packet
     * can advertise answers while containing no complete resource record. Validate
     * every answer/authority/additional record boundary before accepting the probe.
     */
    fun isSuccessfulResolution(query: ByteArray, response: ByteArray?): Boolean {
        if (response == null || !isValidResponse(query, response)) return false
        if (response.size < 12 || responseCode(response) != 0) return false
        // A TC-marked UDP response is not proof of usable resolution; the caller
        // must retry over TCP instead of installing routes based on a partial answer.
        if ((u16(response, 2) and 0x0200) != 0) return false
        val answerCount = u16(response, 6)
        if (answerCount == 0) return false
        val questionEnd = rawQuestionEnd(response) ?: return false
        // The probe asks for an A/IN record. A TXT-only, unrelated A record, or
        // malformed CNAME chain must not be accepted as proof that this resolver
        // can resolve the name that Guardian will send through it.
        val questionType = u16(response, questionEnd - 4)
        val questionClass = u16(response, questionEnd - 2)
        if (questionType != 1 || questionClass != 1) return false
        val questionName = DomainFilter.readQuestionName(query) ?: return false
        val recordCount = answerCount.toLong() + u16(response, 8) + u16(response, 10)
        if (recordCount > response.size.toLong()) return false
        var offset = questionEnd
        val addressOwners = HashSet<String>()
        val cnameTargets = HashMap<String, String>()
        repeat(recordCount.toInt()) { index ->
            val owner = decodeName(response, offset) ?: return false
            offset = owner.nextOffset
            if (offset + 10 > response.size) return false
            val recordType = u16(response, offset)
            val recordClass = u16(response, offset + 2)
            val dataLength = u16(response, offset + 8)
            offset += 10
            if (dataLength > response.size - offset) return false
            val dataEnd = offset + dataLength
            if (index < answerCount && recordClass == 1) {
                when (recordType) {
                    1 -> if (dataLength == 4) addressOwners.add(owner.value)
                    5 -> {
                        val target = decodeName(response, offset) ?: return false
                        if (target.nextOffset != dataEnd) return false
                        val previous = cnameTargets.put(owner.value, target.value)
                        if (previous != null && previous != target.value) return false
                    }
                }
            }
            offset = dataEnd
        }
        if (offset != response.size) return false

        // Follow only the queried name's answer-section CNAME chain. This prevents
        // an unrelated A record elsewhere in the packet from making a dead resolver
        // look healthy, while still supporting ordinary CNAME-based DNS answers.
        var currentName = questionName
        val visited = HashSet<String>()
        repeat(16) {
            if (currentName in addressOwners) return true
            if (!visited.add(currentName)) return false
            currentName = cnameTargets[currentName] ?: return false
        }
        return currentName in addressOwners
    }

    private data class DecodedName(val value: String, val nextOffset: Int)

    /** Decode a DNS name while bounding compression traversal and expanded length. */
    private fun decodeName(message: ByteArray, start: Int): DecodedName? {
        var offset = start
        var encodedEnd = -1
        var steps = 0
        var expandedLength = 0
        val visited = HashSet<Int>()
        val labels = ArrayList<String>()
        while (offset < message.size && steps++ < 128) {
            val length = message[offset].toInt() and 0xff
            when {
                length == 0 -> {
                    expandedLength++
                    if (expandedLength > 255) return null
                    return DecodedName(
                        labels.joinToString(".").lowercase(java.util.Locale.ROOT),
                        if (encodedEnd >= 0) encodedEnd else offset + 1
                    )
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
                    val label = String(message, offset + 1, length, Charsets.US_ASCII)
                    if (label.any { it.code <= 32 || it.code >= 127 || it == '.' }) return null
                    labels.add(label)
                    offset += length + 1
                }
            }
        }
        return null
    }
    private fun rawQuestionEnd(message: ByteArray): Int? {
        var offset = 12
        while (offset < message.size) {
            val length = message[offset++].toInt() and 0xff
            if (length == 0) return if (offset + 4 <= message.size) offset + 4 else null
            if ((length and 0xc0) != 0 || length > 63 || offset + length > message.size) return null
            offset += length
        }
        return null
    }

    /** Returns the byte after the encoded name; compression targets are bounds-checked. */
    private fun skipName(message: ByteArray, start: Int): Int? {
        var offset = start
        var encodedEnd = -1
        var steps = 0
        val visited = HashSet<Int>()
        while (offset < message.size && steps++ < 128) {
            val length = message[offset].toInt() and 0xff
            when {
                length == 0 -> return if (encodedEnd >= 0) encodedEnd else offset + 1
                (length and 0xc0) == 0xc0 -> {
                    if (offset + 1 >= message.size) return null
                    val target = ((length and 0x3f) shl 8) or (message[offset + 1].toInt() and 0xff)
                    if (target < 12 || target >= offset || !visited.add(target)) return null
                    if (encodedEnd < 0) encodedEnd = offset + 2
                    offset = target
                }
                (length and 0xc0) != 0 || length > 63 || offset + 1 + length > message.size -> return null
                else -> {
                    offset += length + 1
                }
            }
        }
        return null
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
