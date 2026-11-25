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

    @Test fun flagsIpHostsAndUrlShortenersForReview() {
        val ip = PremiumUrlRiskScanner.scan("https://192.0.2.10/path")
        assertTrue(ip.indicators.any { it.contains("IPv4") })
        val short = PremiumUrlRiskScanner.scan("https://bit.ly/abc")
        assertEquals(UrlRiskLevel.REVIEW, short.level)
    }

    @Test fun flagsBrowserCompatibleNonCanonicalIpv4AddressForms() {
        val candidates = listOf(
            "https://2130706433/path",       // single 32-bit decimal integer
            "https://0x7f000001/path",      // hexadecimal integer
            "https://0177.0.0.1/path",      // legacy octal-like dotted form
            "https://192.168.001.001/path"  // leading-zero dotted form
        )
        for (candidate in candidates) {
            val result = PremiumUrlRiskScanner.scan(candidate)
            assertTrue("Expected IPv4 warning for $candidate: ${result.indicators}",
                result.indicators.any { it.contains("IPv4") })
            assertEquals("Non-canonical IP notation must not be scored LOW for $candidate",
                UrlRiskLevel.REVIEW, result.level)
        }
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
    }

    @Test fun userInfoCannotHideTheActualHost() {
        val result = PremiumUrlRiskScanner.scan("https://trusted.example@evil.example/path")
        assertTrue(result.valid)
        assertEquals("evil.example", result.host)
        assertEquals(UrlRiskLevel.HIGH, result.level)
    }
}
