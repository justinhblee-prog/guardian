package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterUpdateSchedulePolicyTest {
    @Test fun firstRunFilterSetupRunsEvenWhenAutomaticUpdatesAreDisabled() {
        assertTrue(
            FilterStartupPolicy.shouldRefresh(
                mainCacheMissing = true,
                securityCacheMissing = true,
                securityEnabled = true,
                androidAdsCacheMissing = true,
                androidAdsEnabled = true,
                scheduledRefreshDue = false
            )
        )
    }

    @Test fun missingEnabledOptionalFilterIsFetchedWithoutRecurringUpdates() {
        assertTrue(
            FilterStartupPolicy.shouldRefresh(
                mainCacheMissing = false,
                securityCacheMissing = true,
                securityEnabled = true,
                androidAdsCacheMissing = false,
                androidAdsEnabled = true,
                scheduledRefreshDue = false
            )
        )
    }

    @Test fun disabledOptionalFilterDoesNotBlockReadyStartup() {
        assertFalse(
            FilterStartupPolicy.shouldRefresh(
                mainCacheMissing = false,
                securityCacheMissing = true,
                securityEnabled = false,
                androidAdsCacheMissing = true,
                androidAdsEnabled = false,
                scheduledRefreshDue = false
            )
        )
    }

    @Test fun scheduledRefreshStillRequiresItsExplicitDueSignal() {
        assertTrue(
            FilterStartupPolicy.shouldRefresh(
                mainCacheMissing = false,
                securityCacheMissing = false,
                securityEnabled = true,
                androidAdsCacheMissing = false,
                androidAdsEnabled = true,
                scheduledRefreshDue = true
            )
        )
    }

    @Test fun disablesOptionalFilterOnlyWhenItsFirstDownloadFailsWithoutCache() {
        assertTrue(FilterUpdatePolicy.shouldDisableOptionalFilter(wasEnabled = true, hadValidCache = false))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(wasEnabled = true, hadValidCache = true))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(wasEnabled = false, hadValidCache = false))
    }

    @Test fun preservesSupportedSixHourAndDailyIntervals() {
        assertEquals(6L * 60L * 60L * 1000L, FilterUpdateScheduler.normalizeInterval(6L * 60L * 60L * 1000L))
        assertEquals(FilterUpdateScheduler.DEFAULT_INTERVAL_MS, FilterUpdateScheduler.normalizeInterval(FilterUpdateScheduler.DEFAULT_INTERVAL_MS))
    }

    @Test fun periodicFirstRunWaitsOneFullConfiguredInterval() {
        assertEquals(6L * 60L * 60L * 1000L, FilterUpdateScheduler.initialDelayMs(6L * 60L * 60L * 1000L))
        assertEquals(FilterUpdateScheduler.DEFAULT_INTERVAL_MS, FilterUpdateScheduler.initialDelayMs(FilterUpdateScheduler.DEFAULT_INTERVAL_MS))
    }

    @Test fun shortOrInvalidPeriodicInitialDelayUsesWorkManagerMinimum() {
        assertEquals(FilterUpdateScheduler.MIN_INTERVAL_MS, FilterUpdateScheduler.initialDelayMs(0L))
        assertEquals(FilterUpdateScheduler.MIN_INTERVAL_MS, FilterUpdateScheduler.initialDelayMs(60_000L))
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

    @Test fun cachedMainFilterIsReusableOnlyForTheSelectedProfile() {
        assertTrue(FilterUpdatePolicy.hasUsableMainCache(true, "adguard", "adguard"))
        assertFalse(FilterUpdatePolicy.hasUsableMainCache(true, "hagezi_pro", "adguard"))
        assertFalse(FilterUpdatePolicy.hasUsableMainCache(false, "adguard", "adguard"))
    }

    @Test fun initialCacheRecoveryRunsEvenWhenRecurringUpdatesAreDisabled() {
        assertTrue(FilterUpdatePolicy.shouldRunUpdates(autoUpdateEnabled = false, forceInitialRefresh = true))
        assertTrue(FilterUpdatePolicy.shouldRunUpdates(autoUpdateEnabled = true, forceInitialRefresh = false))
        assertTrue(FilterUpdatePolicy.shouldRunUpdates(autoUpdateEnabled = true, forceInitialRefresh = true))
        assertFalse(FilterUpdatePolicy.shouldRunUpdates(autoUpdateEnabled = false, forceInitialRefresh = false))
    }

    @Test fun initialRetryIsBoundedButNormalPeriodicUpdatesAreNotLimitedByThatBudget() {
        assertTrue(FilterUpdatePolicy.shouldContinueInitialRetry(true, 0, FilterUpdateWorker.MAX_INITIAL_CACHE_ATTEMPTS))
        assertTrue(FilterUpdatePolicy.shouldContinueInitialRetry(true, 5, FilterUpdateWorker.MAX_INITIAL_CACHE_ATTEMPTS))
        assertFalse(FilterUpdatePolicy.shouldContinueInitialRetry(true, 6, FilterUpdateWorker.MAX_INITIAL_CACHE_ATTEMPTS))
        assertFalse(FilterUpdatePolicy.shouldContinueInitialRetry(true, 0, 0))
        assertTrue(FilterUpdatePolicy.shouldContinueInitialRetry(false, 100, FilterUpdateWorker.MAX_INITIAL_CACHE_ATTEMPTS))
    }

    @Test fun initialCacheRetryUsesShortBackoffBeforeNetworkWork() {
        assertEquals(30_000L, FilterUpdateScheduler.INITIAL_CACHE_RETRY_DELAY_MS)
    }

    @Test fun clearsBaseErrorOnlyAfterASuccessfulDueRefresh() {
        assertFalse(FilterUpdatePolicy.shouldClearBaseError(updateWasDue = false, updateSucceeded = false))
        assertFalse(FilterUpdatePolicy.shouldClearBaseError(updateWasDue = false, updateSucceeded = true))
        assertFalse(FilterUpdatePolicy.shouldClearBaseError(updateWasDue = true, updateSucceeded = false))
        assertTrue(FilterUpdatePolicy.shouldClearBaseError(updateWasDue = true, updateSucceeded = true))
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
}
