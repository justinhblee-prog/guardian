package com.guardian.adblock

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeFailureSummaryTest {
    @Test fun neverExposesRawMessagesAddressesOrPaths() {
        val error = IOException("connect to 192.0.2.4 failed at /data/user/0/app/private")
        val detail = SafeFailureSummary.describe(error)
        assertEquals("네트워크 또는 입출력 오류", detail)
        assertFalse(detail.contains("192.0.2.4"))
        assertFalse(detail.contains("/data/"))
    }

    @Test fun classifiesNetworkFailuresAndWrappedCauses() {
        assertEquals("NETWORK_TIMEOUT", SafeFailureSummary.codeFor(SocketTimeoutException("https://secret.invalid")))
        assertEquals("DNS_RESOLUTION_FAILED", SafeFailureSummary.codeFor(UnknownHostException("private-host")))
        assertEquals(
            "TLS_FAILURE",
            SafeFailureSummary.codeFor(IllegalStateException("wrapped secret", SSLException("certificate path")))
        )
    }

    @Test fun handlesCyclicCauseChainsWithoutLooping() {
        val first = IllegalStateException("private detail")
        val second = IOException("private path")
        first.initCause(second)
        second.initCause(first)
        assertEquals("IO_FAILURE", SafeFailureSummary.codeFor(first))
    }

    @Test fun diagnosticTypeChainReportsClassesWithoutLeakingMessages() {
        val error = IllegalStateException(
            "secret URL https://private.invalid",
            IOException("private path", SocketTimeoutException("private resolver"))
        )
        val chain = SafeFailureSummary.diagnosticTypeChain(error)
        assertEquals("IllegalStateException -> IOException -> SocketTimeoutException", chain)
        assertFalse(chain.contains("private"))
        assertFalse(chain.contains("https://"))
    }

    @Test fun diagnosticTypeChainStopsAtEightCauses() {
        var error: Throwable = IOException("private")
        repeat(12) { error = IllegalStateException("private", error) }
        assertEquals(8, SafeFailureSummary.diagnosticTypeChain(error).split(" -> ").size)
    }

    @Test fun unknownLegacyOrArbitraryCodeIsGeneric() {
        assertEquals("원인을 안전하게 분류할 수 없는 오류", SafeFailureSummary.describeCode("https://token.invalid/key"))
    }
}
