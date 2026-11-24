package com.guardian.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test fun nonPositiveBudgetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DnsQueryDeadline.afterMillis(0L)
        }
    }
}
