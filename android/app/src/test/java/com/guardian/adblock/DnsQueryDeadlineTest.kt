package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

class DnsQueryDeadlineTest {
    @Test fun remainingBudgetUsesCeilingAndNeverRestarts() {
        var now = 10_000_000L
        val deadline = DnsQueryDeadline.afterMillis(8000L) { now }
        assertEquals(8000, deadline.remainingTimeoutMillis())
        now += 1_500_001_000L
        assertEquals(6500, deadline.remainingTimeoutMillis())
        now += 6_499_000_000L
        assertEquals(1, deadline.remainingTimeoutMillis())
        now += 1_000_000L
        assertNull(deadline.remainingTimeoutMillis())
    }

    @Test fun expiredDeadlineRejectsAnotherResolverAttempt() {
        var now = 0L
        val deadline = DnsQueryDeadline.afterMillis(100L) { now }
        now = 100_000_000L
        assertNull(deadline.remainingTimeoutMillis())
        assertThrows(java.net.SocketTimeoutException::class.java) {
            deadline.requireRemainingTimeoutMillis()
        }
    }

    @Test fun timeoutIsClampedToSocketIntegerRange() {
        val deadline = DnsQueryDeadline.afterMillis(Long.MAX_VALUE / 1_000_000L)
        assertEquals(Int.MAX_VALUE, deadline.remainingTimeoutMillis())
    }

    @Test fun watchdogClosesSocketWhenSharedDeadlineExpires() {
        val closed = CountDownLatch(1)
        DnsQueryDeadline.afterMillis(80L).closeOnExpiry { closed.countDown() }
        assertTrue("deadline watchdog did not close the socket", closed.await(2, TimeUnit.SECONDS))
    }

    @Test fun nonPositiveBudgetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DnsQueryDeadline.afterMillis(0L)
        }
    }

    @Test fun explicitCancellationClosesRegisteredSocketImmediately() {
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val deadline = DnsQueryDeadline.afterMillis(5_000L)
        val registration = deadline.closeOnExpiry { closed.set(true) }

        deadline.cancel()

        assertTrue("cancel must close the active upstream socket", closed.get())
        assertNull(deadline.remainingTimeoutMillis())
        assertThrows(java.net.SocketTimeoutException::class.java) {
            deadline.requireRemainingTimeoutMillis()
        }
        registration.close()
    }

    @Test fun cancellationBeforeSocketRegistrationRejectsAndClosesLateSocket() {
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val deadline = DnsQueryDeadline.afterMillis(5_000L)
        deadline.cancel()

        assertThrows(java.net.SocketTimeoutException::class.java) {
            deadline.closeOnExpiry { closed.set(true) }
        }
        assertTrue("late socket registration must be closed immediately", closed.get())
    }

    @Test fun cancellationRacingSocketRegistrationClosesTheSocketExactlyOnce() {
        repeat(100) {
            val deadline = DnsQueryDeadline.afterMillis(5_000L)
            val start = CountDownLatch(1)
            val closeCount = java.util.concurrent.atomic.AtomicInteger()
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            val registerThread = Thread {
                try {
                    start.await()
                    deadline.closeOnExpiry { closeCount.incrementAndGet() }
                } catch (_: java.net.SocketTimeoutException) {
                    // Cancellation won before the socket registration completed.
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
            val cancelThread = Thread {
                try {
                    start.await()
                    deadline.cancel()
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
            registerThread.start()
            cancelThread.start()
            start.countDown()
            registerThread.join(2_000L)
            cancelThread.join(2_000L)

            assertTrue("registration thread did not finish", !registerThread.isAlive)
            assertTrue("cancellation thread did not finish", !cancelThread.isAlive)
            failure.get()?.let { throw AssertionError("cancellation race failed", it) }
            assertEquals(1, closeCount.get())
        }
    }
}
