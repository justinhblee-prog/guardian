package com.guardian.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Keeps one persistent periodic refresh request aligned with the user's settings. */
object FilterUpdateScheduler {
    const val UNIQUE_WORK_NAME = "guardian_periodic_filter_update"
    const val DEFAULT_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val MIN_INTERVAL_MS = 15L * 60L * 1000L

    internal fun normalizeInterval(intervalMs: Long): Long = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)

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
