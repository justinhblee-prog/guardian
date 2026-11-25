package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianNavigationTest {
    @Test fun licensePremiumLabelMatchesApprovedKoreanHierarchy() {
        assertEquals("라이선스 및 프리미엄", GuardianNavigation.LICENSE_PREMIUM_LABEL)
    }

    @Test fun primaryNavigationMatchesApprovedFiveDestinations() {
        assertEquals(
            listOf("home", "protection", "app-exclusions", "activity", "settings"),
            GuardianNavigation.primaryItems.map { it.id }
        )
        assertEquals(listOf("홈", "보호", "앱 제외", "활동", "설정"), GuardianNavigation.primaryItems.map { it.label })
    }

    @Test fun exitIsASettingsActionAndNotAContentDestination() {
        assertTrue(GuardianNavigation.settingsItems.contains("exit"))
        assertFalse(GuardianNavigation.isSettingsDestination("exit"))
    }

    @Test fun premiumAndFilterManagementRemainInsideSettings() {
        assertFalse(GuardianNavigation.primaryItems.any { it.id in setOf("premium", "rules", "updates") })
        assertEquals(listOf("general", "filtering", "license-premium", "support", "info", "exit"), GuardianNavigation.settingsItems)
        assertTrue(GuardianNavigation.isSettingsDestination("premium"))
        assertTrue(GuardianNavigation.isSettingsDestination("license-activation"))
        assertTrue(GuardianNavigation.isSettingsDestination("rules"))
        assertTrue(GuardianNavigation.isSettingsDestination("settings-general"))
        assertTrue(GuardianNavigation.isSettingsDestination("settings-support"))
        assertTrue(GuardianNavigation.isSettingsDestination("settings-info"))
    }
}
