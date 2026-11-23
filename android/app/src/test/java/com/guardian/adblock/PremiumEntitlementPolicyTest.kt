package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class PremiumEntitlementPolicyTest {
    private val now = 1_800_000_000L
    private fun entitlement(
        product: String = "guardian-android",
        type: String = "pro",
        issuedAt: Long = now - 60,
        expiresAt: Long = now + 3600,
        licenseRef: String = "lic_test_0001",
        deviceBinding: String = "a".repeat(64)
    ) = LicenseEntitlementVerifier.Entitlement(
        product, type, issuedAt, expiresAt, licenseRef, deviceBinding
    )

    @Test fun activeSignedProClaimsPassThePolicyShapeCheck() {
        assertTrue(PremiumEntitlementPolicy.isEntitlementActive(entitlement(), now))
        assertTrue(PremiumEntitlementPolicy.isEntitlementActive(entitlement(product = "guardian"), now))
    }

    @Test fun freeExpiredFutureAndMalformedClaimsFailClosed() {
        val invalid = listOf(
            entitlement(type = "free"),
            entitlement(expiresAt = now),
            entitlement(issuedAt = now + 301),
            entitlement(product = "other"),
            entitlement(licenseRef = "bad ref"),
            entitlement(deviceBinding = "not-a-device")
        )
        invalid.forEach { assertFalse(PremiumEntitlementPolicy.isEntitlementActive(it, now)) }
        assertFalse(PremiumEntitlementPolicy.isEntitlementActive(null, now))
    }

    @Test fun noPreviewFeatureIsGrantedBeforeItsProductionImplementationIsRegistered() {
        assertTrue(PremiumEntitlementPolicy.productionReadyFeaturesForTest().isEmpty())
        PremiumEntitlementPolicy.Feature.values().forEach { feature ->
            assertFalse(PremiumEntitlementPolicy.canUse(feature, entitlement(), now))
        }
    }

    @Test fun featureListContainsAllSixAdvertisedCapabilities() {
        assertEquals(6, PremiumEntitlementPolicy.Feature.values().size)
    }

    @Test fun expiredEntitlementNeverGrantsFeatureAccess() {
        PremiumEntitlementPolicy.Feature.values().forEach { feature ->
            assertFalse(
                PremiumEntitlementPolicy.canUse(
                    feature, entitlement(expiresAt = now - 1), now
                )
            )
        }
    }
}
