package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnAppExclusionPolicyTest {
    private val guardianPackage = "com.guardian.adblock"

    @Test fun acceptsValidExternalAndroidPackageNames() {
        assertTrue(VpnAppExclusionPolicy.isEligible("com.example.mail", guardianPackage))
        assertTrue(VpnAppExclusionPolicy.isEligible("com.vendor_app.client2", guardianPackage))
    }

    @Test fun rejectsGuardianItselfAndMalformedOrUnboundedPackageNames() {
        listOf(
            guardianPackage,
            "",
            "example",
            "com..example",
            ".com.example",
            "com.example.",
            "com.example;other.app",
            "../other.app",
            "com.example/other",
            "a." + "b".repeat(254)
        ).forEach { packageName ->
            assertFalse(packageName, VpnAppExclusionPolicy.isEligible(packageName, guardianPackage))
        }
    }
}
