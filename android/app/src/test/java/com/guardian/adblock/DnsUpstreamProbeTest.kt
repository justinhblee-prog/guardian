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

    @Test fun acceptsMatchingSuccessfulResolutionWithCompleteAnswer() {
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

    @Test fun rejectsAdvertisedAnswerWhenResourceRecordIsTruncated() {
        val query = DnsUpstreamProbe.query()
        val response = successfulResponse(query).copyOf(successfulResponse(query).size - 1)
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, response))
    }

    @Test fun rejectsCompleteNonAddressAnswerAsProofOfUsableIpv4Dns() {
        val query = DnsUpstreamProbe.query()
        val txtOnly = successfulResponse(query).also {
            it[questionEnd(query) + 2] = 0x10
        }
        val shortAddress = successfulResponse(query).copyOf(successfulResponse(query).size - 1).also {
            it[it.size - 5] = 0
            it[it.size - 4] = 3
        }
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, txtOnly))
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, shortAddress))
    }

    private fun questionEnd(query: ByteArray): Int = 12 + 1 + 7 + 1 + 3 + 1 + 4

    @Test fun rejectsSelfReferencingCompressionPointerInAnswerOwnerName() {
        val query = DnsUpstreamProbe.query()
        val response = successfulResponse(query)
        response[29] = 0xc0.toByte()
        response[30] = 0x1d
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, response))
    }


    @Test fun rejectsCompressionPointerIntoDnsHeader() {
        val query = DnsUpstreamProbe.query()
        val response = successfulResponse(query)
        response[29] = 0xc0.toByte()
        response[30] = 0x04
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, response))
    }

    @Test fun rejectsResourceRecordWhoseRdataLengthExceedsPacket() {
        val query = DnsUpstreamProbe.query()
        val response = successfulResponse(query)
        response[response.size - 6] = 0x7f
        response[response.size - 5] = 0xff.toByte()
        assertFalse(DnsUpstreamProbe.isSuccessfulResolution(query, response))
    }

    private fun successfulResponse(query: ByteArray): ByteArray {
        val headerAndQuestion = DomainFilter.makeServfail(query)!!
        headerAndQuestion[2] = (headerAndQuestion[2].toInt() and 0xff).toByte()
        headerAndQuestion[3] = ((headerAndQuestion[3].toInt() and 0xff) and 0xf0).toByte()
        headerAndQuestion[6] = 0
        headerAndQuestion[7] = 1
        // Compressed owner name (pointer to QNAME), A/IN, TTL=60, RDLENGTH=4, 93.184.216.34.
        return headerAndQuestion + byteArrayOf(
            0xc0.toByte(), 0x0c, 0x00, 0x01, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x3c, 0x00, 0x04,
            0x5d, 0xb8.toByte(), 0xd8.toByte(), 0x22
        )
    }
}
