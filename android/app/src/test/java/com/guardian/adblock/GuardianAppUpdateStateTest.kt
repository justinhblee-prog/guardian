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
        val attempt = GuardianAppUpdateState.begin("확인 중")!!
        assertNull(GuardianAppUpdateState.begin("중복 확인"))
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

    @Test fun staleAttemptCannotOverwriteANewerUpdateCheck() {
        val oldAttempt = GuardianAppUpdateState.begin("첫 확인")!!
        assertTrue(GuardianAppUpdateState.fail(oldAttempt, "첫 확인 실패"))
        val newAttempt = GuardianAppUpdateState.begin("두 번째 확인")!!
        assertFalse(GuardianAppUpdateState.fail(oldAttempt, "늦게 도착한 첫 확인 실패"))
        assertFalse(GuardianAppUpdateState.noUpdate(oldAttempt, "늦게 도착한 첫 확인 완료"))
        assertTrue(GuardianAppUpdateState.snapshot().checkInProgress)
        assertEquals("두 번째 확인", GuardianAppUpdateState.snapshot().status)
        assertTrue(GuardianAppUpdateState.fail(newAttempt, "두 번째 확인 실패"))
    }

    @Test fun verifiedApkStateSurvivesNewScreenSnapshot() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-test.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            assertTrue(GuardianAppUpdateState.begin("확인 중"))
            GuardianAppUpdateState.complete(attempt, apk, false, "다운로드 완료")
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
            val attempt = GuardianAppUpdateState.begin("확인 중")!!
            assertTrue(runCatching { GuardianAppUpdateState.complete(attempt, empty, false, "완료") }.isFailure)
            assertNull(GuardianAppUpdateState.snapshot().pendingApkPath)
        } finally {
            empty.delete()
        }
    }

    @Test fun noUpdateClearsStalePendingInstallArtifact() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-stale.apk")
        apk.writeBytes(byteArrayOf(1))
        GuardianAppUpdateState.complete(GuardianAppUpdateState.begin("확인 중")!!, apk, true, "완료")
        GuardianAppUpdateState.noUpdate(GuardianAppUpdateState.snapshot().activeAttemptId ?: 0L, "최신입니다")
        assertNull(GuardianAppUpdateState.snapshot().pendingApkPath)
        assertNull(GuardianAppUpdateState.pendingApk(true))
        apk.delete()
    }

    @Test fun failedNewCheckKeepsPreviouslyVerifiedFileWhenItStillExists() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-verified.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            GuardianAppUpdateState.complete(apk, true, "다운로드 완료")
            assertTrue(val attempt = GuardianAppUpdateState.begin("다시 확인 중")!!)
            GuardianAppUpdateState.fail(attempt, "네트워크 실패")
            assertEquals(apk, GuardianAppUpdateState.pendingApk(true))
            assertEquals("네트워크 실패", GuardianAppUpdateState.snapshot().status)
        } finally {
            apk.delete()
        }
    }
}
