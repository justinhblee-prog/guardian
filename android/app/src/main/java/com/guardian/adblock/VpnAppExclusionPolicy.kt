package com.guardian.adblock

/** Pure validation for package IDs loaded from persisted VPN exclusion settings. */
internal object VpnAppExclusionPolicy {
    private val packageNamePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun isEligible(packageName: String, guardianPackageName: String): Boolean =
        packageName != guardianPackageName &&
            packageName.length in 3..255 &&
            packageNamePattern.matches(packageName)
}
