package com.guardian.adblock

/** Non-negative, overflow-safe monotonic counters for persisted DNS statistics. */
internal object DnsStatsCounterPolicy {
    fun add(current: Long, increment: Long = 1L): Long {
        val safeCurrent = current.coerceAtLeast(0L)
        val safeIncrement = increment.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - safeCurrent < safeIncrement) {
            Long.MAX_VALUE
        } else {
            safeCurrent + safeIncrement
        }
    }
}
