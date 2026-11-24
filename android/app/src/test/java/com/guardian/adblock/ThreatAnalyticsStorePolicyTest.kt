package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class ThreatAnalyticsStorePolicyTest {
    @Test fun combinesStoredAndPendingThreatCountsWithoutLosingEvents() {
        val stored = ThreatAnalyticsSummary(
            high = 2L,
            informational = 3L,
            unassessed = 4L,
            recent = listOf(ThreatAnalyticsEvent(100L, ThreatAnalyticsEngine.Severity.HIGH, "저장된 이벤트"))
        )
        val pending = ThreatAnalyticsBatch(
            high = 1L,
            informational = 2L,
            unassessed = 0L,
            recent = listOf(ThreatAnalyticsEvent(200L, ThreatAnalyticsEngine.Severity.INFORMATIONAL, "대기 이벤트"))
        )
        val result = ThreatAnalyticsSummaryPolicy.merge(stored, pending)
        assertEquals(3L, result.high)
        assertEquals(5L, result.informational)
        assertEquals(4L, result.unassessed)
        assertEquals(listOf("대기 이벤트", "저장된 이벤트"), result.recent.map { it.title })
    }

    @Test fun capsMergedRecentEventsAndSaturatesCounters() {
        val storedRecent = (0 until 20).map {
            ThreatAnalyticsEvent(it.toLong(), ThreatAnalyticsEngine.Severity.INFORMATIONAL, "저장 $it")
        }
        val stored = ThreatAnalyticsSummary(Long.MAX_VALUE, -1L, 3L, storedRecent)
        val pending = ThreatAnalyticsBatch(
            high = 9L,
            informational = 2L,
            unassessed = 1L,
            recent = listOf(ThreatAnalyticsEvent(999L, ThreatAnalyticsEngine.Severity.HIGH, "최신 대기 이벤트"))
        )
        val result = ThreatAnalyticsSummaryPolicy.merge(stored, pending, maxRecent = 20)
        assertEquals(Long.MAX_VALUE, result.high)
        assertEquals(2L, result.informational)
        assertEquals(4L, result.unassessed)
        assertEquals(20, result.recent.size)
        assertEquals("최신 대기 이벤트", result.recent.first().title)
        assertEquals("저장 18", result.recent.last().title)
    }

    @Test fun rejectsInvalidMergedRecentLimit() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ThreatAnalyticsSummaryPolicy.merge(
                ThreatAnalyticsSummary(0L, 0L, 0L, emptyList()),
                ThreatAnalyticsBatch(0L, 0L, 0L, emptyList()),
                maxRecent = 0
            )
        }
    }

    @Test fun eventSummaryDoesNotExposeDomainOrUrlFields() {
        val event = ThreatAnalyticsEvent(
            1_800_000_000_000L,
            ThreatAnalyticsEngine.Severity.INFORMATIONAL,
            "광고·추적 차단"
        )
        assertEquals(ThreatAnalyticsEngine.Severity.INFORMATIONAL, event.severity)
        assertFalse(event.title.contains("example.com"))
        assertFalse(event.title.contains("https://"))
    }
}
