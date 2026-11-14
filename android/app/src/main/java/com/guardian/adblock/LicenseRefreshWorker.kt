package com.guardian.adblock

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Renews only a cryptographically verified installation-bound entitlement. */
class LicenseRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (BuildConfig.LICENSE_API_BASE_URL.isBlank() ||
            BuildConfig.LICENSE_PUBLIC_KEY.isBlank()) return Result.success()
        if (LicenseEntitlementStore.refreshableLicenseRef(applicationContext) == null) {
            return Result.success()
        }
        return try {
            LicenseApiClient.refresh(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // Keep the signed token for a later attempt. current() still fails
            // closed after expiry, so retrying cannot extend Pro access offline.
            Result.retry()
        }
    }
}
