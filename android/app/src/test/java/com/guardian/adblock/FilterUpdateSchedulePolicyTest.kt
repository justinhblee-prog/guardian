package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterUpdateSchedulePolicyTest {
    @Test fun preservesSupportedSixHourAndDailyIntervals() {
        assertEquals(6L * 60L * 60L * 1000L, FilterUpdateScheduler.normalizeInterval(6L * 60L * 60L * 1000L))
        assertEquals(FilterUpdateScheduler.DEFAULT_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(FilterUpdateScheduler.DEFAULT_INTERVAL_MS))
    }

    @Test fun clampsInvalidOrTooShortIntervalsToWorkManagerMinimum() {
        assertEquals(FilterUpdateScheduler.MIN_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(0L))
        assertEquals(FilterUpdateScheduler.MIN_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(60_000L))
    }
    @Test fun settingsSpinnerPreservesSixAndTwelveHourChoices() {
        val intervals = listOf(6L, 12L, 24L, 168L).map { it * 60L * 60L * 1000L }
        assertEquals(0, FilterUpdateScheduler.selectionIndex(intervals[0], intervals, 2))
        assertEquals(1, FilterUpdateScheduler.selectionIndex(intervals[1], intervals, 2))
        assertEquals(2, FilterUpdateScheduler.selectionIndex(intervals[2], intervals, 2))
        assertEquals(3, FilterUpdateScheduler.selectionIndex(intervals[3], intervals, 2))
    }

    @Test fun settingsSpinnerFallsBackToDailyForUnknownInterval() {
        val intervals = listOf(6L, 12L, 24L, 168L).map { it * 60L * 60L * 1000L }
        assertEquals(2, FilterUpdateScheduler.selectionIndex(123L, intervals, 2))
    }

}
