package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsTlsHostValidatorTest {
    @Test fun normalizesProviderHostnames() {
        assertEquals("cloudflare-dns.com", DnsTlsHostValidator.normalize(" CloudFlare-DNS.COM "))
        assertEquals("dns.google", DnsTlsHostValidator.normalize("DNS.GOOGLE"))
    }

    @Test fun acceptsSingleRootDotAndRemovesIt() {
        assertEquals("dns.quad9.net", DnsTlsHostValidator.normalize("dns.quad9.net."))
    }

    @Test fun rejectsIpAddressesAndSingleLabels() {
        assertNull(DnsTlsHostValidator.normalize("1.1.1.1"))
        assertNull(DnsTlsHostValidator.normalize("localhost"))
        assertNull(DnsTlsHostValidator.normalize("2001:4860:4860::8888"))
    }

    @Test fun usesCanonicalDnsNameForTlsPeerAndSniIdentity() {
        val identity = DnsTlsHostValidator.identity(" CloudFlare-DNS.COM. ")!!
        assertEquals("cloudflare-dns.com", identity.peerHost)
        assertEquals(identity.peerHost, identity.serverName)
        assertNull(DnsTlsHostValidator.identity("1.1.1.1"))
    }

    @Test fun rejectsInvalidDnsLabels() {
        assertNull(DnsTlsHostValidator.normalize("-dns.example.com"))
        assertNull(DnsTlsHostValidator.normalize("dns_.example.com"))
        assertNull(DnsTlsHostValidator.normalize("dns..example.com"))
        assertNull(DnsTlsHostValidator.normalize("dns.example.com.."))
    }
}
