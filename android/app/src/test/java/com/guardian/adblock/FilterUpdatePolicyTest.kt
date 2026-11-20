package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterUpdatePolicyTest {
    @Test
    fun missingOrUninitializedCacheIsAlwaysDue() {
        assertTrue(FilterUpdatePolicy.isDue(hasValidCache = false, lastUpdatedAt = 0L, intervalMs = 86_400_000L, now = 1_000L))
        assertTrue(FilterUpdatePolicy.isDue(hasValidCache = true, lastUpdatedAt = 0L, intervalMs = 86_400_000L, now = 1_000L))
    }

    @Test
    fun validCacheIsNotDueBeforeConfiguredInterval() {
        val interval = 60L * 60L * 1000L
        assertFalse(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = 10_000L,
            intervalMs = interval,
            now = 10_000L + interval - 1L
        ))
    }

    @Test
    fun validCacheBecomesDueAtExactIntervalBoundary() {
        val interval = 60L * 60L * 1000L
        assertTrue(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = 10_000L,
            intervalMs = interval,
            now = 10_000L + interval
        ))
    }

    @Test
    fun clockMovingBackwardsDoesNotCauseRepeatedRefreshes() {
        assertFalse(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = 50_000L,
            intervalMs = 15L * 60L * 1000L,
            now = 40_000L
        ))
    }

    @Test
    fun invalidlyShortIntervalsAreClampedToSchedulerMinimum() {
        val lastUpdated = 1_000L
        val minimum = FilterUpdateScheduler.MIN_INTERVAL_MS
        assertFalse(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = lastUpdated,
            intervalMs = 1L,
            now = lastUpdated + minimum - 1L
        ))
        assertTrue(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = lastUpdated,
            intervalMs = 1L,
            now = lastUpdated + minimum
        ))
    }

    @Test
    fun previousRefreshErrorForcesRetryDespiteFreshTrustedCache() {
        assertTrue(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = 50_000L,
            intervalMs = 24L * 60L * 60L * 1000L,
            now = 51_000L,
            hasRefreshError = true
        ))
    }

    @Test
    fun noRefreshErrorKeepsFreshTrustedCacheOnConfiguredSchedule() {
        assertFalse(FilterUpdatePolicy.isDue(
            hasValidCache = true,
            lastUpdatedAt = 50_000L,
            intervalMs = 24L * 60L * 60L * 1000L,
            now = 51_000L,
            hasRefreshError = false
        ))
    }

    @Test
    fun optionalFilterIsDisabledOnlyWhenFirstDownloadFailsWithoutTrustedCache() {
        assertTrue(FilterUpdatePolicy.shouldDisableOptionalFilter(wasEnabled = true, hadValidCache = false))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(wasEnabled = true, hadValidCache = true))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(wasEnabled = false, hadValidCache = false))
    }
}
