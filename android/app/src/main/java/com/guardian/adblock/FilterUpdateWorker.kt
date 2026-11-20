package com.guardian.adblock

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Periodically refreshes enabled DNS filter sources while a network is available. */
class FilterUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result = FilterUpdateExecutionLock.withLock {
        // WorkManager cancellation is cooperative for this synchronous Worker.
        // Check between feeds so cancelling a refresh cannot start additional
        // network downloads after the active request returns.
        if (isStopped) return@withLock Result.failure()
        val prefs = applicationContext.getSharedPreferences(GuardianVpnService.PREFS, Context.MODE_PRIVATE)
        val forcedRecovery = inputData.getBoolean(FilterUpdateScheduler.KEY_FORCE_RECOVERY, false)
        if (!FilterRecoveryPolicy.shouldRun(
                prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, false),
                forcedRecovery
            )) {
            return@withLock Result.success()
        }

        try {
            val now = System.currentTimeMillis()
            val interval = FilterUpdateScheduler.normalizeInterval(
                prefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, FilterUpdateScheduler.DEFAULT_INTERVAL_MS)
            )
            val selectedProfile = FilterListManager.selectedProfile(applicationContext)
            val cachedProfile = FilterListManager.cachedProfile(applicationContext)
            val baseCacheReady = FilterListManager.hasCachedList(applicationContext) &&
                selectedProfile.key == cachedProfile.key

            // A retry caused by an optional list failure must not download the
            // already successful base list again. Each source has its own age.
            if (isStopped) return@withLock Result.failure()
            if (FilterUpdatePolicy.isDue(
                    baseCacheReady, FilterListManager.lastUpdatedAt(applicationContext), interval, now,
                    hasRefreshError = prefs.contains(FilterListManager.KEY_LAST_ERROR)
                )) {
                FilterListManager.update(applicationContext)
            }
            if (isStopped) return@withLock Result.failure()
            prefs.edit().remove(FilterListManager.KEY_LAST_ERROR).apply()

            var optionalFailure = false
            val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            val securityCacheReady = FilterListManager.hasCachedSecurityList(applicationContext)
            if (isStopped) return@withLock Result.failure()
            if (securityEnabled &&
                FilterUpdatePolicy.isDue(
                    securityCacheReady,
                    FilterListManager.lastSecurityUpdatedAt(applicationContext), interval, now,
                    hasRefreshError = prefs.contains(FilterListManager.KEY_SECURITY_LAST_ERROR)
                )) {
                try {
                    FilterListManager.updateSecurity(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_SECURITY_LAST_ERROR).apply()
                } catch (e: Exception) {
                    if (isStopped) return@withLock Result.failure()
                    optionalFailure = true
                    val editor = prefs.edit().putString(
                        FilterListManager.KEY_SECURITY_LAST_ERROR,
                        e.message ?: e.javaClass.simpleName
                    )
                    editor.apply()
                }
            }
            val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
            val androidAdsCacheReady = FilterListManager.hasCachedAndroidAdsList(applicationContext)
            if (isStopped) return@withLock Result.failure()
            if (androidAdsEnabled &&
                FilterUpdatePolicy.isDue(
                    androidAdsCacheReady,
                    FilterListManager.lastAndroidAdsUpdatedAt(applicationContext), interval, now,
                    hasRefreshError = prefs.contains(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR)
                )) {
                try {
                    FilterListManager.updateAndroidAds(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR).apply()
                } catch (e: Exception) {
                    if (isStopped) return@withLock Result.failure()
                    optionalFailure = true
                    val editor = prefs.edit().putString(
                        FilterListManager.KEY_ANDROID_ADS_LAST_ERROR,
                        e.message ?: e.javaClass.simpleName
                    )
                    editor.apply()
                }
            }

            if (isStopped) return@withLock Result.failure()
            val active = ProtectionState.isActive(
                prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false),
                GuardianVpnService.isTunnelActive()
            )
            if (active) {
                applicationContext.startService(
                    Intent(applicationContext, GuardianVpnService::class.java)
                        .setAction(GuardianVpnService.ACTION_RELOAD_RULES)
                )
            }
            applicationContext.sendBroadcast(
                Intent(ACTION_FILTERS_CHANGED).setPackage(applicationContext.packageName)
            )
            if (optionalFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (isStopped) return@withLock Result.failure()
            prefs.edit().putString(
                FilterListManager.KEY_LAST_ERROR,
                e.message ?: e.javaClass.simpleName
            ).apply()
            applicationContext.sendBroadcast(
                Intent(ACTION_FILTERS_CHANGED).setPackage(applicationContext.packageName)
            )
            Result.retry()
        }
    }

    companion object {
        const val ACTION_FILTERS_CHANGED = "com.guardian.adblock.FILTERS_CHANGED"
    }
}

/** Pure per-source refresh policy used to verify retry behavior. */
internal object FilterUpdatePolicy {
    fun isDue(
        hasValidCache: Boolean,
        lastUpdatedAt: Long,
        intervalMs: Long,
        now: Long,
        hasRefreshError: Boolean = false
    ): Boolean {
        // Keep serving a last-known-good cache, but do not let its freshness
        // suppress recovery after a failed network or validation refresh.
        if (hasRefreshError || !hasValidCache || lastUpdatedAt <= 0L) return true
        val interval = FilterUpdateScheduler.normalizeInterval(intervalMs)
        return now >= lastUpdatedAt && now - lastUpdatedAt >= interval
    }
}
