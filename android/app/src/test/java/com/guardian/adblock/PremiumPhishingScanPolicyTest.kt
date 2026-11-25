package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumPhishingScanPolicyTest {
    @Test
    fun acceptsOrdinaryHttpsAndAddsDefaultScheme() {
        val explicit = PremiumPhishingScanPolicy.inspect("https://example.com/login")
        val implicit = PremiumPhishingScanPolicy.inspect("example.com/login")

        assertEquals(PremiumPhishingScanPolicy.Risk.LOW, explicit.risk)
        assertEquals("example.com", explicit.normalizedHost)
        assertEquals(PremiumPhishingScanPolicy.Risk.LOW, implicit.risk)
    }

    @Test
    fun flagsEmbeddedCredentialsAndIpLiteralHostsAsHighRisk() {
        val credentials = PremiumPhishingScanPolicy.inspect("https://trusted.example@evil.example/login")
        val ip = PremiumPhishingScanPolicy.inspect("http://192.0.2.10/login")

        assertEquals(PremiumPhishingScanPolicy.Risk.HIGH, credentials.risk)
        assertTrue(credentials.signals.contains(PremiumPhishingScanPolicy.Signal.EMBEDDED_CREDENTIALS))
        assertEquals(PremiumPhishingScanPolicy.Risk.HIGH, ip.risk)
        assertTrue(ip.signals.contains(PremiumPhishingScanPolicy.Signal.IP_LITERAL_HOST))
    }

    @Test
    fun flagsPunycodeAndDeepSubdomainChains() {
        val punycode = PremiumPhishingScanPolicy.inspect("https://xn--e1afmkfd.example/")
        val deep = PremiumPhishingScanPolicy.inspect("https://a.b.c.d.example/")

        assertEquals(PremiumPhishingScanPolicy.Risk.ELEVATED, punycode.risk)
        assertTrue(punycode.signals.contains(PremiumPhishingScanPolicy.Signal.PUNYCODE_HOST))
        assertTrue(deep.signals.contains(PremiumPhishingScanPolicy.Signal.DEEP_SUBDOMAIN_CHAIN))
    }

    @Test
    fun rejectsNonWebSchemesAndMalformedInput() {
        val file = PremiumPhishingScanPolicy.inspect("file:///etc/passwd")
        val malformed = PremiumPhishingScanPolicy.inspect("https://")
        val whitespace = PremiumPhishingScanPolicy.inspect("https://example.com/ bad")

        assertEquals(PremiumPhishingScanPolicy.Risk.HIGH, file.risk)
        assertTrue(file.signals.contains(PremiumPhishingScanPolicy.Signal.NON_HTTP_SCHEME))
        assertEquals(PremiumPhishingScanPolicy.Risk.INVALID, malformed.risk)
        assertEquals(PremiumPhishingScanPolicy.Risk.INVALID, whitespace.risk)
    }

    @Test
    fun doesNotTreatAnOrdinaryHostAsKnownSafeOrKnownMalicious() {
        val result = PremiumPhishingScanPolicy.inspect("https://example.com")

        assertFalse(result.signals.isNotEmpty())
        assertTrue(result.explanation.contains("안전을 보장"))
    }
}
