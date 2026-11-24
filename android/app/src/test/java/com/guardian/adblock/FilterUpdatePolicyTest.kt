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
    fun baseRefreshFailureDoesNotSuppressIndependentOptionalFeedRetry() {
        assertTrue(FilterUpdatePolicy.shouldRetryWorker(
            baseUpdateFailed = true,
            optionalUpdateFailed = false
        ))
        assertTrue(FilterUpdatePolicy.shouldRetryWorker(
            baseUpdateFailed = false,
            optionalUpdateFailed = true
        ))
        assertTrue(FilterUpdatePolicy.shouldRetryWorker(
            baseUpdateFailed = true,
            optionalUpdateFailed = true
        ))
        assertFalse(FilterUpdatePolicy.shouldRetryWorker(
            baseUpdateFailed = false,
            optionalUpdateFailed = false
        ))
    }

    @Test
    fun permanentLinkageFailureDoesNotScheduleImmediateOptionalRetry() {
        assertFalse(FilterUpdatePolicy.shouldRetryOptionalUpdate(true, failureRetryable = false))
        assertFalse(FilterUpdatePolicy.shouldRetryOptionalUpdate(false, failureRetryable = false))
        assertTrue(FilterUpdatePolicy.shouldRetryOptionalUpdate(true, failureRetryable = true))
    }

    @Test
    fun linkageFailureDisablesOnlyEnabledOptionalFilterWithoutUsableCache() {
        assertTrue(FilterUpdatePolicy.shouldDisableOptionalFilter(true, false))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(true, true))
        assertFalse(FilterUpdatePolicy.shouldDisableOptionalFilter(false, false))
    }

    @Test
    fun optionalFilterRetriesContinueAfterMainCacheRetryBudgetWhenMainCacheIsReady() {
        assertFalse(FilterUpdatePolicy.shouldFailInitialRetryBudget(
            forceInitialRefresh = true,
            runAttemptCount = 99,
            maxAttempts = 6,
            mainCacheReady = true
        ))
    }

    @Test
    fun missingMainCacheStillExhaustsBoundedInitialRetryBudget() {
        assertTrue(FilterUpdatePolicy.shouldFailInitialRetryBudget(
            forceInitialRefresh = true,
            runAttemptCount = 6,
            maxAttempts = 6,
            mainCacheReady = false
        ))
        assertFalse(FilterUpdatePolicy.shouldFailInitialRetryBudget(
            forceInitialRefresh = true,
            runAttemptCount = 5,
            maxAttempts = 6,
            mainCacheReady = false
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
