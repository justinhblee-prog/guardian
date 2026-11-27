package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListManagerTest {
    @Test
    fun cacheMustBelongToTheSelectedProfile() {
        assertTrue(FilterListManager.isProfileCacheCurrent(true, "adguard", "adguard"))
        assertFalse(FilterListManager.isProfileCacheCurrent(true, "oisd_big", "adguard"))
        assertFalse(FilterListManager.isProfileCacheCurrent(false, "adguard", "adguard"))
    }

    @Test
    fun enabledOptionalSourcesMustHaveTheirOwnCache() {
        assertFalse(
            FilterListManager.isProfileCacheCurrent(
                hasCache = true,
                selectedKey = "adguard",
                cachedKey = "adguard",
                securityEnabled = true,
                hasSecurityCache = false
            )
        )
        assertFalse(
            FilterListManager.isProfileCacheCurrent(
                hasCache = true,
                selectedKey = "adguard",
                cachedKey = "adguard",
                androidAdsEnabled = true,
                hasAndroidAdsCache = false
            )
        )
        assertTrue(
            FilterListManager.isProfileCacheCurrent(
                hasCache = true,
                selectedKey = "adguard",
                cachedKey = "adguard",
                securityEnabled = true,
                hasSecurityCache = true,
                androidAdsEnabled = true,
                hasAndroidAdsCache = true
            )
        )
    }
}
