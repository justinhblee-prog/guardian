package com.guardian.adblock

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Commits a fully downloaded and verified APK without exposing a partial file
 * at the installable cache path or discarding the last complete cached APK.
 */
internal object GuardianApkCacheStore {
    @Synchronized
    fun commit(
        temporary: File,
        target: File,
        moveFile: (File, File) -> Unit = ::move
    ) {
        require(temporary.isFile && temporary.length() > 0L) {
            "검증된 APK 임시 파일이 없습니다."
        }
        val directory = target.parentFile ?: error("APK 캐시 경로가 올바르지 않습니다.")
        check(directory.isDirectory || directory.mkdirs()) {
            "APK 캐시 디렉터리를 만들 수 없습니다."
        }
        val backup = File(directory, "${target.name}.bak")
        recover(target, backup, moveFile)
        val hadPrevious = target.isFile
        if (hadPrevious) moveFile(target, backup)
        try {
            moveFile(temporary, target)
            backup.delete()
        } catch (failure: Exception) {
            target.delete()
            if (hadPrevious && backup.isFile) {
                try {
                    moveFile(backup, target)
                } catch (restoreFailure: Exception) {
                    failure.addSuppressed(restoreFailure)
                }
            }
            throw failure
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    private fun recover(target: File, backup: File, moveFile: (File, File) -> Unit) {
        if (!backup.isFile) return
        if (target.isFile) {
            backup.delete()
        } else {
            moveFile(backup, target)
        }
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
