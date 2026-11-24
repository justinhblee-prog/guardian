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
    fun recoveryRestoresBackupWhenTargetExistsButIsEmpty() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-45.apk").apply { createNewFile() }
            val backup = File(directory, "guardian-45.apk.bak").apply {
                writeBytes("last-known-good-apk".toByteArray())
            }
            val temporary = File(directory, ".guardian-45.apk.part").apply {
                writeBytes("new-verified-apk".toByteArray())
            }

            val moves = mutableListOf<Pair<File, File>>()
            GuardianApkCacheStore.commit(temporary, target) { source, destination ->
                moves += source to destination
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }

            assertEquals(backup to target, moves.first())
            assertArrayEquals("new-verified-apk".toByteArray(), target.readBytes())
            assertFalse(backup.exists())
            assertFalse(temporary.exists())
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
    @Test
    fun failedReplacementRestoresPreviousApkAndRemovesPartialFile() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-42.apk").apply {
                writeBytes("last-known-good-apk".toByteArray())
            }
            val temporary = File(directory, ".guardian-42.apk.part").apply {
                writeBytes("new-verified-apk".toByteArray())
            }
            var failedInstallMove = false

            val failure = runCatching {
                GuardianApkCacheStore.commit(temporary, target) { source, destination ->
                    if (source == temporary && !failedInstallMove) {
                        failedInstallMove = true
                        throw java.io.IOException("simulated atomic rename failure")
                    }
                    Files.move(
                        source.toPath(),
                        destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }.exceptionOrNull()

            assertTrue(failure is java.io.IOException)
            assertArrayEquals("last-known-good-apk".toByteArray(), target.readBytes())
            assertFalse(temporary.exists())
            assertFalse(File(directory, "guardian-42.apk.bak").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedBackupMoveThatAlreadyMovedFileRestoresOriginalAndCleansTemporary() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-43.apk").apply {
                writeBytes("original-apk".toByteArray())
            }
            val temporary = File(directory, ".guardian-43.apk.part").apply {
                writeBytes("replacement-apk".toByteArray())
            }
            var movedThenFailed = false

            val failure = runCatching {
                GuardianApkCacheStore.commit(temporary, target) { source, destination ->
                    Files.move(
                        source.toPath(),
                        destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    if (source == target && !movedThenFailed) {
                        movedThenFailed = true
                        throw java.io.IOException("simulated post-move failure")
                    }
                }
            }.exceptionOrNull()

            assertTrue(failure is java.io.IOException)
            assertArrayEquals("original-apk".toByteArray(), target.readBytes())
            assertFalse(temporary.exists())
            assertFalse(File(directory, "guardian-43.apk.bak").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedFirstCacheMoveDoesNotLeavePartialTargetOrTemporaryFile() {
        val directory = Files.createTempDirectory("guardian-apk-cache").toFile()
        try {
            val target = File(directory, "guardian-44.apk")
            val temporary = File(directory, ".guardian-44.apk.part").apply {
                writeBytes("replacement-apk".toByteArray())
            }

            val failure = runCatching {
                GuardianApkCacheStore.commit(temporary, target) { _, destination ->
                    destination.writeBytes("partial".toByteArray())
                    throw java.io.IOException("simulated first-write failure")
                }
            }.exceptionOrNull()

            assertTrue(failure is java.io.IOException)
            assertFalse(target.exists())
            assertFalse(temporary.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

}
