package com.guardian.adblock

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class GuardianAppUpdateSnapshot(
    val checkInProgress: Boolean = false,
    val status: String? = null,
    val pendingApkPath: String? = null,
    val pendingDevelopmentBuild: Boolean? = null,
    val activeAttemptId: Long? = null
)

/** Process-wide update state shared by recreated MainActivity screens. */
internal object GuardianAppUpdateState {
    const val ACTION_STATE_CHANGED = "com.guardian.adblock.APP_UPDATE_STATE_CHANGED"
    private val current = AtomicReference(GuardianAppUpdateSnapshot())
    private val nextAttemptId = AtomicLong(0L)

    fun snapshot(): GuardianAppUpdateSnapshot = current.get()

    /** Returns an ownership token so late callbacks cannot overwrite a newer check. */
    fun begin(message: String): Long? {
        while (true) {
            val previous = current.get()
            if (previous.checkInProgress) return null
            val attemptId = nextAttemptId.incrementAndGet()
            val next = previous.copy(
                checkInProgress = true,
                status = message,
                activeAttemptId = attemptId
            )
            if (current.compareAndSet(previous, next)) return attemptId
        }
    }

    fun updateStatus(attemptId: Long, message: String): Boolean {
        while (true) {
            val previous = current.get()
            if (previous.activeAttemptId != attemptId || !previous.checkInProgress) return false
            val next = previous.copy(status = message)
            if (current.compareAndSet(previous, next)) return true
        }
    }

    fun complete(attemptId: Long, apk: File, developmentBuild: Boolean, message: String): Boolean {
        require(apk.isFile && apk.length() > 0L) { "Verified update APK must be a non-empty regular file." }
        while (true) {
            val previous = current.get()
            if (previous.activeAttemptId != attemptId || !previous.checkInProgress) return false
            val next = previous.copy(
                checkInProgress = false,
                status = message,
                pendingApkPath = apk.absolutePath,
                pendingDevelopmentBuild = developmentBuild,
                activeAttemptId = null
            )
            if (current.compareAndSet(previous, next)) return true
        }
    }

    fun noUpdate(attemptId: Long, message: String): Boolean {
        while (true) {
            val previous = current.get()
            if (previous.activeAttemptId != attemptId || !previous.checkInProgress) return false
            val next = previous.copy(
                checkInProgress = false,
                status = message,
                pendingApkPath = null,
                pendingDevelopmentBuild = null,
                activeAttemptId = null
            )
            if (current.compareAndSet(previous, next)) return true
        }
    }

    fun fail(attemptId: Long, message: String): Boolean {
        while (true) {
            val previous = current.get()
            if (previous.activeAttemptId != attemptId || !previous.checkInProgress) return false
            val next = previous.copy(
                checkInProgress = false,
                status = message,
                activeAttemptId = null
            )
            if (current.compareAndSet(previous, next)) return true
        }
    }

    fun pendingApk(developmentBuild: Boolean): File? {
        val state = current.get()
        if (state.pendingDevelopmentBuild != developmentBuild) return null
        return state.pendingApkPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    }

    fun updateInstallStatus(message: String) {
        current.updateAndGet { it.copy(status = message) }
    }

    internal fun resetForTest() {
        current.set(GuardianAppUpdateSnapshot())
        nextAttemptId.set(0L)
    }
}
