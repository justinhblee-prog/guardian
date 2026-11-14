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
    fun contentUsesAvailableWidthOnPhone() {
        assertEquals(393, ResponsiveLayoutPolicy.contentWidthPx(393, 1f))
    }

    @Test
    fun contentIsCappedOnLargeTablet() {
        assertEquals(840, ResponsiveLayoutPolicy.contentWidthPx(1200, 1f))
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
}
