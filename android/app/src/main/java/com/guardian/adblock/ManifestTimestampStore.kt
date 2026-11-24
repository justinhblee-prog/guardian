package com.guardian.adblock

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Persists the highest accepted signed-manifest timestamp per source.
 *
 * Call [recordAccepted] only after signature, content hash and rule parsing have all passed.
 * State replacements are synced before rename so rollback recovery is durable across process death.
 */
internal class ManifestTimestampStore(private val stateFile: File) {
    @Synchronized
    fun lastAccepted(sourceId: String): Long {
        validateSourceId(sourceId)
        if (!stateFile.isFile) return 0L
        val properties = Properties()
        stateFile.inputStream().use(properties::load)
        return parseStoredTimestamp(properties.getProperty(sourceId), sourceId)
    }

    @Synchronized
    fun recordAccepted(sourceId: String, generatedAt: Long) {
        validateSourceId(sourceId)
        require(generatedAt > 0L) { "manifest 생성 시각은 양수여야 합니다." }
        val properties = readProperties()
        val stored = properties.getProperty(sourceId)
        val previous = parseStoredTimestamp(stored, sourceId)
        require(generatedAt > previous) { "이전 manifest보다 오래되었거나 동일한 버전입니다." }
        properties.setProperty(sourceId, generatedAt.toString())
        persist(properties)
    }

    @Synchronized
    fun restore(sourceId: String, generatedAt: Long) {
        validateSourceId(sourceId)
        require(generatedAt >= 0L) { "manifest 복구 시각은 음수일 수 없습니다." }
        val properties = readProperties()
        if (generatedAt == 0L) properties.remove(sourceId)
        else properties.setProperty(sourceId, generatedAt.toString())
        persist(properties)
    }

    /**
     * Persisted timestamps are security state, not best-effort metadata. Accept only
     * canonical non-negative decimal Unix seconds; signs, trailing whitespace, overflow and
     * negative values must not silently weaken anti-rollback protection.
     */
    private fun parseStoredTimestamp(value: String?, sourceId: String): Long {
        if (value == null) return 0L
        if (!value.matches(Regex("(0|[1-9][0-9]{0,15})"))) {
            throw IllegalStateException("저장된 manifest 시각 데이터가 손상되었습니다: $sourceId")
        }
        return value.toLongOrNull()
            ?: throw IllegalStateException("저장된 manifest 시각 데이터가 손상되었습니다: $sourceId")
    }
    private fun readProperties(): Properties {
        val properties = Properties()
        if (stateFile.isFile) stateFile.inputStream().use(properties::load)
        return properties
    }

    /** Write and fsync the complete state before atomically publishing the new generation. */
    private fun persist(properties: Properties) {
        val parent = stateFile.absoluteFile.parentFile
            ?: throw IllegalStateException("manifest 상태 저장 경로가 올바르지 않습니다.")
        check(parent.exists() || parent.mkdirs()) { "manifest 상태 디렉터리를 만들 수 없습니다." }
        val temporary = File(parent, stateFile.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Guardian accepted filter manifest timestamps")
                output.fd.sync()
            }
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
