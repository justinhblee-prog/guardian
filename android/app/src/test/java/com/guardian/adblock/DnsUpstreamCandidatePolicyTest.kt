package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUpstreamCandidatePolicyTest {
    @Test fun udpCandidatesPreferConfiguredThenPhysicalDnsThenPublicFallback() {
        val normalizedIpv6 = DnsAddressValidator.normalize("2001:db8::53")!!
        assertEquals(
            listOf("192.0.2.53", "10.0.0.1", normalizedIpv6, "1.1.1.1", "8.8.8.8"),
            DnsUpstreamCandidatePolicy.udpCandidates(
                configured = "192.0.2.53",
                systemDns = listOf("10.0.0.1", "2001:db8::53", "192.0.2.53"),
                publicFallback = listOf("1.1.1.1", "8.8.8.8"),
                virtualDns = setOf("10.13.37.2", "fd13:37::2")
            )
        )
    }

    @Test fun systemDnsOrderingPrefersActivePhysicalNetworkThenValidatedNetworks() {
        assertEquals(
            listOf("192.0.2.10", "192.0.2.20"),
            DnsUpstreamCandidatePolicy.orderedSystemDns(
                listOf(
                    DnsUpstreamCandidatePolicy.PhysicalDnsCandidate(listOf("192.0.2.20"), false, true, true),
                    DnsUpstreamCandidatePolicy.PhysicalDnsCandidate(listOf("192.0.2.10"), true, false, true),
                    DnsUpstreamCandidatePolicy.PhysicalDnsCandidate(listOf("192.0.2.30"), false, false, false)
                )
            )
        )
    }

    @Test fun systemDnsOrderingSkipsInvalidAndDuplicateAddressesAndHonorsLimit() {
        assertEquals(
            listOf("192.0.2.53", "2001:db8::53"),
            DnsUpstreamCandidatePolicy.orderedSystemDns(
                listOf(
                    DnsUpstreamCandidatePolicy.PhysicalDnsCandidate(listOf("bad", "192.0.2.53", "192.0.2.53"), false, true, true),
                    DnsUpstreamCandidatePolicy.PhysicalDnsCandidate(listOf("2001:db8::53"), false, false, true)
                ),
                maxServers = 2
            )
        )
    }

    @Test fun udpCandidatesRejectInvalidAndGuardianVirtualDnsAddresses() {
        assertEquals(
            listOf("1.1.1.1"),
            DnsUpstreamCandidatePolicy.udpCandidates(
                configured = "not-a-resolver",
                systemDns = listOf("10.13.37.2", "fd13:37::2", "127.0.0.1", "bad::address"),
                publicFallback = listOf("1.1.1.1"),
                virtualDns = setOf("10.13.37.2", "fd13:37::2")
            )
        )
    }

    @Test fun udpModeRetriesSameResolverOverTcpBeforeMovingToNextCandidate() {
        val attempts = DnsUpstreamCandidatePolicy.udpAttempts(
            "192.0.2.53", listOf("10.0.0.1"), listOf("1.1.1.1"),
            setOf("10.13.37.2", "fd13:37::2")
        ).map { "${it.transport}:${it.address}" }
        assertEquals(
            listOf(
                "udp:192.0.2.53", "tcp:192.0.2.53",
                "udp:10.0.0.1",
                "udp:1.1.1.1"
            ),
            attempts
        )
    }

    @Test fun truncatedFallbackResolverNeedsTcpUnlessItsTcpAttemptIsAlreadyQueued() {
        assertTrue(DnsUpstreamCandidatePolicy.needsTcpRetryAfterUdpTruncation(false))
        assertFalse(DnsUpstreamCandidatePolicy.needsTcpRetryAfterUdpTruncation(true))
    }

    @Test fun explicitTcpDotAndDohDoNotDowngradeToUdp() {
        listOf("tcp", "dot", "doh").forEach { transport ->
            assertEquals(listOf(transport), DnsUpstreamCandidatePolicy.transports(transport))
        }
    }
}
