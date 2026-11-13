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
}
