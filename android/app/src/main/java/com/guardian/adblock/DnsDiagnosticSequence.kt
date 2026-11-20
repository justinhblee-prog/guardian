package com.guardian.adblock

import java.util.concurrent.atomic.AtomicLong

/**
 * Orders asynchronous diagnostic writers so an older operation cannot replace
 * a newer packet event or repopulate a diagnostic after the user clears history.
 */
internal class DnsDiagnosticSequence {
    private val sequence = AtomicLong(0L)

    fun reserve(): Long = sequence.incrementAndGet()

    fun isCurrent(candidate: Long): Boolean = sequence.get() == candidate

    fun invalidate(): Long = reserve()
}
