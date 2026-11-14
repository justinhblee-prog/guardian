package com.guardian.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules signed entitlement renewal independently from DNS filter updates. */
object LicenseRefreshScheduler {
    const val UNIQUE_WORK_NAME = "guardian_license_entitlement_refresh"
    const val REFRESH_INTERVAL_HOURS = 12L

    fun sync(context: Context) {
        val appContext = context.applicationContext
        val configured = BuildConfig.LICENSE_API_BASE_URL.isNotBlank() &&
            BuildConfig.LICENSE_PUBLIC_KEY.isNotBlank()
        val workManager = WorkManager.getInstance(appContext)
        if (!configured) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<LicenseRefreshWorker>(
            REFRESH_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
