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

    @Test fun rejectsMissingLocationAndUnsupportedStatus() {
        assertNull(FilterRedirectPolicy.resolve(base, 302, null, 0))
        assertNull(FilterRedirectPolicy.resolve(base, 304, "https://example.com/filter.txt", 0))
    }

    @Test fun rejectsRedirectsWithInvalidPortsOrFragments() {
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com:99999/filter.txt", 0))
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/filter.txt#fragment", 0))
    }

    @Test fun rejectsUnsafeInitialDownloadUrlsBeforeConnecting() {
        listOf(
            "http://example.com/filter.txt",
            "https:///filter.txt",
            "https://user:pass@example.com/filter.txt",
            "https://example.com:99999/filter.txt",
            "https://example.com/filter.txt#fragment"
        ).forEach { value ->
            try {
                assertFalse("must reject $value", FilterRedirectPolicy.isAllowedHttpsUrl(java.net.URL(value)))
            } catch (_: java.net.MalformedURLException) {
                // A malformed URL is rejected before a connection can be opened.
            }
        }
    }

    @Test fun acceptsSafeInitialDownloadUrl() {
        assertTrue(FilterRedirectPolicy.isAllowedHttpsUrl(java.net.URL(base)))
    }

    @Test fun capsRedirectChainAtFiveHops() {
        assertNotNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/next", 4))
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/next", 5))
    }
    @Test fun rejectsMalformedRawRequestTargetCharacters() {
        listOf(
            "https://example.com/filter list.txt",
            """https://example.com/filter.txt\segment"""
        ).forEach { value ->
            try {
                assertFalse("must reject malformed URL $value",
                    FilterRedirectPolicy.isAllowedHttpsUrl(java.net.URL(value)))
            } catch (_: java.net.MalformedURLException) {
                // A malformed URL is rejected before a connection can be opened.
            }
        }
        assertNull(FilterRedirectPolicy.resolve(base, 302, "https://example.com/filter list.txt", 0))
        assertNull(FilterRedirectPolicy.resolve(base, 302, """https://example.com/filter.txt\segment""", 0))
    }

    @Test fun acceptsIpv6HttpsAndPercentEncodedPath() {
        assertTrue(
            FilterRedirectPolicy.isAllowedHttpsUrl(
                java.net.URL("https://[2001:db8::1]:8443/filter%20list.txt")
            )
        )
        assertEquals(
            "https://[2001:db8::1]:8443/filter%20list.txt",
            FilterRedirectPolicy.resolve(
                base, 302, "https://[2001:db8::1]:8443/filter%20list.txt", 0
            )
        )
    }

    @Test fun rejectsControlCharactersInRedirectTargets() {
        listOf(
            "https://example.com/filter\nlist.txt",
            "https://example.com/filter\u0001list.txt"
        ).forEach { value ->
            assertNull(FilterRedirectPolicy.resolve(base, 302, value, 0))
        }
    }

}
