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

    @Test fun rejectsExtensionHeadersAndInconsistentLengths() {
        val packet = queryPacket()
        packet[6] = 0 // Hop-by-hop extension header, not UDP
        assertNull(Ipv6Udp.parseDns(packet, packet.size))

        val badLength = queryPacket()
        badLength[5] = (badLength[5].toInt() - 1).toByte()
        assertNull(Ipv6Udp.parseDns(badLength, badLength.size))
        assertNull(Ipv6Udp.parseDns(queryPacket(), 1000))
    }

    @Test fun responseHasValidIpv6UdpChecksum() {
        val parsed = Ipv6Udp.parseDns(queryPacket(), queryPacket().size)!!
        val response = Ipv6Udp.response(parsed, byteArrayOf(5, 6, 7))
        assertEquals(6, (response[0].toInt() ushr 4) and 0x0f)
        assertEquals(17, response[6].toInt() and 0xff)
        // A generated packet must pass the same strict parser after reversing the
        // endpoint ports so its destination is the virtual DNS service port.
        val loopback = response.copyOf()
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
