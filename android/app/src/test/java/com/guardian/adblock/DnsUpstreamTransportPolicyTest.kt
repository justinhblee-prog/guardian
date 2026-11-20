package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsUpstreamTransportPolicyTest {
    @Test fun udpRetriesSameResolverOverTcp() {
        assertEquals(listOf("udp", "tcp"), DnsUpstreamTransportPolicy.attempts("udp"))
    }

    @Test fun configuredResolverThenDeviceResolversThenPublicFallbacks() {
        assertEquals(
            listOf("9.9.9.9", "192.0.2.53", "2001:db8::53", "1.1.1.1"),
            DnsUpstreamTransportPolicy.resolverCandidates(
                "9.9.9.9",
                listOf("192.0.2.53", "2001:db8::53", "9.9.9.9"),
                listOf("1.1.1.1")
            )
        )
    }

    @Test fun explicitTcpDoesNotChangeTransport() {
        assertEquals(listOf("tcp"), DnsUpstreamTransportPolicy.attempts("tcp"))
    }

    @Test fun encryptedTransportsAreNeverDowngraded() {
        assertEquals(listOf("dot"), DnsUpstreamTransportPolicy.attempts("dot"))
        assertEquals(listOf("doh"), DnsUpstreamTransportPolicy.attempts("doh"))
    }
}
