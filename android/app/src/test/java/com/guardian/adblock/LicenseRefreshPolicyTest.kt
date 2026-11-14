package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseRefreshPolicyTest {
    @Test fun doesNotRenewWhenMoreThanTwelveHoursRemain() {
        val now = 1_800_000_000L
        assertFalse(LicenseRefreshScheduler.shouldRenew(now + 12L * 60L * 60L + 1L, now))
    }

    @Test fun renewsAtTwelveHourBoundary() {
        val now = 1_800_000_000L
        assertTrue(LicenseRefreshScheduler.shouldRenew(now + 12L * 60L * 60L, now))
    }

    @Test fun renewsExpiredEntitlementAfterOfflinePeriod() {
        val now = 1_800_000_000L
        assertTrue(LicenseRefreshScheduler.shouldRenew(now - 1L, now))
    }
}
