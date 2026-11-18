package com.guardian.adblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Re-establishes the user's previously active DNS VPN after device reboot or
 * an in-place app update. The service still performs filter and upstream DNS
 * preflight checks before committing a new tunnel.
 */
class ProtectionRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val prefs = context.getSharedPreferences(GuardianVpnService.PREFS, Context.MODE_PRIVATE)
        val wasEnabled = prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)
        if (!ProtectionRestorePolicy.shouldRestore(action, wasEnabled)) {
            Log.i("GuardianProtection", "Skipping automatic restore; action=$action protectionPreviouslyActive=$wasEnabled")
            return
        }

        val service = Intent(context, GuardianVpnService::class.java)
            .setAction(GuardianVpnService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
            Log.i("GuardianProtection", "Requested VPN restore after system event: $action")
        } catch (error: Exception) {
            Log.e("GuardianProtection", "Could not request VPN restore after system event: $action", error)
            prefs.edit()
                .putString(
                    GuardianVpnService.KEY_STATUS,
                    "기기 재시작 또는 앱 업데이트 후 VPN 자동 복구를 시작하지 못했습니다. Guardian을 열어 보호를 다시 연결해 주세요."
                )
                .apply()
            context.sendBroadcast(Intent(GuardianVpnService.ACTION_STATUS_CHANGED).setPackage(context.packageName))
        }
    }
}
