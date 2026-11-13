package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class DnsProbeTest {
    @Test fun createsStandardRecursiveDnsAQuery() {
        val query = DnsProbe.buildQuery("doubleclick.net")
        assertEquals(0x4755, ((query[0].toInt() and 0xff) shl 8) or (query[1].toInt() and 0xff))
        assertEquals(0x0100, ((query[2].toInt() and 0xff) shl 8) or (query[3].toInt() and 0xff))
        assertEquals(1, ((query[4].toInt() and 0xff) shl 8) or (query[5].toInt() and 0xff))
        assertEquals("doubleclick.net", DomainFilter.readQuestionName(query))
        assertArrayEquals(byteArrayOf(0, 1, 0, 1), query.copyOfRange(query.size - 4, query.size))
    }

    @Test fun rejectsInvalidSelfTestHostnames() {
        assertThrows(IllegalArgumentException::class.java) {
            DnsProbe.buildQuery("bad domain.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DnsProbe.buildQuery("bad_domain.example")
        }
    }
}
