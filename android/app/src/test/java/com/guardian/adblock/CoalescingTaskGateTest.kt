package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CoalescingTaskGateTest {
    @Test
    fun burstRequestsCreateOneWorkerAndOneFollowUpPass() {
        val gate = CoalescingTaskGate()
        val workers = AtomicInteger(0)
        assertTrue(gate.request())
        repeat(100) { assertFalse(gate.request()) }
        workers.incrementAndGet()

        assertTrue(gate.completePass())
        workers.incrementAndGet()
        assertFalse(gate.completePass())
        assertTrue(gate.request())
        assertTrue(workers.get() == 2)
    }

    @Test
    fun simultaneousRequestsNeverAcquireMoreThanOneWorker() {
        val gate = CoalescingTaskGate()
        val start = CountDownLatch(1)
        val ready = CountDownLatch(32)
        val acquired = AtomicInteger(0)
        val threads = (0 until 32).map {
            thread(name = "reload-gate-test-$it") {
                ready.countDown()
                start.await(2, TimeUnit.SECONDS)
                if (gate.request()) acquired.incrementAndGet()
            }
        }
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        threads.forEach { it.join(2_000L) }
        assertTrue(threads.none { it.isAlive })
        assertTrue(acquired.get() == 1)
        assertTrue(gate.completePass())
        assertFalse(gate.completePass())
    }

    @Test
    fun unexpectedWorkerExitCanReleaseGateAndPreserveOneQueuedPass() {
        val gate = CoalescingTaskGate()
        assertTrue(gate.request())
        assertFalse(gate.request())
        assertTrue(gate.releaseAfterFailure(allowRetry = true))
        assertFalse(gate.completePass())
        assertTrue(gate.request())
        assertFalse(gate.completePass())
    }

    @Test
    fun requestAfterNormalCompletionCanAcquireImmediately() {
        val gate = CoalescingTaskGate()
        assertTrue(gate.request())
        assertFalse(gate.completePass())
        assertTrue(gate.request())
        assertFalse(gate.completePass())
    }
}
