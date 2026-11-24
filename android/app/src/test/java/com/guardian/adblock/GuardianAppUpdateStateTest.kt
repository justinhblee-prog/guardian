package com.guardian.adblock

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianAppUpdateStateTest {
    @After fun reset() = GuardianAppUpdateState.resetForTest()

    @Test fun onlyOneUpdateCheckCanRunAcrossScreenRebuilds() {
        val attempt = GuardianAppUpdateState.begin("확인 중")
        assertTrue(attempt != null)
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
                if (GuardianAppUpdateState.begin("확인 중 $index") != null) winners.incrementAndGet()
            }.apply { start() }
        }
        ready.await()
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, winners.get())
        assertTrue(GuardianAppUpdateState.snapshot().checkInProgress)
    }

    @Test fun staleAttemptCannotOverwriteANewerUpdateCheck() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-stale-attempt.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val oldAttempt = GuardianAppUpdateState.begin("첫 확인")!!
            assertTrue(GuardianAppUpdateState.fail(oldAttempt, "첫 확인 실패"))
            val newAttempt = GuardianAppUpdateState.begin("두 번째 확인")!!
            assertFalse(GuardianAppUpdateState.updateStatus(oldAttempt, "두 번째 확인"))
            assertFalse(GuardianAppUpdateState.updateStatus(oldAttempt, "늦게 도착한 첫 확인 진행 상태"))
            assertFalse(GuardianAppUpdateState.complete(oldAttempt, apk, true, "늦게 도착한 첫 APK"))
            assertFalse(GuardianAppUpdateState.fail(oldAttempt, "늦게 도착한 첫 확인 실패"))
            assertFalse(GuardianAppUpdateState.noUpdate(oldAttempt, "늦게 도착한 첫 확인 완료"))
            assertTrue(GuardianAppUpdateState.snapshot().checkInProgress)
            assertEquals("두 번째 확인", GuardianAppUpdateState.snapshot().status)
            assertNull(GuardianAppUpdateState.snapshot().pendingApkPath)
            assertTrue(GuardianAppUpdateState.fail(newAttempt, "두 번째 확인 실패"))
        } finally {
            apk.delete()
        }
    }

    @Test fun verifiedApkStateSurvivesNewScreenSnapshot() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-test.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val attempt = GuardianAppUpdateState.begin("확인 중")!!
            assertTrue(GuardianAppUpdateState.complete(attempt, apk, false, "다운로드 완료"))
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
        try {
            val attempt = GuardianAppUpdateState.begin("확인 중")!!
            assertTrue(GuardianAppUpdateState.complete(attempt, apk, true, "완료"))
            val nextAttempt = GuardianAppUpdateState.begin("최신 여부 확인")!!
            assertTrue(GuardianAppUpdateState.noUpdate(nextAttempt, "최신입니다"))
            assertNull(GuardianAppUpdateState.snapshot().pendingApkPath)
            assertNull(GuardianAppUpdateState.pendingApk(true))
        } finally {
            apk.delete()
        }
    }

    @Test fun failedNewCheckKeepsPreviouslyVerifiedFileWhenItStillExists() {
        val apk = File(System.getProperty("java.io.tmpdir"), "guardian-update-verified.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val firstAttempt = GuardianAppUpdateState.begin("최초 확인")!!
            assertTrue(GuardianAppUpdateState.complete(firstAttempt, apk, true, "다운로드 완료"))
            val retryAttempt = GuardianAppUpdateState.begin("다시 확인 중")!!
            assertTrue(GuardianAppUpdateState.fail(retryAttempt, "네트워크 실패"))
            assertEquals(apk, GuardianAppUpdateState.pendingApk(true))
            assertEquals("네트워크 실패", GuardianAppUpdateState.snapshot().status)
        } finally {
            apk.delete()
        }
    }
}
