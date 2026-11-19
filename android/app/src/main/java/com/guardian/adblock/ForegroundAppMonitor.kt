package com.guardian.adblock

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal object AppForegroundPauseSettings {
    const val ACTION_SETTINGS_CHANGED = "com.guardian.adblock.COMPATIBILITY_SETTINGS_CHANGED"
    const val KEY_ENABLED = "app_foreground_auto_pause_enabled"
    const val KEY_PACKAGES = "app_foreground_auto_pause_packages"
    const val KEY_PAUSED = "app_foreground_auto_pause_active"
    const val KEY_PAUSED_PACKAGE = "app_foreground_auto_pause_package"
    const val KEY_ERROR = "app_foreground_auto_pause_error"
}

/**
 * Tracks only whether one of the user-selected packages currently has a resumed activity.
 * Events are processed in memory and are never persisted or uploaded.
 */
internal class ForegroundAppTracker {
    private val resumedActivityCounts = linkedMapOf<String, Int>()

    fun accept(packageName: String?, eventType: Int) {
        if (packageName.isNullOrBlank()) return
        when {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                eventType == UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                resumedActivityCounts[packageName] =
                    (resumedActivityCounts[packageName] ?: 0) + 1
            }

            eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                eventType == UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val count = resumedActivityCounts[packageName] ?: return
                if (count <= 1) {
                    resumedActivityCounts.remove(packageName)
                } else {
                    resumedActivityCounts[packageName] = count - 1
                }
            }
        }
    }

    fun activeTarget(targetPackages: Set<String>): String? =
        targetPackages.firstOrNull { (resumedActivityCounts[it] ?: 0) > 0 }

    fun reset() {
        resumedActivityCounts.clear()
    }
}

/**
 * Polls UsageStatsManager for foreground activity transitions while Guardian's VPN
 * foreground service is running. Only selected package names are surfaced to the caller.
 */
internal class ForegroundAppMonitor(
    context: Context,
    targetPackages: Set<String>,
    private val onForegroundTargetChanged: (String?) -> Unit,
    private val onPermissionRevoked: () -> Unit,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    private val appContext = context.applicationContext
    private val targets = targetPackages.toSet()
    private val running = AtomicBoolean(false)

    @Volatile
    private var activeTarget: String? = null

    @Volatile
    private var monitorThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): String? {
        if (targets.isEmpty() || !hasUsageAccess(appContext)) return null
        if (!running.compareAndSet(false, true)) return activeTarget

        val usageStats = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStats == null) {
            running.set(false)
            return null
        }

        val tracker = ForegroundAppTracker()
        val now = System.currentTimeMillis()
        val initialEvents = try {
            usageStats.queryEvents(now - INITIAL_LOOKBACK_MS, now)
        } catch (_: SecurityException) {
            running.set(false)
            onPermissionRevoked()
            return null
        }

        if (initialEvents == null) {
            tracker.reset()
        } else {
            consumeEvents(initialEvents, tracker)
        }
        activeTarget = tracker.activeTarget(targets)

        monitorThread = thread(
            start = true,
            isDaemon = true,
            name = "guardian-foreground-app-monitor"
        ) {
            var cursor = now
            while (running.get()) {
                if (!hasUsageAccess(appContext)) {
                    running.set(false)
                    onPermissionRevoked()
                    break
                }

                val end = System.currentTimeMillis()
                try {
                    val events = usageStats.queryEvents(cursor, end)
                    if (events == null) {
                        // Usage events can be unavailable while the user is locked.
                        // Treat unknown foreground state as not using a pause target:
                        // restoring protection is the fail-safe behavior.
                        tracker.reset()
                    } else {
                        consumeEvents(events, tracker)
                    }
                } catch (_: SecurityException) {
                    running.set(false)
                    onPermissionRevoked()
                    break
                }

                cursor = end
                val nextTarget = tracker.activeTarget(targets)
                if (nextTarget != activeTarget) {
                    activeTarget = nextTarget
                }
                // Poll callbacks are intentional: if a prior packet worker needed
                // extra time to exit, the service can retry the automatic VPN resume.
                onForegroundTargetChanged(nextTarget)

                try {
                    Thread.sleep(pollIntervalMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        return activeTarget
    }

    fun stop() {
        running.set(false)
        monitorThread?.interrupt()
        try {
            monitorThread?.join(MONITOR_STOP_JOIN_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        monitorThread = null
        activeTarget = null
    }

    private fun consumeEvents(events: UsageEvents, tracker: ForegroundAppTracker) {
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            tracker.accept(event.packageName, event.eventType)
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 500L
        private const val INITIAL_LOOKBACK_MS = 24L * 60L * 60L * 1000L
        private const val MONITOR_STOP_JOIN_MS = 1_000L

        @Suppress("DEPRECATION")
        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            return try {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                ) == AppOpsManager.MODE_ALLOWED
            } catch (_: RuntimeException) {
                false
            }
        }
    }
}
