package com.guardian.adblock

import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal data class GuardianAppUpdateSnapshot(
    val checkInProgress: Boolean = false,
    val status: String? = null,
    val pendingApkPath: String? = null,
    val pendingDevelopmentBuild: Boolean? = null
)

/** Process-wide update state shared by recreated MainActivity screens. */
internal object GuardianAppUpdateState {
    const val ACTION_STATE_CHANGED = "com.guardian.adblock.APP_UPDATE_STATE_CHANGED"
    private val current = AtomicReference(GuardianAppUpdateSnapshot())

    fun snapshot(): GuardianAppUpdateSnapshot = current.get()

    fun begin(message: String): Boolean {
        while (true) {
            val previous = current.get()
            if (previous.checkInProgress) return false
            val next = previous.copy(checkInProgress = true, status = message)
            if (current.compareAndSet(previous, next)) return true
        }
    }

    fun updateStatus(message: String) {
        current.updateAndGet { it.copy(status = message) }
    }

    fun complete(apk: File, developmentBuild: Boolean, message: String) {
        current.updateAndGet {
            it.copy(
                checkInProgress = false,
                status = message,
                pendingApkPath = apk.absolutePath,
                pendingDevelopmentBuild = developmentBuild
            )
        }
    }

    fun noUpdate(message: String) {
        current.updateAndGet {
            it.copy(
                checkInProgress = false,
                status = message,
                pendingApkPath = null,
                pendingDevelopmentBuild = null
            )
        }
    }

    fun fail(message: String) {
        current.updateAndGet { it.copy(checkInProgress = false, status = message) }
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
    }
}
