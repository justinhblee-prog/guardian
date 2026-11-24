package com.guardian.adblock

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PremiumReportRetentionPolicyTest {
    @Test fun retainsExactlyTheRollingNinetyDayBoundary() {
        val today = LocalDate.of(2026, 11, 24)
        val oldest = today.minusDays(89)
        val oneDayTooOld = oldest.minusDays(1)
        val retained = PremiumReportRetentionPolicy.retainDates(
            setOf(today.toString(), oldest.toString(), oneDayTooOld.toString()),
            today,
            90
        )
        assertEquals(setOf(today.toString(), oldest.toString()), retained)
    }

    @Test fun removesMalformedAndFutureDateKeys() {
        val today = LocalDate.of(2026, 11, 24)
        val retained = PremiumReportRetentionPolicy.retainDates(
            setOf(today.toString(), "2026-13-40", "not-a-date", today.plusDays(1).toString()),
            today,
            90
        )
        assertEquals(setOf(today.toString()), retained)
    }

    @Test fun rejectsZeroOrNegativeRetentionPeriod() {
        assertThrows(IllegalArgumentException::class.java) {
            PremiumReportRetentionPolicy.retainDates(emptySet(), LocalDate.of(2026, 11, 24), 0)
        }
    }
}
