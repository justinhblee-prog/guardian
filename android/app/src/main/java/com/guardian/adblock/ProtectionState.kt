package com.guardian.adblock

/**
 * Persisted UI state is only a hint. A protection session is active only while
 * the current process still owns the live TUN packet loop.
 */
object ProtectionState {
    fun isActive(persistedActive: Boolean, tunnelActive: Boolean): Boolean =
        persistedActive && tunnelActive

    /** Resume a user's queued VPN start after a filter refresh fails only when the last verified cache set is still complete. */
    fun shouldResumeProtectionAfterFilterFailure(startWasRequested: Boolean, cachedFiltersReady: Boolean): Boolean =
        startWasRequested && cachedFiltersReady

    /** A sticky service restart should rebuild the TUN only when the last user-requested state was active. */
    fun shouldRestoreAfterServiceRestart(
        persistedActive: Boolean,
        restoreRequested: Boolean? = null
    ): Boolean = restoreRequested ?: persistedActive

    /** Explicit stop always clears restart intent, even before a TUN is committed. */
    fun shouldPublishStoppedState(explicitStop: Boolean, tunnelWasActive: Boolean): Boolean =
        explicitStop || tunnelWasActive
}
