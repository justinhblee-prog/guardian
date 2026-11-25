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
        var failureCleanupCommitted = false
        var cleanupWaitCompleted = false

        val cleanupThread = thread(name = "vpn-start-failure-test") {
            failureCleanupCommitted = gate.tryCommitStart(firstStart) {
                cleanupEntered.countDown()
                cleanupWaitCompleted = allowCleanupToFinish.await(2, TimeUnit.SECONDS)
            }
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
        assertTrue(failureCleanupCommitted)
        assertTrue(cleanupWaitCompleted)
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

    @Test fun stopCancellationWaitsForAnInFlightTunnelCommit() {
        val gate = VpnStartCancellationGate()
        val guard = VpnTunnelSessionGuard()
        val start = gate.onStartRequested()
        val commitEntered = CountDownLatch(1)
        val allowCommitToFinish = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        var committed = false

        val commitThread = thread(name = "vpn-start-commit-test") {
            assertTrue(gate.tryCommitStart(start) {
                guard.activate(start) { committed = true }
                commitEntered.countDown()
                assertTrue(allowCommitToFinish.await(2, TimeUnit.SECONDS))
            })
        }
        assertTrue(commitEntered.await(2, TimeUnit.SECONDS))

        val stopThread = thread(name = "vpn-stop-cancel-test") {
            gate.cancel()
            guard.invalidate()
            stopReturned.countDown()
        }

        // Stop cannot invalidate the generation halfway through the atomic
        // commit; once the commit exits, stop invalidates its session ownership.
        assertFalse(stopReturned.await(100, TimeUnit.MILLISECONDS))
        allowCommitToFinish.countDown()
        commitThread.join(2_000L)
        stopThread.join(2_000L)

        assertFalse(commitThread.isAlive)
        assertFalse(stopThread.isAlive)
        assertTrue(stopReturned.count == 0L)
        assertTrue(committed)
        assertFalse(gate.isCurrent(start))
        assertFalse(guard.hasActiveSession())
    }

