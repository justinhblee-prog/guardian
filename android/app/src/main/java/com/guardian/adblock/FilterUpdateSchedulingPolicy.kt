package com.guardian.adblock

/** Pure gate for recurring filter refreshes; first-run cache population has a separate policy. */
internal object FilterUpdateSchedulingPolicy {
    fun shouldSchedule(userOptedIn: Boolean, profileAllowsUpdates: Boolean): Boolean =
        userOptedIn && profileAllowsUpdates
}
