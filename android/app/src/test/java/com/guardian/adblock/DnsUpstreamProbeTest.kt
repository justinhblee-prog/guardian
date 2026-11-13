package com.guardian.adblock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUpstreamProbeTest {
    @Test fun buildsAValidExampleComAQuery() {
        val query = DnsUpstreamProbe.query()
        assertEquals(29, query.size)
        assertEquals(0x1234, ((query[0].toInt() and 255) shl 8) or (query[1].toInt() and 255))
        assertArrayEquals("example".toByteArray(), query.copyOfRange(13, 20))
        assertArrayEquals("com".toByteArray(), query.copyOfRange(21, 24))
    }

    @Test fun validatesMatchingDnsResponses() {
        val query = DnsUpstreamProbe.query()
        assertTrue(DnsUpstreamProbe.isValidResponse(query, DomainFilter.makeServfail(query)))
    }

    @Test fun rejectsMissingOrMismatchedResponses() {
        val query = DnsUpstreamProbe.query()
        assertFalse(DnsUpstreamProbe.isValidResponse(query, null))
        val other = DnsUpstreamProbe.query().also { it[1] = 0x35 }
        assertFalse(DnsUpstreamProbe.isValidResponse(query, DomainFilter.makeServfail(other)))
    }
}
