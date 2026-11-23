package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class Ipv6ExtensionHeaderParserTest {
    @Test fun returnsBaseTransportOffsetWhenNoExtensionHeadersExist() {
        val packet = basePacket(nextHeader = 17, payloadLength = 8)
        assertEquals(40, Ipv6ExtensionHeaderParser.transportOffset(packet, packet.size, 17, 8))
    }

    @Test fun walksHopByHopAndDestinationOptionsHeaders() {
        val packet = basePacket(nextHeader = 0, payloadLength = 24)
        packet[40] = 60
        packet[41] = 0
        packet[48] = 17
        packet[49] = 0
        assertEquals(56, Ipv6ExtensionHeaderParser.transportOffset(packet, packet.size, 17, 8))
    }

    @Test fun acceptsCompletedRoutingHeaderButRejectsActiveRoutingHeader() {
        val completed = basePacket(nextHeader = 43, payloadLength = 16)
        completed[40] = 17
        completed[41] = 0
        completed[42] = 0
        completed[43] = 0
        assertEquals(48, Ipv6ExtensionHeaderParser.transportOffset(completed, completed.size, 17, 8))

        val active = completed.copyOf()
        active[43] = 1
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(active, active.size, 17, 8))
    }

    @Test fun rejectsFragmentHeadersAndUnsupportedNextHeaders() {
        val fragment = basePacket(nextHeader = 44, payloadLength = 16)
        fragment[40] = 17
        fragment[41] = 0
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(fragment, fragment.size, 17, 8))

        val esp = basePacket(nextHeader = 50, payloadLength = 8)
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(esp, esp.size, 17, 8))
    }

    @Test fun rejectsUndersizedAuthenticationHeaderAndAcceptsValidHeader() {
        val undersized = basePacket(nextHeader = 51, payloadLength = 16)
        undersized[40] = 17
        undersized[41] = 0 // (0 + 2) * 4 = 8 bytes; AH minimum is 12.
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(undersized, undersized.size, 17, 8))

        val valid = basePacket(nextHeader = 51, payloadLength = 20)
        valid[40] = 17
        valid[41] = 1 // (1 + 2) * 4 = 12 bytes.
        assertEquals(52, Ipv6ExtensionHeaderParser.transportOffset(valid, valid.size, 17, 8))
    }

    @Test fun rejectsTruncatedAndExcessiveExtensionChains() {
        val truncated = basePacket(nextHeader = 60, payloadLength = 4)
        truncated[40] = 17
        truncated[41] = 0
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(truncated, truncated.size, 17, 8))

        val chain = basePacket(nextHeader = 60, payloadLength = 9 * 8 + 8)
        for (index in 0 until 9) {
            val offset = 40 + index * 8
            chain[offset] = if (index == 8) 17 else 60
            chain[offset + 1] = 0
        }
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(chain, chain.size, 17, 8))
    }

    @Test fun enforcesPacketAndTransportBounds() {
        val packet = basePacket(nextHeader = 17, payloadLength = 8)
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(packet, packet.size + 1, 17, 8))
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(packet, 40, 17, 8))
        assertNull(Ipv6ExtensionHeaderParser.transportOffset(packet, packet.size, 17, 9))
        assertNotNull(Ipv6ExtensionHeaderParser.transportOffset(packet, packet.size, 17, 8))
    }

    private fun basePacket(nextHeader: Int, payloadLength: Int): ByteArray =
        ByteArray(40 + payloadLength).also {
            it[0] = 0x60
            it[4] = (payloadLength ushr 8).toByte()
            it[5] = payloadLength.toByte()
            it[6] = nextHeader.toByte()
        }
}
