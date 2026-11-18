package com.guardian.adblock

/**
 * Only restore VPN protection for an explicit system lifecycle event and when
 * the user had previously left protection enabled. A normal app launch or an
 * explicit user stop must never silently enable the VPN.
 */
internal object ProtectionRestorePolicy {
    const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

    fun shouldRestore(
        action: String?,
        persistedActive: Boolean,
        restoreRequested: Boolean? = null
    ): Boolean {
        val userWantsProtection = restoreRequested ?: persistedActive
        return userWantsProtection &&
            (action == ACTION_BOOT_COMPLETED || action == ACTION_MY_PACKAGE_REPLACED)
    }
}
