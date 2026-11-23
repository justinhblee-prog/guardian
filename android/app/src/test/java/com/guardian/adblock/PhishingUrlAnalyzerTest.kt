package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class PhishingUrlAnalyzerTest {
    @Test fun ordinaryHttpsUrlHasNoStructuralWarningSignals() {
        val result = PhishingUrlAnalyzer.analyze("https://example.com/help")
        assertTrue(result.inputAccepted)
        assertEquals("example.com", result.normalizedHost)
        assertEquals(PhishingUrlAnalyzer.Risk.LOW, result.risk)
        assertTrue(result.signals.isEmpty())
    }

    @Test fun insecureHttpAndSensitiveLoginHostAreFlaggedWithoutClaimingPhishing() {
        val result = PhishingUrlAnalyzer.analyze("http://secure-login.example.com/account")
        assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.INSECURE_HTTP))
        assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.DECEPTIVE_HOST_WORDS))
        assertTrue(result.risk != PhishingUrlAnalyzer.Risk.INVALID)
        assertTrue(result.explanation.contains("안전한 사이트"))
    }

    @Test fun punycodeAndMixedScriptSignalsAreRecognized() {
        val punycode = PhishingUrlAnalyzer.analyze("https://xn--e1afmkfd.example/")
        assertTrue(punycode.signals.contains(PhishingUrlAnalyzer.Signal.PUNYCODE_HOST))
        val mixed = PhishingUrlAnalyzer.analyze("https://раypal.example/")
        assertTrue(mixed.signals.contains(PhishingUrlAnalyzer.Signal.MIXED_SCRIPT_HOST))
    }

    @Test fun ipLiteralAndRedirectShortenerAreFlagged() {
        val ip = PhishingUrlAnalyzer.analyze("http://192.0.2.10:8080/login")
        assertTrue(ip.signals.contains(PhishingUrlAnalyzer.Signal.IP_LITERAL_HOST))
        assertTrue(ip.signals.contains(PhishingUrlAnalyzer.Signal.UNUSUAL_PORT))
        val short = PhishingUrlAnalyzer.analyze("https://bit.ly/abc123")
        assertTrue(short.signals.contains(PhishingUrlAnalyzer.Signal.URL_SHORTENER))
    }

    @Test fun userInformationAndExecutableDownloadsRaiseRisk() {
        val result = PhishingUrlAnalyzer.analyze("https://trusted.example@evil.example/download.apk")
        assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.EMBEDDED_CREDENTIALS))
        assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.EXECUTABLE_DOWNLOAD))
        assertEquals(PhishingUrlAnalyzer.Risk.HIGH, result.risk)
    }

    @Test fun rejectsUnsupportedSchemesMalformedHostsAndOversizedInput() {
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "https://",
            "https://bad host.example/",
            "https://" + "a".repeat(2050)
        ).forEach { raw ->
            assertEquals(raw, PhishingUrlAnalyzer.Risk.INVALID, PhishingUrlAnalyzer.analyze(raw).risk)
        }
    }

    @Test fun analysisNeverClaimsThatALowRiskResultIsSafe() {
        val result = PhishingUrlAnalyzer.analyze("https://example.com")
        assertTrue(result.explanation.contains("보증하지는 않습니다"))
    }
}
