package com.guardian.adblock

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Schedules renewal before the server-signed 24-hour entitlement expires. */
internal object LicenseRefreshScheduler {
    const val UNIQUE_WORK_NAME = "guardian_license_entitlement_refresh"
    const val REFRESH_INTERVAL_HOURS = 12L
    const val RENEWAL_WINDOW_SECONDS = 12L * 60L * 60L

    /** Require the same strict HTTPS-origin and RSA-key validation as the API client. */
    internal fun isConfigured(apiBaseUrl: String, publicKey: String): Boolean =
        isLicenseServiceConfigurationValid(apiBaseUrl, publicKey)

    internal fun shouldRenew(expiresAt: Long, nowEpochSeconds: Long): Boolean =
        expiresAt - nowEpochSeconds <= RENEWAL_WINDOW_SECONDS

    internal fun shouldRetryHttpStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 429 || statusCode >= 500

    fun sync(context: Context) {
        if (!isConfigured(LicenseApiClient.configuredBaseUrl(context), BuildConfig.LICENSE_PUBLIC_KEY)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<LicenseRefreshWorker>(
            REFRESH_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

/** Renews only a cryptographically verified token; expiry never grants Pro access. */
class LicenseRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        if (!LicenseRefreshScheduler.isConfigured(LicenseApiClient.configuredBaseUrl(applicationContext), BuildConfig.LICENSE_PUBLIC_KEY)) {
            return Result.success()
        }
        val now = System.currentTimeMillis() / 1000L
        val entitlement = LicenseEntitlementStore.currentForRefresh(applicationContext, now)
            ?: return Result.success()
        if (!LicenseRefreshScheduler.shouldRenew(entitlement.expiresAt, now)) {
            return Result.success()
        }
        return try {
            LicenseApiClient.refresh(applicationContext)
            Result.success()
        } catch (error: LicenseApiException) {
            when {
                error.invalidatesEntitlement -> Result.success()
                error.isRetryable -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: IOException) {
            // DNS, socket and timeout failures are transient and use WorkManager backoff.
            Result.retry()
        } catch (_: Exception) {
            // Invalid payloads, signatures or permanent local configuration errors must not loop.
            Result.failure()
        }
    }
}
