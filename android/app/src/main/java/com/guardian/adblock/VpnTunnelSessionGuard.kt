package com.guardian.adblock

/**
 * Owns the active packet-loop generation.
 *
 * A stopped TUN reader may unwind after a new VPN session has already started.
 * Only the generation that still owns the active session may publish loop failure
 * or tear down the current tunnel.
 */
internal class VpnTunnelSessionGuard {
    private val lock = Any()
    private var activeGeneration = 0L

    fun activate(generation: Long, commit: () -> Unit) = synchronized(lock) {
        require(generation > 0L) { "VPN session generation must be positive." }
        activeGeneration = generation
        commit()
    }

    fun invalidate(commit: () -> Unit = {}) = synchronized(lock) {
        activeGeneration = 0L
        commit()
    }

    fun release(generation: Long, onCurrentSession: () -> Unit): Boolean = synchronized(lock) {
        if (generation <= 0L || activeGeneration != generation) {
            false
        } else {
            activeGeneration = 0L
            onCurrentSession()
            true
        }
    }

    fun isOwner(generation: Long): Boolean = synchronized(lock) {
        generation > 0L && activeGeneration == generation
    }
}
