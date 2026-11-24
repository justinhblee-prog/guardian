package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class VpnStartCancellationGateTest {
    @Test fun stopInvalidatesTheCurrentStartGeneration() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        assertTrue(gate.isCurrent(start))
        gate.cancel()
        assertFalse(gate.isCurrent(start))
    }

    @Test fun aNewStartDoesNotReviveAnOlderCancelledWorker() {
        val gate = VpnStartCancellationGate()
        val firstStart = gate.onStartRequested()
        gate.cancel()
        val secondStart = gate.onStartRequested()

        assertFalse(gate.isCurrent(firstStart))
        assertTrue(gate.isCurrent(secondStart))
        var oldCommitted = false
        var newCommitted = false
        assertFalse(gate.tryCommitStart(firstStart) { oldCommitted = true })
        assertTrue(gate.tryCommitStart(secondStart) { newCommitted = true })
        assertFalse(oldCommitted)
        assertTrue(newCommitted)
    }

    @Test fun staleStartupFailureCannotPublishOrCleanUpOverANewerStart() {
        val gate = VpnStartCancellationGate()
        val oldStart = gate.onStartRequested()
        val newStart = gate.onStartRequested()
        var staleFailurePublished = false
        var newerTunnelClosed = false

        val oldFailureCommitted = gate.tryCommitStart(oldStart) {
            staleFailurePublished = true
            newerTunnelClosed = true
        }

        assertFalse(oldFailureCommitted)
        assertFalse(staleFailurePublished)
        assertFalse(newerTunnelClosed)
        assertTrue(gate.isCurrent(newStart))
    }

    @Test fun newerStartWaitsUntilCurrentFailureCleanupIsCommitted() {
        val gate = VpnStartCancellationGate()
        val firstStart = gate.onStartRequested()
        val cleanupEntered = CountDownLatch(1)
        val allowCleanupToFinish = CountDownLatch(1)
        val newerStartReturned = CountDownLatch(1)
        var newerGeneration = 0L

        val cleanupThread = thread(name = "vpn-start-failure-test") {
            assertTrue(gate.tryCommitStart(firstStart) {
                cleanupEntered.countDown()
                assertTrue(allowCleanupToFinish.await(2, TimeUnit.SECONDS))
            })
        }
        assertTrue(cleanupEntered.await(2, TimeUnit.SECONDS))

        val newerStartThread = thread(name = "vpn-new-start-test") {
            newerGeneration = gate.onStartRequested()
            newerStartReturned.countDown()
        }

        // onStartRequested shares the same gate lock, so it cannot overtake
        // the failure cleanup that has already acquired ownership.
        assertFalse(newerStartReturned.await(100, TimeUnit.MILLISECONDS))
        allowCleanupToFinish.countDown()
        cleanupThread.join(2_000L)
        newerStartThread.join(2_000L)

        assertTrue(newerStartReturned.await(2, TimeUnit.SECONDS))
        assertFalse(cleanupThread.isAlive)
        assertFalse(newerStartThread.isAlive)
        assertTrue(newerGeneration > firstStart)
        assertTrue(gate.isCurrent(newerGeneration))
    }

    @Test fun currentStartupFailureCanCommitItsOwnCleanup() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        var failurePublished = false

        assertTrue(gate.tryCommitStart(start) { failurePublished = true })
        assertTrue(failurePublished)
    }

    @Test fun cancelledStartCannotCommitItsTunDescriptor() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        gate.cancel()
        var committed = false
        assertFalse(gate.tryCommitStart(start) { committed = true })
        assertFalse(committed)
    }

    @Test fun stopAfterCommitIsSerializedAfterSuccessfulActivation() {
        val gate = VpnStartCancellationGate()
        val start = gate.onStartRequested()
        var committed = false
        assertTrue(gate.tryCommitStart(start) { committed = true })
        assertTrue(committed)
        gate.cancel()
        assertFalse(gate.isCurrent(start))
    }

    @Test fun stalePendingGenerationCannotReplaceTheLatestStart() {
        val gate = VpnStartCancellationGate()
        val first = gate.onStartRequested()
        gate.cancel()
        val second = gate.onStartRequested()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}
