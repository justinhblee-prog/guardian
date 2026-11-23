package com.guardian.adblock

/**
 * Bounded IPv6 extension-header walker for the virtual DNS UDP endpoint.
 *
 * Only headers whose lengths and semantics can be validated here are accepted.
 * Fragmented packets and unsupported extension types are rejected rather than
 * handing an ambiguous transport offset to the DNS parser.
 */
internal object Ipv6ExtensionHeaderParser {
    private const val IPV6_HEADER_BYTES = 40
    private const val MAX_EXTENSION_HEADERS = 8
    private const val MAX_EXTENSION_BYTES = 2048

    fun transportOffset(
        packet: ByteArray,
        length: Int,
        expectedNextHeader: Int,
        minimumTransportBytes: Int
    ): Int? {
        if (length < IPV6_HEADER_BYTES || length > packet.size) return null
        if (((packet[0].toInt() ushr 4) and 0x0f) != 6) return null
        if (expectedNextHeader !in 0..255 || minimumTransportBytes < 0) return null

        var nextHeader = packet[6].toInt() and 0xff
        var offset = IPV6_HEADER_BYTES
        var extensionCount = 0
        val extensionStart = offset

        while (nextHeader != expectedNextHeader) {
            if (++extensionCount > MAX_EXTENSION_HEADERS) return null
            if (offset >= length) return null

            val currentHeader = nextHeader
            val followingHeader = packet[offset].toInt() and 0xff
            val headerLength = when (currentHeader) {
                0, 43, 60 -> {
                    if (offset + 2 > length) return null
                    ((packet[offset + 1].toInt() and 0xff) + 1) * 8
                }
                44 -> {
                    // Fragment reassembly is intentionally unsupported, including
                    // atomic fragments; reject rather than parse an incomplete stream.
                    return null
                }
                51 -> {
                    if (offset + 2 > length) return null
                    val bytes = ((packet[offset + 1].toInt() and 0xff) + 2) * 4
                    if (bytes < 12) return null // RFC 4302 minimum AH size.
                    bytes
                }
                // ESP, No Next Header, Mobility, HIP, Shim6 and unknown headers
                // are not needed for DNS UDP and are rejected fail-closed.
                else -> return null
            }

            if (headerLength <= 0 || offset + headerLength > length) return null
            if (offset + headerLength - extensionStart > MAX_EXTENSION_BYTES) return null

            if (currentHeader == 43) {
                // An active routing header can change the destination en route.
                // Only accept it after all segments have been processed.
                if (headerLength < 4) return null
                val segmentsLeft = packet[offset + 3].toInt() and 0xff
                if (segmentsLeft != 0) return null
            }

            offset += headerLength
            nextHeader = followingHeader
        }

        if (offset + minimumTransportBytes > length) return null
        return offset
    }
}
