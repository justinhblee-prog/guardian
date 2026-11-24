package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class VpnTunnelSessionGuardTest {
    @Test
    fun previousPacketLoopCannotTearDownANewerSession() {
        val guard = VpnTunnelSessionGuard()
        var oldCleanup = false
        var newCleanup = false

        guard.activate(1L) {}
        guard.invalidate()
        guard.activate(2L) {}

        assertFalse(guard.release(1L) { oldCleanup = true })
        assertTrue(guard.isOwner(2L))
        assertFalse(oldCleanup)
        assertTrue(guard.release(2L) { newCleanup = true })
        assertTrue(newCleanup)
        assertFalse(guard.isOwner(2L))
    }

    @Test
    fun explicitStopInvalidatesPacketLoopOwnership() {
        val guard = VpnTunnelSessionGuard()
        var cleanupCalled = false
        guard.activate(9L) {}
        guard.invalidate()

        assertFalse(guard.release(9L) { cleanupCalled = true })
        assertFalse(cleanupCalled)
    }

    @Test
    fun newStartIsIgnoredOnlyWhileAnOwnedTunnelIsActive() {
        val guard = VpnTunnelSessionGuard()
        guard.activate(4L) {}
        assertTrue(guard.shouldIgnoreStart(running = true))
        guard.invalidate()
        assertFalse(guard.shouldIgnoreStart(running = true))
        assertFalse(guard.shouldIgnoreStart(running = false))
    }

    @Test
    fun invalidatedSessionIsNotReportedAsActiveDuringItsShutdownWindow() {
        val guard = VpnTunnelSessionGuard()
        guard.activate(4L) {}
        assertTrue(guard.hasActiveSession())
        guard.invalidate()
        assertFalse(guard.hasActiveSession())
    }

    @Test
    fun newSessionActivationWaitsUntilOldSessionCleanupIsFinished() {
        val guard = VpnTunnelSessionGuard()
        val cleanupEntered = CountDownLatch(1)
        val allowCleanupToFinish = CountDownLatch(1)
        val newStartAttempted = CountDownLatch(1)
        val newSessionActivated = CountDownLatch(1)
        val cleanupWaitSucceeded = AtomicBoolean(false)
        guard.activate(10L) {}

        val oldLoop = thread(name = "test-old-vpn-loop") {
            assertTrue(guard.release(10L) {
                cleanupEntered.countDown()
                cleanupWaitSucceeded.set(allowCleanupToFinish.await(2, TimeUnit.SECONDS))
            })
        }
        assertTrue(cleanupEntered.await(2, TimeUnit.SECONDS))

        val newStart = thread(name = "test-new-vpn-start") {
            newStartAttempted.countDown()
            guard.activate(11L) { newSessionActivated.countDown() }
        }
        assertTrue(newStartAttempted.await(2, TimeUnit.SECONDS))
        assertFalse(newSessionActivated.await(100, TimeUnit.MILLISECONDS))
        allowCleanupToFinish.countDown()
        oldLoop.join(2_000L)
        newStart.join(2_000L)

        assertFalse(oldLoop.isAlive)
        assertFalse(newStart.isAlive)
        assertTrue(cleanupWaitSucceeded.get())
        assertTrue(newSessionActivated.count == 0L)
        assertTrue(guard.isOwner(11L))
    }

    @Test
    fun currentPacketLoopCanPublishItsOwnUnexpectedTerminationOnlyOnce() {
        val guard = VpnTunnelSessionGuard()
        var cleanupCount = 0
        guard.activate(3L) {}

        assertTrue(guard.release(3L) { cleanupCount++ })
        assertFalse(guard.release(3L) { cleanupCount++ })
        assertTrue(cleanupCount == 1)
    }
}
