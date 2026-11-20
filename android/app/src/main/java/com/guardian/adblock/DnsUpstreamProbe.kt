package com.guardian.adblock

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

    fun isRetryableResolverError(response: ByteArray): Boolean =
        responseCode(response) == 2 || responseCode(response) == 5

    /** Require NOERROR and a structurally complete first answer resource record. */
    fun isSuccessfulResolution(query: ByteArray, response: ByteArray?): Boolean {
        if (response == null || !DomainFilter.isMatchingResponse(query, response)) return false
        if (response.size < 12 || responseCode(response) != 0) return false
        if (u16(response, 6) == 0) return false
        val questionEnd = questionEnd(response) ?: return false
        val answerNameEnd = encodedNameEnd(response, questionEnd) ?: return false
        if (answerNameEnd + 10 > response.size) return false
        val dataLength = u16(response, answerNameEnd + 8)
        return dataLength <= response.size - (answerNameEnd + 10)
    }

    private fun questionEnd(message: ByteArray): Int? {
        val nameEnd = encodedNameEnd(message, 12) ?: return null
        return (nameEnd + 4).takeIf { it <= message.size }
    }

    /** Returns the end of an encoded DNS name, safely following compression pointers. */
    private fun encodedNameEnd(message: ByteArray, start: Int): Int? {
        if (start !in message.indices) return null
        val visited = BooleanArray(message.size)
        var cursor = start
        var encodedEnd = -1
        var expandedLength = 0
        var steps = 0
        while (cursor < message.size && steps++ <= message.size) {
            if (visited[cursor]) return null
            visited[cursor] = true
            val length = message[cursor].toInt() and 0xff
            when (length and 0xc0) {
                0xc0 -> {
                    if (cursor + 1 >= message.size) return null
                    val target = ((length and 0x3f) shl 8) or (message[cursor + 1].toInt() and 0xff)
                    if (target >= cursor || target >= message.size) return null
                    if (encodedEnd < 0) encodedEnd = cursor + 2
                    cursor = target
                }
                0x00 -> {
                    cursor++
                    if (length == 0) return if (encodedEnd >= 0) encodedEnd else cursor
                    if (length > 63 || cursor + length > message.size) return null
                    expandedLength += length + 1
                    if (expandedLength > 255) return null
                    cursor += length
                }
                else -> return null
            }
        }
        return null
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
