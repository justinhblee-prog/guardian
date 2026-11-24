package com.guardian.adblock

/**
 * Defensive TCP packet codec for DNS-over-TCP traffic addressed to Guardian's
 * virtual DNS endpoint. IPv6 extension-header traversal is bounded and fragment
 * reassembly is intentionally unsupported; TCP stream state is managed by the
 * VPN service, not by this codec.
 */
object DnsTcpPacket {
    data class Parsed(
        val version: Int,
        val source: ByteArray,
        val destination: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val sequence: Long,
        val acknowledgement: Long,
        val flags: Int,
        val window: Int,
        val maximumSegmentSize: Int?,
        val payload: ByteArray
    ) {
        val syn: Boolean get() = flags and SYN != 0
        val ack: Boolean get() = flags and ACK != 0
        val fin: Boolean get() = flags and FIN != 0
        val rst: Boolean get() = flags and RST != 0
    }

    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10

    fun parse(packet: ByteArray, length: Int, expectedDestinationPort: Int = 53): Parsed? {
        if (length < 40 || length > packet.size) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        val tcpOffset: Int
        val source: ByteArray
        val destination: ByteArray
        if (version == 4) {
            val ihl = (packet[0].toInt() and 0x0f) * 4
            if (ihl < 20 || length < ihl + 20) return null
            if ((packet[9].toInt() and 0xff) != 6) return null
            if (u16(packet, 2) != length || !checksumValid(packet, 0, ihl)) return null
            val fragmentField = u16(packet, 6)
            // The IPv4 reserved flag must be zero; reject fragments and malformed reserved-bit use.
            if ((fragmentField and 0x8000) != 0 || (fragmentField and 0x3fff) != 0) return null
            tcpOffset = ihl
            source = packet.copyOfRange(12, 16)
            destination = packet.copyOfRange(16, 20)
        } else if (version == 6) {
            if (length < 60 || u16(packet, 4) + 40 != length) return null
            tcpOffset = ipv6TcpOffset(packet, length) ?: return null
            source = packet.copyOfRange(8, 24)
            destination = packet.copyOfRange(24, 40)
        } else return null

        val tcpLength = length - tcpOffset
        val headerLengthByte = packet[tcpOffset + 12].toInt() and 0xff
        // The low nibble contains TCP reserved bits and the experimental NS bit.
        // Guardian does not implement these semantics, so reject rather than silently ignore them.
        if ((headerLengthByte and 0x0f) != 0) return null
        val headerLength = ((headerLengthByte ushr 4) and 0x0f) * 4
        if (headerLength < 20 || headerLength > tcpLength) return null
        val checksum = u16(packet, tcpOffset + 16)
        if (checksum == 0 || !tcpChecksumValid(packet, version, tcpOffset, tcpLength, source, destination)) return null
        val tcpOptions = parseTcpOptions(packet, tcpOffset + 20, tcpOffset + headerLength) ?: return null
        val destinationPort = u16(packet, tcpOffset + 2)
        if (destinationPort != expectedDestinationPort) return null
        val flags = packet[tcpOffset + 13].toInt() and 0xff
        // Reject contradictory control-flag combinations instead of letting
        // malformed segments mutate the DNS TCP session state machine.
        if (flags and SYN != 0 && flags and (FIN or RST) != 0) return null
        if (flags and RST != 0 && flags and (FIN or PSH) != 0) return null
        return Parsed(
            version, source, destination, u16(packet, tcpOffset), destinationPort,
            u32(packet, tcpOffset + 4), u32(packet, tcpOffset + 8),
            flags, u16(packet, tcpOffset + 14), tcpOptions.maximumSegmentSize,
            packet.copyOfRange(tcpOffset + headerLength, length)
        )
    }

    
    private data class TcpOptions(val maximumSegmentSize: Int?)

    /** Parse bounded TCP options; malformed option lengths must not shift payload parsing. */
    private fun parseTcpOptions(packet: ByteArray, start: Int, end: Int): TcpOptions? {
        var offset = start
        var maximumSegmentSize: Int? = null
        while (offset < end) {
            val kind = packet[offset].toInt() and 0xff
            when (kind) {
                0 -> break // End of option list; remaining bytes are padding.
                1 -> offset++ // No-operation padding.
                else -> {
                    if (offset + 1 >= end) return null
                    val optionLength = packet[offset + 1].toInt() and 0xff
                    if (optionLength < 2 || offset + optionLength > end) return null
                    if (kind == 2) {
                        if (optionLength != 4 || maximumSegmentSize != null) return null
                        val value = u16(packet, offset + 2)
                        if (value == 0) return null
                        maximumSegmentSize = value
                    }
                    offset += optionLength
                }
            }
        }
        return TcpOptions(maximumSegmentSize)
    }

