package com.guardian.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GuardianVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.guardian.adblock.START"
        const val ACTION_STOP = "com.guardian.adblock.STOP"
        const val ACTION_RELOAD_RULES = "com.guardian.adblock.RELOAD_RULES"
        const val ACTION_RECONFIGURE_EXCLUDED_APPS = "com.guardian.adblock.RECONFIGURE_EXCLUDED_APPS"
        const val ACTION_STATUS_CHANGED = "com.guardian.adblock.STATUS_CHANGED"
        const val ACTION_TEST_UPSTREAM = "com.guardian.adblock.TEST_UPSTREAM"
        const val ACTION_UPSTREAM_TEST_RESULT = "com.guardian.adblock.UPSTREAM_TEST_RESULT"
        const val PREFS = "guardian_settings"
        const val KEY_USER_BLOCK = "user_block_domains"
        const val KEY_USER_ALLOW = "user_allow_domains"
        const val KEY_ACTIVE = "protection_active"
        // Durable user intent, separate from whether this process currently owns a live TUN.
        const val KEY_RESTORE_REQUESTED = "protection_restore_requested"
        const val KEY_RECOVERY_PENDING = "protection_recovery_pending"
        const val KEY_STATUS = "protection_status"
        const val KEY_STARTUP_TRACE = "protection_startup_trace"
        const val KEY_DNS_QUERIES = "dns_queries"
        const val KEY_DNS_BLOCKED = "dns_blocked"
        const val KEY_DNS_FORWARDED = "dns_forwarded"
        const val KEY_DNS_FAILED = "dns_failed"
        const val KEY_DNS_IGNORED = "dns_ignored"
        const val KEY_DNS_AD_BLOCKED = "dns_ad_blocked"
        const val KEY_DNS_TRACKER_BLOCKED = "dns_tracker_blocked"
        const val KEY_DNS_OTHER_BLOCKED = "dns_other_blocked"
        const val KEY_DNS_DIAGNOSTIC = "dns_diagnostic"
        const val KEY_FILTER_READINESS = "filter_readiness_diagnostic"
        const val KEY_DNS_RECENT = "dns_recent"
        private const val CHANNEL = "guardian_protection"
        private const val VIRTUAL_DNS = "10.13.37.2"
        private const val VIRTUAL_DNS_V6 = "fd13:37::2"
        private const val MAX_UDP_DNS_RESPONSE_BYTES = 1232
        private const val MAX_DNS_MESSAGE_BYTES = 65535
        const val KEY_UPSTREAM_DNS = "upstream_dns"
        const val KEY_UPSTREAM_TRANSPORT = "upstream_transport"
        const val KEY_UPSTREAM_TLS_HOST = "upstream_tls_host"
        const val KEY_UPSTREAM_TEST_SUCCESS = "upstream_test_success"
        const val KEY_UPSTREAM_TEST_LATENCY_MS = "upstream_test_latency_ms"
        const val KEY_UPSTREAM_TEST_TRANSPORT = "upstream_test_transport"
        const val KEY_UPSTREAM_TEST_AT = "upstream_test_at"
        const val DEFAULT_UPSTREAM_TRANSPORT = "udp"
        const val DEFAULT_UPSTREAM_TLS_HOST = "cloudflare-dns.com"
        const val KEY_EXCLUDED_APPS = "excluded_apps"
        const val KEY_EXCLUDED_APPS_APPLIED = "excluded_apps_applied"
        const val KEY_EXCLUDED_APPS_ERROR = "excluded_apps_error"
        const val KEY_DIRECT_DNS_CAPTURE = "direct_dns_capture"
        const val DEFAULT_DIRECT_DNS_CAPTURE = false
        const val DEFAULT_UPSTREAM_DNS = "1.1.1.1"
        private val KNOWN_PUBLIC_DNS_IPV4 = listOf(
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4",
            "9.9.9.9", "149.112.112.112", "94.140.14.14", "94.140.15.15",
            "94.140.14.15", "94.140.15.16",
            "208.67.222.222", "208.67.220.220"
        )
        private val KNOWN_PUBLIC_DNS_IPV6 = listOf(
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2620:fe::fe", "2620:fe::9",
            "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
            "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff"
        )
        private val DNS_STATS_LOCK = Any()
        private const val DNS_STATS_BROADCAST_INTERVAL_MS = 500L
        @Volatile private var tunnelActive = false
        fun isTunnelActive(): Boolean = tunnelActive
    }

    private val lastUpstreamFailureContext = DnsThreadFailureContext()
    private var lastUpstreamFailure: String?
        get() = lastUpstreamFailureContext.current
        set(value) { lastUpstreamFailureContext.current = value }
    @Volatile private var tun: ParcelFileDescriptor? = null
    private val tunnelSessionGuard = VpnTunnelSessionGuard()
    @Volatile private var packetWorker: Thread? = null
    private val running = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    private data class PendingStart(val generation: Long, val startId: Int)
    private val pendingStart = AtomicReference<PendingStart?>(null)
    private val startCancellation = VpnStartCancellationGate()
    private val ruleReloadGate = CoalescingTaskGate()
    private val lastStatsBroadcastAt = AtomicLong(0L)
    @Volatile private var lastStartId = 0
    @Volatile private var foregroundAppMonitor: ForegroundAppMonitor? = null
    private val compatibilityResumeInProgress = AtomicBoolean(false)
    private val compatibilityMonitorGeneration = AtomicLong(0L)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            AppForegroundPauseSettings.ACTION_SETTINGS_CHANGED -> {
                handleCompatibilitySettingsChanged(startId)
            }
            ACTION_STOP -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_RESTORE_REQUESTED, false).commit()
                stopForegroundAppMonitor()
                stopProtection(explicitStop = true)
            }
            ACTION_START -> {
                // Commit before starting asynchronously so an in-place package update or
                // process death cannot lose the user's explicit request to keep protection on.
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_RESTORE_REQUESTED, true).commit()
                if (!ensureForegroundServiceNotification(startId)) return START_NOT_STICKY
                val foregroundTarget = startForegroundAppMonitor()
                if (foregroundTarget != null) {
                    pauseProtectionForCompatibility(foregroundTarget)
                } else if (getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)) {
                    resumeProtectionAfterCompatibility()
                } else {
                    val generation = startCancellation.onStartRequested()
                    thread(name = "guardian-vpn-start") { startProtection(generation, startId) }
                }
            }
            ACTION_RELOAD_RULES -> requestRuleReload()
            ACTION_RECONFIGURE_EXCLUDED_APPS -> thread(name = "guardian-vpn-exclusion-reconfigure") {
                reconfigureExcludedApplications(startId)
            }
            ACTION_TEST_UPSTREAM -> thread(name = "guardian-upstream-test") {
                runUpstreamConnectivityTest(startId)
            }
            null -> {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val legacyActive = prefs.getBoolean(KEY_ACTIVE, false)
                val restoreRequested = if (prefs.contains(KEY_RESTORE_REQUESTED)) {
                    prefs.getBoolean(KEY_RESTORE_REQUESTED, false)
                } else null
                val shouldRestore = ProtectionState.shouldRestoreAfterServiceRestart(
                    persistedActive = legacyActive,
                    restoreRequested = restoreRequested
                )
                if (shouldRestore) {
                    if (!ensureForegroundServiceNotification(startId)) return START_NOT_STICKY
                    val foregroundTarget = startForegroundAppMonitor()
                    if (foregroundTarget != null) {
                        pauseProtectionForCompatibility(foregroundTarget)
                    } else if (prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)) {
                        resumeProtectionAfterCompatibility()
                    } else {
                        val generation = startCancellation.onStartRequested()
                        thread(name = "guardian-vpn-restore") { startProtection(generation, startId) }
                    }
                } else {
                    stopSelf(startId)
                }
            }
        }
        // If Android reclaims the process, recreate the service and TUN when
        // the last persisted state says the user had protection enabled.
        return START_STICKY
    }

    /**
     * Foreground promotion happens before the asynchronous VPN setup. Android can
     * reject it (for example, a background-start restriction or notification
     * permission/platform failure). Do not let that exception escape onStartCommand:
     * it otherwise leaves the durable restore flag set and causes a sticky restart
     * loop with no user-visible failure state.
     */
    private fun ensureForegroundServiceNotification(startId: Int): Boolean {
        return try {
            createNotificationChannel()
            startForeground(41, buildNotification())
            true
        } catch (error: Exception) {
            android.util.Log.e(
                "GuardianProtection",
                "Foreground service promotion failed: ${SafeFailureSummary.codeFor(error)}"
            )
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_RESTORE_REQUESTED, false)
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_RECOVERY_PENDING, false)
                .putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error))
                .commit()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (cleanupError: Exception) {
                android.util.Log.w(
                    "GuardianProtection",
                    "Could not remove failed foreground notification: ${SafeFailureSummary.codeFor(cleanupError)}"
                )
            }
            publishState(
                false,
                "보호 서비스를 Android 백그라운드 실행 상태로 등록하지 못했습니다 " +
                    "(${ProtectionDiagnostics.exceptionTypeChain(error)}): ${SafeFailureSummary.describe(error)}"
            )
            stopSelf(startId)
            false
        }
    }

    private fun handleCompatibilitySettingsChanged(startId: Int) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false)) {
            stopForegroundAppMonitor()
            stopSelf(startId)
            return
        }
        if (!ensureForegroundServiceNotification(startId)) return
        val foregroundTarget = startForegroundAppMonitor()
        if (foregroundTarget != null) {
            pauseProtectionForCompatibility(foregroundTarget)
        } else if (prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)) {
            resumeProtectionAfterCompatibility()
        }
    }

    private fun startForegroundAppMonitor(): String? {
        stopForegroundAppMonitor()
        val generation = compatibilityMonitorGeneration.get()
        return try {
            startWalletMonitorIfConfigured(generation)
        } catch (error: Exception) {
            // App-usage monitoring is optional. A package-manager/permission or
            // monitor initialization failure must never prevent the core VPN
            // protection request from reaching startProtection().
            android.util.Log.w(
                "GuardianProtection",
                "Could not initialize app compatibility monitor: ${SafeFailureSummary.codeFor(error)}"
            )
            compatibilityMonitorGeneration.incrementAndGet()
            val failedMonitor = foregroundAppMonitor
            foregroundAppMonitor = null
            try {
                failedMonitor?.stop()
            } catch (cleanupError: Exception) {
                android.util.Log.w(
                    "GuardianProtection",
                    "Could not stop partially initialized app monitor: ${SafeFailureSummary.codeFor(cleanupError)}"
                )
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                .putString(
                    AppForegroundPauseSettings.KEY_ERROR,
                    "자동 일시중지 기능을 시작하지 못해 껐습니다. ${SafeFailureSummary.describe(error)}"
                )
                .apply()
            sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(this.packageName))
            null
        }
    }

    private fun startWalletMonitorIfConfigured(generation: Long): String? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false) ||
            !prefs.getBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
        ) return null

        val selectedPackages = prefs.getStringSet(
            AppForegroundPauseSettings.KEY_PACKAGES, emptySet()
        ).orEmpty()
        val installedPackages = packageManager.getInstalledApplications(0)
            .mapTo(mutableSetOf()) { it.packageName }
        val availableTargets = selectedPackages.filterTo(mutableSetOf()) {
            it in installedPackages
        }
        if (availableTargets != selectedPackages) {
            prefs.edit().putStringSet(
                AppForegroundPauseSettings.KEY_PACKAGES, availableTargets
            ).apply()
        }
        if (availableTargets.isEmpty()) {
            prefs.edit()
                .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                .putString(AppForegroundPauseSettings.KEY_ERROR, "설치된 자동 일시중지 대상 앱이 없어 기능을 껐습니다.")
                .apply()
            return null
        }

        if (!ForegroundAppMonitor.hasUsageAccess(this)) {
            prefs.edit()
                .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                .putString(
                    AppForegroundPauseSettings.KEY_ERROR,
                    "사용 정보 접근 권한이 해제되어 자동 일시중지를 껐습니다."
                )
                .apply()
            sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(this.packageName))
            return null
        }

        val monitor = ForegroundAppMonitor(
            context = this,
            targetPackages = availableTargets,
            onForegroundTargetChanged = { packageName ->
                if (compatibilityMonitorGeneration.get() == generation) {
                    if (packageName != null) {
                        pauseProtectionForCompatibility(packageName)
                    } else {
                        resumeProtectionAfterCompatibility()
                    }
                }
            },
            onPermissionRevoked = {
                if (compatibilityMonitorGeneration.get() == generation) {
                    val currentPrefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                    currentPrefs.edit()
                        .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                        .putString(
                            AppForegroundPauseSettings.KEY_ERROR,
                            "사용 정보 접근 권한이 해제되어 자동 일시중지를 껐습니다."
                        )
                        .apply()
                    sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(this.packageName))
                    resumeProtectionAfterCompatibility()
                }
            }
        )
        foregroundAppMonitor = monitor
        return monitor.start()
    }

    private fun stopForegroundAppMonitor() {
        // Invalidate callbacks before detaching the monitor. Cleanup of this
        // optional feature must not abort an explicit VPN start/stop request.
        compatibilityMonitorGeneration.incrementAndGet()
        val monitor = foregroundAppMonitor
        foregroundAppMonitor = null
        try {
            monitor?.stop()
        } catch (error: Exception) {
            android.util.Log.w(
                "GuardianProtection",
                "Could not stop app compatibility monitor: ${SafeFailureSummary.codeFor(error)}"
            )
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(AppForegroundPauseSettings.KEY_ENABLED, false)
                .putString(
                    AppForegroundPauseSettings.KEY_ERROR,
                    "자동 일시중지 감시를 정상 종료하지 못했습니다. ${SafeFailureSummary.describe(error)}"
                )
                .apply()
            sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
        }
    }

    @Synchronized
    private fun pauseProtectionForCompatibility(packageName: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false)) return
        if (prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)) {
            prefs.edit().putString(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE, packageName).apply()
            return
        }

        startCancellation.cancel()
        pendingStart.set(null)
        var oldTun: ParcelFileDescriptor? = null
        tunnelSessionGuard.invalidate {
            running.set(false)
            tunnelActive = false
            oldTun = tun
            tun = null
        }
        try {
            oldTun?.close()
        } catch (error: Exception) {
            android.util.Log.w("GuardianProtection", "Could not close TUN for app compatibility pause: ${SafeFailureSummary.codeFor(error)}")
        }
        try {
            packetWorker?.join(5_000L)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            android.util.Log.w("GuardianProtection", "Interrupted while waiting for DNS worker to pause: ${SafeFailureSummary.codeFor(error)}")
        }

        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false)) return
        val stillSelected = prefs.getBoolean(AppForegroundPauseSettings.KEY_ENABLED, false) &&
            packageName in prefs.getStringSet(
                AppForegroundPauseSettings.KEY_PACKAGES, emptySet()
            ).orEmpty()
        if (!stillSelected) {
            prefs.edit()
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(AppForegroundPauseSettings.KEY_PAUSED, true)
                .putString(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE, packageName)
                .commit()
            resumeProtectionAfterCompatibility()
            return
        }

        val appLabel = try {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }
        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .putBoolean(KEY_RECOVERY_PENDING, false)
            .putBoolean(AppForegroundPauseSettings.KEY_PAUSED, true)
            .putString(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE, packageName)
            .putString(KEY_STATUS, "$appLabel 실행 중 · 기기 전체 VPN 보호가 일시 중지되었습니다. 앱을 닫으면 자동으로 다시 연결됩니다.")
            .commit()
        try {
            createNotificationChannel()
            startForeground(41, buildNotification())
        } catch (error: Exception) {
            android.util.Log.e("GuardianProtection", "Could not update compatibility pause notification: ${SafeFailureSummary.codeFor(error)}")
        }
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(this.packageName))
    }

    private fun resumeProtectionAfterCompatibility() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false) ||
            !prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
        ) return
        if (!compatibilityResumeInProgress.compareAndSet(false, true)) return

        thread(name = "guardian-compatibility-vpn-resume") {
            var retryResume = false
            try {
                val oldWorker = packetWorker
                try {
                    oldWorker?.join(5_000L)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@thread
                }
                if (oldWorker?.isAlive == true) {
                    android.util.Log.w("GuardianProtection", "DNS worker is still exiting; compatibility resume will retry")
                    retryResume = true
                    return@thread
                }
                val currentPrefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                if (!currentPrefs.getBoolean(KEY_RESTORE_REQUESTED, false) ||
                    !currentPrefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
                ) return@thread
                currentPrefs.edit()
                    .putBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
                    .remove(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE)
                    .commit()
                val generation = startCancellation.onStartRequested()
                startProtection(generation, lastStartId)
            } finally {
                compatibilityResumeInProgress.set(false)
                if (retryResume) {
                    android.os.Handler(mainLooper).postDelayed(
                        { resumeProtectionAfterCompatibility() },
                        1_000L
                    )
                }
            }
        }
    }

    /**
     * Per-app VPN exclusions are part of the Builder configuration and cannot be
     * changed on an established TUN. Rebuild the tunnel so a saved selection is
     * actually reflected by Android's VPN routing policy.
     */
    private fun reconfigureExcludedApplications(startId: Int) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false)) {
            publishState(false, "앱별 보호 제외 설정을 저장했습니다. 보호를 다시 시작하면 적용됩니다.")
            return
        }
        if (prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)) {
            publishState(false, "선택 앱 실행 중 VPN이 일시 중지되어 있습니다. 앱을 닫으면 저장된 제외 설정과 함께 자동 재연결됩니다.")
            return
        }
        startCancellation.cancel()
        pendingStart.set(null)
        var oldTun: ParcelFileDescriptor? = null
        tunnelSessionGuard.invalidate {
            running.set(false)
            tunnelActive = false
            oldTun = tun
            tun = null
        }
        try { oldTun?.close() } catch (error: Exception) {
            android.util.Log.w("GuardianProtection", "Could not close old TUN before exclusion refresh: ${SafeFailureSummary.codeFor(error)}")
        }
        // Wait for the previous blocking packet reader to exit before allowing a
        // new worker to set running=true; otherwise its finally block could stop
        // the newly-created tunnel after observing the shared flag.
        try { packetWorker?.join(5000L) } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            publishState(false, "앱별 보호 제외 재적용이 중단되었습니다. 다시 시도해 주세요.")
            return
        }
        if (packetWorker?.isAlive == true) {
            publishState(false, "이전 VPN 패킷 처리가 종료되지 않아 제외 설정을 재적용하지 못했습니다. 보호 상태를 확인해 주세요.")
            return
        }
        prefs.edit().putBoolean(KEY_ACTIVE, false).commit()
        val generation = startCancellation.onStartRequested()
        startProtection(generation, startId)
    }

    private fun startProtection(requestGeneration: Long, startId: Int) {
        if (!startCancellation.isCurrent(requestGeneration)) return
        // The session guard serializes this check with packet-loop teardown.
        // A stale running flag from a terminating worker must not drop a new tap.
        if (tunnelSessionGuard.shouldIgnoreStart(running.get())) return
        if (!starting.compareAndSet(false, true)) {
            if (startCancellation.isCurrent(requestGeneration)) {
                pendingStart.set(PendingStart(requestGeneration, startId))
            }
            return
        }
        try {
            try {
                startProtectionInternal(requestGeneration, startId)
            } catch (error: Exception) {
                reportUnexpectedStartupFailure(requestGeneration, startId, error)
            } catch (error: LinkageError) {
                // Native/runtime library loading failures are Errors, not Exceptions.
                // Surface them instead of leaving the foreground service apparently starting.
                reportUnexpectedStartupFailure(requestGeneration, startId, error)
            }
        } finally {
            starting.set(false)
            // Keep only the latest still-current request. An older worker can
            // never be revived by a later start after an intervening stop.
            val pending = pendingStart.getAndSet(null)
            if (pending != null && !running.get() && startCancellation.isCurrent(pending.generation)) {
                thread(name = "guardian-vpn-start-pending") {
                    startProtection(pending.generation, pending.startId)
                }
            }
        }
    }

    private fun reportUnexpectedStartupFailure(
        requestGeneration: Long,
        startId: Int,
        error: Throwable
    ) {
        android.util.Log.e("GuardianProtection", "Unexpected VPN startup failure: ${SafeFailureSummary.codeFor(error)}")
        // Serialize the whole failure publication/cleanup with start requests.
        // A newer generation arriving between a check and tun.close() must not
        // let this stale worker close the newer session's descriptor.
        startCancellation.tryCommitStart(requestGeneration) {
            if (running.get()) return@tryCommitStart
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error))
                .commit()
            tunnelActive = false
            try { tun?.close() } catch (closeError: Exception) {
                android.util.Log.w("GuardianProtection", "Failed to close partial VPN tunnel: ${SafeFailureSummary.codeFor(closeError)}")
            }
            tun = null
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (stopError: Exception) {
                android.util.Log.w("GuardianProtection", "Failed to remove startup notification: ${SafeFailureSummary.codeFor(stopError)}")
            }
            val detail = SafeFailureSummary.describe(error)
            publishState(
                false,
                "보호 시작 중 예기치 않은 오류가 발생했습니다 (${ProtectionDiagnostics.exceptionTypeChain(error)}): $detail"
            )
            stopSelf(startId)
        }
    }
    private fun startProtectionInternal(requestGeneration: Long, startId: Int) {
        tunnelActive = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_FILTER_READINESS)
            .apply()
        try {
            createNotificationChannel()
            startForeground(41, buildNotification())
        } catch (e: Exception) {
            reportStartupFailureIfCurrent(
                requestGeneration, startId, e,
                "포그라운드 서비스 시작 실패 (${ProtectionDiagnostics.exceptionTypeChain(e)}): ${SafeFailureSummary.describe(e)}"
            )
            return
        }
        try {
            // Parsing a large downloaded filter list must not block the service main thread.
            loadConfiguredRules()
        } catch (e: Exception) {
            reportStartupFailureIfCurrent(
                requestGeneration, startId, e,
                "필터 규칙 로드 실패 (${ProtectionDiagnostics.exceptionTypeChain(e)}): ${SafeFailureSummary.describe(e)}"
            )
            return
        }
        if (!startCancellation.isCurrent(requestGeneration)) {
            // A newer start, explicit stop, or compatibility transition owns
            // foreground/service cleanup. This stale worker must not touch it.
            return
        }

        // Never install a DNS-only VPN route until its upstream resolver has
        // answered a real DNS query. If every configured/fallback resolver is
        // unreachable, leaving the device's normal network untouched is safer
        // than activating a tunnel that makes every website appear offline.
        val preflightQuery = DnsUpstreamProbe.query()
        val preflightResponse = forwardDns(preflightQuery, requireSuccessfulResolution = true)
        if (!startCancellation.isCurrent(requestGeneration)) {
            // A newer start, explicit stop, or compatibility transition owns
            // foreground/service cleanup. This stale worker must not touch it.
            return
        }
        if (!DnsUpstreamProbe.isSuccessfulResolution(preflightQuery, preflightResponse)) {
            val detail = lastUpstreamFailure ?: "업스트림 DNS 응답 검증 실패"
            reportStartupFailureIfCurrent(
                requestGeneration, startId, null,
                "업스트림 DNS 연결을 확인하지 못해 보호를 시작하지 않았습니다. 일반 인터넷 연결은 변경하지 않았습니다. ($detail)",
                diagnostic = "보호 시작 전 DNS 연결 확인 실패: $detail"
            )
            return
        }

        var establishError: Exception? = null
        val descriptor = try {
            val builder = Builder()
                .setSession("Guardian AdBlock")
                .setMtu(1500)
                .addAddress("10.13.37.1", 32)
                .addAddress("fd13:37::1", 128)
                .addDnsServer(VIRTUAL_DNS)
                .addDnsServer(VIRTUAL_DNS_V6)
                .addRoute(VIRTUAL_DNS, 32)
                .addRoute(VIRTUAL_DNS_V6, 128)
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            if (prefs.getBoolean(KEY_DIRECT_DNS_CAPTURE, DEFAULT_DIRECT_DNS_CAPTURE)) {
                // Capture direct DNS requests to common public resolvers. Routes
                // are IP-based, not port-based, so this is an advanced opt-in
                // behavior: non-DNS traffic to these IPs can also enter the TUN.
                KNOWN_PUBLIC_DNS_IPV4.forEach { builder.addRoute(it, 32) }
                KNOWN_PUBLIC_DNS_IPV6.forEach { builder.addRoute(it, 128) }
                val selectedResolver = configuredUpstreamDns()
                val selectedResolverPrefix = if (selectedResolver.contains(":")) 128 else 32
                val resolverAlreadyRouted = if (selectedResolverPrefix == 128) {
                    selectedResolver in KNOWN_PUBLIC_DNS_IPV6
                } else selectedResolver in KNOWN_PUBLIC_DNS_IPV4
                if (!resolverAlreadyRouted) builder.addRoute(selectedResolver, selectedResolverPrefix)
            }
            val requestedExclusions = prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet()).orEmpty()
            val appliedExclusions = linkedSetOf<String>()
            val staleExclusions = linkedSetOf<String>()
            requestedExclusions.sorted().forEach { excludedPackage ->
                // Android does not permit a VPN to exclude its own package. Treat
                // legacy/corrupt saved entries as unapplied instead of failing the
                // entire VPN tunnel for one invalid exclusion.
                if (excludedPackage == packageName) {
                    staleExclusions.add(excludedPackage)
                    return@forEach
                }
                try {
                    packageManager.getApplicationInfo(
                        excludedPackage,
                        android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
                    )
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    staleExclusions.add(excludedPackage)
                    return@forEach
                }
                try {
                    builder.addDisallowedApplication(excludedPackage)
                    appliedExclusions.add(excludedPackage)
                } catch (error: Exception) {
                    // A single OEM/package-policy rejection must not prevent the
                    // remaining valid exclusions and DNS protection from starting.
                    staleExclusions.add(excludedPackage)
                    android.util.Log.w(
                        "GuardianProtection",
                        "Android rejected a VPN app exclusion: ${SafeFailureSummary.codeFor(error)}"
                    )
                }
            }
            val established = builder.setBlocking(true).establish()
            if (established != null) {
                prefs.edit()
                    .putStringSet(KEY_EXCLUDED_APPS_APPLIED, appliedExclusions)
                    .putStringSet(KEY_EXCLUDED_APPS_ERROR, staleExclusions)
                    .commit()
            }
            established
        } catch (e: Exception) {
            establishError = e
            null
        }
        if (descriptor == null) {
            val detail = establishError?.let { " (${ProtectionDiagnostics.exceptionTypeChain(it)}: ${SafeFailureSummary.describe(it)})" }.orEmpty()
            reportStartupFailureIfCurrent(
                requestGeneration, startId, establishError,
                "VPN 연결 생성 실패$detail. VPN 권한과 다른 VPN 앱의 실행 여부를 확인하세요."
            )
            return
        }
        val committed = startCancellation.tryCommitStart(requestGeneration) {
            tunnelSessionGuard.activate(requestGeneration) {
                tun = descriptor
                running.set(true)
                tunnelActive = true
            }
            try {
                val filterNotice = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(KEY_FILTER_READINESS, null)
                    ?.takeIf { it.isNotBlank() && it != "모든 선택 필터가 준비되었습니다." }
                val activeMessage = if (filterNotice == null) {
                    "DNS 보호가 실행 중입니다. IPv4/IPv6 DNS 광고·추적 도메인 질의를 필터링합니다."
                } else {
                    "DNS 보호가 실행 중입니다. $filterNotice"
                }
                publishState(true, activeMessage)
                packetWorker = thread(name = "guardian-vpn-dns") {
                    packetLoop(descriptor, requestGeneration, startId)
                }
            } catch (error: Exception) {
                rollbackCommittedTunnel(requestGeneration, descriptor)
                throw error
            } catch (error: LinkageError) {
                rollbackCommittedTunnel(requestGeneration, descriptor)
                throw error
            }
        }
        if (!committed) {
            // A rejected descriptor belongs only to this stale start request.
            // A newer start (or an explicit stop) owns foreground/service cleanup;
            // touching either here could remove the newer request's notification.
            try { descriptor.close() } catch (_: Exception) { }
        }
    }

    /**
     * If publishing the active state or starting the packet worker fails after
     * the TUN was committed, roll back that exact generation before surfacing
     * the startup error. Otherwise the service can report a live tunnel with no
     * packet reader attached.
     */
    /**
     * Publishes startup failure only while this request still owns the start
     * generation. An old preflight/establish operation must not clear a newer
     * start's foreground notification or overwrite its active UI state.
     */
    private fun reportStartupFailureIfCurrent(
        requestGeneration: Long,
        startId: Int,
        error: Throwable?,
        message: String,
        diagnostic: String? = null
    ) {
        startCancellation.tryCommitStart(requestGeneration) {
            val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            if (error != null) {
                editor.putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error))
            }
            if (diagnostic != null) editor.putString(KEY_DNS_DIAGNOSTIC, diagnostic)
            editor.commit()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            publishState(false, message)
            stopSelf(startId)
        }
    }

    private fun rollbackCommittedTunnel(
        requestGeneration: Long,
        descriptor: ParcelFileDescriptor
    ) {
        tunnelSessionGuard.release(requestGeneration) {
            running.set(false)
            tunnelActive = false
            if (tun === descriptor) tun = null
        }
        try { descriptor.close() } catch (_: Exception) { }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor, requestGeneration: Long, startId: Int) {
        var loopFailure: Throwable? = null
        val retransmissionFailure = AtomicReference<Throwable?>(null)
        var retransmissionScheduler: ScheduledExecutorService? = null
        try {
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(32767)
            val tcpDns = DnsTcpSessionManager()
            retransmissionScheduler = Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "GuardianDnsTcpRto").apply { isDaemon = true }
            }
            retransmissionScheduler!!.scheduleAtFixedRate({
                if (!running.get() || retransmissionFailure.get() != null) return@scheduleAtFixedRate
                try {
                    val due = tcpDns.retransmitDue()
                    if (due.isNotEmpty()) {
                        synchronized(output) {
                            due.forEach { packet -> output.write(packet) }
                            output.flush()
                        }
                    }
                } catch (error: Exception) {
                    retransmissionFailure.compareAndSet(null, error)
                    try { descriptor.close() } catch (_: Exception) { }
                } catch (error: LinkageError) {
                    retransmissionFailure.compareAndSet(null, error)
                    try { descriptor.close() } catch (_: Exception) { }
                }
            }, 250L, 250L, TimeUnit.MILLISECONDS)
            while (running.get()) {
                try {
                    val length = input.read(buffer)
                    if (length < 0) break
                    if (length == 0) continue

                    val tcpReplies = tcpDns.handle(buffer, length) { query ->
                        val result = DnsQueryPolicy.resolve(query, ::forwardDns)
                        if (result == null) {
                            recordIgnoredPacket("TCP DNS 질의 형식 또는 질문 영역 검증 실패")
                            null
                        } else {
                            recordDnsActivity(result.domain, result.blocked, result.forwarded)
                            result.response
                        }
                    }
                    if (tcpReplies.isNotEmpty()) {
                        synchronized(output) {
                            tcpReplies.forEach { output.write(it) }
                            output.flush()
                        }
                        continue
                    }

                    val ipv4 = Ipv4Udp.parseDns(buffer, length)
                    val ipv6 = if (ipv4 == null) Ipv6Udp.parseDns(buffer, length) else null
                    val payload = ipv4?.payload ?: ipv6?.payload
                    if (payload == null) {
                        // Ignore non-DNS traffic quietly; only count malformed UDP DNS
                        // packets when their IP protocol explicitly identifies UDP.
                        continue
                    }
                    val result = DnsQueryPolicy.resolve(payload, ::forwardDns)
                    if (result == null) {
                        recordIgnoredPacket("IPv4/IPv6 UDP DNS 질의 형식 또는 질문 영역 검증 실패")
                        continue
                    }
                    // Keep UDP DNS replies within the IPv6 minimum-MTU safe payload.
                    // Oversized upstream answers are returned as TC responses instead
                    // of emitting an IP packet that exceeds the VPN interface MTU.
                    val boundedDnsResponse = if (result.response.size > MAX_UDP_DNS_RESPONSE_BYTES) {
                        DomainFilter.makeTruncatedResponse(payload) ?: continue
                    } else result.response
                    recordDnsActivity(result.domain, result.blocked, result.forwarded)
                    val responsePacket = if (ipv4 != null) Ipv4Udp.response(ipv4, boundedDnsResponse)
                        else Ipv6Udp.response(ipv6!!, boundedDnsResponse)
                    synchronized(output) {
                        output.write(responsePacket)
                        output.flush()
                    }
                } catch (e: Exception) {
                    if (!running.get()) break
                    loopFailure = e
                    break
                } catch (e: LinkageError) {
                    if (!running.get()) break
                    loopFailure = e
                    break
                }
            }
        } catch (e: Exception) {
            if (running.get()) loopFailure = e
        } catch (e: LinkageError) {
            if (running.get()) loopFailure = e
        } finally {
            retransmissionScheduler?.shutdownNow()
            try {
                retransmissionScheduler?.awaitTermination(500L, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            loopFailure = retransmissionFailure.get() ?: loopFailure
            // A dead packet loop must never leave the UI claiming protection is active.
            tunnelSessionGuard.release(requestGeneration) {
                if (running.compareAndSet(true, false)) {
                    tunnelActive = false
                    try { descriptor.close() } catch (_: Exception) { }
                    if (tun === descriptor) tun = null
                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                    val detail = loopFailure?.let { " (${ProtectionDiagnostics.exceptionTypeChain(it)}: ${SafeFailureSummary.describe(it)})" }.orEmpty()
                    publishState(false, "DNS 패킷 처리 중단$detail. 보호를 다시 시작해 주세요.")
                    // Do not stop a newer ACTION_START request that arrived while
                    // this generation was unwinding.
                    stopSelf(startId)
                }
            }
        }
    }

    private fun recordIgnoredPacket(reason: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        synchronized(DNS_STATS_LOCK) {
            prefs.edit()
                .putLong(KEY_DNS_IGNORED, DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_IGNORED, 0L)))
                .putString(KEY_DNS_DIAGNOSTIC, reason)
                .apply()
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val previousBroadcast = lastStatsBroadcastAt.get()
        if (now - previousBroadcast >= DNS_STATS_BROADCAST_INTERVAL_MS &&
            lastStatsBroadcastAt.compareAndSet(previousBroadcast, now)) {
            sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
        }
    }

    private fun recordDnsActivity(domain: String, blocked: Boolean, forwarded: Boolean) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        synchronized(DNS_STATS_LOCK) {
            val queries = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_QUERIES, 0L))
            val blockedCount = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_BLOCKED, 0L), if (blocked) 1L else 0L)
            val forwardedCount = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_FORWARDED, 0L), if (forwarded) 1L else 0L)
            val failedCount = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_FAILED, 0L), if (!blocked && !forwarded) 1L else 0L)
            val category = if (blocked) DomainFilter.classifyBlockedDomain(domain) else null
            val adBlocked = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_AD_BLOCKED, 0L),
                if (category == DomainFilter.BlockCategory.AD) 1L else 0L)
            val trackerBlocked = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_TRACKER_BLOCKED, 0L),
                if (category == DomainFilter.BlockCategory.TRACKER) 1L else 0L)
            val otherBlocked = DnsStatsCounterPolicy.add(prefs.getLong(KEY_DNS_OTHER_BLOCKED, 0L),
                if (category == DomainFilter.BlockCategory.OTHER) 1L else 0L)
            val outcome = when {
                blocked -> "차단"
                forwarded -> "전달"
                else -> "DNS 응답 실패"
            }
            val previous = prefs.getString(KEY_DNS_RECENT, "").orEmpty()
            val lines = (listOf("$outcome · $domain") + previous.lineSequence().filter { it.isNotBlank() }.toList()).take(8)
            prefs.edit()
                .putLong(KEY_DNS_QUERIES, queries)
                .putLong(KEY_DNS_BLOCKED, blockedCount)
                .putLong(KEY_DNS_FORWARDED, forwardedCount)
                .putLong(KEY_DNS_FAILED, failedCount)
                .putLong(KEY_DNS_AD_BLOCKED, adBlocked)
                .putLong(KEY_DNS_TRACKER_BLOCKED, trackerBlocked)
                .putLong(KEY_DNS_OTHER_BLOCKED, otherBlocked)
                .putString(KEY_DNS_RECENT, lines.joinToString("\n"))
                .apply()
            if (!blocked && !forwarded) {
                prefs.edit().putString(
                    KEY_DNS_DIAGNOSTIC,
                    "업스트림 DNS 오류: " + (lastUpstreamFailure ?: "응답 시간 초과, 연결 실패 또는 DNS 응답 검증 실패")
                ).apply()
            }
            // The current DNS event has consumed its diagnostic. Remove the
            // ThreadLocal value so a long-lived packet worker retains no stale
            // failure detail between packets.
            lastUpstreamFailure = null
        }
        // Persist every query, but coalesce UI refresh broadcasts to avoid flooding
        // the main thread when an app generates many DNS requests in a short burst.
        val now = android.os.SystemClock.elapsedRealtime()
        val previousBroadcast = lastStatsBroadcastAt.get()
        if (now - previousBroadcast >= DNS_STATS_BROADCAST_INTERVAL_MS &&
            lastStatsBroadcastAt.compareAndSet(previousBroadcast, now)) {
            sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
        }
    }

    @Synchronized
    private fun loadConfiguredRules() {
        // Startup and user-triggered reloads may run on separate worker threads.
        // Serialize the complete read/parse/swap operation so an older, slower
        // load cannot publish over a newer rule snapshot or interleave diagnostics.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val selectedProfile = FilterListManager.selectedProfile(this)
        val cachedProfile = FilterListManager.cachedProfile(this)
        // The bundled rules are the minimum safe startup set. Downloaded feeds are
        // optional enhancements: missing, stale or unreadable caches must not prevent
        // the VPN tunnel from starting with the bundled baseline.
        val sources = mutableListOf<() -> FilterRuleParser.Parsed>()
        val minimumRuleCounts = mutableListOf<Int>()
        val unavailable = mutableListOf<String>()
        sources.add {
            val bundled = try {
                assets.open("filter_rules.txt").bufferedReader().use(FilterRuleParser::parse)
            } catch (error: Exception) {
                // Keep VPN startup available even if a packaged APK is missing its
                // shared baseline asset. This is a degraded emergency list, not a
                // substitute for repairing the APK asset packaging.
                unavailable.add("기본 필터 라이브러리 누락 (${SafeFailureSummary.codeFor(error)})")
                prefs.edit().putString(
                    KEY_STARTUP_TRACE,
                    ProtectionDiagnostics.exceptionTraceSummary(error)
                ).apply()
                android.util.Log.e(
                    "Guardian",
                    "Bundled filter asset unavailable; using emergency rules: ${SafeFailureSummary.codeFor(error)}"
                )
                null
            }
            if (bundled != null && FilterStartupGate.hasUsableBundledBaseline(
                    bundled.blocked.size,
                    bundled.importantBlocked.size
                )
            ) {
                bundled
            } else {
                if (bundled != null) {
                    unavailable.add("기본 필터 라이브러리 손상 — 내장 긴급 규칙")
                    prefs.edit().putString(
                        KEY_STARTUP_TRACE,
                        "filter_rules.txt was present but contained no usable DNS block rules"
                    ).apply()
                    android.util.Log.e(
                        "Guardian",
                        "Bundled filter asset contains no usable block rules; using emergency rules"
                    )
                }
                FilterRuleParser.parse(DomainFilter.bootstrapRuleLines())
            }
        }
        minimumRuleCounts.add(1)

        fun addOptionalCache(
            label: String,
            cacheName: String,
            minimumRules: Int,
            cacheReady: Boolean
        ) {
            if (!cacheReady) {
                unavailable.add(label)
                return
            }
            // Keep parsing lazy to avoid holding multiple large community feeds in
            // memory. If a cache disappears or becomes unreadable between readiness
            // check and parse, skip only that optional source rather than aborting VPN.
            sources.add {
                FilterStartupGate.loadOptionalRules(
                    minimumRules = minimumRules,
                    load = { FilterListManager.parseCachedList(this, cacheName) },
                    onUnavailable = { error ->
                        unavailable.add(label)
                        android.util.Log.w("Guardian", "Optional filter cache skipped: $label (${SafeFailureSummary.codeFor(error)})")
                    }
                )
            }
            // Zero is intentional: this source is optional at runtime. The
            // baseline source above retains its mandatory one-rule validation.
            minimumRuleCounts.add(0)
        }

        val mainCacheReady = FilterStartupGate.isOptionalCacheReady {
            FilterListManager.isCacheValid(
                FilterListManager.hasCachedFile(this),
                FilterListManager.cachedBlockCount(this),
                selectedProfile.minimumRules
            ) && selectedProfile.key == cachedProfile.key
        }
        addOptionalCache(
            "광고·추적",
            "adguard_dns_filter.txt",
            selectedProfile.minimumRules,
            mainCacheReady
        )

        val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        if (securityEnabled) {
            val securityReady = FilterStartupGate.isOptionalCacheReady {
                FilterListManager.hasCachedSecurityList(this)
            }
            addOptionalCache(
                "보안",
                "hagezi_threat_intelligence.txt",
                FilterListManager.MIN_SECURITY_BLOCKS,
                securityReady
            )
        }

        val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
        if (androidAdsEnabled) {
            val androidAdsReady = FilterStartupGate.isOptionalCacheReady {
                FilterListManager.hasCachedAndroidAdsList(this)
            }
            addOptionalCache(
                "Android 앱 광고",
                "awavenue_android_ads.txt",
                FilterListManager.MIN_ANDROID_ADS_BLOCKS,
                androidAdsReady
            )
        }

        val userBlock = prefs.getString(KEY_USER_BLOCK, "").orEmpty().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val userAllow = prefs.getString(KEY_USER_ALLOW, "").orEmpty().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.toList()
        android.util.Log.i(
            "Guardian",
            "VPN rules load started; sources=${sources.size}, security=$securityEnabled, androidAds=$androidAdsEnabled"
        )
        DomainFilter.loadParsedRuleSources(sources, minimumRuleCounts, userBlock, userAllow)
        val blockedCount = DomainFilter.blockedRuleCount()
        android.util.Log.i(
            "Guardian",
            "VPN rules load completed; blocked=$blockedCount, allowed=${DomainFilter.allowedRuleCount()}, " +
                "missingOptional=${unavailable.joinToString()}, freeHeap=${Runtime.getRuntime().freeMemory()}"
        )
        prefs.edit().putString(
            KEY_FILTER_READINESS,
            if (unavailable.isEmpty()) "모든 선택 필터가 준비되었습니다."
            else "기본 보호 규칙으로 시작했습니다. 필터 업데이트 필요: ${unavailable.joinToString()}"
        ).apply()
    }

    private fun requestRuleReload() {
        if (!ruleReloadGate.request()) return
        thread(name = "guardian-vpn-rule-reload") {
            var completedNormally = false
            try {
                while (running.get()) {
                    reloadUserRules()
                    val anotherPassRequested = ruleReloadGate.completePass()
                    if (!anotherPassRequested) {
                        completedNormally = true
                        break
                    }
                }
            } finally {
                // Do not release a gate already handed to a newer worker after a
                // normal completion. Unexpected exits release ownership and retain
                // at most one coalesced follow-up request.
                if (!completedNormally && ruleReloadGate.releaseAfterFailure(running.get())) {
                    requestRuleReload()
                }
            }
        }
    }

    private fun reloadUserRules() {
        if (!running.get()) return
        try {
            loadConfiguredRules()
            val filterReadiness = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_FILTER_READINESS, null)
            val status = when {
                filterReadiness.isNullOrBlank() -> "DNS 보호 실행 중 · 사용자 규칙을 적용했습니다."
                filterReadiness == "모든 선택 필터가 준비되었습니다." ->
                    "DNS 보호 실행 중 · 사용자 규칙을 적용했고 모든 선택 필터가 준비되었습니다."
                else -> "DNS 보호 실행 중 · 사용자 규칙을 적용했습니다. $filterReadiness"
            }
            publishState(true, status)
        } catch (error: Exception) {
            // Keep the previously loaded in-memory rules if the replacement is invalid.
            android.util.Log.w(
                "GuardianProtection",
                "Could not reload DNS filter rules: ${SafeFailureSummary.codeFor(error)}"
            )
            publishState(true, "DNS 보호 실행 중 · 새 규칙 적용에 실패해 기존 규칙을 유지합니다.")
        } catch (error: LinkageError) {
            // Optional parser/runtime linkage failures must not terminate the
            // rule-reload worker or disturb the already active VPN session.
            android.util.Log.e(
                "GuardianProtection",
                "DNS filter rule reload linkage failure: ${SafeFailureSummary.codeFor(error)}"
            )
            publishState(true, "DNS 보호 실행 중 · 필터 구성요소 오류로 새 규칙을 적용하지 못해 기존 규칙을 유지합니다.")
        }
    }

    private fun publishState(active: Boolean, message: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (active && (!running.get() ||
                prefs.getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false))
        ) {
            return
        }
        val update = prefs.edit()
            .putBoolean(KEY_ACTIVE, active)
            .putBoolean(KEY_RECOVERY_PENDING, false)
            .putString(KEY_STATUS, message)
        if (active) update.remove(KEY_STARTUP_TRACE)
        update.apply()
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    private fun configuredUpstreamDns(): String =
        DnsAddressValidator.normalize(
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_UPSTREAM_DNS, DEFAULT_UPSTREAM_DNS).orEmpty()
        ) ?: DEFAULT_UPSTREAM_DNS

    private fun configuredUpstreamTransport(): String {
        val value = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_UPSTREAM_TRANSPORT, DEFAULT_UPSTREAM_TRANSPORT)
        return value?.takeIf { it == "udp" || it == "tcp" || it == "dot" || it == "doh" } ?: DEFAULT_UPSTREAM_TRANSPORT
    }

    private fun configuredUpstreamTlsHost(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_UPSTREAM_TLS_HOST, DEFAULT_UPSTREAM_TLS_HOST)
            ?: DEFAULT_UPSTREAM_TLS_HOST

    private fun systemDnsServers(): List<String> {
        return try {
            val manager = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
            val networks = buildList {
                manager.activeNetwork?.let(::add)
                addAll(manager.allNetworks)
            }.distinct()
            networks.asSequence()
                .filter { network ->
                    val capabilities = manager.getNetworkCapabilities(network) ?: return@filter false
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
                .flatMap { network ->
                    manager.getLinkProperties(network)?.dnsServers.orEmpty().asSequence()
                }
                .mapNotNull { address -> DnsAddressValidator.normalize(address.hostAddress.orEmpty()) }
                .distinct()
                .take(2)
                .toList()
        } catch (error: Exception) {
            // Device DNS discovery is an optional fallback. A permission or
            // transient connectivity error must not abort the DNS packet worker.
            android.util.Log.w("GuardianProtection", "Could not inspect physical DNS servers: ${SafeFailureSummary.codeFor(error)}")
            emptyList()
        }
    }

    private fun forwardDns(query: ByteArray, requireSuccessfulResolution: Boolean = false): ByteArray? {
        lastUpstreamFailure = null
        val configured = configuredUpstreamDns()
        val transport = configuredUpstreamTransport()
        val candidates = when (transport) {
            "dot", "doh" -> {
                // A TLS resolver address may only be substituted when it belongs
                // to the configured Cloudflare TLS hostname.
                if (configuredUpstreamTlsHost().equals("cloudflare-dns.com", ignoreCase = true)) {
                    listOf(configured, "1.1.1.1", "1.0.0.1").distinct()
                } else listOf(configured)
            }
            "tcp" -> listOf(configured, "1.1.1.1", "8.8.8.8").distinct()
            else -> emptyList()
        }
        val attempts = if (transport == "udp") {
            DnsUpstreamCandidatePolicy.udpAttempts(
                configured = configured,
                systemDns = systemDnsServers(),
                publicFallback = listOf("1.1.1.1", "8.8.8.8"),
                virtualDns = setOf(VIRTUAL_DNS, VIRTUAL_DNS_V6)
            )
        } else {
            candidates.flatMap { address ->
                DnsUpstreamCandidatePolicy.transports(transport).map { selected ->
                    DnsUpstreamCandidatePolicy.Attempt(address, selected)
                }
            }
        }
        val candidateFailures = mutableListOf<String>()
        for ((index, attempt) in attempts.withIndex()) {
            // Each fallback attempt gets an isolated diagnostic so a failure
            // from an earlier resolver is never attributed to this one.
            lastUpstreamFailure = null
            val response = when (attempt.transport) {
                "tcp" -> forwardDnsTcp(query, attempt.address)
                "dot" -> forwardDnsOverTls(query, attempt.address)
                "doh" -> forwardDnsOverHttps(query, attempt.address)
                else -> forwardDnsUdp(query, attempt.address)
            }
            if (response != null && DomainFilter.isMatchingResponse(query, response)) {
                val usable = if (requireSuccessfulResolution) {
                    DnsUpstreamProbe.isSuccessfulResolution(query, response)
                } else {
                    !DnsUpstreamProbe.isRetryableResolverError(response)
                }
                if (usable) return response
                lastUpstreamFailure = attempt.transport.uppercase() +
                    ": resolver returned an unusable response (RCODE=" +
                    DnsUpstreamProbe.responseCode(response) + ")"
            }
            var detail = lastUpstreamFailure
                ?: (attempt.transport.uppercase() + ": DNS 응답 없음 또는 검증 실패")
            if (attempt.transport == "udp" &&
                lastUpstreamFailure?.startsWith("UDP: 잘린 응답") == true
            ) {
                val following = attempts.getOrNull(index + 1)
                val alreadyScheduledTcp = following != null &&
                    following.address == attempt.address && following.transport == "tcp"
                if (DnsUpstreamCandidatePolicy.needsTcpRetryAfterUdpTruncation(alreadyScheduledTcp)) {
                    // DNS requires TCP when a resolver sets TC. The configured
                    // resolver already has a queued TCP attempt; apply the same
                    // protocol fallback to system/public candidates as well.
                    lastUpstreamFailure = null
                    val tcpResponse = forwardDnsTcp(query, attempt.address)
                    if (tcpResponse != null && DomainFilter.isMatchingResponse(query, tcpResponse)) {
                        val tcpUsable = if (requireSuccessfulResolution) {
                            DnsUpstreamProbe.isSuccessfulResolution(query, tcpResponse)
                        } else {
                            !DnsUpstreamProbe.isRetryableResolverError(tcpResponse)
                        }
                        if (tcpUsable) return tcpResponse
                        lastUpstreamFailure = "TCP: resolver returned an unusable response (RCODE=" +
                            DnsUpstreamProbe.responseCode(tcpResponse) + ")"
                    }
                    val tcpDetail = lastUpstreamFailure ?: "TCP: DNS 응답 없음 또는 검증 실패"
                    detail += "; TCP 재시도: $tcpDetail"
                }
            }
            candidateFailures += "${attempt.transport.uppercase()} 후보 ${index + 1} (${attempt.address}): $detail"
        }
        lastUpstreamFailure = DnsUpstreamFailurePolicy.summarize(candidateFailures)
        return null
    }

    private fun forwardDnsUdp(query: ByteArray, upstreamAddress: String): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) {
                lastUpstreamFailure = "UDP: VPN 소켓 우회 설정 실패"
                return null
            }
            socket.soTimeout = 3500
            val address = InetAddress.getByName(upstreamAddress)
            socket.send(DatagramPacket(query, query.size, address, 53))
            // DNS UDP payloads may use the full 16-bit DNS message length.
            // Do not silently truncate large upstream replies at an arbitrary 4 KiB cap.
            val response = ByteArray(MAX_DNS_MESSAGE_BYTES)
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)
            if (packet.address != address || packet.port != 53) {
                lastUpstreamFailure = "UDP: 예상하지 않은 DNS 응답 주소 또는 포트"
                return null
            }
            val received = response.copyOf(packet.length)
            if (received.size >= 4 && (received[2].toInt() and 0x02) != 0) {
                // The caller owns the ordered TCP fallback; do not open a second
                // TCP socket here and repeat the same resolver attempt on failure.
                lastUpstreamFailure = "UDP: 잘린 응답으로 TCP/53 재시도가 필요합니다"
                null
            } else if (DomainFilter.isMatchingResponse(query, received)) received else {
                lastUpstreamFailure = "UDP: DNS 트랜잭션 ID 또는 질문이 일치하지 않는 응답"
                null
            }
        } catch (error: Exception) {
            lastUpstreamFailure = "UDP: ${SafeFailureSummary.describe(error)}"
            null
        } finally {
            socket?.close()
        }
    }

    private fun forwardDnsTcp(query: ByteArray, upstreamAddress: String): ByteArray? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            if (!protect(socket)) {
                lastUpstreamFailure = "TCP: VPN 소켓 우회 설정 실패"
                return null
            }
            socket.connect(InetSocketAddress(upstreamAddress, 53), 3500)
            socket.soTimeout = 3500
            val output = DataOutputStream(socket.getOutputStream())
            if (query.size !in 17..65535) {
                lastUpstreamFailure = "TCP: DNS 질의 길이 검증 실패"
                return null
            }
            output.writeShort(query.size)
            output.write(query)
            output.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            if (length !in 17..65535) {
                lastUpstreamFailure = "TCP: DNS 응답 길이가 유효하지 않습니다"
                return null
            }
            val answer = ByteArray(length)
            input.readFully(answer)
            if (DomainFilter.isMatchingResponse(query, answer)) answer else {
                lastUpstreamFailure = "TCP: DNS 응답 검증 실패"
                null
            }
        } catch (error: Exception) {
            lastUpstreamFailure = "TCP: ${SafeFailureSummary.describe(error)}"
            null
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    private fun runUpstreamConnectivityTest(startId: Int) {
        val query = DnsUpstreamProbe.query()
        val transport = configuredUpstreamTransport()
        val startedAt = System.nanoTime()
        val response = forwardDns(query)
        val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val success = DnsUpstreamProbe.isSuccessfulResolution(query, response)
        val testedAt = System.currentTimeMillis()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_UPSTREAM_TEST_SUCCESS, success)
            .putLong(KEY_UPSTREAM_TEST_LATENCY_MS, latencyMs)
            .putString(KEY_UPSTREAM_TEST_TRANSPORT, transport)
            .putLong(KEY_UPSTREAM_TEST_AT, testedAt)
            .putString(
                KEY_DNS_DIAGNOSTIC,
                if (success) "업스트림 DNS 연결 테스트 성공 (" + transport + ", " + latencyMs + "ms)"
                else "업스트림 DNS 연결 테스트 실패: " + (lastUpstreamFailure ?: "응답 검증 실패 또는 원인 미확인")
            )
            .apply()
        sendBroadcast(Intent(ACTION_UPSTREAM_TEST_RESULT).apply {
            setPackage(packageName)
            putExtra("success", success)
            putExtra("latency_ms", latencyMs)
            putExtra("transport", transport)
            putExtra("tested_at", testedAt)
        })
        if (!isTunnelActive() &&
            !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)) {
            stopSelf(startId)
        }
    }

    private fun forwardDnsOverTls(query: ByteArray, upstreamAddress: String): ByteArray? {
        if (query.size !in 17..65535) {
            lastUpstreamFailure = "DoT: DNS 질의 길이 검증 실패"
            return null
        }
        val tlsHost = DnsTlsHostValidator.normalize(configuredUpstreamTlsHost()) ?: run {
            lastUpstreamFailure = "DoT: TLS 호스트명이 유효하지 않습니다"
            return null
        }
        var socket: SSLSocket? = null
        return try {
            val address = InetAddress.getByName(upstreamAddress)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket() as SSLSocket
            if (!protect(socket)) {
                lastUpstreamFailure = "DoT: VPN 소켓 우회 설정 실패"
                return null
            }
            socket.connect(InetSocketAddress(address, 853), 3500)
            socket.soTimeout = 3500
            val parameters = socket.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            parameters.serverNames = listOf(SNIHostName(tlsHost))
            socket.sslParameters = parameters
            socket.startHandshake()

            val output = DataOutputStream(socket.outputStream)
            output.writeShort(query.size)
            output.write(query)
            output.flush()

            val input = DataInputStream(socket.inputStream)
            val length = input.readUnsignedShort()
            if (length !in 17..65535) {
                lastUpstreamFailure = "DoT: DNS 응답 길이가 유효하지 않습니다"
                return null
            }
            val answer = ByteArray(length)
            input.readFully(answer)
            if (DomainFilter.isMatchingResponse(query, answer)) answer else {
                lastUpstreamFailure = "DoT: DNS 트랜잭션 ID 또는 질문이 일치하지 않는 응답"
                null
            }
        } catch (error: Exception) {
            lastUpstreamFailure = "DoT: ${SafeFailureSummary.describe(error)}"
            null
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    private fun forwardDnsOverHttps(query: ByteArray, upstreamAddress: String): ByteArray? {
        if (query.size !in 17..65535) {
            lastUpstreamFailure = "DoH: DNS 질의 길이 검증 실패"
            return null
        }
        val tlsHost = DnsTlsHostValidator.normalize(configuredUpstreamTlsHost()) ?: run {
            lastUpstreamFailure = "DoH: TLS 호스트명이 유효하지 않습니다"
            return null
        }
        val request = DnsDohHttpCodec.buildPostRequest(tlsHost, "/dns-query", query) ?: run {
            lastUpstreamFailure = "DoH: HTTP 요청 형식 검증 실패"
            return null
        }
        var socket: SSLSocket? = null
        return try {
            val address = InetAddress.getByName(upstreamAddress)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket() as SSLSocket
            if (!protect(socket)) {
                lastUpstreamFailure = "DoH: VPN 소켓 우회 설정 실패"
                return null
            }
            socket.connect(InetSocketAddress(address, 443), 3500)
            socket.soTimeout = 3500
            val parameters = socket.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            parameters.serverNames = listOf(SNIHostName(tlsHost))
            socket.sslParameters = parameters
            socket.startHandshake()

            socket.outputStream.write(request)
            socket.outputStream.flush()
            val answer = DnsDohHttpCodec.readResponse(socket.inputStream) ?: run {
                lastUpstreamFailure = "DoH: HTTP 상태·헤더·Content-Type 또는 DNS 본문 검증 실패"
                return null
            }
            if (DomainFilter.isMatchingResponse(query, answer)) answer else {
                lastUpstreamFailure = "DoH: DNS 트랜잭션 ID 또는 질문이 일치하지 않는 응답"
                null
            }
        } catch (error: Exception) {
            lastUpstreamFailure = "DoH: ${SafeFailureSummary.describe(error)}"
            null
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "DNS 보호", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val compatibilityPaused = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle(if (compatibilityPaused) "Guardian 보호 일시 중지" else "Guardian 보호 실행 중")
                .setContentText(
                    if (compatibilityPaused) {
                        "선택한 앱 사용 중 · 앱을 닫으면 VPN 보호가 자동으로 다시 연결됩니다."
                    } else {
                        "광고 · 추적 DNS 필터링이 백그라운드에서 실행 중입니다."
                    }
                )
                .setSmallIcon(R.drawable.ic_stat_guardian)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setTicker(if (compatibilityPaused) "Guardian VPN 일시 중지" else "Guardian DNS 보호 실행 중")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Guardian AdBlock")
                .setContentText(if (compatibilityPaused) "선택 앱 사용 중 · 종료 후 자동 재연결" else "DNS 필터링 실행 중")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        }
    }

    @Synchronized
    private fun stopProtection(
        explicitStop: Boolean = false,
        preserveRestoreIntent: Boolean = false
    ) {
        stopForegroundAppMonitor()
        if (explicitStop) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RESTORE_REQUESTED, false)
                .putBoolean(KEY_RECOVERY_PENDING, false)
                .putBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
                .remove(AppForegroundPauseSettings.KEY_PAUSED_PACKAGE)
                .commit()
        }
        pendingStart.set(null)
        startCancellation.cancel()
        var wasActive = false
        var stoppedTun: ParcelFileDescriptor? = null
        tunnelSessionGuard.invalidate {
            tunnelActive = false
            wasActive = running.getAndSet(false) || tun != null
            stoppedTun = tun
            tun = null
        }
        try { stoppedTun?.close() } catch (_: Exception) { }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (error: Exception) {
            // Teardown must continue even if Android has already detached the
            // foreground notification during service/process shutdown.
            android.util.Log.w(
                "GuardianProtection",
                "Could not remove foreground notification during stop: ${SafeFailureSummary.codeFor(error)}"
            )
        }
        // An explicit user stop must clear the persisted restart intent even
        // when startup was still in flight and no TUN descriptor was committed.
        if (!preserveRestoreIntent && ProtectionState.shouldPublishStoppedState(explicitStop, wasActive)) {
            publishState(false, "DNS 보호가 중지되었습니다.")
        }
        stopSelf()
    }

    override fun onRevoke() {
        // Android revoked the user's VPN authorization; do not silently retry it.
        stopProtection(explicitStop = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        // Process/service teardown is not an explicit user stop. Preserve the durable
        // restore request so MY_PACKAGE_REPLACED and START_STICKY can rebuild the tunnel.
        stopProtection(preserveRestoreIntent = true)
        super.onDestroy()
    }
}