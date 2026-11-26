package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumUrlRiskScannerTest {
    @Test fun flagsPlainHttpAndPunycodeHosts() {
        val http = PremiumUrlRiskScanner.scan("http://example.com/login")
        assertEquals(UrlRiskLevel.HIGH, http.level)
        assertTrue(http.indicators.any { it.contains("HTTPS") })
        val idn = PremiumUrlRiskScanner.scan("https://xn--pple-43d.com")
        assertEquals(UrlRiskLevel.HIGH, idn.level)
        assertTrue(idn.indicators.any { it.contains("Punycode") })
    }

    @Test fun unicodeInternationalizedHostIsNormalizedAndFlagged() {
        val result = PremiumUrlRiskScanner.scan("https://bücher.com/login")
        assertTrue(result.valid)
        assertEquals("xn--bcher-kva.com", result.host)
        assertEquals(UrlRiskLevel.HIGH, result.level)
        assertTrue(result.indicators.any { it.contains("Punycode") })
    }

    @Test fun modernUnicodeLabelsUnsupportedByLegacyJdkIdnRemainValidAndFlagged() {
        val result = PremiumUrlRiskScanner.scan("https://аррӏе.com/login")
        assertTrue(result.valid)
        assertEquals("аррӏе.com", result.host)
        assertEquals(UrlRiskLevel.HIGH, result.level)
        assertTrue(result.indicators.any { it.contains("Punycode") })
    }

    @Test fun rejectsMalformedAndOutOfRangeExplicitPorts() {
        val invalidPorts = listOf(
            "https://example.com:abc/path",
            "https://example.com:-1/path",
            "https://example.com:65536/path",
            "https://example.com:999999999999999999999/path"
        )
        invalidPorts.forEach { candidate ->
            val result = PremiumUrlRiskScanner.scan(candidate)
            assertFalse("Expected invalid port to be rejected: $candidate", result.valid)
            assertTrue(result.indicators.any { it.contains("포트 번호") })
        }
    }

    @Test fun acceptsValidExplicitPorts() {
        listOf("https://example.com:0/path", "https://example.com:443/path", "https://example.com:65535/path")
            .forEach { candidate ->
                val result = PremiumUrlRiskScanner.scan(candidate)
                assertTrue("Expected valid port to be accepted: $candidate", result.valid)
                assertEquals(UrlRiskLevel.LOW, result.level)
            }
    }

    @Test fun flagsIpHostsAndUrlShortenersForReview() {
        val ip = PremiumUrlRiskScanner.scan("https://192.0.2.10/path")
        assertTrue(ip.indicators.any { it.contains("IPv4") })
        val short = PremiumUrlRiskScanner.scan("https://bit.ly/abc")
        assertEquals(UrlRiskLevel.REVIEW, short.level)
    }

    @Test fun flagsBrowserCompatibleNonCanonicalIpv4AddressForms() {
        val candidates = listOf(
            "https://2130706433/path",
            "https://0x7f000001/path",
            "https://0177.0.0.1/path",
            "https://192.168.001.001/path"
        )
        for (candidate in candidates) {
            val result = PremiumUrlRiskScanner.scan(candidate)
            assertTrue("Expected IPv4 warning for $candidate: ${result.indicators}",
                result.indicators.any { it.contains("IPv4") })
            assertEquals("Non-canonical IP notation must not be scored LOW for $candidate",
                UrlRiskLevel.REVIEW, result.level)
        }
    }

    @Test fun flagsBrandNamesEmbeddedInUnrelatedDomainsButAllowsOfficialDomains() {
        val cases = listOf(
            "https://paypal-login.example/path",
            "https://accounts.google.com.evil.example/",
            "https://secure-microsoft.example/"
        )
        cases.forEach { candidate ->
            val result = PremiumUrlRiskScanner.scan(candidate)
            assertTrue("Expected brand impersonation signal for $candidate", result.indicators.any { it.contains("브랜드명") })
            assertEquals(UrlRiskLevel.HIGH, result.level)
        }
        val official = PremiumUrlRiskScanner.scan("https://accounts.google.com/signin")
        assertFalse(official.indicators.any { it.contains("브랜드명") })
    }

    @Test fun ordinaryHttpsUrlHasNoHeuristicIndicators() {
        val result = PremiumUrlRiskScanner.scan("https://www.example.org/about")
        assertTrue(result.valid)
        assertEquals(UrlRiskLevel.LOW, result.level)
        assertTrue(result.indicators.isEmpty())
    }

    @Test fun malformedAndOversizedInputsAreRejected() {
        assertFalse(PremiumUrlRiskScanner.scan("").valid)
        assertFalse(PremiumUrlRiskScanner.scan("x".repeat(2049)).valid)
        assertFalse(PremiumUrlRiskScanner.scan("https:///missing-host").valid)
        assertFalse(PremiumUrlRiskScanner.scan("https://bad host.example").valid)
    }

    @Test fun userInfoCannotHideTheActualHost() {
        val result = PremiumUrlRiskScanner.scan("https://trusted.example@evil.example/path")
        assertTrue(result.valid)
        assertEquals("evil.example", result.host)
        assertEquals(UrlRiskLevel.HIGH, result.level)
    }
}
