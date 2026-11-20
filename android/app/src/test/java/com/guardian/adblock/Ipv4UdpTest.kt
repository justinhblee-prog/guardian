package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class Ipv4UdpTest {
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

    @Test fun acceptsValidNonzeroIpv4UdpChecksum() {
        val packet = ByteArray(31)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = packet.size.toByte()
        packet[9] = 17
        packet[12] = 10
        packet[15] = 1
        packet[16] = 10
        packet[19] = 2
        packet[20] = 0x30
        packet[21] = 0x39
        packet[22] = 0
        packet[23] = 53
        packet[24] = 0
        packet[25] = 11
        packet[28] = 1
        packet[29] = 2
        packet[30] = 3
        sealUdpChecksum(packet, 20, 11)
        sealHeader(packet, 20)

        val parsed = Ipv4Udp.parseDns(packet, packet.size)
        assertNotNull(parsed)
        assertArrayEquals(byteArrayOf(1, 2, 3), parsed!!.payload)
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

    private fun sealHeader(packet: ByteArray, headerLength: Int) {
    private fun sealUdpChecksum(packet: ByteArray, offset: Int, length: Int) {
        packet[offset + 6] = 0
        packet[offset + 7] = 0
        var sum = 0L
        var i = 12
        while (i < 20) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or
                (packet[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        sum += 17L
        sum += length.toLong()
        i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += (((packet[i].toInt() and 0xff) shl 8) or
                (packet[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        if (i < end) sum += ((packet[i].toInt() and 0xff) shl 8).toLong()
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        var checksum = sum.inv().toInt() and 0xffff
        if (checksum == 0) checksum = 0xffff
        packet[offset + 6] = (checksum ushr 8).toByte()
        packet[offset + 7] = checksum.toByte()
    }


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
