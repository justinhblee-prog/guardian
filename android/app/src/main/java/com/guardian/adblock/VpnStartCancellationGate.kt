package com.guardian.adblock

/** Serializes cancellation with the final TUN-start commit to avoid check/commit races. */
internal class VpnStartCancellationGate {
    private val lock = Any()
    private var cancelled = false

    fun onStartRequested() = synchronized(lock) { cancelled = false }
    fun cancel() = synchronized(lock) { cancelled = true }
    fun isCancelled(): Boolean = synchronized(lock) { cancelled }

    /** Runs the state publication atomically with respect to cancel(). */
    fun tryCommitStart(commit: () -> Unit): Boolean = synchronized(lock) {
        if (cancelled) false else {
            commit()
            true
        }
    }
}
