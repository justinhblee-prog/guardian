package com.guardian.adblock

import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One monotonic time budget shared by every resolver candidate and transport
 * used to answer a single DNS question. Explicit cancellation closes any
 * currently registered socket instead of waiting for the deadline to expire.
 */
internal class DnsQueryDeadline private constructor(
    private val startedAtNanos: Long,
    private val budgetNanos: Long,
    private val nanoTime: () -> Long
) {
    private val cancelled = AtomicBoolean(false)
    private val activeClosers = CopyOnWriteArrayList<() -> Unit>()

    fun remainingTimeoutMillis(): Int? {
        if (cancelled.get()) return null
        val elapsedNanos = nanoTime() - startedAtNanos
        val remainingNanos = budgetNanos - elapsedNanos
        if (remainingNanos <= 0L) return null
        val roundedUpMillis = remainingNanos / NANOS_PER_MILLI +
            if (remainingNanos % NANOS_PER_MILLI == 0L) 0L else 1L
        return roundedUpMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
    }

    fun requireRemainingTimeoutMillis(): Int =
        remainingTimeoutMillis() ?: throw SocketTimeoutException(
            if (cancelled.get()) "DNS query cancelled" else "DNS query deadline exceeded"
        )

    /** Immediately closes active upstream sockets and prevents another candidate from starting. */
    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        activeClosers.toList().forEach { closer ->
            if (activeClosers.remove(closer)) runCatching(closer)
        }
    }

    /**
     * SO_TIMEOUT alone is insufficient for TLS handshakes: each successful
     * partial read can restart its per-read timer. Closing the active socket
     * at the shared absolute deadline also bounds handshake and blocked writes.
     */
    fun closeOnExpiry(closeSocket: () -> Unit): AutoCloseable {
        val closer: () -> Unit = { runCatching(closeSocket) }
        activeClosers.add(closer)
        if (cancelled.get()) {
            if (activeClosers.remove(closer)) closer()
            throw SocketTimeoutException("DNS query cancelled")
        }

        val remaining = remainingTimeoutMillis()
        if (remaining == null) {
            if (activeClosers.remove(closer)) closer()
            throw SocketTimeoutException(
                if (cancelled.get()) "DNS query cancelled" else "DNS query deadline exceeded"
            )
        }
        val future: ScheduledFuture<*> = EXPIRY_EXECUTOR.schedule(
            {
                if (activeClosers.remove(closer)) closer()
            },
            remaining.toLong(),
            TimeUnit.MILLISECONDS
        )
        return AutoCloseable {
            future.cancel(false)
            activeClosers.remove(closer)
        }
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
