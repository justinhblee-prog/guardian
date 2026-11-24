package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResponsiveLayoutPolicyTest {
    @Test
    fun phoneUsesCompactHorizontalPadding() {
        assertEquals(22, ResponsiveLayoutPolicy.horizontalPaddingDp(393))
    }

    @Test
    fun smallTabletUsesExpandedPadding() {
        assertEquals(32, ResponsiveLayoutPolicy.horizontalPaddingDp(600))
    }

    @Test
    fun wideTabletUsesComfortablePadding() {
        assertEquals(40, ResponsiveLayoutPolicy.horizontalPaddingDp(900))
    }

    @Test
    fun phoneAndCompactTabletUseBottomNavigation() {
        assertEquals(false, ResponsiveLayoutPolicy.usesNavigationRail(839))
    }

    @Test
    fun expandedTabletUsesSideNavigationRail() {
        assertEquals(true, ResponsiveLayoutPolicy.usesNavigationRail(840))
        assertEquals(true, ResponsiveLayoutPolicy.usesNavigationRail(1200))
    }

    @Test
    fun wideTabletContentWidthExcludesNavigationRail() {
        assertEquals(752, ResponsiveLayoutPolicy.contentAreaWidthDp(840))
        assertEquals(1192, ResponsiveLayoutPolicy.contentAreaWidthDp(1280))
    }

    @Test
    fun phoneContentWidthKeepsFullScreenWidth() {
        assertEquals(393, ResponsiveLayoutPolicy.contentAreaWidthDp(393))
    }

    @Test
    fun contentUsesAvailableWidthOnPhone() {
        assertEquals(393, ResponsiveLayoutPolicy.contentWidthPx(393, 1f))
    }

    @Test
    fun contentIsCappedOnLargeTablet() {
        assertEquals(840, ResponsiveLayoutPolicy.contentWidthPx(1200, 1f))
    }

    @Test
    fun dashboardRemainsSingleColumnBelowWideTabletBreakpoint() {
        assertEquals(false, ResponsiveLayoutPolicy.usesTwoColumnDashboard(999))
    }

    @Test
    fun dashboardUsesTwoColumnsAtWideTabletBreakpoint() {
        assertEquals(true, ResponsiveLayoutPolicy.usesTwoColumnDashboard(1000))
    }

    @Test
    fun contentWidthScalesWithDisplayDensity() {
        assertEquals(1680, ResponsiveLayoutPolicy.contentWidthPx(1200, 2f))
    }

    @Test
    fun invalidWidthIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayoutPolicy.contentWidthPx(0, 1f)
        }
    }

    @Test
    fun invalidDensityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ResponsiveLayoutPolicy.contentWidthPx(600, Float.NaN)
        }
    }

    @Test
    fun settingsUseSinglePaneOnPhonesAndSplitPaneOnTablets() {
        assertEquals(false, ResponsiveLayoutPolicy.usesSettingsSplitPane(599))
        assertEquals(true, ResponsiveLayoutPolicy.usesSettingsSplitPane(600))
        assertEquals(true, ResponsiveLayoutPolicy.usesSettingsSplitPane(840))
    }

    @Test
    fun phoneSystemBackReturnsFromSettingsDetailToCategoryIndex() {
        assertEquals(true, ResponsiveLayoutPolicy.shouldReturnToPhoneSettingsIndex(393, "settings", true))
        assertEquals(false, ResponsiveLayoutPolicy.shouldReturnToPhoneSettingsIndex(393, "settings", false))
    }

    @Test
    fun settingsBackPolicyDoesNotInterceptTabletSplitPaneOrOtherTabs() {
        assertEquals(false, ResponsiveLayoutPolicy.shouldReturnToPhoneSettingsIndex(600, "settings", true))
        assertEquals(false, ResponsiveLayoutPolicy.shouldReturnToPhoneSettingsIndex(393, "home", true))
        assertEquals(false, ResponsiveLayoutPolicy.shouldReturnToPhoneSettingsIndex(393, "settings-filtering", true))
    }

    @Test
    fun bottomSystemInsetHasExactlyOneOwnerForPhoneAndTabletNavigation() {
        assertEquals(false, ResponsiveLayoutPolicy.rootHandlesBottomSystemInset(393))
        assertEquals(false, ResponsiveLayoutPolicy.rootHandlesBottomSystemInset(839))
        assertEquals(true, ResponsiveLayoutPolicy.rootHandlesBottomSystemInset(840))
        assertEquals(true, ResponsiveLayoutPolicy.rootHandlesBottomSystemInset(1200))
    }
}
