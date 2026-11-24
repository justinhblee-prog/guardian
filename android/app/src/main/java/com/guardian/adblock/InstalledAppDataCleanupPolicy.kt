package com.guardian.adblock

/** Pure guard for destructive app-data cleanup when package enumeration is incomplete. */
internal object InstalledAppDataCleanupPolicy {
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
