package com.guardian.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun currentPacketLoopCanPublishItsOwnUnexpectedTerminationOnlyOnce() {
        val guard = VpnTunnelSessionGuard()
        var cleanupCount = 0
        guard.activate(3L) {}

        assertTrue(guard.release(3L) { cleanupCount++ })
        assertFalse(guard.release(3L) { cleanupCount++ })
        assertTrue(cleanupCount == 1)
    }
}
