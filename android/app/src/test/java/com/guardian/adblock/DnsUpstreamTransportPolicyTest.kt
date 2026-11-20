package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsUpstreamTransportPolicyTest {
    @Test fun udpRetriesSameResolverOverTcp() {
        assertEquals(listOf("udp", "tcp"), DnsUpstreamTransportPolicy.attempts("udp"))
    }

    @Test fun explicitTcpDoesNotChangeTransport() {
        assertEquals(listOf("tcp"), DnsUpstreamTransportPolicy.attempts("tcp"))
    }

    @Test fun encryptedTransportsAreNeverDowngraded() {
        assertEquals(listOf("dot"), DnsUpstreamTransportPolicy.attempts("dot"))
        assertEquals(listOf("doh"), DnsUpstreamTransportPolicy.attempts("doh"))
    }
}
