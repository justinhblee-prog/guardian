package com.guardian.adblock

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsThreadFailureContextTest {
    @Test fun simultaneousDnsOperationsKeepIndependentFailureDetails() {
        val context = DnsThreadFailureContext()
        val firstWritten = CountDownLatch(1)
        val secondWritten = CountDownLatch(1)
        val firstResult = AtomicReference<String?>()
        val secondResult = AtomicReference<String?>()
        val first = Thread {
            context.current = "UDP: timeout"
            firstWritten.countDown()
            check(secondWritten.await(2, TimeUnit.SECONDS))
            firstResult.set(context.current)
            context.current = null
        }
        val second = Thread {
            check(firstWritten.await(2, TimeUnit.SECONDS))
            context.current = "DoH: invalid response"
            secondResult.set(context.current)
            secondWritten.countDown()
            context.current = null
        }
        first.start()
        second.start()
        first.join(3_000)
        second.join(3_000)
        assertEquals("UDP: timeout", firstResult.get())
        assertEquals("DoH: invalid response", secondResult.get())
    }

    @Test fun clearingOneThreadDoesNotClearAnotherThreadsFailure() {
        val context = DnsThreadFailureContext()
        val firstWritten = CountDownLatch(1)
        val secondCleared = CountDownLatch(1)
        val firstResult = AtomicReference<String?>()
        val first = Thread {
            context.current = "TCP: timeout"
            firstWritten.countDown()
            check(secondCleared.await(2, TimeUnit.SECONDS))
            firstResult.set(context.current)
            context.current = null
        }
        val second = Thread {
            check(firstWritten.await(2, TimeUnit.SECONDS))
            context.current = "DoT: handshake failed"
            context.current = null
            secondCleared.countDown()
        }
        first.start()
        second.start()
        first.join(3_000)
        second.join(3_000)
        assertEquals("TCP: timeout", firstResult.get())
    }

    @Test fun nullAssignmentRemovesTheCurrentThreadsValue() {
        val context = DnsThreadFailureContext()
        context.current = "UDP: failed"
        context.current = null
        assertNull(context.current)
    }
}
