package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun missingOrInvalidCacheIsAlwaysDue() {
        assertTrue(FilterUpdatePolicy.isDue(false, 0L, 6L * 60L * 60L * 1000L, 100_000L))
        assertTrue(FilterUpdatePolicy.isDue(true, 0L, 6L * 60L * 60L * 1000L, 100_000L))
    }

    @Test fun freshCacheIsNotDownloadedAgainDuringRetry() {
        val interval = 6L * 60L * 60L * 1000L
        val updatedAt = 1_000_000L
        assertFalse(FilterUpdatePolicy.isDue(true, updatedAt, interval, updatedAt + 30_000L))
    }

    @Test fun staleCacheIsRefreshedAtItsOwnInterval() {
        val interval = 6L * 60L * 60L * 1000L
        val updatedAt = 1_000_000L
        assertTrue(FilterUpdatePolicy.isDue(true, updatedAt, interval, updatedAt + interval))
    }

    @Test fun futureTimestampDoesNotCauseImmediateRedownloadLoop() {
        assertFalse(FilterUpdatePolicy.isDue(true, 200_000L, 6L * 60L * 60L * 1000L, 100_000L))
    }

    @Test fun invalidIntervalUsesWorkManagerMinimum() {
        assertFalse(FilterUpdatePolicy.isDue(true, 100_000L, 0L, 100_000L + FilterUpdateScheduler.MIN_INTERVAL_MS - 1L))
        assertTrue(FilterUpdatePolicy.isDue(true, 100_000L, 0L, 100_000L + FilterUpdateScheduler.MIN_INTERVAL_MS))
    }

    @Test fun failedFirstDownloadDisablesOnlyTheUnavailableOptionalFilter() {
        assertTrue(FilterUpdatePolicy.shouldDisableOptionalFilter(enabled = true, hasValidCache = false))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(enabled = true, hasValidCache = true))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(enabled = false, hasValidCache = false))
    }
}
