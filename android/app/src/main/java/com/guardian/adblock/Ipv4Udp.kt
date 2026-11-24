package com.guardian.adblock

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Ipv4Udp {
    data class Parsed(val source: ByteArray, val destination: ByteArray, val sourcePort: Int, val destinationPort: Int, val payload: ByteArray)

    fun parseDns(packet: ByteArray, length: Int): Parsed? {
        if (length < 28 || length > packet.size) return null
        val version = (packet[0].toInt() ushr 4) and 0xf
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0xf) * 4
        if (ihl < 20 || length < ihl + 8) return null
        if ((packet[9].toInt() and 0xff) != 17) return null
        val total = u16(packet, 2)
        if (total != length || total < ihl + 8) return null
        // Reject corrupted IPv4 headers before trusting addresses, offsets or ports.
        if (!checksumValid(packet, 0, ihl)) return null
        // DNS datagrams must not be IP-fragmented; reassembly is not implemented.
        // DNS datagrams must not be IP-fragmented; reassembly is not implemented.
        // Check the reserved flag, MF flag, and fragment offset independently.
        // The DF flag (0x4000) is valid and intentionally remains accepted.
        val fragment = u16(packet, 6)
        if ((packet[6].toInt() and 0x80) != 0) return null // Reserved flag (high bit of first flags byte).
        if ((fragment and 0x3fff) != 0) return null // MF flag or fragment offset; DF (0x4000) remains valid.
        val src = packet.copyOfRange(12, 16)
        val dst = packet.copyOfRange(16, 20)
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(packet, ihl + 4)
        if (udpLen < 8 || ihl + udpLen != total) return null
        // IPv4 permits an omitted UDP checksum (zero); a supplied checksum must
        // validate against the IPv4 pseudo-header and the complete UDP datagram.
        val udpChecksum = u16(packet, ihl + 6)
        if (udpChecksum != 0 && !validUdpChecksum(packet, ihl, udpLen)) return null
        return Parsed(src, dst, srcPort, dstPort, packet.copyOfRange(ihl + 8, ihl + udpLen))
    }

    fun response(request: Parsed, dns: ByteArray): ByteArray {
        // IPv4's 16-bit total-length field includes the 20-byte IP header.
        // Reject DNS bodies that would otherwise wrap that field and emit an
        // invalid packet (IPv6 UDP has a larger 65,527-byte payload ceiling).
        require(dns.size <= MAX_DNS_RESPONSE_BYTES) { "DNS response exceeds IPv4 UDP packet limit" }
        val udpLength = 8 + dns.size
        val totalLength = 20 + udpLength
        val out = ByteArray(totalLength)
        out[0] = 0x45
        put16(out, 2, totalLength)
        put16(out, 4, 0x4755)
        put16(out, 6, 0x4000)
        out[8] = 64
        out[9] = 17
        System.arraycopy(request.destination, 0, out, 12, 4)
        System.arraycopy(request.source, 0, out, 16, 4)
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, 53)
        put16(out, 22, request.sourcePort)
        put16(out, 24, udpLength)
        System.arraycopy(dns, 0, out, 28, dns.size)
        return out
    }

    private const val MAX_DNS_RESPONSE_BYTES = 65_507

    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }
    private fun validUdpChecksum(packet: ByteArray, offset: Int, length: Int): Boolean {
        var sum = 0L
        // IPv4 pseudo-header: source, destination, zero, protocol, UDP length.
        for (i in 12 until 20 step 2) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
        }
        sum += 17L
        sum += length.toLong()
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        if (i < end) sum += ((packet[i].toInt() and 0xff) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return (sum.toInt() and 0xffff) == 0xffff
    }

    private fun checksumValid(b: ByteArray, start: Int, length: Int): Boolean =
        checksum(b, start, length) == 0

    private fun checksum(b: ByteArray, start: Int, length: Int): Int {
        var sum = 0L
        var i = start
        while (i < start + length - 1) {
            sum += (((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        if (length % 2 != 0) sum += ((b[start + length - 1].toInt() and 0xff) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }
}