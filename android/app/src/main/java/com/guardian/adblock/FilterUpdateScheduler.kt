package com.guardian.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * First-run cache population is mandatory even when recurring updates are disabled.
 * The opt-in flag controls only scheduled refreshes, never the initial protection setup.
 */
internal object FilterStartupPolicy {
    fun shouldRefresh(
        mainCacheMissing: Boolean,
        securityCacheMissing: Boolean,
        securityEnabled: Boolean,
        androidAdsCacheMissing: Boolean,
        androidAdsEnabled: Boolean,
        scheduledRefreshDue: Boolean
    ): Boolean =
        mainCacheMissing ||
            (securityEnabled && securityCacheMissing) ||
            (androidAdsEnabled && androidAdsCacheMissing) ||
            scheduledRefreshDue
}

/** Keeps one persistent periodic refresh request aligned with the user's settings. */
object FilterUpdateScheduler {
    const val UNIQUE_WORK_NAME = "guardian_periodic_filter_update"
    const val UNIQUE_INITIAL_CACHE_RETRY_WORK_NAME = "guardian_initial_filter_cache_retry"
    const val INITIAL_CACHE_RETRY_DELAY_MS = 30_000L
    const val DEFAULT_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val MIN_INTERVAL_MS = 15L * 60L * 1000L
    internal fun normalizeInterval(intervalMs: Long): Long = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)

    /**
     * The Activity performs the first-run download immediately. Delay the periodic
     * worker's first execution by one full interval so it cannot race that initial
     * download and redundantly fetch the same base list before optional lists run.
     */
    internal fun initialDelayMs(intervalMs: Long): Long = normalizeInterval(intervalMs)

    /** Preserve any supported interval in the settings spinner; use the daily default only for unknown values. */
    internal fun selectionIndex(intervalMs: Long, supportedIntervals: List<Long>, defaultIndex: Int): Int {
        val index = supportedIntervals.indexOf(intervalMs)
        return if (index >= 0) index else defaultIndex.coerceIn(0, (supportedIntervals.size - 1).coerceAtLeast(0))
    }

    /** Retry a failed first cache population as soon as a network becomes available. */
    fun enqueueInitialCacheRetry(context: Context) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FilterUpdateWorker>()
            .setInputData(workDataOf(FilterUpdateWorker.KEY_FORCE_INITIAL_REFRESH to true))
            .setInitialDelay(INITIAL_CACHE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_INITIAL_CACHE_RETRY_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelInitialCacheRetry(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_INITIAL_CACHE_RETRY_WORK_NAME)
    }

    fun sync(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(GuardianVpnService.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)) {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val interval = prefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, DEFAULT_INTERVAL_MS)
            .let(::normalizeInterval)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(interval, TimeUnit.MILLISECONDS)
            .setInitialDelay(initialDelayMs(interval), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
