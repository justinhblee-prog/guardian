package com.guardian.adblock

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppMonitorTest {
    @Test
    fun resumedSelectedPackageIsReportedAsActiveTarget() {
        val tracker = ForegroundAppTracker()

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)

        assertEquals(
            "com.samsung.android.spay",
            tracker.activeTarget(setOf("com.samsung.android.spay"))
        )
    }

    @Test
    fun pausedSelectedPackageIsNoLongerActive() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)

        assertNull(tracker.activeTarget(setOf("com.samsung.android.spay")))
    }

    @Test
    fun multipleActivitiesKeepPackageActiveUntilLastActivityPauses() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)
        assertEquals(
            "com.samsung.android.spay",
            tracker.activeTarget(setOf("com.samsung.android.spay"))
        )

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)
        assertNull(tracker.activeTarget(setOf("com.samsung.android.spay")))
    }

    @Test
    fun unrelatedForegroundPackageDoesNotPauseVpn() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.android.settings", UsageEvents.Event.ACTIVITY_RESUMED)

        assertNull(tracker.activeTarget(setOf("com.samsung.android.spay")))
    }

    @Test
    fun oneOfSeveralSelectedPackagesCanTriggerPause() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.android.settings", UsageEvents.Event.ACTIVITY_RESUMED)

        assertEquals(
            "com.android.settings",
            tracker.activeTarget(
                setOf("com.samsung.android.spay", "com.android.settings")
            )
        )
    }

    @Test
    fun pauseWithoutMatchingResumeDoesNotCreateNegativeActivityCount() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)

        assertNull(tracker.activeTarget(setOf("com.samsung.android.spay")))
    }

    @Test
    fun legacyForegroundAndBackgroundEventsAreHandled() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.MOVE_TO_FOREGROUND)

        assertEquals(
            "com.samsung.android.spay",
            tracker.activeTarget(setOf("com.samsung.android.spay"))
        )

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.MOVE_TO_BACKGROUND)
        assertNull(tracker.activeTarget(setOf("com.samsung.android.spay")))
    }

    @Test
    fun resetClearsAllResumedActivities() {
        val tracker = ForegroundAppTracker()
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)
        tracker.accept("com.android.settings", UsageEvents.Event.ACTIVITY_RESUMED)

        tracker.reset()

        assertNull(
            tracker.activeTarget(setOf("com.samsung.android.spay", "com.android.settings"))
        )
    }
}
