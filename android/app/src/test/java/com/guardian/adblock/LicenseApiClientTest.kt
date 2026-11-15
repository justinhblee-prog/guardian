
package com.guardian.adblock

import android.content.Context
import java.lang.reflect.Modifier
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseApiClientTest {

    @Test
    fun entitlementMutationRequestsShareOneSynchronizationLock() {
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
