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

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(GuardianVpnService.PREFS, Context.MODE_PRIVATE)
        val forceInitialRefresh = inputData.getBoolean(KEY_FORCE_INITIAL_REFRESH, false)
        if (!FilterUpdatePolicy.shouldRunUpdates(
                autoUpdateEnabled = prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true),
                forceInitialRefresh = forceInitialRefresh
            )
        ) return Result.success()

        if (!FilterUpdatePolicy.shouldContinueInitialRetry(
                forceInitialRefresh, runAttemptCount, MAX_INITIAL_CACHE_ATTEMPTS
            )
        ) {
            android.util.Log.e("Guardian", "Initial filter cache retry budget exhausted after $runAttemptCount attempts")
            applicationContext.sendBroadcast(
                Intent(ACTION_FILTERS_CHANGED).setPackage(applicationContext.packageName)
            )
            return Result.failure()
        }

        return try {
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
            val baseUpdateDue = FilterUpdatePolicy.isDue(
                baseCacheReady, FilterListManager.lastUpdatedAt(applicationContext), interval, now
            )
            var baseUpdateSucceeded = false
            if (baseUpdateDue) {
                FilterListManager.update(applicationContext)
                baseUpdateSucceeded = true
            }
            if (FilterUpdatePolicy.shouldClearBaseError(baseUpdateDue, baseUpdateSucceeded)) {
                prefs.edit().remove(FilterListManager.KEY_LAST_ERROR).apply()
            }

            var optionalFailure = false
            val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            val securityCacheReady = FilterListManager.hasCachedSecurityList(applicationContext)
            if (securityEnabled &&
                FilterUpdatePolicy.isDue(
                    securityCacheReady,
                    FilterListManager.lastSecurityUpdatedAt(applicationContext), interval, now
                )) {
                try {
                    FilterListManager.updateSecurity(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_SECURITY_LAST_ERROR).apply()
                } catch (e: Exception) {
                    optionalFailure = securityCacheReady
                    val editor = prefs.edit().putString(
                        FilterListManager.KEY_SECURITY_LAST_ERROR,
                        SafeFailureSummary.codeFor(e)
                    )
                    if (FilterUpdatePolicy.shouldDisableOptionalFilter(securityEnabled, securityCacheReady)) {
                        editor.putBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
                    }
                    editor.apply()
                }
            }
            val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
            val androidAdsCacheReady = FilterListManager.hasCachedAndroidAdsList(applicationContext)
            if (androidAdsEnabled &&
                FilterUpdatePolicy.isDue(
                    androidAdsCacheReady,
                    FilterListManager.lastAndroidAdsUpdatedAt(applicationContext), interval, now
                )) {
                try {
                    FilterListManager.updateAndroidAds(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR).apply()
                } catch (e: Exception) {
                    optionalFailure = optionalFailure || androidAdsCacheReady
                    val editor = prefs.edit().putString(
                        FilterListManager.KEY_ANDROID_ADS_LAST_ERROR,
                        SafeFailureSummary.codeFor(e)
                    )
                    if (FilterUpdatePolicy.shouldDisableOptionalFilter(androidAdsEnabled, androidAdsCacheReady)) {
                        editor.putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false)
                    }
                    editor.apply()
                }
            }

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
            android.util.Log.e("Guardian", "Scheduled filter update failed: ${SafeFailureSummary.codeFor(e)}; causeTypes=${SafeFailureSummary.diagnosticTypeChain(e)}")
            prefs.edit().putString(
                FilterListManager.KEY_LAST_ERROR,
                SafeFailureSummary.codeFor(e)
            ).apply()
            applicationContext.sendBroadcast(
                Intent(ACTION_FILTERS_CHANGED).setPackage(applicationContext.packageName)
            )
            Result.retry()
        }
    }

    companion object {
        const val ACTION_FILTERS_CHANGED = "com.guardian.adblock.FILTERS_CHANGED"
        const val KEY_FORCE_INITIAL_REFRESH = "force_initial_filter_refresh"
        // WorkManager runAttemptCount starts at 0: six executions mean the initial
        // request plus at most five retries with exponential backoff.
        const val MAX_INITIAL_CACHE_ATTEMPTS = 6
    }
}

/** Pure per-source refresh policy, also used to verify retry behavior. */
internal object FilterUpdatePolicy {
    /** A first-run cache repair is mandatory even if recurring refreshes are opted out. */
    fun shouldRunUpdates(autoUpdateEnabled: Boolean, forceInitialRefresh: Boolean): Boolean =
        autoUpdateEnabled || forceInitialRefresh

    fun shouldContinueInitialRetry(forceInitialRefresh: Boolean, runAttemptCount: Int, maxAttempts: Int): Boolean =
        !forceInitialRefresh || (maxAttempts > 0 && runAttemptCount >= 0 && runAttemptCount < maxAttempts)

    /** Disable an optional source only when its first download failed and no trusted cache exists. */
    fun shouldDisableOptionalFilter(wasEnabled: Boolean, hadValidCache: Boolean): Boolean =
        wasEnabled && !hadValidCache

    /** Preserve the last failure until a due refresh actually succeeds. */
    fun shouldClearBaseError(updateWasDue: Boolean, updateSucceeded: Boolean): Boolean =
        updateWasDue && updateSucceeded

    fun isDue(hasValidCache: Boolean, lastUpdatedAt: Long, intervalMs: Long, now: Long): Boolean {
        if (!hasValidCache || lastUpdatedAt <= 0L) return true
        val interval = FilterUpdateScheduler.normalizeInterval(intervalMs)
        return now >= lastUpdatedAt && now - lastUpdatedAt >= interval
    }
}
