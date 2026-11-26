package com.guardian.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
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

    @Volatile private var lastUpstreamFailure: String? = null
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var packetWorker: Thread? = null
    private val running = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    private data class PendingStart(val generation: Long, val startId: Int)
    private val pendingStart = AtomicReference<PendingStart?>(null)
    private val startCancellation = VpnStartCancellationGate()
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
                ensureForegroundServiceNotification()
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
            ACTION_RELOAD_RULES -> thread(name = "guardian-vpn-rule-reload") { reloadUserRules() }
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
                    ensureForegroundServiceNotification()
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

    private fun ensureForegroundServiceNotification() {
        createNotificationChannel()
        startForeground(41, buildNotification())
    }

    private fun handleCompatibilitySettingsChanged(startId: Int) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_REQUESTED, false)) {
            stopForegroundAppMonitor()
            stopSelf(startId)
            return
        }
        ensureForegroundServiceNotification()
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
        return startWalletMonitorIfConfigured(generation)
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
        compatibilityMonitorGeneration.incrementAndGet()
        val monitor = foregroundAppMonitor
        foregroundAppMonitor = null
        monitor?.stop()
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
        running.set(false)
        tunnelActive = false
        val oldTun = tun
        tun = null
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
        running.set(false)
        tunnelActive = false
        val oldTun = tun
        tun = null
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
        if (running.get()) return
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
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error)).apply()
        // A stale worker must not overwrite an explicit stop or a newer start request.
        if (!startCancellation.isCurrent(requestGeneration) || running.get()) return
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
    private fun startProtectionInternal(requestGeneration: Long, startId: Int) {
        tunnelActive = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_FILTER_READINESS)
            .apply()
        try {
            createNotificationChannel()
            startForeground(41, buildNotification())
        } catch (e: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(e)).apply()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            publishState(false, "포그라운드 서비스 시작 실패 (${ProtectionDiagnostics.exceptionTypeChain(e)}): ${SafeFailureSummary.describe(e)}")
            stopSelf(startId)
            return
        }
        try {
            // Parsing a large downloaded filter list must not block the service main thread.
            loadConfiguredRules()
        } catch (e: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(e)).apply()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            publishState(false, "필터 규칙 로드 실패 (${ProtectionDiagnostics.exceptionTypeChain(e)}): ${SafeFailureSummary.describe(e)}")
            stopSelf(startId)
            return
        }
        if (!startCancellation.isCurrent(requestGeneration)) {
            val compatibilityPaused = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
            if (!compatibilityPaused) {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                stopSelf(startId)
            }
            return
        }

        // Never install a DNS-only VPN route until its upstream resolver has
        // answered a real DNS query. If every configured/fallback resolver is
        // unreachable, leaving the device's normal network untouched is safer
        // than activating a tunnel that makes every website appear offline.
        val preflightQuery = DnsUpstreamProbe.query()
        val preflightResponse = forwardDns(preflightQuery, requireSuccessfulResolution = true)
        if (!startCancellation.isCurrent(requestGeneration)) {
            val compatibilityPaused = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
            if (!compatibilityPaused) {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                stopSelf(startId)
            }
            return
        }
        if (!DnsUpstreamProbe.isSuccessfulResolution(preflightQuery, preflightResponse)) {
            val detail = lastUpstreamFailure ?: "업스트림 DNS 응답 검증 실패"
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_DNS_DIAGNOSTIC, "보호 시작 전 DNS 연결 확인 실패: $detail")
                .apply()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            publishState(
                false,
                "업스트림 DNS 연결을 확인하지 못해 보호를 시작하지 않았습니다. 일반 인터넷 연결은 변경하지 않았습니다. ($detail)"
            )
            stopSelf(startId)
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
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            val detail = establishError?.let { " (${ProtectionDiagnostics.exceptionTypeChain(it)}: ${SafeFailureSummary.describe(it)})" }.orEmpty()
            establishError?.let { error ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STARTUP_TRACE, ProtectionDiagnostics.exceptionTraceSummary(error)).apply()
            }
            publishState(false, "VPN 연결 생성 실패$detail. VPN 권한과 다른 VPN 앱의 실행 여부를 확인하세요.")
            stopSelf(startId)
            return
        }
        val committed = startCancellation.tryCommitStart(requestGeneration) {
            tun = descriptor
            running.set(true)
            tunnelActive = true
            val filterNotice = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_FILTER_READINESS, null)
                ?.takeIf { it.isNotBlank() && it != "모든 선택 필터가 준비되었습니다." }
            val activeMessage = if (filterNotice == null) {
                "DNS 보호가 실행 중입니다. IPv4/IPv6 DNS 광고·추적 도메인 질의를 필터링합니다."
            } else {
                "DNS 보호가 실행 중입니다. $filterNotice"
            }
            publishState(true, activeMessage)
            packetWorker = thread(name = "guardian-vpn-dns") { packetLoop(descriptor) }
        }
        if (!committed) {
            try { descriptor.close() } catch (_: Exception) { }
            val compatibilityPaused = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(AppForegroundPauseSettings.KEY_PAUSED, false)
            if (!compatibilityPaused) {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                stopSelf(startId)
            }
        }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        var loopFailure: Throwable? = null
        try {
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(32767)
            val tcpDns = DnsTcpSessionManager()
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
            // A dead packet loop must never leave the UI claiming protection is active.
            if (running.compareAndSet(true, false)) {
                tunnelActive = false
                try { descriptor.close() } catch (_: Exception) { }
                if (tun === descriptor) tun = null
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                val detail = loopFailure?.let { " (${ProtectionDiagnostics.exceptionTypeChain(it)}: ${SafeFailureSummary.describe(it)})" }.orEmpty()
                publishState(false, "DNS 패킷 처리 중단$detail. 보호를 다시 시작해 주세요.")
                stopSelf()
            }
        }
    }

    private fun recordIgnoredPacket(reason: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        synchronized(DNS_STATS_LOCK) {
            prefs.edit()
                .putLong(KEY_DNS_IGNORED, prefs.getLong(KEY_DNS_IGNORED, 0L) + 1L)
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
            val queries = prefs.getLong(KEY_DNS_QUERIES, 0L) + 1L
            val blockedCount = prefs.getLong(KEY_DNS_BLOCKED, 0L) + if (blocked) 1L else 0L
            val forwardedCount = prefs.getLong(KEY_DNS_FORWARDED, 0L) + if (forwarded) 1L else 0L
            val failedCount = prefs.getLong(KEY_DNS_FAILED, 0L) + if (!blocked && !forwarded) 1L else 0L
            val category = if (blocked) DomainFilter.classifyBlockedDomain(domain) else null
            val adBlocked = prefs.getLong(KEY_DNS_AD_BLOCKED, 0L) +
                if (category == DomainFilter.BlockCategory.AD) 1L else 0L
            val trackerBlocked = prefs.getLong(KEY_DNS_TRACKER_BLOCKED, 0L) +
                if (category == DomainFilter.BlockCategory.TRACKER) 1L else 0L
            val otherBlocked = prefs.getLong(KEY_DNS_OTHER_BLOCKED, 0L) +
                if (category == DomainFilter.BlockCategory.OTHER) 1L else 0L
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

    private fun loadConfiguredRules() {
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
            assets.open(BundledFilterRules.ASSET_NAME).bufferedReader().use(BundledFilterRules::parse)
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

        val mainCacheReady = runCatching {
            FilterListManager.isCacheValid(
                FilterListManager.hasCachedFile(this),
                FilterListManager.cachedBlockCount(this),
                selectedProfile.minimumRules
            ) && selectedProfile.key == cachedProfile.key
        }.getOrDefault(false)
        addOptionalCache(
            "광고·추적",
            "adguard_dns_filter.txt",
            selectedProfile.minimumRules,
            mainCacheReady
        )

        val securityEnabled = prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)
        if (securityEnabled) {
            val securityReady = runCatching {
                FilterListManager.hasCachedSecurityList(this)
            }.getOrDefault(false)
            addOptionalCache(
                "보안",
                "hagezi_threat_intelligence.txt",
                FilterListManager.MIN_SECURITY_BLOCKS,
                securityReady
            )
        }

        val androidAdsEnabled = prefs.getBoolean(FilterListManager.KEY_ANDROID_ADS_ENABLED, true)
        if (androidAdsEnabled) {
            val androidAdsReady = runCatching {
                FilterListManager.hasCachedAndroidAdsList(this)
            }.getOrDefault(false)
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
        } catch (_: Exception) {
            // Keep the previously loaded in-memory rules if the replacement is invalid.
            publishState(true, "DNS 보호 실행 중 · 새 규칙 적용에 실패해 기존 규칙을 유지합니다.")
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
            else -> listOf(configured, "1.1.1.1", "8.8.8.8").distinct()
        }
        for (address in candidates) {
            val response = when (transport) {
                "tcp" -> forwardDnsTcp(query, address)
                "dot" -> forwardDnsOverTls(query, address)
                "doh" -> forwardDnsOverHttps(query, address)
                else -> forwardDnsUdp(query, address)
            }
            if (response != null && DomainFilter.isMatchingResponse(query, response)) {
                val usable = if (requireSuccessfulResolution) {
                    DnsUpstreamProbe.isSuccessfulResolution(query, response)
                } else {
                    !DnsUpstreamProbe.isRetryableResolverError(response)
                }
                if (usable) return response
                lastUpstreamFailure = transport.uppercase() +
                    ": resolver returned an unusable response (RCODE=" +
                    DnsUpstreamProbe.responseCode(response) + ")"
                continue
            }
            if (lastUpstreamFailure == null) {
                lastUpstreamFailure = transport.uppercase() + ": DNS 응답 없음 또는 검증 실패"
            }
        }
        return null
    }

    private fun forwardDnsUdp(query: ByteArray, upstreamAddress: String): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (isTunnelActive() && !protect(socket)) {
                lastUpstreamFailure = "UDP: VPN 소켓 우회 설정 실패"
                return null
            }
            socket.soTimeout = 3500
            val address = InetAddress.getByName(upstreamAddress)
            socket.send(DatagramPacket(query, query.size, address, 53))
            val response = ByteArray(4096)
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)
            if (packet.address != address || packet.port != 53) {
                lastUpstreamFailure = "UDP: 예상하지 않은 DNS 응답 주소 또는 포트"
                return null
            }
            val received = response.copyOf(packet.length)
            if (received.size >= 4 && (received[2].toInt() and 0x02) != 0) {
                forwardDnsTcp(query, upstreamAddress)
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
            if (isTunnelActive() && !protect(socket)) {
                lastUpstreamFailure = "TCP: VPN 소켓 우회 설정 실패"
                return null
            }
            socket.connect(InetSocketAddress(upstreamAddress, 53), 3500)
            socket.soTimeout = 3500
            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(query.size)
            output.write(query)
            output.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            if (length !in 17..4096) {
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
        if (query.size !in 17..4096) return null
        val tlsHost = DnsTlsHostValidator.normalize(configuredUpstreamTlsHost()) ?: return null
        var socket: SSLSocket? = null
        return try {
            val address = InetAddress.getByName(upstreamAddress)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket() as SSLSocket
            if (isTunnelActive() && !protect(socket)) {
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
            if (length !in 17..4096) return null
            val answer = ByteArray(length)
            input.readFully(answer)
            if (DomainFilter.isMatchingResponse(query, answer)) answer else null
        } catch (error: Exception) {
            lastUpstreamFailure = "DoT: ${SafeFailureSummary.describe(error)}"
            null
        } finally {
            try { socket?.close() } catch (_: Exception) { }
        }
    }

    private fun forwardDnsOverHttps(query: ByteArray, upstreamAddress: String): ByteArray? {
        if (query.size !in 17..4096) return null
        val tlsHost = DnsTlsHostValidator.normalize(configuredUpstreamTlsHost()) ?: return null
        val request = DnsDohHttpCodec.buildPostRequest(tlsHost, "/dns-query", query) ?: return null
        var socket: SSLSocket? = null
        return try {
            val address = InetAddress.getByName(upstreamAddress)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket() as SSLSocket
            if (isTunnelActive() && !protect(socket)) {
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
            val answer = DnsDohHttpCodec.readResponse(socket.inputStream) ?: return null
            if (DomainFilter.isMatchingResponse(query, answer)) answer else null
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
        tunnelActive = false
        val wasActive = running.getAndSet(false) || tun != null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
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