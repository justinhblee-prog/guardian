package com.guardian.adblock

internal object GuardianAutoUpdateCheckPolicy {
    const val DEVELOPMENT_INTERVAL_MILLIS: Long = 6L * 60L * 60L * 1000L
    const val STABLE_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun shouldCheck(
        lastCheckAtMillis: Long,
        nowMillis: Long,
        intervalMillis: Long
    ): Boolean {
        require(intervalMillis >= 0L) { "Update check interval cannot be negative." }
        if (lastCheckAtMillis <= 0L || nowMillis < lastCheckAtMillis) return true
        return nowMillis - lastCheckAtMillis >= intervalMillis
    }
}
