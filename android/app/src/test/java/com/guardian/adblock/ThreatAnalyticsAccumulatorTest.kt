package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatAnalyticsAccumulatorTest {
    private fun assessment(severity: ThreatAnalyticsEngine.Severity, title: String) =
        ThreatAnalyticsEngine.Assessment(severity, emptySet(), title, "test")

    @Test fun batchesSeverityCountersAndDrainsWithoutLosingCounts() {
        val accumulator = ThreatAnalyticsAccumulator()
        accumulator.add(assessment(ThreatAnalyticsEngine.Severity.HIGH, "위협 목록 일치"), 100L)
        accumulator.add(assessment(ThreatAnalyticsEngine.Severity.HIGH, "위협 목록 일치"), 200L)
        accumulator.add(assessment(ThreatAnalyticsEngine.Severity.INFORMATIONAL, "광고·추적 차단"), 300L)
        accumulator.add(assessment(ThreatAnalyticsEngine.Severity.UNASSESSED, "사용자 지정 차단"), 400L)

        val batch = accumulator.drain()
        assertEquals(2L, batch.high)
        assertEquals(1L, batch.informational)
        assertEquals(1L, batch.unassessed)
        assertEquals(4, batch.recent.size)
        assertFalse(accumulator.hasPending)
        assertTrue(accumulator.drain().isEmpty)
    }

    @Test fun keepsOnlyNewestTwentyEventsInDescendingTimeOrder() {
        val accumulator = ThreatAnalyticsAccumulator(20)
        repeat(25) { index ->
            accumulator.add(
                assessment(ThreatAnalyticsEngine.Severity.INFORMATIONAL, "광고·추적 차단"),
                index.toLong()
            )
        }
        val batch = accumulator.drain()
        assertEquals(25L, batch.informational)
        assertEquals(20, batch.recent.size)
        assertEquals(24L, batch.recent.first().timestampEpochMillis)
        assertEquals(5L, batch.recent.last().timestampEpochMillis)
    }

    @Test fun ignoresNullAssessmentAndRejectsInvalidRecentLimit() {
        val accumulator = ThreatAnalyticsAccumulator()
        accumulator.add(null, 123L)
        assertFalse(accumulator.hasPending)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ThreatAnalyticsAccumulator(0)
        }
    }

    @Test fun persistedEventLabelsDoNotContainDomainOrUrlInputs() {
        val accumulator = ThreatAnalyticsAccumulator()
        val result = ThreatAnalyticsEngine.assess(
            true, setOf(ThreatAnalyticsEngine.Evidence.SECURITY_INTELLIGENCE_LIST)
        )!!
        accumulator.add(result, 500L)
        assertEquals("위협 인텔리전스 목록 일치", accumulator.drain().recent.single().title)
    }
}
