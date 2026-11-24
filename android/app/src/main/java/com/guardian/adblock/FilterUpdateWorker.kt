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

        return try {
            val now = System.currentTimeMillis()
            val interval = FilterUpdateScheduler.normalizeInterval(
                prefs.getLong(FilterListManager.KEY_UPDATE_INTERVAL_MS, FilterUpdateScheduler.DEFAULT_INTERVAL_MS)
            )
            val selectedProfile = FilterListManager.selectedProfile(applicationContext)
            val cachedProfile = FilterListManager.cachedProfile(applicationContext)
            val baseCacheReady = FilterUpdatePolicy.hasUsableMainCache(
                cacheValid = FilterListManager.hasCachedList(applicationContext),
                selectedProfileKey = selectedProfile.key,
                cachedProfileKey = cachedProfile.key
            )

            // The bounded first-install retry budget protects only the mandatory
            // main filter cache. Once that cache is ready, an enabled optional
            // security/Android-ads source must keep retrying independently; otherwise
            // a transient optional-feed outage silently exhausts the shared budget.
            if (FilterUpdatePolicy.shouldFailInitialRetryBudget(
                    forceInitialRefresh, runAttemptCount, MAX_INITIAL_CACHE_ATTEMPTS, baseCacheReady
                )) {
                android.util.Log.e("Guardian", "Initial main filter cache retry budget exhausted after $runAttemptCount attempts")
                prefs.edit().putString(
                    FilterListManager.KEY_LAST_ERROR,
                    ERROR_INITIAL_CACHE_RETRY_EXHAUSTED
                ).apply()
                applicationContext.sendBroadcast(
                    Intent(ACTION_FILTERS_CHANGED).setPackage(applicationContext.packageName)
                )
                return Result.failure()
            }

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
                    // A failed first download is just as retry-worthy as a failed refresh:
                    // without a cache, this enabled protection layer is not active yet.
                    optionalFailure = FilterUpdatePolicy.shouldRetryOptionalUpdate(securityEnabled)
                    // Keep the user's enabled preference; retry until this first cache is available.
                    prefs.edit().putString(
                        FilterListManager.KEY_SECURITY_LAST_ERROR,
                        SafeFailureSummary.codeFor(e)
                    ).apply()
                } catch (e: LinkageError) {
                    optionalFailure = FilterUpdatePolicy.shouldRetryOptionalUpdate(securityEnabled, failureRetryable = false)
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
                    // Retry initial population too; otherwise WorkManager would mark the
                    // run successful and leave this enabled protection layer absent.
                    optionalFailure = optionalFailure || FilterUpdatePolicy.shouldRetryOptionalUpdate(androidAdsEnabled)
                    // Keep the user's enabled preference; retry until this first cache is available.
                    prefs.edit().putString(
                        FilterListManager.KEY_ANDROID_ADS_LAST_ERROR,
                        SafeFailureSummary.codeFor(e)
                    ).apply()
                } catch (e: LinkageError) {
                    optionalFailure = optionalFailure || FilterUpdatePolicy.shouldRetryOptionalUpdate(androidAdsEnabled, failureRetryable = false)
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
            recordScheduledFailure(prefs, e)
            Result.retry()
        } catch (e: LinkageError) {
            recordScheduledFailure(prefs, e)
            Result.retry()
        }
    }

    private fun recordScheduledFailure(
        prefs: android.content.SharedPreferences,
        error: Throwable
    ) {
        android.util.Log.e(
            "Guardian",
            "Scheduled filter update failed: ${SafeFailureSummary.codeFor(error)}; causeTypes=${SafeFailureSummary.diagnosticTypeChain(error)}"
        )
        prefs.edit().putString(
            FilterListManager.KEY_LAST_ERROR,
            SafeFailureSummary.codeFor(error)
        ).apply()
        applicationContext.sendBroadcast(
            Intent(ACTION_FILTERS_CHANGED).setPackage(applicationContext.packageName)
        )
    }

    companion object {
        const val ACTION_FILTERS_CHANGED = "com.guardian.adblock.FILTERS_CHANGED"
        const val KEY_FORCE_INITIAL_REFRESH = "force_initial_filter_refresh"
        const val ERROR_INITIAL_CACHE_RETRY_EXHAUSTED = "initial-filter-cache-retry-exhausted"
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

    fun hasUsableMainCache(cacheValid: Boolean, selectedProfileKey: String, cachedProfileKey: String): Boolean =
        cacheValid && selectedProfileKey == cachedProfileKey

    fun shouldContinueInitialRetry(forceInitialRefresh: Boolean, runAttemptCount: Int, maxAttempts: Int): Boolean =
        !forceInitialRefresh || (maxAttempts > 0 && runAttemptCount >= 0 && runAttemptCount < maxAttempts)

    /** Exhaust only when the required main cache is still unavailable. */
    fun shouldFailInitialRetryBudget(
        forceInitialRefresh: Boolean,
        runAttemptCount: Int,
        maxAttempts: Int,
        mainCacheReady: Boolean
    ): Boolean = !mainCacheReady &&
        !shouldContinueInitialRetry(forceInitialRefresh, runAttemptCount, maxAttempts)

    /** An enabled optional source remains eligible for retry until its first cache is installed. */
    fun shouldRetryOptionalUpdate(wasEnabled: Boolean, failureRetryable: Boolean = true): Boolean =
        wasEnabled && failureRetryable

    /**
     * Disable an optional source only when a runtime linkage failure makes it
     * unusable and no previously validated cache can continue protecting it.
     */
    fun shouldDisableOptionalFilter(wasEnabled: Boolean, hasUsableCache: Boolean): Boolean =
        wasEnabled && !hasUsableCache

    /** Do not claim an old list is active when the selected profile has no valid cache. */
    fun mainErrorStatus(errorCode: String?, hasUsableCache: Boolean): String? {
        if (errorCode.isNullOrBlank()) return null
        if (errorCode == FilterUpdateWorker.ERROR_INITIAL_CACHE_RETRY_EXHAUSTED) {
            return "초기 필터 복구 재시도 한도 초과 · 선택한 필터 캐시 없음 · 내장 기본 규칙으로 제한 보호 중 · 네트워크 확인 후 다시 시도하세요."
        }
        return if (hasUsableCache) {
            "광고 필터 업데이트 실패(이전 목록 유지): ${SafeFailureSummary.describeCode(errorCode)}"
        } else {
            "광고 필터 다운로드 실패(선택한 목록 없음 · 내장 기본 규칙으로 제한 보호 중): ${SafeFailureSummary.describeCode(errorCode)}"
        }
    }

    /** Preserve the last failure until a due refresh actually succeeds. */
    fun shouldClearBaseError(updateWasDue: Boolean, updateSucceeded: Boolean): Boolean =
        updateWasDue && updateSucceeded

    fun isDue(hasValidCache: Boolean, lastUpdatedAt: Long, intervalMs: Long, now: Long): Boolean {
        if (!hasValidCache || lastUpdatedAt <= 0L) return true
        val interval = FilterUpdateScheduler.normalizeInterval(intervalMs)
        // Wall-clock corrections can move the device time behind the last
        // successful update. Treat that timestamp as stale once, so a successful
        // refresh records the corrected clock and restores normal interval checks.
        return now < lastUpdatedAt || now - lastUpdatedAt >= interval
    }
}
