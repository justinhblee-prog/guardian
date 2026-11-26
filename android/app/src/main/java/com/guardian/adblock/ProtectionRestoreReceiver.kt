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
        val persistedActive = prefs.getBoolean(GuardianVpnService.KEY_ACTIVE, false)
        val restoreRequested = if (prefs.contains(GuardianVpnService.KEY_RESTORE_REQUESTED)) {
            prefs.getBoolean(GuardianVpnService.KEY_RESTORE_REQUESTED, false)
        } else null // Migrate existing installations that predate the durable intent flag.
        Log.i(
            "GuardianProtection",
            "Restore receiver invoked; action=$action protectionPreviouslyActive=$persistedActive restoreRequested=$restoreRequested"
        )
        if (!ProtectionRestorePolicy.shouldRestore(action, persistedActive, restoreRequested)) {
            Log.i("GuardianProtection", "Skipping automatic restore; action=$action protectionPreviouslyActive=$persistedActive restoreRequested=$restoreRequested")
            return
        }

        // Clear the stale "active" bit before relaunching the service. The UI must
        // not claim protection is live until the new TUN has actually been established.
        prefs.edit()
            .putBoolean(GuardianVpnService.KEY_ACTIVE, false)
            .putBoolean(GuardianVpnService.KEY_RECOVERY_PENDING, true)
            .putLong("protection_recovery_requested_at", System.currentTimeMillis())
            .putString(GuardianVpnService.KEY_STATUS, "VPN 보호를 자동으로 다시 연결하고 있습니다.")
            .commit()
        context.sendBroadcast(Intent(GuardianVpnService.ACTION_STATUS_CHANGED).setPackage(context.packageName))

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
            Log.e("GuardianProtection", "Could not request VPN restore after system event: $action (${SafeFailureSummary.codeFor(error)})")
            prefs.edit()
                .putBoolean(GuardianVpnService.KEY_ACTIVE, false)
                .putBoolean(GuardianVpnService.KEY_RECOVERY_PENDING, false)
                .remove("protection_recovery_requested_at")
                .putString(
                    GuardianVpnService.KEY_STATUS,
                    "기기 재시작 또는 앱 업데이트 후 VPN 자동 복구를 시작하지 못했습니다. Guardian을 열면 자동 복구를 다시 시도합니다."
                )
                .apply()
            context.sendBroadcast(Intent(GuardianVpnService.ACTION_STATUS_CHANGED).setPackage(context.packageName))
        }
    }
}
