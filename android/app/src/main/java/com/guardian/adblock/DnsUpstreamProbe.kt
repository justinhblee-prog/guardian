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

    fun isValidResponse(query: ByteArray, response: ByteArray?): Boolean =
        response != null && DomainFilter.isMatchingResponse(query, response)

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
        if (response == null || !DomainFilter.isMatchingResponse(query, response)) return false
        if (response.size < 12 || responseCode(response) != 0) return false
        val answerCount = u16(response, 6)
        if (answerCount == 0) return false
        val questionEnd = rawQuestionEnd(response) ?: return false
        val recordCount = answerCount.toLong() + u16(response, 8) + u16(response, 10)
        if (recordCount > response.size.toLong()) return false
        var offset = questionEnd
        repeat(recordCount.toInt()) {
            offset = skipName(response, offset) ?: return false
            if (offset + 10 > response.size) return false
            val dataLength = u16(response, offset + 8)
            offset += 10
            if (dataLength > response.size - offset) return false
            offset += dataLength
        }
        return offset == response.size
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
                    if (target >= message.size || !visited.add(target)) return null
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
