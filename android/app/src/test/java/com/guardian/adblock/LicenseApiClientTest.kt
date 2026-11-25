package com.guardian.adblock

import android.content.Context
import java.lang.reflect.Modifier
import java.net.URL
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseApiClientTest {

    @Test
    fun acceptsRsa2048OrStrongerPinnedLicenseKey() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val encoded = Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        validateLicensePublicKeyConfiguration(encoded)
    }

    @Test
    fun rejectsMissingMalformedAndWeakPinnedLicenseKeys() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(1024)
        val weakKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        listOf("", "not-base64!", weakKey).forEach { encoded ->
            try {
                validateLicensePublicKeyConfiguration(encoded)
                throw AssertionError("invalid pinned license key must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun invalidVerifierConfigurationIsRecognizedWithoutTreatingItAsTokenCorruption() {
        assertFalse(isLicensePublicKeyConfigurationValid(""))
        assertFalse(isLicensePublicKeyConfigurationValid("not-base64!"))
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(1024)
        val weakKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        assertFalse(isLicensePublicKeyConfigurationValid(weakKey))
        generator.initialize(2048)
        val validKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)
        assertTrue(isLicensePublicKeyConfigurationValid(validKey))
    }

    @Test
    fun refreshForbiddenInvalidatesLocalEntitlementButTransientFailuresDoNot() {
        assertTrue(LicenseApiException(403, "device inactive").invalidatesEntitlement)
        assertFalse(LicenseApiException(401, "unauthorized").invalidatesEntitlement)
        assertFalse(LicenseApiException(429, "rate limited").invalidatesEntitlement)
        assertFalse(LicenseApiException(503, "service unavailable").invalidatesEntitlement)
    }

    @Test
    fun statusEndpointRequiresExplicitActiveStatusToRetainProEntitlement() {
        assertTrue(isActiveLicenseStatus("active"))
        assertTrue(isActiveLicenseStatus("ACTIVE"))
        assertFalse(isActiveLicenseStatus("free"))
        assertFalse(isActiveLicenseStatus("inactive"))
        assertFalse(isActiveLicenseStatus(""))
    }

    @Test
    fun serverStatusExplainsExpiredLocalTokenWithoutGrantingLocalPro() {
        assertEquals(
            "서버 확인 완료 · 이 기기의 PRO 라이선스가 활성 상태입니다.",
            licenseStatusMessage("active", localEntitlementActive = true)
        )
        assertEquals(
            "서버 라이선스는 활성 상태입니다. 로컬 토큰이 만료되어 상태 갱신이 필요합니다.",
            licenseStatusMessage("active", localEntitlementActive = false)
        )
        assertEquals(
            "서버에서 이 기기의 PRO 이용 권한을 확인하지 못했습니다.",
            licenseStatusMessage("free", localEntitlementActive = false)
        )
    }

    @Test
    fun entitlementMutationsAndStatusShareOneSynchronizationLock() {
        val client = LicenseApiClient::class.java
        val activate = client.getDeclaredMethod(
            "activate", Context::class.java, String::class.java, String::class.java
        )
        val refresh = client.getDeclaredMethod("refresh", Context::class.java)
        val deactivate = client.getDeclaredMethod("deactivate", Context::class.java)
        val status = client.getDeclaredMethod("status", Context::class.java)
        assertTrue(Modifier.isSynchronized(activate.modifiers))
        assertTrue(Modifier.isSynchronized(refresh.modifiers))
        assertTrue(Modifier.isSynchronized(deactivate.modifiers))
        assertTrue(Modifier.isSynchronized(status.modifiers))
    }

    @Test
    fun decodesValidUtf8LicenseApiResponse() {
        assertEquals("{\"status\":\"ok\"}", decodeLicenseApiResponseUtf8("{\"status\":\"ok\"}".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun rejectsMalformedUtf8LicenseApiResponse() {
        try {
            decodeLicenseApiResponseUtf8(byteArrayOf(0x7b, 0x22, 0x78, 0x22, 0x3a, 0xc3.toByte(), 0x28, 0x7d))
            throw AssertionError("malformed UTF-8 must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun acceptsHttpsOriginAndOptionalRootSlash() {
        assertEquals("https://licenses.example.com", parseLicenseApiBaseUrl("https://licenses.example.com/").toString())
        assertEquals("https://licenses.example.com:8443", parseLicenseApiBaseUrl("https://licenses.example.com:8443").toString())
    }

    @Test
    fun rejectsNonHttpsAndCredentialBearingOrigins() {
        listOf(
            "http://licenses.example.com",
            "https://user:pass@licenses.example.com",
            "https://licenses.example.com/path",
            "https://licenses.example.com/path/",
            "https://licenses.example.com///",
            "https://licenses.example.com?tenant=1",
            "https://licenses.example.com#fragment",
            "",
            "   "
        ).forEach { value ->
            try {
                parseLicenseApiBaseUrl(value)
                throw AssertionError("invalid license API origin must be rejected: $value")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun endpointResolutionUsesRootOriginInsteadOfAppendingToConfiguredPath() {
        val origin = parseLicenseApiBaseUrl("https://licenses.example.com/")
        assertEquals(
            "https://licenses.example.com/v1/licenses/status",
            URL(origin, "/v1/licenses/status").toString()
        )
    }
}