    fun response(request: Parsed, sequence: Long, acknowledgement: Long,
                 flags: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(request.version == 4 || request.version == 6)
        val addressLength = if (request.version == 4) 4 else 16
        require(request.source.size == addressLength && request.destination.size == addressLength)
        val ipLength = if (request.version == 4) 20 else 40
        // IPv4 total_length counts IP + TCP headers; IPv6 payload_length counts
        // the TCP header and data only. A common 65,515-byte payload cap would
        // overflow the IPv4 16-bit total-length field by 20 bytes.
        val maxTcpLength = if (request.version == 4) 65535 - ipLength else 65535
        require(payload.size <= maxTcpLength - 20)
        val tcpLength = 20 + payload.size
        val total = ipLength + tcpLength
        val out = ByteArray(total)
        if (request.version == 4) {
            out[0] = 0x45
            put16(out, 2, total)
            put16(out, 4, 0x4755)
            put16(out, 6, 0x4000)
            out[8] = 64
            out[9] = 6
            System.arraycopy(request.destination, 0, out, 12, 4)
            System.arraycopy(request.source, 0, out, 16, 4)
            put16(out, 10, checksum(out, 0, 20))
        } else {
            out[0] = 0x60
            put16(out, 4, tcpLength)
            out[6] = 6
            out[7] = 64
            System.arraycopy(request.destination, 0, out, 8, 16)
            System.arraycopy(request.source, 0, out, 24, 16)
        }
        val tcp = ipLength
        put16(out, tcp, 53)
        put16(out, tcp + 2, request.sourcePort)
        put32(out, tcp + 4, sequence)
        put32(out, tcp + 8, acknowledgement)
        out[tcp + 12] = 0x50
        out[tcp + 13] = flags.toByte()
        put16(out, tcp + 14, 65535)
        System.arraycopy(payload, 0, out, tcp + 20, payload.size)
        put16(out, tcp + 16, tcpChecksum(out, request.version, tcp, tcpLength,
            request.destination, request.source))
        return out
    }

    /**
     * Walks a bounded IPv6 extension-header chain to locate TCP. Fragment headers
     * remain deliberately unsupported because reassembly is not implemented.
     */
    private fun ipv6TcpOffset(packet: ByteArray, packetLength: Int): Int? =
        Ipv6ExtensionHeaderParser.transportOffset(packet, packetLength, expectedNextHeader = 6, minimumTransportBytes = 20)

    private fun tcpChecksumValid(packet: ByteArray, version: Int, offset: Int, length: Int,
                                 source: ByteArray, destination: ByteArray): Boolean =
        tcpChecksumSum(packet, version, offset, length, source, destination) == 0xffff

    private fun tcpChecksum(packet: ByteArray, version: Int, offset: Int, length: Int,
                            source: ByteArray, destination: ByteArray): Int {
        val result = tcpChecksumSum(packet, version, offset, length, source, destination).inv() and 0xffff
        return if (result == 0) 0xffff else result
    }

    private fun tcpChecksumSum(packet: ByteArray, version: Int, offset: Int, length: Int,
                               source: ByteArray, destination: ByteArray): Int {
        var sum = 0L
        fun add(bytes: ByteArray, start: Int, count: Int) {
            var i = start
            val end = start + count
            while (i + 1 < end) {
                sum += (((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)).toLong()
                i += 2
            }
            if (i < end) sum += ((bytes[i].toInt() and 0xff) shl 8).toLong()
        }
        add(source, 0, source.size)
        add(destination, 0, destination.size)
        if (version == 4) {
            sum += 6L
            sum += length.toLong()
        } else {
            sum += ((length ushr 16) and 0xffff).toLong()
            sum += (length and 0xffff).toLong()
            sum += 6L
        }
        add(packet, offset, length)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt() and 0xffff
    }

    private fun checksumValid(bytes: ByteArray, start: Int, length: Int) =
        checksum(bytes, start, length) == 0

    private fun checksum(bytes: ByteArray, start: Int, length: Int): Int {
        var sum = 0L
        var i = start
        while (i + 1 < start + length) {
            sum += (((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        if (i < start + length) sum += ((bytes[i].toInt() and 0xff) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
    private fun u32(b: ByteArray, i: Int): Long =
        ((b[i].toLong() and 0xff) shl 24) or ((b[i + 1].toLong() and 0xff) shl 16) or
            ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }
    private fun put32(b: ByteArray, i: Int, v: Long) {
        b[i] = (v ushr 24).toByte(); b[i + 1] = (v ushr 16).toByte()
        b[i + 2] = (v ushr 8).toByte(); b[i + 3] = v.toByte()
    }
}

/**
 * Shared bounded IPv6 extension-header walker for UDP and TCP DNS packets.
 * Fragment headers are rejected because IP fragment reassembly is not implemented.
 */
internal object Ipv6ExtensionHeaderParser {
    fun transportOffset(packet: ByteArray, packetLength: Int, expectedNextHeader: Int, minimumTransportBytes: Int): Int? {
        if (packetLength < 40 || packetLength > packet.size ||
            ((packet[0].toInt() ushr 4) and 0x0f) != 6) return null
        var nextHeader = packet[6].toInt() and 0xff
        var offset = 40
        var count = 0
        while (nextHeader != expectedNextHeader) {
            if (++count > 8 || offset - 40 > 2048 || offset + 2 > packetLength) return null
            val following = packet[offset].toInt() and 0xff
            val headerLength = when (nextHeader) {
                0, 43, 60, 135, 139, 140 -> ((packet[offset + 1].toInt() and 0xff) + 1) * 8
                51 -> ((packet[offset + 1].toInt() and 0xff) + 2) * 4
                44 -> return null
                else -> return null
            }
            val minimumHeaderLength = if (nextHeader == 51) 12 else 8
            if (headerLength < minimumHeaderLength || offset + headerLength > packetLength) return null
            // A Routing header with segments left still changes the packet's
            // final destination; Guardian cannot safely synthesize its reply.
            if (nextHeader == 43 && (packet[offset + 3].toInt() and 0xff) != 0) return null
            offset += headerLength
            nextHeader = following
        }
        return offset.takeIf { it - 40 <= 2048 && packetLength - it >= minimumTransportBytes }
    }
}

