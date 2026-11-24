package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class DnsQueryPolicyTest {
    private fun query(domain: String): ByteArray = DnsProbe.buildQuery(domain)

    @Test fun blockedDomainReturnsNxdomainWithoutCallingUpstream() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        var calls = 0
        val result = DnsQueryPolicy.resolve(query("sub.ads.example")) { calls++; null }
        assertNotNull(result)
        assertEquals("sub.ads.example", result!!.domain)
        assertTrue(result.blocked)
        assertFalse(result.forwarded)
        assertEquals(0, calls)
        assertEquals(3, result.response[3].toInt() and 0x0f)
    }

    @Test fun explicitAllowRuleOverridesOrdinaryBlockForDomainAndChildren() {
        DomainFilter.loadRules(listOf(
            "block=ads.example",
            "allow=trusted.ads.example"
        ))
        var calls = 0
        val result = DnsQueryPolicy.resolve(query("cdn.trusted.ads.example")) {
            calls++
            DomainFilter.makeServfail(it)
        }
        assertNotNull(result)
        assertFalse(result!!.blocked)
        assertTrue(result.forwarded)
        assertEquals(1, calls)
    }

    @Test fun importantBlockOverridesAllowRule() {
        DomainFilter.loadRules(listOf(
            "allow=trusted.ads.example",
            "block-important=trusted.ads.example"
        ))
        var calls = 0
        val result = DnsQueryPolicy.resolve(query("trusted.ads.example")) { calls++; null }
        assertNotNull(result)
        assertTrue(result!!.blocked)
        assertEquals(0, calls)
    }

    @Test fun appSpecificRulesBlockOnlyTheSelectedAppsQuery() {
        DomainFilter.loadRules(listOf("block=global.example"))
        var calls = 0
        val selectedApp = DnsQueryPolicy.resolve(
            query("app-only.example"),
            setOf("app-only.example")
        ) { calls++; null }
        assertNotNull(selectedApp)
        assertTrue(selectedApp!!.blocked)
        assertEquals(0, calls)

        val otherApp = DnsQueryPolicy.resolve(query("app-only.example")) {
            calls++
            DomainFilter.makeServfail(it)
        }
        assertNotNull(otherApp)
        assertFalse(otherApp!!.blocked)
        assertTrue(otherApp.forwarded)
        assertEquals(1, calls)
    }

    @Test fun globalAllowRuleOverridesAppSpecificAdditionalBlock() {
        DomainFilter.loadRules(listOf("allow=trusted.example"))
        var calls = 0
        val result = DnsQueryPolicy.resolve(
            query("sub.trusted.example"),
            setOf("sub.trusted.example")
        ) {
            calls++
            DomainFilter.makeServfail(it)
        }
        assertNotNull(result)
        assertFalse(result!!.blocked)
        assertTrue(result.forwarded)
        assertEquals(1, calls)
    }

    @Test fun globalImportantBlockOverridesMatchingAppSpecificAdditionalBlock() {
        DomainFilter.loadRules(listOf("block-important=critical.example"))
        var calls = 0
        val result = DnsQueryPolicy.resolve(
            query("critical.example"),
            setOf("critical.example")
        ) {
            calls++
            null
        }
        assertNotNull(result)
        assertTrue(result!!.blocked)
        assertFalse(result.forwarded)
        assertEquals(0, calls)
    }

    @Test fun upstreamNullReturnsServfailAndMarksFailure() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        val result = DnsQueryPolicy.resolve(query("news.example")) { null }
        assertNotNull(result)
        assertFalse(result!!.blocked)
        assertFalse(result.forwarded)
        assertEquals(2, result.response[3].toInt() and 0x0f)
    }

    @Test fun upstreamExceptionReturnsServfailWithoutEscapingPacketHandler() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        val result = DnsQueryPolicy.resolve(query("news.example")) {
            throw IllegalStateException("simulated upstream failure")
        }
        assertNotNull(result)
        assertFalse(result!!.blocked)
        assertFalse(result.forwarded)
        assertEquals(2, result.response[3].toInt() and 0x0f)
    }

    @Test fun validUpstreamResponseIsForwarded() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        val request = query("news.example")
        val expected = DomainFilter.makeServfail(request)!!
        val result = DnsQueryPolicy.resolve(request) { expected }
        assertNotNull(result)
        assertTrue(result!!.forwarded)
        assertArrayEquals(expected, result.response)
    }

    @Test fun mismatchedUpstreamTransactionReturnsServfailInsteadOfForwarding() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        val request = query("news.example")
        val mismatched = DomainFilter.makeServfail(request)!!.also {
            it[1] = (it[1].toInt() xor 0x01).toByte()
        }
        val result = DnsQueryPolicy.resolve(request) { mismatched }
        assertNotNull(result)
        assertFalse(result!!.forwarded)
        assertEquals(2, result.response[3].toInt() and 0x0f)
        assertEquals(request[1].toInt() and 0xff, result.response[1].toInt() and 0xff)
    }

    @Test fun truncatedUpstreamUdpReplyIsNotForwardedAsFinalAnswer() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        val request = query("news.example")
        val truncated = DomainFilter.makeServfail(request)!!.also {
            it[2] = (it[2].toInt() or 0x02).toByte()
        }
        val result = DnsQueryPolicy.resolve(request) { truncated }
        assertNotNull(result)
        assertFalse(result!!.forwarded)
        assertEquals(2, result.response[3].toInt() and 0x0f)
        assertEquals(0, result.response[2].toInt() and 0x02)
    }

    @Test fun malformedQueryIsRejectedWithoutCallingUpstream() {
        DomainFilter.loadRules(listOf("block=ads.example"))
        var calls = 0
        assertNull(DnsQueryPolicy.resolve(byteArrayOf(1, 2, 3)) { calls++; null })
        assertEquals(0, calls)
    }
}
