package com.guardian.adblock

import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * One monotonic time budget shared by every resolver candidate and transport
 * used to answer a single DNS question.
 */
internal class DnsQueryDeadline private constructor(
    private val startedAtNanos: Long,
    private val budgetNanos: Long,
    private val nanoTime: () -> Long
) {
    fun remainingTimeoutMillis(): Int? {
        val elapsedNanos = nanoTime() - startedAtNanos
        val remainingNanos = budgetNanos - elapsedNanos
        if (remainingNanos <= 0L) return null
        val roundedUpMillis = remainingNanos / NANOS_PER_MILLI +
            if (remainingNanos % NANOS_PER_MILLI == 0L) 0L else 1L
        return roundedUpMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
    }

    fun requireRemainingTimeoutMillis(): Int =
        remainingTimeoutMillis() ?: throw SocketTimeoutException("DNS query deadline exceeded")

    /**
     * SO_TIMEOUT alone is insufficient for TLS handshakes: each successful
     * partial read can restart its per-read timer. Closing the active socket
     * at the shared absolute deadline also bounds handshake and blocked writes.
     */
    fun closeOnExpiry(closeSocket: () -> Unit): AutoCloseable {
        val remaining = requireRemainingTimeoutMillis()
        val future: ScheduledFuture<*> = EXPIRY_EXECUTOR.schedule(
            { runCatching(closeSocket) },
            remaining.toLong(),
            TimeUnit.MILLISECONDS
        )
        return AutoCloseable { future.cancel(false) }
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private val EXPIRY_EXECUTOR = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "guardian-dns-deadline").apply { isDaemon = true }
        }.apply {
            // DNS traffic can be high; do not retain canceled watchdog tasks
            // in the delayed queue until their original deadline arrives.
            removeOnCancelPolicy = true
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        }

        fun afterMillis(
            timeoutMillis: Long,
            nanoTime: () -> Long = System::nanoTime
        ): DnsQueryDeadline {
            require(timeoutMillis > 0L && timeoutMillis <= Long.MAX_VALUE / NANOS_PER_MILLI)
            return DnsQueryDeadline(
                startedAtNanos = nanoTime(),
                budgetNanos = timeoutMillis * NANOS_PER_MILLI,
                nanoTime = nanoTime
            )
        }
    }
}
