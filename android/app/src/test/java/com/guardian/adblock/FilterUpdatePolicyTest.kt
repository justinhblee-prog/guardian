package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FilterUpdatePolicyTest {
    @Test
    fun recurringRefreshRequiresOptInButForcedRecoveryDoesNot() {
        assertFalse(FilterRecoveryPolicy.shouldRun(autoUpdateEnabled = false, forcedRecovery = false))
        assertTrue(FilterRecoveryPolicy.shouldRun(autoUpdateEnabled = true, forcedRecovery = false))
        assertTrue(FilterRecoveryPolicy.shouldRun(autoUpdateEnabled = false, forcedRecovery = true))
        assertTrue(FilterRecoveryPolicy.shouldRun(autoUpdateEnabled = true, forcedRecovery = true))
    }

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
    fun failedFirstOptionalDownloadRemainsDueForAutomaticRetry() {
        assertTrue(FilterUpdatePolicy.isDue(
            hasValidCache = false,
            lastUpdatedAt = 0L,
            intervalMs = 24L * 60L * 60L * 1000L,
            now = 51_000L,
            hasRefreshError = true
        ))
    }

    @Test
    fun disabledOptionalSourceErrorsDoNotAffectVisibleHealth() {
        assertFalse(FilterUpdateErrorPolicy.hasVisibleError(
            baseError = false,
            securityEnabled = false,
            securityError = true,
            androidAdsEnabled = false,
            androidAdsError = true
        ))
    }

    @Test
    fun enabledOptionalSourceErrorsRemainVisible() {
        assertTrue(FilterUpdateErrorPolicy.hasVisibleError(
            baseError = false,
            securityEnabled = true,
            securityError = true,
            androidAdsEnabled = false,
            androidAdsError = true
        ))
        assertTrue(FilterUpdateErrorPolicy.hasVisibleError(
            baseError = false,
            securityEnabled = false,
            securityError = true,
            androidAdsEnabled = true,
            androidAdsError = true
        ))
    }

    @Test
    fun baseFilterErrorIsVisibleRegardlessOfOptionalSources() {
        assertTrue(FilterUpdateErrorPolicy.hasVisibleError(
            baseError = true,
            securityEnabled = false,
            securityError = false,
            androidAdsEnabled = false,
            androidAdsError = false
        ))
    }

    @Test
    fun manualAndBackgroundRefreshTransactionsCannotOverlap() {
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val first = Thread {
            FilterUpdateExecutionLock.withLock {
                firstEntered.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS))
            }
        }
        first.start()
        assertTrue("first refresh should acquire the transaction lock",
            firstEntered.await(5, TimeUnit.SECONDS))

        val second = Thread {
            FilterUpdateExecutionLock.withLock { }
            secondFinished.countDown()
        }
        second.start()
        try {
            assertFalse("a second refresh must wait for the active transaction",
                secondFinished.await(150, TimeUnit.MILLISECONDS))
        } finally {
            releaseFirst.countDown()
        }
        assertTrue("queued refresh should proceed after the active transaction",
            secondFinished.await(5, TimeUnit.SECONDS))
        first.join(5000)
        second.join(5000)
    }
}
