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
            FilterListManager.update(applicationContext)
            prefs.edit().remove(FilterListManager.KEY_LAST_ERROR).apply()

            var optionalFailure = false
            if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)) {
                try {
                    FilterListManager.updateSecurity(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_SECURITY_LAST_ERROR).apply()
                } catch (e: Exception) {
                    optionalFailure = true
                    prefs.edit().putString(
                        FilterListManager.KEY_SECURITY_LAST_ERROR,
                        e.message ?: e.javaClass.simpleName
                    ).apply()
                }
            }
            if (prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, false)) {
                try {
                    FilterListManager.updateAndroidAds(applicationContext)
                    prefs.edit().remove(FilterListManager.KEY_ANDROID_ADS_LAST_ERROR).apply()
                } catch (e: Exception) {
                    optionalFailure = true
                    prefs.edit().putString(
                        FilterListManager.KEY_ANDROID_ADS_LAST_ERROR,
                        e.message ?: e.javaClass.simpleName
                    ).apply()
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
            if (optionalFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            prefs.edit().putString(
                FilterListManager.KEY_LAST_ERROR,
                e.message ?: e.javaClass.simpleName
            ).apply()
            Result.retry()
        }
    }
}
