package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PremiumReportPolicyTest {
    @Test
    fun recordStoresOnlyDailyAggregatesAndClassifiesBlockedEvents() {
        val day = LocalDate.parse("2026-11-25")
        var stored = ""
        stored = PremiumReportPolicy.record(stored, day, blocked = true, forwarded = false, category = "ad")
        stored = PremiumReportPolicy.record(stored, day, blocked = true, forwarded = false, category = "tracker")
        stored = PremiumReportPolicy.record(stored, day, blocked = true, forwarded = false, category = null)
        stored = PremiumReportPolicy.record(stored, day, blocked = false, forwarded = true, category = null)
        stored = PremiumReportPolicy.record(stored, day, blocked = false, forwarded = false, category = null)

        val report = PremiumReportPolicy.summarize(stored, day, day)
        assertEquals(5L, report.queries)
        assertEquals(3L, report.blocked)
        assertEquals(1L, report.ads)
        assertEquals(1L, report.trackers)
        assertEquals(1L, report.other)
        assertEquals(1L, report.forwarded)
        assertEquals(1L, report.failed)
        assertEquals(60, report.blockRatePercent)
        assertEquals(
            "2026-11-25|5|3|1|1|1|1|1",
            stored
        )
        assertTrue("Serialized rows contain only date and numeric counters", stored.split('|').all {
            it == day.toString() || it.toLongOrNull() != null
        })
    }

    @Test
    fun summariesAggregateOnlyRequestedDateRange() {
        val first = LocalDate.parse("2026-11-20")
        val second = first.plusDays(1)
        val outside = first.minusDays(1)
        var stored = ""
        stored = PremiumReportPolicy.record(stored, outside, true, false, "ad")
        stored = PremiumReportPolicy.record(stored, first, true, false, "tracker")
        stored = PremiumReportPolicy.record(stored, second, false, true, null)

        val report = PremiumReportPolicy.summarize(stored, first, second)
        assertEquals(2L, report.queries)
        assertEquals(1L, report.blocked)
        assertEquals(1L, report.trackers)
        assertEquals(1L, report.forwarded)
    }

    @Test
    fun retentionRemovesRowsOlderThanNinetyDaysAndFutureDates() {
        val today = LocalDate.parse("2026-11-25")
        val retained = today.minusDays(89)
        val expired = today.minusDays(90)
        var stored = ""
        stored = PremiumReportPolicy.record(stored, retained, true, false, "ad")
        stored = PremiumReportPolicy.record(stored, expired, true, false, "ad")
        stored = PremiumReportPolicy.record(stored, today.plusDays(1), true, false, "ad")

        val pruned = PremiumReportPolicy.prune(stored, today)
        assertTrue(pruned.contains(retained.toString()))
        assertTrue(pruned.contains(today.toString()))
        assertTrue(!pruned.contains(expired.toString()))
        assertTrue(!pruned.contains(today.plusDays(1).toString()))
    }

    @Test
    fun duplicateDateRowsAreMergedWithoutLosingCounters() {
        val day = LocalDate.parse("2026-11-25")
        val serialized = listOf(
            "$day|2|1|1|0|0|1|0",
            "$day|3|2|0|1|1|0|1"
        ).joinToString("\n")

        val summary = PremiumReportPolicy.summarize(serialized, day, day)
        assertEquals(5L, summary.queries)
        assertEquals(3L, summary.blocked)
        assertEquals(1L, summary.ads)
        assertEquals(1L, summary.trackers)
        assertEquals(1L, summary.other)
        assertEquals(1L, summary.forwarded)
        assertEquals(1L, summary.failed)
    }

    @Test
    fun malformedRowsAreIgnoredAndCountersSaturate() {
        val today = LocalDate.parse("2026-11-25")
        val corrupt = "not-a-date|1|1|1|1|1|1|1\n2026-11-25|-1|0|0|0|0|0|0"
        val report = PremiumReportPolicy.summarize(corrupt, today, today)
        assertEquals(0L, report.queries)

        val max = "2026-11-25|9223372036854775807|9223372036854775807|0|0|0|0|0"
        val stored = PremiumReportPolicy.record(max, today, true, false, "ad")
        val saturated = PremiumReportPolicy.summarize(stored, today, today)
        assertEquals(Long.MAX_VALUE, saturated.queries)
        assertEquals(Long.MAX_VALUE, saturated.blocked)
    }
}
