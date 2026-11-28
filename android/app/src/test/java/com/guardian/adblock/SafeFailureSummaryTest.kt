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

    @Test fun updaterHttpStatusesAreSpecificAndDoNotExposeResponseBodies() {
        assertEquals("HTTP_403", SafeFailureSummary.codeFor(GuardianHttpStatusException(403)))
        assertEquals("HTTP_408", SafeFailureSummary.codeFor(GuardianHttpStatusException(408)))
        assertEquals("HTTP_429", SafeFailureSummary.codeFor(GuardianHttpStatusException(429)))
        assertEquals("GitHub 접근 인증 또는 권한 제한(HTTP 403)", SafeFailureSummary.describe(GuardianHttpStatusException(403)))
        assertEquals("업데이트 서버 응답 시간 초과(HTTP 408)", SafeFailureSummary.describe(GuardianHttpStatusException(408)))
        assertEquals("GitHub 요청 한도 초과(HTTP 429)", SafeFailureSummary.describe(GuardianHttpStatusException(429)))
        assertEquals("GitHub 서버 일시 오류(501)", SafeFailureSummary.describe(GuardianHttpStatusException(501)))
        assertEquals("원인을 안전하게 분류할 수 없는 오류", SafeFailureSummary.describe(GuardianHttpStatusException(418)))
    }

    @Test fun unknownLegacyOrArbitraryCodeIsGeneric() {
        assertEquals("원인을 안전하게 분류할 수 없는 오류", SafeFailureSummary.describeCode("https://token.invalid/key"))
    }
}
