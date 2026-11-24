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

    @Test fun onlyTheSchemeMatchingDefaultPortIsTreatedAsOrdinary() {
        val ordinaryHttp = PhishingUrlAnalyzer.analyze("http://example.com:80/")
        val ordinaryHttps = PhishingUrlAnalyzer.analyze("https://example.com:443/")
        val httpsOnHttpPort = PhishingUrlAnalyzer.analyze("https://example.com:80/")
        val httpOnHttpsPort = PhishingUrlAnalyzer.analyze("http://example.com:443/")

        assertFalse(ordinaryHttp.signals.contains(PhishingUrlAnalyzer.Signal.UNUSUAL_PORT))
        assertFalse(ordinaryHttps.signals.contains(PhishingUrlAnalyzer.Signal.UNUSUAL_PORT))
        assertTrue(httpsOnHttpPort.signals.contains(PhishingUrlAnalyzer.Signal.UNUSUAL_PORT))
        assertTrue(httpOnHttpsPort.signals.contains(PhishingUrlAnalyzer.Signal.UNUSUAL_PORT))
    }

    @Test fun rejectsNonCanonicalNumericIpv4HostForms() {
        listOf(
            "https://2130706433/",
            "https://0x7f000001/",
            "https://0x7f.1/",
            "https://127.1/",
            "https://999.999.999.999/"
        ).forEach { raw ->
            assertEquals(raw, PhishingUrlAnalyzer.Risk.INVALID, PhishingUrlAnalyzer.analyze(raw).risk)
        }
        val conventional = PhishingUrlAnalyzer.analyze("https://192.0.2.1/")
        assertTrue(conventional.signals.contains(PhishingUrlAnalyzer.Signal.IP_LITERAL_HOST))
    }

    @Test fun validatesBracketedIpv6LiteralsInsteadOfAcceptingDnsNames() {
        val valid = PhishingUrlAnalyzer.analyze("https://[2001:db8::1]:443/")
        val invalidDnsName = PhishingUrlAnalyzer.analyze("https://[example.com]/")
        val invalidIpv6 = PhishingUrlAnalyzer.analyze("https://[2001:db8:::1]/")

        assertTrue(valid.inputAccepted)
        assertTrue(valid.signals.contains(PhishingUrlAnalyzer.Signal.IP_LITERAL_HOST))
        assertEquals(PhishingUrlAnalyzer.Risk.INVALID, invalidDnsName.risk)
        assertEquals(PhishingUrlAnalyzer.Risk.INVALID, invalidIpv6.risk)
    }

    @Test fun userInformationAndExecutableDownloadsRaiseRisk() {
        val result = PhishingUrlAnalyzer.analyze("http://trusted.example@192.0.2.10:8080/download.apk")
        assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.EMBEDDED_CREDENTIALS))
        assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.EXECUTABLE_DOWNLOAD))
        assertEquals(PhishingUrlAnalyzer.Risk.HIGH, result.risk)
    }

    @Test fun detectsExecutableExtensionsAfterPercentDecodingPathBytes() {
        val encodedDot = PhishingUrlAnalyzer.analyze("https://example.com/download/payload%2Eapk")
        val encodedExtension = PhishingUrlAnalyzer.analyze("https://example.com/download/payload.%61%70%6b")
        val doubleEncodedDot = PhishingUrlAnalyzer.analyze("https://example.com/download/payload%252Eapk")
        val mixedCase = PhishingUrlAnalyzer.analyze("https://example.com/download/payload.ExE")
        val queryOnly = PhishingUrlAnalyzer.analyze("https://example.com/download?file=payload.apk")

        listOf(encodedDot, encodedExtension, doubleEncodedDot, mixedCase).forEach { result ->
            assertTrue(result.signals.contains(PhishingUrlAnalyzer.Signal.EXECUTABLE_DOWNLOAD))
        }
        assertFalse(queryOnly.signals.contains(PhishingUrlAnalyzer.Signal.EXECUTABLE_DOWNLOAD))
    }

    @Test fun acceptsOneTerminalDnsDotButRejectsMultipleTerminalDots() {
        val absoluteName = PhishingUrlAnalyzer.analyze("https://example.com./")
        val malformedName = PhishingUrlAnalyzer.analyze("https://example.com../")
        assertTrue(absoluteName.inputAccepted)
        assertEquals("example.com", absoluteName.normalizedHost)
        assertEquals(PhishingUrlAnalyzer.Risk.INVALID, malformedName.risk)
    }

    @Test fun rejectsUnsupportedSchemesMalformedHostsAndOversizedInput() {
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "https://",
            "https://bad host.example/",
            "https://example.com:70000/",
            "https://999.999.999.999/",
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
