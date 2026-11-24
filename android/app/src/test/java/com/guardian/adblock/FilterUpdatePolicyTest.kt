package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterUpdatePolicyTest {
    @Test
    fun enabledOptionalFilterRemainsRetryableAfterItsFirstDownloadFails() {
        assertTrue(FilterUpdatePolicy.shouldRetryOptionalUpdate(wasEnabled = true))
        assertFalse(FilterUpdatePolicy.shouldRetryOptionalUpdate(wasEnabled = false))
    }


    @Test
    fun optionalFilterRetriesAreNotStoppedByMainCacheRetryBudgetOnceMainCacheIsReady() {
        // The one-shot retry cap is applied only while the required main cache
        // is missing. Optional enabled sources have their own retry lifecycle.
        assertTrue(FilterUpdatePolicy.shouldContinueInitialRetry(
            forceInitialRefresh = true, runAttemptCount = 99, maxAttempts = 6
        ))
    }

    @Test
    fun clockRollbackMakesRefreshDueUntilSuccessfulRefreshResetsTimestamp() {
        val interval = 24L * 60L * 60L * 1000L

        assertTrue(
            FilterUpdatePolicy.isDue(
                hasValidCache = true,
                lastUpdatedAt = 2_000_000L,
                intervalMs = interval,
                now = 1_000_000L
            )
        )
        assertFalse(
            FilterUpdatePolicy.isDue(
                hasValidCache = true,
                lastUpdatedAt = 1_000_000L,
                intervalMs = interval,
                now = 1_000_001L
            )
        )
    }

    @Test
    fun missingOrExpiredCacheStillMakesRefreshDue() {
        val interval = 60_000L
        assertTrue(FilterUpdatePolicy.isDue(false, 10_000L, interval, 10_001L))
        assertTrue(FilterUpdatePolicy.isDue(true, 10_000L, interval, 70_000L))
        assertFalse(FilterUpdatePolicy.isDue(true, 10_000L, interval, 69_999L))
    }
}
