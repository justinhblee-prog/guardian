package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterRedirectPolicyTest {
    private val base = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt"

    @Test fun acceptsRelativeHttpsRedirect() {
        assertEquals(
            "https://adguardteam.github.io/HostlistsRegistry/assets/filter_2.txt",
            FilterRedirectPolicy.resolve(base, 302, "filter_2.txt", 0)
        )
    }

    @Test fun acceptsAbsoluteHttpsRedirectToReleaseHost() {
        assertEquals(
            "https://raw.githubusercontent.com/example/filter.txt",
            FilterRedirectPolicy.resolve(base, 301, "https://raw.githubusercontent.com/example/filter.txt", 0)
        )
    }

    @Test fun rejectsHttpDowngradeRedirect() {
        assertNull(FilterRedirectPolicy.resolve(base, 302, "http://example.com/filter.txt", 0))
    }

    @Test fun rejectsRedirectWithEmbeddedCredentials() {
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://user:pass@example.com/filter.txt", 0))
    }

    @Test fun rejectsNonStandardTlsPortsAndFragments() {
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com:8443/filter.txt", 0))
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/filter.txt#section", 0))
        assertNotNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/filter.txt", 0))
    }

    @Test fun rejectsMissingLocationAndUnsupportedStatus() {
        assertNull(FilterRedirectPolicy.resolve(base, 302, null, 0))
        assertNull(FilterRedirectPolicy.resolve(base, 304, "https://example.com/filter.txt", 0))
    }

    @Test fun capsRedirectChainAtFiveHops() {
        assertNotNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/next", 4))
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/next", 5))
    }
}
