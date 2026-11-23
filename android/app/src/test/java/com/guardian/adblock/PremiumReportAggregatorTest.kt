package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PremiumReportAggregatorTest {
    private val start = LocalDate.parse("2026-11-01")
    private val end = LocalDate.parse("2026-11-07")

    @Test fun aggregatesOnlyRowsInsideRequestedWindow() {
        val result = PremiumReportAggregator.aggregate(
            start, end,
            listOf(
                PremiumDailyMetrics(start.minusDays(1), queries = 100, blocked = 50),
                PremiumDailyMetrics(start, queries = 10, blocked = 3, ads = 2),
                PremiumDailyMetrics(start.plusDays(2), queries = 20, blocked = 5, trackers = 4),
                PremiumDailyMetrics(end.plusDays(1), queries = 200, blocked = 100)
            )
        )
        assertEquals(2, result.daysWithActivity)
        assertEquals(30L, result.queries)
        assertEquals(8L, result.blocked)
        assertEquals(2L, result.ads)
        assertEquals(4L, result.trackers)
        assertEquals(26, result.blockRatePercent)
    }

    @Test fun emptyWindowReturnsZeroTotalsWithoutDivisionByZero() {
        val result = PremiumReportAggregator.aggregate(start, end, emptyList())
        assertEquals(0L, result.queries)
        assertEquals(0L, result.blocked)
        assertEquals(0, result.blockRatePercent)
        assertEquals(0, result.daysWithActivity)
    }

    @Test fun negativeStoredCountersAreIgnoredAndBlockedNeverExceedsQueries() {
        val result = PremiumReportAggregator.aggregate(
            start, end,
            listOf(PremiumDailyMetrics(start, queries = 5, blocked = 9, forwarded = -2, failed = 1))
        )
        assertEquals(5L, result.queries)
        assertEquals(5L, result.blocked)
        assertEquals(0L, result.forwarded)
        assertEquals(1L, result.failed)
        assertEquals(100, result.blockRatePercent)
    }

    @Test fun rejectsReversedDateRange() {
        try {
            PremiumReportAggregator.aggregate(end, start, emptyList())
            fail("reversed date range must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
