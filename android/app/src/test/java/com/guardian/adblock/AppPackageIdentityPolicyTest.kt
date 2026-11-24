package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class AppPackageIdentityPolicyTest {
    @Test fun attributesOnlyAUniqueValidPackageForAUid() {
        assertEquals("com.example.mail", AppPackageIdentityPolicy.singleOwnerPackage(arrayOf("com.example.mail")))
        assertNull(AppPackageIdentityPolicy.singleOwnerPackage(emptyArray()))
        assertNull(AppPackageIdentityPolicy.singleOwnerPackage(arrayOf("com.example.one", "com.example.two")))
    }

    @Test fun deduplicatesIdenticalPackageEntriesButRejectsAmbiguousOwners() {
        assertEquals(
            "com.example.mail",
            AppPackageIdentityPolicy.singleOwnerPackage(arrayOf("com.example.mail", "com.example.mail"))
        )
        assertNull(
            AppPackageIdentityPolicy.singleOwnerPackage(
                arrayOf("com.example.mail", "com.example.calendar")
            )
        )
    }

    @Test fun rejectsMalformedPackageNames() {
        listOf(
            arrayOf("not a package"),
            arrayOf("com..example"),
            arrayOf("../private"),
            arrayOf("com.example;injected")
        ).forEach { assertNull(AppPackageIdentityPolicy.singleOwnerPackage(it)) }
    }
    @Test fun cleanupRequiresBothPackageManagerInventoriesToAgree() {
        val installed = setOf("com.example.mail", "com.example.calendar")
        assertTrue(
            InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
                queryAllPackagesGranted = true,
                applicationQuerySucceeded = true,
                packageInfoQuerySucceeded = true,
                applicationPackages = installed,
                packageInfoPackages = installed,
                launcherFallbackCount = 0
            )
        )
        assertFalse(
            InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
                queryAllPackagesGranted = true,
                applicationQuerySucceeded = true,
                packageInfoQuerySucceeded = true,
                applicationPackages = installed,
                packageInfoPackages = setOf("com.example.mail"),
                launcherFallbackCount = 0
            )
        )
    }

    @Test fun cleanupIsDisabledWhenLauncherFallbackFindsAdditionalApps() {
        val installed = setOf("com.example.mail")
        assertFalse(InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
            queryAllPackagesGranted = true,
            applicationQuerySucceeded = true,
            packageInfoQuerySucceeded = true,
            applicationPackages = installed,
            packageInfoPackages = installed,
            launcherFallbackCount = 1
        ))
    }

    @Test fun cleanupDoesNotRunWhenEitherPackageManagerQueryFailsOrReturnsEmpty() {
        val installed = setOf("com.example.mail")
        assertFalse(InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
            true, false, true, installed, installed, 0
        ))
        assertFalse(InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
            true, true, false, installed, installed, 0
        ))
        assertFalse(InstalledAppDataCleanupPolicy.isAuthoritativeInventory(
            true, true, true, emptySet(), emptySet(), 0
        ))
    }

    @Test fun doesNotPruneAnyAppDataFromAnIncompleteInventory() {
        assertTrue(
            InstalledAppDataCleanupPolicy.stalePackages(
                setOf("com.example.removed"), emptySet(), inventoryComplete = false
            ).isEmpty()
        )
    }

    @Test fun anEmptyInventoryNeverDeletesPreviouslyStoredAppData() {
        assertTrue(
            InstalledAppDataCleanupPolicy.stalePackages(
                existingPackages = setOf("com.example.mail", "com.example.calendar"),
                installedPackages = emptySet(),
                inventoryComplete = true
            ).isEmpty()
        )
    }

    @Test fun prunesOnlyPackagesMissingFromACompleteInventory() {
        assertEquals(
            setOf("com.example.removed", "com.example.old"),
            InstalledAppDataCleanupPolicy.stalePackages(
                setOf("com.example.keep", "com.example.removed", "com.example.old"),
                setOf("com.example.keep"),
                inventoryComplete = true
            )
        )
    }

}
