package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class PacketSessionOwnershipTest {
    @Test
    fun stalePacketLoopCannotTearDownReplacementSession() {
        val ownership = PacketSessionOwnership()
        val oldSession = Any()
        val newSession = Any()
        var oldCleanupCount = 0
        var newCleanupCount = 0

        ownership.activate(oldSession) { }
        ownership.activate(newSession) { }

        assertFalse(ownership.finish(oldSession) { oldCleanupCount++ })
        assertEquals(0, oldCleanupCount)
        assertTrue(ownership.finish(newSession) { newCleanupCount++ })
        assertEquals(1, newCleanupCount)
    }

    @Test
    fun explicitStopInvalidatesPendingPacketLoopCleanup() {
        val ownership = PacketSessionOwnership()
        val session = Any()
        var cleanupCount = 0
        var stopCount = 0

        ownership.activate(session) { }
        ownership.stop { stopCount++ }

        assertEquals(1, stopCount)
        assertFalse(ownership.finish(session) { cleanupCount++ })
        assertEquals(0, cleanupCount)
    }

    @Test
    fun finishOnlySucceedsOnceForCurrentSession() {
        val ownership = PacketSessionOwnership()
        val session = Any()
        var cleanupCount = 0

        ownership.activate(session) { }
        assertTrue(ownership.finish(session) { cleanupCount++ })
        assertFalse(ownership.finish(session) { cleanupCount++ })
        assertEquals(1, cleanupCount)
    }
}
