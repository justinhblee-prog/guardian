package com.guardian.adblock

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class GuardianAppUpdateStateTest {
    @After fun reset() = GuardianAppUpdateState.resetForTest()

    @Test fun onlyOneUpdateCheckCanRunAcrossScreenRebuilds() {
        assertTrue(GuardianAppUpdateState.begin("확인 중"))
        assertFalse(GuardianAppUpdateState.begin("중복 확인"))
        assertTrue(GuardianAppUpdateState.snapshot().checkInProgress)
        assertEquals("확인 중", GuardianAppUpdateState.snapshot().status)
    }

    @Test fun concurrentUpdateChecksAllowExactlyOneOwner() {
        val workers = 8
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val winners = AtomicInteger(0)
        val threads = (0 until workers).map { index ->
            Thread {
                ready.countDown()
                start.await()
                if (GuardianAppUpdateState.begin("확인 중 $index")) winners.incrementAndGet()
            }.apply { start() }
        }
        ready.await()
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, winners.get())
        assertTrue(GuardianAppUpdateState.snapshot().checkInProgress)
    }

    @Test fun verifiedApkStateSurvivesNewScreenSnapshot() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-test.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            assertTrue(GuardianAppUpdateState.begin("확인 중"))
            GuardianAppUpdateState.complete(apk, false, "다운로드 완료")
            val recreatedScreen = GuardianAppUpdateState.snapshot()
            assertFalse(recreatedScreen.checkInProgress)
            assertEquals("다운로드 완료", recreatedScreen.status)
            assertEquals(apk.absolutePath, recreatedScreen.pendingApkPath)
            assertEquals(apk, GuardianAppUpdateState.pendingApk(false))
            assertNull(GuardianAppUpdateState.pendingApk(true))
        } finally {
            apk.delete()
        }
    }

    @Test fun refusesToPublishMissingOrEmptyApkAsInstallable() {
        val empty = File(System.getProperty("java.io.tmpdir"), "guardian-update-empty.apk")
        empty.delete()
        empty.createNewFile()
        try {
            assertTrue(runCatching { GuardianAppUpdateState.complete(empty, false, "완료") }.isFailure)
            assertNull(GuardianAppUpdateState.snapshot().pendingApkPath)
        } finally {
            empty.delete()
        }
    }

    @Test fun noUpdateClearsStalePendingInstallArtifact() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-stale.apk")
        apk.writeBytes(byteArrayOf(1))
        GuardianAppUpdateState.complete(apk, true, "완료")
        GuardianAppUpdateState.noUpdate("최신입니다")
        assertNull(GuardianAppUpdateState.snapshot().pendingApkPath)
        assertNull(GuardianAppUpdateState.pendingApk(true))
        apk.delete()
    }

    @Test fun failedNewCheckKeepsPreviouslyVerifiedFileWhenItStillExists() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-verified.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            GuardianAppUpdateState.complete(apk, true, "다운로드 완료")
            assertTrue(GuardianAppUpdateState.begin("다시 확인 중"))
            GuardianAppUpdateState.fail("네트워크 실패")
            assertEquals(apk, GuardianAppUpdateState.pendingApk(true))
            assertEquals("네트워크 실패", GuardianAppUpdateState.snapshot().status)
        } finally {
            apk.delete()
        }
    }
}
