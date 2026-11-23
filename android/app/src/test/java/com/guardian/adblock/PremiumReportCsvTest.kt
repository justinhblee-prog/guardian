package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PremiumReportCsvTest {
    @Test fun exportsOnlyAggregateMetricsInCsvWithUtf8Bom() {
        val csv = PremiumReportCsv.render(
            PremiumReportSummary(
                startDate = LocalDate.parse("2026-11-01"),
                endDate = LocalDate.parse("2026-11-07"),
                daysWithActivity = 5,
                queries = 100,
                blocked = 25,
                forwarded = 70,
                failed = 5,
                ads = 15,
                trackers = 8,
                other = 2
            )
        )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("start_date,end_date,days_with_activity,queries,blocked"))
        assertTrue(csv.contains("2026-11-01,2026-11-07,5,100,25,25,70,5,15,8,2"))
        assertFalse(csv.contains("domain"))
        assertFalse(csv.contains("package"))
        assertFalse(csv.contains("https://"))
    }

    @Test fun exportClampsInvalidCountersAndPercentages() {
        val csv = PremiumReportCsv.render(
            PremiumReportSummary(
                startDate = LocalDate.parse("2026-11-01"),
                endDate = LocalDate.parse("2026-11-01"),
                daysWithActivity = -1,
                queries = -4,
                blocked = Long.MAX_VALUE,
                forwarded = -3,
                failed = -2,
                ads = -1,
                trackers = 0,
                other = 0
            )
        )
        assertTrue(csv.contains("2026-11-01,2026-11-01,0,0,0,0,0,0,0,0,0"))
    }
    @Test fun reportFileNameUsesOnlyIsoDateRange() {
        val summary = PremiumReportSummary(
            startDate = LocalDate.parse("2026-11-01"),
            endDate = LocalDate.parse("2026-11-07"),
            daysWithActivity = 2,
            queries = 10,
            blocked = 2,
            forwarded = 8,
            failed = 0,
            ads = 1,
            trackers = 1,
            other = 0
        )
        assertEquals(
            "guardian-premium-report-2026-11-01-2026-11-07.csv",
            PremiumReportCsv.fileName(summary)
        )
    }

}
