package com.guardian.adblock

/** Pure guard for destructive app-data cleanup when package enumeration is incomplete. */
internal object InstalledAppDataCleanupPolicy {
    /**
     * Destructive cleanup is safe only when two independent PackageManager
     * enumeration APIs both succeed and return the same non-empty package set.
     */
    fun isAuthoritativeInventory(
        queryAllPackagesGranted: Boolean,
        applicationQuerySucceeded: Boolean,
        packageInfoQuerySucceeded: Boolean,
        applicationPackages: Set<String>,
        packageInfoPackages: Set<String>,
        launcherFallbackCount: Int
    ): Boolean = queryAllPackagesGranted &&
        applicationQuerySucceeded && packageInfoQuerySucceeded &&
        launcherFallbackCount == 0 &&
        applicationPackages.isNotEmpty() && applicationPackages == packageInfoPackages

    fun stalePackages(
        existingPackages: Set<String>,
        installedPackages: Set<String>,
        inventoryComplete: Boolean
    ): Set<String> = if (!inventoryComplete || installedPackages.isEmpty()) {
        emptySet()
    } else {
        existingPackages.filterTo(linkedSetOf()) { it !in installedPackages }
    }
}
