package com.guardian.adblock

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumDailyStatsStoreTest {
    @Test fun storesOnlyDailyCountersAndSeparatesDnsOutcomes() {
        val date = LocalDate.of(2026, 11, 27)
        var raw = PremiumDailyStatsStore.record(
            null, date, blocked = true, forwarded = false,
            category = DomainFilter.BlockCategory.AD
        )
        raw = PremiumDailyStatsStore.record(
            raw, date, blocked = false, forwarded = true, category = null
        )
        raw = PremiumDailyStatsStore.record(
            raw, date, blocked = false, forwarded = false, category = null
        )

        val day = PremiumDailyStatsStore.parse(raw).single()
        assertEquals(3L, day.totalQueries)
        assertEquals(1L, day.blockedQueries)
        assertEquals(1L, day.forwardedQueries)
        assertEquals(1L, day.failedQueries)
        assertEquals(1L, day.adBlocked)
        assertEquals(0L, day.trackerBlocked)
        assertEquals(0L, day.otherBlocked)
        assertFalse(raw.contains("example.com"))
    }

    @Test fun retainsOnlyTheMostRecentThirtyCalendarDays() {
        val first = LocalDate.of(2026, 10, 23)
        var raw: String? = null
        for (offset in 0L..35L) {
            val date = first.plusDays(offset)
            raw = PremiumDailyStatsStore.record(
                raw, date, blocked = false, forwarded = true, category = null
            )
        }
        val days = PremiumDailyStatsStore.parse(raw)
        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 10, 29), days.first().date)
        assertEquals(LocalDate.of(2026, 11, 27), days.last().date)
    }

    @Test fun malformedAndNegativeRowsAreIgnored() {
        val raw = """
            not-a-date|1|0|1|0|0|0|0
            2026-11-27|1|-1|1|0|0|0|0
            2026-11-27|2|1|1|0|1|0|0
        """.trimIndent()
        val days = PremiumDailyStatsStore.parse(raw)
        assertEquals(1, days.size)
        assertEquals(2L, days.single().totalQueries)
        assertEquals(1L, days.single().blockedQueries)
    }

    @Test fun reportCanDisplayAggregatesWithoutExposingDnsNames() {
        val date = LocalDate.of(2026, 11, 27)
        val daily = PremiumDailyStatsStore.parse(
            PremiumDailyStatsStore.record(
                null, date, blocked = true, forwarded = false,
                category = DomainFilter.BlockCategory.TRACKER
            )
        )
        val report = PremiumReportBuilder.build(
            PremiumReportSnapshot(
                totalQueries = 1,
                blockedQueries = 1,
                forwardedQueries = 0,
                failedQueries = 0,
                adBlocked = 0,
                trackerBlocked = 1,
                otherBlocked = 0,
                protectionActive = true,
                dailyStats = daily
            ),
            0L
        )
        assertTrue(report.contains("최근 7일 일별 DNS 요약"))
        assertTrue(report.contains("2026-11-27"))
        assertTrue(report.contains("질의 1"))
        assertFalse(report.contains("tracker.example"))
    }
}
