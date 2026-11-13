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
}