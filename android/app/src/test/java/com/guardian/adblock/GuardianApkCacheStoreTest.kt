package com.guardian.adblock

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianApkCacheStoreTest {
    @Test
    fun commitReplacesOnlyWithCompleteTemporaryApk() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-42.apk").apply { writeBytes("old-apk".toByteArray()) }
            val temporary = File(directory, ".guardian-42.apk.part").apply {
                writeBytes("verified-new-apk".toByteArray())
            }

            GuardianApkCacheStore.commit(temporary, target)

            assertArrayEquals("verified-new-apk".toByteArray(), target.readBytes())
            assertFalse(temporary.exists())
            assertFalse(File(directory, "guardian-42.apk.bak").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingTemporaryApkDoesNotDiscardExistingCache() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-42.apk").apply { writeBytes("known-good-apk".toByteArray()) }
            val temporary = File(directory, ".guardian-42.apk.part")

            val failure = runCatching { GuardianApkCacheStore.commit(temporary, target) }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertArrayEquals("known-good-apk".toByteArray(), target.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun commitRecoversPreviousApkAfterInterruptedReplacement() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-42.apk")
            val backup = File(directory, "guardian-42.apk.bak").apply {
                writeBytes("previous-good-apk".toByteArray())
            }
            val temporary = File(directory, ".guardian-42.apk.part").apply {
                writeBytes("new-verified-apk".toByteArray())
            }

            GuardianApkCacheStore.commit(temporary, target)

            assertTrue(target.isFile)
            assertArrayEquals("new-verified-apk".toByteArray(), target.readBytes())
            assertFalse(backup.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
