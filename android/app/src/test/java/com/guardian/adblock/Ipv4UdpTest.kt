package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class Ipv4UdpTest {
    @Test fun rejectsDnsResponseThatWouldOverflowIpv4TotalLength() {
        val request = Ipv4Udp.Parsed(
            source = byteArrayOf(10, 0, 0, 1),
            destination = byteArrayOf(10, 0, 0, 2),
            sourcePort = 53000,
            destinationPort = 53,
            payload = ByteArray(12)
        )
        assertFailsWith<IllegalArgumentException> {
            Ipv4Udp.response(request, ByteArray(65_508))
        }
        assertEquals(65_535, Ipv4Udp.response(request, ByteArray(65_507)).size)
    }

    @Test fun parsesUdpDnsPacket() {
        val p = ByteArray(20 + 8 + 12)
        p[0] = 0x45
        p[2] = 0; p[3] = p.size.toByte()
        p[9] = 17
        p[12] = 10; p[15] = 1
        p[16] = 10; p[19] = 2
        p[20] = 0x30; p[21] = 0x39
        p[22] = 0; p[23] = 53
        p[24] = 0; p[25] = 20
        sealHeader(p, 20)
        val parsed = Ipv4Udp.parseDns(p, p.size)
        assertNotNull(parsed)
        assertEquals(12345, parsed!!.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertEquals(12, parsed.payload.size)
    }

    @Test fun rejectsFragmentedDnsPacket() {
        val p = ByteArray(28)
        p[0] = 0x45; p[2] = 0; p[3] = 28; p[9] = 17
        p[6] = 0x20 // More-fragments flag
        p[22] = 0; p[23] = 53
        p[24] = 0; p[25] = 8
        assertNull(Ipv4Udp.parseDns(p, p.size))
    }

    @Test fun rejectsInconsistentIpAndUdpLengths() {
        val p = ByteArray(28)
        p[0] = 0x45; p[2] = 0; p[3] = 27; p[9] = 17
        p[22] = 0; p[23] = 53
        p[24] = 0; p[25] = 8
        assertNull(Ipv4Udp.parseDns(p, p.size))
        p[3] = 28; p[25] = 9
        assertNull(Ipv4Udp.parseDns(p, p.size))
    }

    @Test fun ignoresNonDnsUdp() {
        val p = ByteArray(28)
        p[0] = 0x45; p[2] = 0; p[3] = 28; p[9] = 17
        p[22] = 0; p[23] = 54
        assertNull(Ipv4Udp.parseDns(p, p.size))
    }
    @Test fun rejectsCallerLengthBeyondBufferAndTrailingBytes() {
        val packet = ByteArray(30)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 28
        packet[9] = 17
        packet[22] = 0
        packet[23] = 53
        packet[24] = 0
        packet[25] = 8

        assertNull(Ipv4Udp.parseDns(packet, 31))
        assertNull(Ipv4Udp.parseDns(packet, 30))
        sealHeader(packet, 20)
        assertNotNull(Ipv4Udp.parseDns(packet, 28))
    }

    @Test fun acceptsExactPacketLengthWithIpv4Options() {
        val packet = ByteArray(32)
        packet[0] = 0x46
        packet[2] = 0
        packet[3] = 32
        packet[9] = 17
        packet[26] = 0
        packet[27] = 53
        packet[28] = 0
        packet[29] = 8
        sealHeader(packet, 24)
        assertNotNull(Ipv4Udp.parseDns(packet, packet.size))
    }

    @Test fun rejectsCorruptedIpv4HeaderChecksum() {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 28
        packet[9] = 17
        packet[22] = 0
        packet[23] = 53
        packet[24] = 0
        packet[25] = 8
        sealHeader(packet, 20)
        assertNotNull(Ipv4Udp.parseDns(packet, packet.size))
        packet[12] = 1 // Change source address without updating header checksum.
        assertNull(Ipv4Udp.parseDns(packet, packet.size))
    }

    @Test fun rejectsInvalidNonzeroIpv4UdpChecksum() {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 28
        packet[9] = 17
        packet[12] = 10
        packet[16] = 10
        packet[22] = 0
        packet[23] = 53
        packet[24] = 0
        packet[25] = 8
        packet[26] = 0x12
        packet[27] = 0x34
        sealHeader(packet, 20)
        assertNull(Ipv4Udp.parseDns(packet, packet.size))
    }


    @Test fun rejectsReservedIpv4FlagButAllowsDontFragment() {
        val reserved = ByteArray(28)
        reserved[0] = 0x45
        reserved[2] = 0
        reserved[3] = 28
        reserved[6] = 0x80.toByte() // Reserved IPv4 flag must be zero.
        reserved[9] = 17
        reserved[22] = 0
        reserved[23] = 53
        reserved[24] = 0
        reserved[25] = 8
        sealHeader(reserved, 20)
        assertNull(Ipv4Udp.parseDns(reserved, reserved.size))

        val dontFragment = reserved.copyOf()
        dontFragment[6] = 0x40.toByte() // DF is valid and must remain accepted.
        sealHeader(dontFragment, 20)
        assertNotNull(Ipv4Udp.parseDns(dontFragment, dontFragment.size))
    }

    @Test fun acceptsValidNonzeroIpv4UdpChecksumAndBuildsValidResponse() {
        val packet = ByteArray(40)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 40
        packet[9] = 17
        packet[12] = 10
        packet[15] = 1
        packet[16] = 10
        packet[19] = 2
        packet[20] = 0xCF.toByte()
        packet[21] = 0x08.toByte()
        packet[22] = 0
        packet[23] = 53
        packet[24] = 0
        packet[25] = 20
        for (i in 28 until 40) packet[i] = (i - 27).toByte()
        val requestChecksum = ipv4UdpChecksum(packet, 20, 20)
        packet[26] = (requestChecksum ushr 8).toByte()
        packet[27] = requestChecksum.toByte()
        sealHeader(packet, 20)

        val parsed = Ipv4Udp.parseDns(packet, packet.size)
        assertNotNull(parsed)
        val response = Ipv4Udp.response(parsed!!, ByteArray(32) { it.toByte() })
        assertEquals(0, ipv4HeaderChecksum(response, 20))
        assertTrue(ipv4UdpChecksum(response, 20, response.size - 20) == 0)
        assertEquals(53, Ipv4Udp.u16(response, 20))
        assertEquals(parsed.sourcePort, Ipv4Udp.u16(response, 22))
    }

    private fun ipv4UdpChecksum(packet: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        for (i in 12 until 20 step 2) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
        }
        sum += 17L + length
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        if (i < end) sum += ((packet[i].toInt() and 0xff) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val result = sum.inv().toInt() and 0xffff
        return if (result == 0) 0xffff else result
    }

    private fun ipv4HeaderChecksum(packet: ByteArray, length: Int): Int {
        var sum = 0L
        var i = 0
        while (i < length) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt() and 0xffff
    }

    private fun sealHeader(packet: ByteArray, headerLength: Int) {
        packet[10] = 0
        packet[11] = 0
        var sum = 0L
        var i = 0
        while (i < headerLength) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or (packet[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val checksum = sum.inv().toInt() and 0xffff
        packet[10] = (checksum ushr 8).toByte()
        packet[11] = checksum.toByte()
    }

}
