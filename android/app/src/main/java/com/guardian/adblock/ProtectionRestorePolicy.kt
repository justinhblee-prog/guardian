package com.guardian.adblock

/**
 * Only restore VPN protection for an explicit system lifecycle event and when
 * the user had previously left protection enabled. A normal app launch or an
 * explicit user stop must never silently enable the VPN.
 */
internal object ProtectionRestorePolicy {
    const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

    fun shouldRestore(action: String?, persistedActive: Boolean): Boolean =
        persistedActive && (action == ACTION_BOOT_COMPLETED || action == ACTION_MY_PACKAGE_REPLACED)

    // Android may destroy the foreground VPN service while replacing the APK.
    // Keep the user's restore intent until the package-replaced receiver starts it again.
    fun shouldPreserveRestoreIntentOnServiceDestroy(wasActive: Boolean): Boolean = wasActive
}
