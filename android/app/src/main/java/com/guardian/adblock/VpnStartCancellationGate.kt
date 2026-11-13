package com.guardian.adblock

import java.util.concurrent.atomic.AtomicBoolean

/** Preserves a stop request even when it arrives before the startup worker begins. */
internal class VpnStartCancellationGate {
    private val cancelled = AtomicBoolean(false)
    fun onStartRequested() { cancelled.set(false) }
    fun cancel() { cancelled.set(true) }
    fun isCancelled(): Boolean = cancelled.get()
}
