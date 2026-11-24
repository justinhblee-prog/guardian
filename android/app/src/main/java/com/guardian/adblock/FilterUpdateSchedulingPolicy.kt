package com.guardian.adblock

/** Pure gate for recurring filter refreshes; first-run cache population has a separate policy. */
internal object FilterUpdateSchedulingPolicy {
    fun shouldSchedule(userOptedIn: Boolean, profileAllowsUpdates: Boolean): Boolean =
        userOptedIn && profileAllowsUpdates

    fun shouldRun(
        userOptedIn: Boolean,
        profileAllowsUpdates: Boolean,
        networkUnmetered: Boolean
    ): Boolean = shouldSchedule(userOptedIn, profileAllowsUpdates) && networkUnmetered
}
