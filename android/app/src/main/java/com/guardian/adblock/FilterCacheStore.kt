package com.guardian.adblock

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces a downloaded filter without sacrificing the last known-good cache.
 * A failed metadata commit restores the previous bytes as well as a failed file move.
 */
internal object FilterCacheStore {
    @Synchronized
    fun recover(target: File, isCommitted: (File) -> Boolean = { true }) {
        val backup = File(target.parentFile, "${target.name}.bak")
        val temporary = File(target.parentFile, "${target.name}.tmp")
        if (backup.isFile) {
            when {
                target.isFile && isCommitted(target) -> backup.delete()
                isCommitted(backup) -> {
                    target.delete()
                    move(backup, target)
                }
                else -> throw IllegalStateException("필터 캐시와 백업이 저장된 서명 해시와 일치하지 않습니다.")
            }
        }
        if (target.isFile && !isCommitted(target)) {
            throw IllegalStateException("필터 캐시가 저장된 서명 해시와 일치하지 않습니다.")
        }
        temporary.delete()
    }

    @Synchronized
    fun replace(
        target: File,
        bytes: ByteArray,
        commitMetadata: () -> Unit,
        rollbackMetadata: () -> Unit = {},
        moveFile: (File, File) -> Unit = ::move
    ) {
        recover(target)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val backup = File(target.parentFile, "${target.name}.bak")
        var hadPrevious = false
        try {
            // Persist the complete payload before renaming it into the committed cache path.
            // Metadata is committed only after this sync.
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            backup.delete()
            hadPrevious = target.isFile
            if (hadPrevious) moveFile(target, backup)
            moveFile(temporary, target)
            commitMetadata()
            backup.delete()
        } catch (failure: Exception) {
            // A filesystem operation can move a file and still report an error.
            // Restore based on the actual backup path, not only the operation result.
            if (backup.isFile) {
                target.delete()
                try {
                    moveFile(backup, target)
                } catch (restoreFailure: Exception) {
                    failure.addSuppressed(restoreFailure)
                }
            } else if (!hadPrevious) {
                target.delete()
            }
            try {
                rollbackMetadata()
            } catch (metadataRestoreFailure: Exception) {
                failure.addSuppressed(metadataRestoreFailure)
            }
            throw failure
        } finally {
            temporary.delete()
        }
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
