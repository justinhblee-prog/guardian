package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumFeatureAccessPolicyTest {
    @Test
    fun activeProEntitlementUnlocksKnownPremiumFeatures() {
        listOf(
            PremiumFeatureAccessPolicy.ADVANCED_FILTERS,
            PremiumFeatureAccessPolicy.APP_PROTECTION,
            PremiumFeatureAccessPolicy.THREAT_ANALYTICS,
            PremiumFeatureAccessPolicy.SECURITY_PROFILES,
            PremiumFeatureAccessPolicy.PHISHING_SCANNER,
            PremiumFeatureAccessPolicy.PREMIUM_REPORTS
        ).forEach { feature ->
            assertTrue(PremiumFeatureAccessPolicy.isEntitled(feature, proActive = true, developmentPreview = false))
        }
    }

    @Test
    fun freeProductionBuildCannotAccessPremiumFeatures() {
        assertFalse(PremiumFeatureAccessPolicy.isEntitled(
            PremiumFeatureAccessPolicy.ADVANCED_FILTERS,
            proActive = false,
            developmentPreview = false
        ))
    }

    @Test
    fun developmentPreviewIsExplicitAndDoesNotImplyProEntitlement() {
        assertTrue(PremiumFeatureAccessPolicy.isEntitled(
            PremiumFeatureAccessPolicy.SECURITY_PROFILES,
            proActive = false,
            developmentPreview = true
        ))
        assertEquals(
            "개발 미리보기 · 권한 미부여",
            PremiumFeatureAccessPolicy.statusLabel(
                PremiumFeatureAccessPolicy.SECURITY_PROFILES,
                proActive = false,
                developmentPreview = true
            )
        )
    }

    @Test
    fun unknownFeatureIdsAreAlwaysDenied() {
        assertFalse(PremiumFeatureAccessPolicy.isEntitled(
            "future_unregistered_feature",
            proActive = true,
            developmentPreview = true
        ))
        assertEquals(
            "지원하지 않는 기능",
            PremiumFeatureAccessPolicy.statusLabel("future_unregistered_feature", true, true)
        )
    }
}
