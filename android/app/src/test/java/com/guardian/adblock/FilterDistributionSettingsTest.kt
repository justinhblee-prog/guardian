package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterDistributionSettingsTest {
    @Test
    fun acceptsHttpsOriginAndOptionalPathPrefix() {
        assertEquals(
            "https://filters.example.com",
            FilterDistributionSettings.parseBaseUrl("https://filters.example.com/")
        )
        assertEquals(
            "https://example.com/guardian/filters",
            FilterDistributionSettings.parseBaseUrl("https://example.com/guardian/filters/")
        )
        assertEquals(
            "https://filters.example.com:8443",
            FilterDistributionSettings.parseBaseUrl("https://filters.example.com:8443")
        )
    }

    @Test
    fun rejectsInsecureOrAmbiguousFilterServerAddresses() {
        listOf(
            "",
            "   ",
            "http://filters.example.com",
            "https://user:pass@filters.example.com",
            "https://filters.example.com?tenant=1",
            "https://filters.example.com#fragment",
            "https://filters.example.com:0",
            "https://filters.example.com:65536"
        ).forEach { value ->
            try {
                FilterDistributionSettings.parseBaseUrl(value)
                throw AssertionError("invalid filter server URL must be rejected: $value")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}
