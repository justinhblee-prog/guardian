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

    @Test fun acceptsMatchingSuccessfulResolution() {
        val query = DnsUpstreamProbe.query()
        val response = successfulResponse(query)
        assertTrue(DnsUpstreamProbe.isValidResponse(query, response))
        assertTrue(DnsUpstreamProbe.isSuccessfulResolution(query, response))
    }

    @Test fun rejectsServfailEvenWhenQuestionAndTransactionIdMatch() {
        val query = DnsUpstreamProbe.query()
        val response = DomainFilter.makeServfail(query)
        assertTrue(DnsUpstreamProbe.isValidResponse(query, response))
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, response))
    }

    @Test fun retriesServfailAndRefusedButKeepsNxDomainAsAValidAnswer() {
        val query = DnsUpstreamProbe.query()
        val servfail = DomainFilter.makeServfail(query)!!
        val refused = servfail.copyOf().also { it[3] = ((it[3].toInt() and 0xff) and 0xf0 or 5).toByte() }
        val nxdomain = servfail.copyOf().also { it[3] = ((it[3].toInt() and 0xff) and 0xf0 or 3).toByte() }
        assertTrue(DnsUpstreamProbe.isRetryableResolverError(servfail))
        assertTrue(DnsUpstreamProbe.isRetryableResolverError(refused))
        assertFalse(DnsUpstreamProbe.isRetryableResolverError(nxdomain))
    }

    @Test fun rejectsNoErrorWithoutAnswersAndMismatchedResponses() {
        val query = DnsUpstreamProbe.query()
        val emptyAnswer = successfulResponse(query).also {
            it[6] = 0
            it[7] = 0
        }
        val other = DnsUpstreamProbe.query().also { it[1] = 0x35 }
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, emptyAnswer))
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, DomainFilter.makeServfail(other)))
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, null))
    }

    private fun successfulResponse(query: ByteArray): ByteArray {
        val response = DomainFilter.makeServfail(query)!!
        response[2] = (response[2].toInt() and 0xff).toByte()
        response[3] = ((response[3].toInt() and 0xff) and 0xf0).toByte()
        response[6] = 0
        response[7] = 1
        return response
    }
}
