package com.guardian.adblock

/** Minimal, defensive IPv6 UDP/53 packet codec for the VPN's virtual DNS endpoint. */
object Ipv6Udp {
    data class Parsed(
        val source: ByteArray,
        val destination: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
        val hopLimit: Int
    )

    fun parseDns(packet: ByteArray, length: Int): Parsed? {
        if (length < 48 || length > packet.size) return null
        if (((packet[0].toInt() ushr 4) and 0x0f) != 6) return null
        val payloadLength = u16(packet, 4)
        if (payloadLength < 8 || 40 + payloadLength != length) return null
        // Extension headers and fragmentation are deliberately rejected until a
        // complete reassembly/extension-header implementation is available.
        if ((packet[6].toInt() and 0xff) != 17) return null
        val udpOffset = 40
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < 8 || udpLength != payloadLength) return null
        if (u16(packet, udpOffset + 2) != 53) return null
        // IPv6 UDP checksum is mandatory; reject zero and verify the pseudo-header.
        val checksum = u16(packet, udpOffset + 6)
        if (checksum == 0 || !validUdpChecksum(packet, udpOffset, udpLength)) return null
        return Parsed(
            packet.copyOfRange(8, 24), packet.copyOfRange(24, 40),
            u16(packet, udpOffset), 53,
            packet.copyOfRange(udpOffset + 8, udpOffset + udpLength),
            packet[7].toInt() and 0xff
        )
    }

    fun response(request: Parsed, dns: ByteArray): ByteArray {
        require(request.source.size == 16 && request.destination.size == 16)
        require(dns.size <= 65527)
        val udpLength = 8 + dns.size
        val out = ByteArray(40 + udpLength)
        out[0] = 0x60
        put16(out, 4, udpLength)
        out[6] = 17
        out[7] = 64
        System.arraycopy(request.destination, 0, out, 8, 16)
        System.arraycopy(request.source, 0, out, 24, 16)
        put16(out, 40, 53)
        put16(out, 42, request.sourcePort)
        put16(out, 44, udpLength)
        System.arraycopy(dns, 0, out, 48, dns.size)
        put16(out, 46, udpChecksum(out, 40, udpLength))
        return out
    }

    private fun validUdpChecksum(packet: ByteArray, offset: Int, length: Int): Boolean =
        checksumSum(packet, 8, 32, offset, length, length) == 0xffff

    private fun udpChecksum(packet: ByteArray, offset: Int, length: Int): Int {
        val result = checksumSum(packet, 8, 32, offset, length, length).inv() and 0xffff
        return if (result == 0) 0xffff else result
    }

    private fun checksumSum(packet: ByteArray, addressOffset: Int, addressLength: Int,
                            dataOffset: Int, dataLength: Int, udpLength: Int): Int {
        var sum = 0L
        fun addRange(start: Int, count: Int) {
            var i = start
            val end = start + count
            while (i + 1 < end) {
                sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
                i += 2
            }
            if (i < end) sum += ((packet[i].toInt() and 0xff) shl 8).toLong()
        }
        addRange(addressOffset, addressLength)
        sum += ((udpLength ushr 16) and 0xffff).toLong()
        sum += (udpLength and 0xffff).toLong()
        sum += 17L // next-header UDP
        addRange(dataOffset, dataLength)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt() and 0xffff
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }
}