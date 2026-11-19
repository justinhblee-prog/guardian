package com.guardian.adblock

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppMonitorTest {
    @Test
    fun resumedSelectedPackageIsReportedAsActiveTarget() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay"))

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)

        assertEquals(
            "com.samsung.android.spay",
            tracker.activeTarget()
        )
    }

    @Test
    fun pausedSelectedPackageIsNoLongerActive() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay"))
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)

        assertNull(tracker.activeTarget())
    }

    @Test
    fun multipleActivitiesKeepPackageActiveUntilLastActivityPauses() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay"))
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)
        assertEquals(
            "com.samsung.android.spay",
            tracker.activeTarget()
        )

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)
        assertNull(tracker.activeTarget())
    }

    @Test
    fun unrelatedForegroundPackageDoesNotPauseVpn() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay", "com.android.settings"))
        tracker.accept("com.android.settings", UsageEvents.Event.ACTIVITY_RESUMED)

        assertNull(tracker.activeTarget())
    }

    @Test
    fun oneOfSeveralSelectedPackagesCanTriggerPause() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay", "com.android.settings"))
        tracker.accept("com.android.settings", UsageEvents.Event.ACTIVITY_RESUMED)

        assertEquals(
            "com.android.settings",
            tracker.activeTarget()
        )
    }

    @Test
    fun pauseWithoutMatchingResumeDoesNotCreateNegativeActivityCount() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay"))
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_PAUSED)

        assertNull(tracker.activeTarget())
    }

    @Test
    fun legacyForegroundAndBackgroundEventsAreHandled() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay"))
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.MOVE_TO_FOREGROUND)

        assertEquals(
            "com.samsung.android.spay",
            tracker.activeTarget()
        )

        tracker.accept("com.samsung.android.spay", UsageEvents.Event.MOVE_TO_BACKGROUND)
        assertNull(tracker.activeTarget())
    }

    @Test
    fun resetClearsAllResumedActivities() {
        val tracker = ForegroundAppTracker(setOf("com.samsung.android.spay"))
        tracker.accept("com.samsung.android.spay", UsageEvents.Event.ACTIVITY_RESUMED)
        tracker.accept("com.android.settings", UsageEvents.Event.ACTIVITY_RESUMED)

        tracker.reset()

        assertNull(
            tracker.activeTarget(setOf("com.samsung.android.spay", "com.android.settings"))
        )
    }
}
