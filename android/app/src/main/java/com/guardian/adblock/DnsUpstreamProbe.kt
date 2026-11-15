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

    /** Require NOERROR and at least one answer, not merely a matching DNS error packet. */
    fun isSuccessfulResolution(query: ByteArray, response: ByteArray?): Boolean {
        if (response == null || !DomainFilter.isMatchingResponse(query, response)) return false
        if (response.size < 12) return false
        val flags = ((response[2].toInt() and 0xff) shl 8) or (response[3].toInt() and 0xff)
        val answerCount = ((response[6].toInt() and 0xff) shl 8) or (response[7].toInt() and 0xff)
        return (flags and 0x000f) == 0 && answerCount > 0
    }
}
