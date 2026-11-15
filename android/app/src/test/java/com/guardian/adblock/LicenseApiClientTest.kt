
package com.guardian.adblock

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseApiClientTest {
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
