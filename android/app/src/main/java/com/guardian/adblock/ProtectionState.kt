package com.guardian.adblock

/**
 * Persisted UI state is only a hint. A protection session is active only while
 * the current process still owns the live TUN packet loop.
 */
object ProtectionState {
    fun isActive(persistedActive: Boolean, tunnelActive: Boolean): Boolean =
        persistedActive && tunnelActive
}
