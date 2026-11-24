package com.guardian.adblock

/**
 * Serializes bursty requests into one active task plus at most one follow-up pass.
 * Requests arriving during a pass are coalesced; they never create another worker.
 */
internal class CoalescingTaskGate {
    private var active = false
    private var pending = false

    @Synchronized
    fun request(): Boolean {
        if (active) {
            pending = true
            return false
        }
        active = true
        return true
    }

    /** Returns true when the current worker should execute one more pass. */
    @Synchronized
    fun completePass(): Boolean {
        if (pending) {
            pending = false
            return true
        }
        active = false
        return false
    }

    /** Releases ownership after an unexpected worker exit and preserves a queued request. */
    @Synchronized
    fun releaseAfterFailure(allowRetry: Boolean): Boolean {
        active = false
        if (!pending) return false
        pending = false
        if (!allowRetry) return false
        active = true
        return true
    }
}
