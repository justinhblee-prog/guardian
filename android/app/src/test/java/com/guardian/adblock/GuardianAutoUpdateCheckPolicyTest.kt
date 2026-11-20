package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianAutoUpdateCheckPolicyTest {
    @Test
    fun firstSettingsEntryChecksImmediately() {
        assertTrue(
            GuardianAutoUpdateCheckPolicy.shouldCheck(
                lastCheckAtMillis = 0L,
                nowMillis = 1_000_000L,
                intervalMillis = GuardianAutoUpdateCheckPolicy.DEVELOPMENT_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun developmentAutoCheckIsSuppressedWithinSixHours() {
        val interval = GuardianAutoUpdateCheckPolicy.DEVELOPMENT_INTERVAL_MILLIS
        assertFalse(
            GuardianAutoUpdateCheckPolicy.shouldCheck(
                lastCheckAtMillis = 10_000L,
                nowMillis = 10_000L + interval - 1L,
                intervalMillis = interval
            )
        )
    }

    @Test
    fun developmentAutoCheckRunsAtSixHourBoundary() {
        val interval = GuardianAutoUpdateCheckPolicy.DEVELOPMENT_INTERVAL_MILLIS
        assertTrue(
            GuardianAutoUpdateCheckPolicy.shouldCheck(
                lastCheckAtMillis = 10_000L,
                nowMillis = 10_000L + interval,
                intervalMillis = interval
            )
        )
    }

    @Test
    fun stableAutoCheckIsSuppressedWithinTwentyFourHours() {
        val interval = GuardianAutoUpdateCheckPolicy.STABLE_INTERVAL_MILLIS
        assertFalse(
            GuardianAutoUpdateCheckPolicy.shouldCheck(
                lastCheckAtMillis = 10_000L,
                nowMillis = 10_000L + interval - 1L,
                intervalMillis = interval
            )
        )
    }

    @Test
    fun clockRollbackDoesNotLockOutTheNextCheck() {
        assertTrue(
            GuardianAutoUpdateCheckPolicy.shouldCheck(
                lastCheckAtMillis = 20_000L,
                nowMillis = 10_000L,
                intervalMillis = GuardianAutoUpdateCheckPolicy.DEVELOPMENT_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun manualRetryRemainsIndependentOfAutomaticInterval() {
        // The interval policy only decides whether Settings should auto-click.
        // The visible manual button remains enabled and is not gated by this policy.
        assertFalse(
            GuardianAutoUpdateCheckPolicy.shouldCheck(
                lastCheckAtMillis = 20_000L,
                nowMillis = 20_001L,
                intervalMillis = GuardianAutoUpdateCheckPolicy.DEVELOPMENT_INTERVAL_MILLIS
            )
        )
    }
}
