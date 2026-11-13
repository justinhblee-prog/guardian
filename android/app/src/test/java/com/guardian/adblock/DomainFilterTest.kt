package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class DomainFilterTest {
    @Test fun loadsSharedRuleFormatAndAllowTakesPrecedence() {
        DomainFilter.loadRules(listOf(
            "# comment",
            "block=doubleclick.net",
            "allow=trusted.doubleclick.net",
            "invalid line"
        ))
        assertTrue(DomainFilter.isBlocked("ads.doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("trusted.doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("x.trusted.doubleclick.net"))
    }

    @Test fun rejectsSharedRulesWithoutValidBlockEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            DomainFilter.loadRules(listOf("# empty", "block=bad domain", "allow=example.com", "unknown=ads.example"))
        }
    }

    @Test fun blocksExactAndSubdomains() {
        assertTrue(DomainFilter.isBlocked("doubleclick.net"))
        assertTrue(DomainFilter.isBlocked("ads.doubleclick.net"))
        assertFalse(DomainFilter.isBlocked("notdoubleclick.net"))
        assertFalse(DomainFilter.isBlocked("example.com"))
    }

    @Test fun extractsDnsQuestionName() {
        val q = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,
            0x0b,0x64,0x6f,0x75,0x62,0x6c,0x65,0x63,0x6c,0x69,0x63,0x6b,
            0x03,0x6e,0x65,0x74,0x00,0x00,0x01,0x00,0x01
        ).map { it.toByte() }.toByteArray()
        assertEquals("doubleclick.net", DomainFilter.readQuestionName(q))
        val nx = DomainFilter.makeNxdomain(q)!!
        assertEquals(0x81, nx[2].toInt() and 0xff)
        assertEquals(0x83, nx[3].toInt() and 0xff)
        assertEquals(0, nx[6].toInt() and 0xff)
    }

    @Test fun normalizesAndValidatesUserEnteredDomains() {
        assertEquals("ads.example.com", DomainFilter.normalizeRuleDomain("  ADS.Example.COM. "))
        assertNull(DomainFilter.normalizeRuleDomain("-ads.example.com"))
        assertNull(DomainFilter.normalizeRuleDomain("ads-.example.com"))
        assertNull(DomainFilter.normalizeRuleDomain("ads example.com"))
        assertNull(DomainFilter.normalizeRuleDomain("한글.example"))
        assertNull(DomainFilter.normalizeRuleDomain(""))
    }

    @Test fun customBlockAndAllowRulesComposeWithSharedRules() {
        DomainFilter.loadRules(listOf(
            "block=doubleclick.net",
            "block=ads.customer.example",
            "allow=trusted.ads.customer.example"
        ))
        assertTrue(DomainFilter.isBlocked("banner.ads.customer.example"))
        assertFalse(DomainFilter.isBlocked("trusted.ads.customer.example"))
        assertFalse(DomainFilter.isBlocked("pixel.trusted.ads.customer.example"))
    }

    @Test fun failedRuleReplacementKeepsPreviouslyActiveRules() {
        DomainFilter.loadRules(listOf("block=stable.example"))
        assertThrows(IllegalArgumentException::class.java) {
            DomainFilter.loadRules(listOf("allow=only.example"))
        }
        assertTrue(DomainFilter.isBlocked("ads.stable.example"))
    }

    @Test fun rejectsMalformedName() {
        assertNull(DomainFilter.readQuestionName(byteArrayOf(0,1,2)))
    }

    @Test fun rejectsDnsResponseAndMultipleQuestions() {
        val q = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val response = q.copyOf().also { it[2] = 0x81.toByte() }
        assertNull(DomainFilter.readQuestionName(response))
        val multi = q.copyOf().also { it[5] = 2 }
        assertNull(DomainFilter.makeNxdomain(multi))
    }

    @Test fun preservesRecursionDesiredInNxdomain() {
        val q = intArrayOf(
            0,1,0x01,0,0,1,0,0,0,0,0,0,
            1,0x61,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val nx = DomainFilter.makeNxdomain(q)!!
        assertEquals(0x81, nx[2].toInt() and 0xff)
        assertEquals(0x83, nx[3].toInt() and 0xff)
        assertEquals(1, nx[5].toInt() and 0xff)
    }
    @Test fun rejectsTruncatedAndQuestionMismatchUpstreamResponses() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val valid = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOf().also { it[2] = 0x83.toByte() }))
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOfRange(0, valid.size - 1)))
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOf().also { it[it.lastIndex] = 2 }))
        assertFalse(DomainFilter.isMatchingResponse(query, valid.copyOf().also { it[2] = 0x89.toByte() }))
    }

    @Test fun acceptsOnlyMatchingUpstreamDnsResponses() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x00,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val response = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
        assertTrue(DomainFilter.isMatchingResponse(query, response))
        assertFalse(DomainFilter.isMatchingResponse(query, response.copyOf().also { it[1] = 0x35 }))
        assertFalse(DomainFilter.isMatchingResponse(query, response.copyOf().also { it[2] = 0x01 }))
        assertFalse(DomainFilter.isMatchingResponse(query, response.copyOf().also { it[2] = 0x83.toByte() }))
    }

    @Test fun returnsServfailWhenUpstreamCannotResolve() {
        val query = intArrayOf(
            0x12,0x34,0x01,0x10,0x00,0x01,0,0,0,0,0,0,
            0x07,0x65,0x78,0x61,0x6d,0x70,0x6c,0x65,
            0x03,0x63,0x6f,0x6d,0,0,1,0,1
        ).map { it.toByte() }.toByteArray()
        val servfail = DomainFilter.makeServfail(query)!!
        assertEquals(0x81, servfail[2].toInt() and 0xff) // QR + RD
        assertEquals(0x92, servfail[3].toInt() and 0xff) // RA + CD + SERVFAIL
        assertEquals(1, servfail[5].toInt() and 0xff)
        assertEquals(0, servfail[7].toInt() and 0xff)
        assertNull(DomainFilter.makeServfail(byteArrayOf(1, 2, 3)))
    }

}
