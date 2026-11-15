package com.guardian.adblock

/**
 * Gives every start request its own generation. A later start must never
 * revive a worker that was cancelled by an intervening stop.
 */
internal class VpnStartCancellationGate {
    private val lock = Any()
    private var generation = 0L

    fun onStartRequested(): Long = synchronized(lock) {
        generation += 1L
        generation
    }

    fun cancel() = synchronized(lock) {
        generation += 1L
    }

    fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        requestGeneration != 0L && requestGeneration == generation
    }

    /** Commits the TUN only if this exact start request is still current. */
    fun tryCommitStart(requestGeneration: Long, commit: () -> Unit): Boolean =
        synchronized(lock) {
            if (requestGeneration == 0L || requestGeneration != generation) false
            else {
                commit()
                true
            }
        }
}
