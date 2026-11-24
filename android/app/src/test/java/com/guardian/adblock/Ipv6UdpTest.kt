package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class Ipv6UdpTest {
    @Test fun parsesIpv6UdpDnsWithValidMandatoryChecksum() {
        val packet = queryPacket()
        val parsed = Ipv6Udp.parseDns(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(53000, parsed!!.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parsed.payload)
    }

    @Test fun rejectsZeroOrInvalidIpv6UdpChecksum() {
        val packet = queryPacket()
        packet[46] = 0
        packet[47] = 0
        assertNull(Ipv6Udp.parseDns(packet, packet.size))

        val corrupt = queryPacket()
        corrupt[48] = 9
        assertNull(Ipv6Udp.parseDns(corrupt, corrupt.size))
    }

    @Test fun rejectsMalformedExtensionHeadersAndInconsistentLengths() {
        val packet = queryPacket()
        packet[6] = 0 // Hop-by-hop extension header, not UDP
        assertNull(Ipv6Udp.parseDns(packet, packet.size))

        val badLength = queryPacket()
        badLength[5] = (badLength[5].toInt() - 1).toByte()
        assertNull(Ipv6Udp.parseDns(badLength, badLength.size))
        assertNull(Ipv6Udp.parseDns(queryPacket(), 1000))
    }

    @Test fun parsesIpv6UdpDnsAfterHopByHopAndDestinationOptions() {
        val base = queryPacket()
        val packet = ByteArray(base.size + 16)
        System.arraycopy(base, 0, packet, 0, 40)
        packet[4] = 0
        packet[5] = 28 // 16 extension-header bytes + 12 UDP bytes
        packet[6] = 0 // Hop-by-Hop Options
        packet[40] = 60 // Destination Options follows
        packet[41] = 0
        packet[48] = 17 // UDP follows
        packet[49] = 0
        System.arraycopy(base, 40, packet, 56, 12)
        packet[62] = 0; packet[63] = 0
        val checksum = udpChecksum(packet, 56, 12)
        packet[62] = (checksum ushr 8).toByte()
        packet[63] = checksum.toByte()

        val parsed = Ipv6Udp.parseDns(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(53000, parsed!!.sourcePort)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parsed.payload)
    }

    @Test fun rejectsMisplacedOrRepeatedHopByHopOptionsHeaders() {
        val base = queryPacket()
        fun packetWithTwoExtensionHeaders(first: Int, second: Int): ByteArray {
            val packet = ByteArray(base.size + 16)
            System.arraycopy(base, 0, packet, 0, 40)
            packet[4] = 0
            packet[5] = 28 // Two 8-byte extension headers + 12-byte UDP datagram
            packet[6] = first
            packet[40] = second
            packet[41] = 0
            packet[48] = 17
            packet[49] = 0
            System.arraycopy(base, 40, packet, 56, 12)
            packet[62] = 0
            packet[63] = 0
            val checksum = udpChecksum(packet, 56, 12)
            packet[62] = (checksum ushr 8).toByte()
            packet[63] = checksum.toByte()
            return packet
        }

        // Hop-by-Hop Options is valid only immediately after the IPv6 base header.
        assertNull(Ipv6Udp.parseDns(packetWithTwoExtensionHeaders(60, 0), base.size + 16))
        // It may occur only once in the extension-header chain.
        assertNull(Ipv6Udp.parseDns(packetWithTwoExtensionHeaders(0, 0), base.size + 16))
    }

    @Test fun rejectsActiveIpv6RoutingHeaderButAcceptsCompletedRoute() {
        val base = queryPacket()
        fun packetWithSegmentsLeft(segmentsLeft: Int): ByteArray {
            val packet = ByteArray(base.size + 8)
            System.arraycopy(base, 0, packet, 0, 40)
            packet[4] = 0
            packet[5] = 20
            packet[6] = 43 // Routing header
            packet[40] = 17 // UDP follows
            packet[41] = 0
            packet[42] = 0 // Routing Type 0 (not interpreted when segments-left is zero)
            packet[43] = segmentsLeft.toByte()
            System.arraycopy(base, 40, packet, 48, 12)
            packet[54] = 0; packet[55] = 0
            val checksum = udpChecksum(packet, 48, 12)
            packet[54] = (checksum ushr 8).toByte()
            packet[55] = checksum.toByte()
            return packet
        }

        assertNull(Ipv6Udp.parseDns(packetWithSegmentsLeft(1), 60))
        val completed = packetWithSegmentsLeft(0)
        assertNotNull(Ipv6Udp.parseDns(completed, completed.size))
    }

    @Test fun rejectsUndersizedIpv6AuthenticationHeader() {
        val packet = ByteArray(60)
        packet[0] = 0x60
        packet[4] = 0
        packet[5] = 20
        packet[6] = 51 // Authentication Header
        packet[40] = 17 // UDP follows
        packet[41] = 0 // AH length field describes only 8 bytes; RFC minimum is 12
        assertNull(Ipv6Udp.parseDns(packet, packet.size))
    }

    @Test fun rejectsTruncatedUdpHeaderAfterValidExtensionHeader() {
        val packet = ByteArray(52)
        packet[0] = 0x60
        packet[4] = 0
        packet[5] = 12
        packet[6] = 60 // Destination Options
        packet[40] = 17 // UDP follows
        packet[41] = 0
        assertNull(Ipv6Udp.parseDns(packet, packet.size))
    }

    @Test fun rejectsIpv6UdpFragmentAndMalformedExtensionHeader() {
        val base = queryPacket()
        val fragment = ByteArray(base.size + 8)
        System.arraycopy(base, 0, fragment, 0, 40)
        fragment[4] = 0
        fragment[5] = 20
        fragment[6] = 44
        fragment[40] = 17
        fragment[41] = 0
        System.arraycopy(base, 40, fragment, 48, 12)
        assertNull(Ipv6Udp.parseDns(fragment, fragment.size))

        val malformed = ByteArray(base.size + 8)
        System.arraycopy(base, 0, malformed, 0, 40)
        malformed[4] = 0
        malformed[5] = 20
        malformed[6] = 60
        malformed[40] = 17
        malformed[41] = 10
        System.arraycopy(base, 40, malformed, 48, 12)
        assertNull(Ipv6Udp.parseDns(malformed, malformed.size))
    }

    @Test fun responseHasValidIpv6UdpChecksum() {
        val parsed = Ipv6Udp.parseDns(queryPacket(), queryPacket().size)!!
        val response = Ipv6Udp.response(parsed, byteArrayOf(5, 6, 7))
        assertEquals(6, (response[0].toInt() ushr 4) and 0x0f)
        assertEquals(17, response[6].toInt() and 0xff)
        // A generated packet must pass the same strict parser after reversing the
        // endpoint ports so its destination is the virtual DNS service port.
        val loopback = response.copyOf()
        loopback[46] = 0; loopback[47] = 0 // zero checksum before recomputing after port rewrite
        loopback[40] = 0xCF.toByte(); loopback[41] = 0x08.toByte() // source 53000
        loopback[42] = 0; loopback[43] = 53
        val checksum = udpChecksum(loopback, 40, 11)
        loopback[46] = (checksum ushr 8).toByte()
        loopback[47] = checksum.toByte()
        assertNotNull(Ipv6Udp.parseDns(loopback, loopback.size))
    }

    private fun queryPacket(): ByteArray {
        val dns = byteArrayOf(1, 2, 3, 4)
        val packet = ByteArray(40 + 8 + dns.size)
        packet[0] = 0x60
        packet[4] = 0
        packet[5] = 12
        packet[6] = 17
        packet[7] = 64
        packet[8] = 0x20
        packet[23] = 1
        packet[24] = 0x20
        packet[39] = 2
        packet[40] = 0xCF.toByte()
        packet[41] = 0x08.toByte()
        packet[42] = 0
        packet[43] = 53
        packet[44] = 0
        packet[45] = 12
        System.arraycopy(dns, 0, packet, 48, dns.size)
        val checksum = udpChecksum(packet, 40, 12)
        packet[46] = (checksum ushr 8).toByte()
        packet[47] = checksum.toByte()
        return packet
    }

    private fun udpChecksum(packet: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        fun add(start: Int, count: Int) {
            var i = start
            val end = start + count
            while (i + 1 < end) {
                sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
                i += 2
            }
            if (i < end) sum += ((packet[i].toInt() and 0xff) shl 8).toLong()
        }
        add(8, 32)
        sum += (length and 0xffff).toLong()
        sum += 17L
        add(offset, length)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val result = sum.inv().toInt() and 0xffff
        return if (result == 0) 0xffff else result
    }
}
