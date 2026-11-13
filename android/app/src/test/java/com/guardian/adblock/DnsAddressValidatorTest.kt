package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class DnsAddressValidatorTest {
    @Test fun normalizesIpv4AndAcceptsIpv6Literals() {
        assertEquals("1.1.1.1", DnsAddressValidator.normalize("001.001.001.001"))
        assertNotNull(DnsAddressValidator.normalize("2001:4860:4860::8888"))
    }

    @Test fun rejectsHostnamesAndInvalidAddresses() {
        assertNull(DnsAddressValidator.normalize("dns.google"))
        assertNull(DnsAddressValidator.normalize("256.1.1.1"))
        assertNull(DnsAddressValidator.normalize("1.1.1"))
        assertNull(DnsAddressValidator.normalize("fe80::1%wlan0"))
    }

    @Test fun acceptsOfficialIpv6ResolverPresets() {
        assertNotNull(DnsAddressValidator.normalize("2a10:50c0::ad1:ff"))
        assertNotNull(DnsAddressValidator.normalize("2a10:50c0::bad1:ff"))
        assertNotNull(DnsAddressValidator.normalize("2606:4700:4700::1111"))
    }
}
