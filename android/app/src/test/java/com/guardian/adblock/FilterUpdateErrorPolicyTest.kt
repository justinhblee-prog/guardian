package com.guardian.adblock

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FilterUpdateErrorPolicyTest {
    @Test fun doesNotExposeExceptionMessageOrRequestUrl() {
        val error = IOException("GET https://filters.example/list?token=private failed")
        val code = FilterUpdateErrorPolicy.codeFor(error)

        assertEquals("IO_FAILURE", code)
        assertFalse(FilterUpdateErrorPolicy.displayMessage(code).contains("filters.example"))
        assertFalse(FilterUpdateErrorPolicy.displayMessage(code).contains("private"))
    }

    @Test fun classifiesCommonNetworkFailuresWithoutPersistingRawDetails() {
        assertEquals(
            "NETWORK_TIMEOUT",
            FilterUpdateErrorPolicy.codeFor(SocketTimeoutException("timeout for https://secret.example"))
        )
        assertEquals(
            "DNS_RESOLUTION_FAILED",
            FilterUpdateErrorPolicy.codeFor(UnknownHostException("secret-host.example"))
        )
    }

    @Test fun findsSafeCategoryInsideWrappedCauseChain() {
        val wrapped = IllegalStateException(
            "download failed: https://secret.example",
            SocketTimeoutException("private endpoint")
        )

        assertEquals("NETWORK_TIMEOUT", FilterUpdateErrorPolicy.codeFor(wrapped))
    }

    @Test fun unknownLegacyPreferenceValueIsNeverRenderedVerbatim() {
        val legacyValue = "https://private.example/path?api_key=secret"
        val display = FilterUpdateErrorPolicy.displayMessage(legacyValue)

        assertFalse(display.contains("private.example"))
        assertFalse(display.contains("api_key"))
        assertFalse(display.contains("secret"))
    }
}
