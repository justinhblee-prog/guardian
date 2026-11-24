package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUpstreamFailurePolicyTest {
    @Test fun summarizesEachFallbackCandidateWithoutExposingAddresses() {
        val result = DnsUpstreamFailurePolicy.summarize(
            listOf("UDP 후보 1: 응답 시간 초과", "UDP 후보 2: 소켓 우회 실패")
        )
        assertEquals("UDP 후보 1: 응답 시간 초과 · UDP 후보 2: 소켓 우회 실패", result)
        assertTrue(result!!.contains("후보 1"))
        assertTrue(result.contains("후보 2"))
    }

    @Test fun ignoresBlankFailuresAndCapsCandidateCount() {
        val failures = listOf("첫 번째", " ") + (2..8).map { "후보 $it" }
        val result = DnsUpstreamFailurePolicy.summarize(failures)!!
        assertEquals(6, result.split(" · ").size)
        assertTrue(result.startsWith("첫 번째"))
        assertTrue(result.contains("후보 6"))
        assertTrue(!result.contains("후보 7"))
    }

    @Test fun boundsEachEntryAndTheOverallSummaryLength() {
        val long = "x".repeat(5000)
        val result = DnsUpstreamFailurePolicy.summarize(listOf(long, long))!!
        assertEquals(DnsUpstreamFailurePolicy.MAX_SUMMARY_CHARS, result.length)
    }

    @Test fun returnsNullWhenThereAreNoFailureDetails() {
        assertNull(DnsUpstreamFailurePolicy.summarize(listOf("", "  ")))
    }
}
