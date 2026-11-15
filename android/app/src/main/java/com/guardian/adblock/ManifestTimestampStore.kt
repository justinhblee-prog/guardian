package com.guardian.adblock

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Persists the highest accepted signed-manifest timestamp per source.
 *
 * Call [recordAccepted] only after signature, content hash and rule parsing have all passed.
 * The state file is app-private and atomically replaced so process death cannot reset rollback
 * protection to an older value.
 */
internal class ManifestTimestampStore(private val stateFile: File) {
    @Synchronized
    fun lastAccepted(sourceId: String): Long {
        validateSourceId(sourceId)
        if (!stateFile.isFile) return 0L
        val properties = Properties()
        stateFile.inputStream().use(properties::load)
        return properties.getProperty(sourceId)?.toLongOrNull()
            ?: if (properties.containsKey(sourceId)) {
                throw IllegalStateException("저장된 manifest 시각 데이터가 손상되었습니다.")
            } else 0L
    }

    @Synchronized
    fun recordAccepted(sourceId: String, generatedAt: Long) {
        validateSourceId(sourceId)
        require(generatedAt > 0L) { "manifest 생성 시각은 양수여야 합니다." }
        val properties = Properties()
        if (stateFile.isFile) stateFile.inputStream().use(properties::load)
        val stored = properties.getProperty(sourceId)
        val previous = stored?.toLongOrNull()
            ?: if (stored == null) 0L else throw IllegalStateException("저장된 manifest 시각 데이터가 손상되었습니다.")
        require(generatedAt > previous) { "이전 manifest보다 오래되었거나 동일한 버전입니다." }
        properties.setProperty(sourceId, generatedAt.toString())
        val parent = stateFile.absoluteFile.parentFile
            ?: throw IllegalStateException("manifest 상태 저장 경로가 올바르지 않습니다.")
        check(parent.exists() || parent.mkdirs()) { "manifest 상태 디렉터리를 만들 수 없습니다." }
        val temporary = File(parent, stateFile.name + ".tmp")
        try {
            temporary.outputStream().use { properties.store(it, "Guardian accepted filter manifest timestamps") }
            try {
                Files.move(temporary.toPath(), stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }


    @Synchronized
    fun restore(sourceId: String, generatedAt: Long) {
        validateSourceId(sourceId)
        require(generatedAt >= 0L) { "manifest 복구 시각은 음수일 수 없습니다." }
        val properties = Properties()
        if (stateFile.isFile) stateFile.inputStream().use(properties::load)
        if (generatedAt == 0L) properties.remove(sourceId)
        else properties.setProperty(sourceId, generatedAt.toString())
        val parent = stateFile.absoluteFile.parentFile
            ?: throw IllegalStateException("manifest 상태 저장 경로가 올바르지 않습니다.")
        check(parent.exists() || parent.mkdirs()) { "manifest 상태 디렉터리를 만들 수 없습니다." }
        val temporary = File(parent, stateFile.name + ".tmp")
        try {
            temporary.outputStream().use { properties.store(it, "Guardian accepted filter manifest timestamps") }
            try {
                Files.move(temporary.toPath(), stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun validateSourceId(sourceId: String) {
        require(sourceId.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) {
            "manifest sourceId 형식이 올바르지 않습니다."
        }
    }
}
