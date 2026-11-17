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
        if (!prefs.getBoolean(FilterListManager.KEY_AUTO_UPDATE, true)) return Result.success()

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
            if (FilterUpdatePolicy.isDue(
                    baseCacheReady, FilterListManager.lastUpdatedAt(applicationContext), interval, now
                )) {
                FilterListManager.update(applicationContext)
            }
            prefs.edit().remove(FilterListManager.KEY_LAST_ERROR).apply()

            var optionalFailure = false
            val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
            val securityCacheReady = securityEnabled && FilterListManager.hasCachedSecurityList(applicationContext)
            if (securityEnabled && FilterUpdatePolicy.isDue(
                    securityCacheReady,
                    FilterListManager.lastSecurityUpdatedAt(applicationContext), interval, now
                )) {
                try {
                    FilterListManager.updateSecurity(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_SECURITY_LAST_ERROR).apply()
                } catch (e: Exception) {
                    val editor = prefs.edit().putString(
                        FilterListManager.KEY_SECURITY_LAST_ERROR,
                        e.message ?: e.javaClass.simpleName
                    )
                    if (FilterUpdatePolicy.shouldDisableOptionalFilter(securityEnabled, securityCacheReady)) {
                        editor.putBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
                    } else {
                        optionalFailure = true
                    }
                    editor.apply()
                }
            }
            val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
            val androidAdsCacheReady = androidAdsEnabled && FilterListManager.hasCachedAndroidAdsList(applicationContext)
            if (androidAdsEnabled && FilterUpdatePolicy.isDue(
                    androidAdsCacheReady,
                    FilterListManager.lastAndroidAdsUpdatedAt(applicationContext), interval, now
                )) {
                try {
                    FilterListManager.updateAndroidAds(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR).apply()
                } catch (e: Exception) {
                    val editor = prefs.edit().putString(
                        FilterListManager.KEY_ANDROID_ADS_LAST_ERROR,
                        e.message ?: e.javaClass.simpleName
                    )
                    if (FilterUpdatePolicy.shouldDisableOptionalFilter(androidAdsEnabled, androidAdsCacheReady)) {
                        editor.putBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false)
                    } else {
                        optionalFailure = true
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

/** Pure per-source refresh policy, also used to verify retry behavior. */
internal object FilterUpdatePolicy {
    fun isDue(hasValidCache: Boolean, lastUpdatedAt: Long, intervalMs: Long, now: Long): Boolean {
        if (!hasValidCache || lastUpdatedAt <= 0L) return true
        val interval = FilterUpdateScheduler.normalizeInterval(intervalMs)
        return now >= lastUpdatedAt && now - lastUpdatedAt >= interval
    }

    /** An unavailable optional source must not prevent the base DNS protection from starting. */
    fun shouldDisableOptionalFilter(enabled: Boolean, hasValidCache: Boolean): Boolean =
        enabled && !hasValidCache
}
