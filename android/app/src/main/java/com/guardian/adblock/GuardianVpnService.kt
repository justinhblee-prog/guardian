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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class GuardianVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.guardian.adblock.START"
        const val ACTION_STOP = "com.guardian.adblock.STOP"
        const val ACTION_RELOAD_RULES = "com.guardian.adblock.RELOAD_RULES"
        const val ACTION_STATUS_CHANGED = "com.guardian.adblock.STATUS_CHANGED"
        const val PREFS = "guardian_settings"
        const val KEY_USER_BLOCK = "user_block_domains"
        const val KEY_USER_ALLOW = "user_allow_domains"
        const val KEY_ACTIVE = "protection_active"
        const val KEY_STATUS = "protection_status"
        const val KEY_DNS_QUERIES = "dns_queries"
        const val KEY_DNS_BLOCKED = "dns_blocked"
        const val KEY_DNS_FORWARDED = "dns_forwarded"
        const val KEY_DNS_FAILED = "dns_failed"
        const val KEY_DNS_IGNORED = "dns_ignored"
        const val KEY_DNS_DIAGNOSTIC = "dns_diagnostic"
        const val KEY_DNS_RECENT = "dns_recent"
        private const val CHANNEL = "guardian_protection"
        private const val VIRTUAL_DNS = "10.13.37.2"
        private const val VIRTUAL_DNS_V6 = "fd13:37::2"
        const val KEY_UPSTREAM_DNS = "upstream_dns"
        const val KEY_EXCLUDED_APPS = "excluded_apps"
        const val DEFAULT_UPSTREAM_DNS = "1.1.1.1"
        private val KNOWN_PUBLIC_DNS_IPV4 = listOf(
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4",
            "9.9.9.9", "149.112.112.112", "94.140.14.14", "94.140.15.15",
            "208.67.222.222", "208.67.220.220"
        )
        private val KNOWN_PUBLIC_DNS_IPV6 = listOf(
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2620:fe::fe", "2620:fe::9"
        )
        private val DNS_STATS_LOCK = Any()
        private const val DNS_STATS_BROADCAST_INTERVAL_MS = 500L
        @Volatile private var tunnelActive = false
        fun isTunnelActive(): Boolean = tunnelActive
    }

    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)
    @Volatile private var startCancelled = false
    private val lastStatsBroadcastAt = AtomicLong(0L)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProtection()
            ACTION_START -> thread(name = "guardian-vpn-start") { startProtection() }
            ACTION_RELOAD_RULES -> thread(name = "guardian-vpn-rule-reload") { reloadUserRules() }
        }
        return START_NOT_STICKY
    }

    private fun startProtection() {
        if (running.get() || !starting.compareAndSet(false, true)) return
        startCancelled = false
        try {
            startProtectionInternal()
        } finally {
            starting.set(false)
        }
    }

    private fun startProtectionInternal() {
        tunnelActive = false
        try {
            createNotificationChannel()
            startForeground(41, buildNotification())
        } catch (e: Exception) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            publishState(false, "포그라운드 서비스 시작 실패 (${e.javaClass.simpleName}): ${e.message ?: "권한 또는 Android 정책을 확인하세요."}")
            stopSelf()
            return
        }
        try {
            // Parsing a large downloaded filter list must not block the service main thread.
            loadConfiguredRules()
        } catch (e: Exception) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            publishState(false, "필터 규칙 로드 실패 (${e.javaClass.simpleName}): ${e.message ?: "원인 정보 없음"}")
            stopSelf()
            return
        }
        if (startCancelled) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            stopSelf()
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
            // Capture direct UDP/53 queries to common public resolvers as well
            // as requests sent to Android's configured virtual DNS endpoint.
            KNOWN_PUBLIC_DNS_IPV4.forEach { builder.addRoute(it, 32) }
            KNOWN_PUBLIC_DNS_IPV6.forEach { builder.addRoute(it, 128) }
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_EXCLUDED_APPS, emptySet()).orEmpty()
                .forEach { packageName ->
                    try { builder.addDisallowedApplication(packageName) } catch (_: Exception) { }
                }
            builder.setBlocking(true).establish()
        } catch (e: Exception) {
            establishError = e
            null
        }
        if (startCancelled && descriptor != null) {
            try { descriptor.close() } catch (_: Exception) { }
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            stopSelf()
            return
        }
        if (descriptor == null) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
            val detail = establishError?.let { " (${it.javaClass.simpleName}: ${it.message ?: "원인 정보 없음"})" }.orEmpty()
            publishState(false, "VPN 연결 생성 실패$detail. VPN 권한과 다른 VPN 앱의 실행 여부를 확인하세요.")
            stopSelf()
            return
        }
        tun = descriptor
        running.set(true)
        tunnelActive = true
        publishState(true, "DNS 보호가 실행 중입니다. IPv4/IPv6 DNS 광고·추적 도메인 질의를 필터링합니다.")
        thread(name = "guardian-vpn-dns") { packetLoop(descriptor) }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        var loopFailure: Exception? = null
        try {
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(32767)
            while (running.get()) {
                try {
                    val length = input.read(buffer)
                    if (length < 0) break
                    if (length == 0) continue
                    val ipv4 = Ipv4Udp.parseDns(buffer, length)
                    val ipv6 = if (ipv4 == null) Ipv6Udp.parseDns(buffer, length) else null
                    val payload = ipv4?.payload ?: ipv6?.payload
                    if (payload == null) {
                        recordIgnoredPacket("IPv4/IPv6 UDP DNS 패킷이 아니거나 헤더 검증 실패")
                        continue
                    }
                    val domain = DomainFilter.readQuestionName(payload)
                    if (domain == null) {
                        recordIgnoredPacket("DNS 질의 형식 또는 질문 영역 검증 실패")
                        continue
                    }
                    val blocked = DomainFilter.isBlocked(domain)
                    val forwarded = if (blocked) null else forwardDns(payload)
                    val dnsResponse = (if (blocked) DomainFilter.makeNxdomain(payload)
                        else forwarded ?: DomainFilter.makeServfail(payload)) ?: continue
                    recordDnsActivity(domain, blocked, forwarded != null)
                    val packet = if (ipv4 != null) Ipv4Udp.response(ipv4, dnsResponse)
                        else Ipv6Udp.response(ipv6!!, dnsResponse)
                    synchronized(output) {
                        output.write(packet)
                        output.flush()
                    }
                } catch (e: Exception) {
                    if (!running.get()) break
                    loopFailure = e
                    break
                }
            }
        } catch (e: Exception) {
            if (running.get()) loopFailure = e
        } finally {
            // A dead packet loop must never leave the UI claiming protection is active.
            if (running.compareAndSet(true, false)) {
                tunnelActive = false
                try { descriptor.close() } catch (_: Exception) { }
                if (tun === descriptor) tun = null
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
                val detail = loopFailure?.let { " (${it.javaClass.simpleName}: ${it.message ?: "원인 정보 없음"})" }.orEmpty()
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
                .putString(KEY_DNS_RECENT, lines.joinToString("\n"))
                .apply()
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
        val sources = mutableListOf<List<String>>()
        sources.add(assets.open("filter_rules.txt").bufferedReader().use { it.readLines() })
        // A failed or absent update never replaces the last known-good filter cache.
        sources.add(FilterListManager.cachedLines(this))
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(FilterListManager.KEY_SECURITY_ENABLED, false)) {
            sources.add(FilterListManager.cachedSecurityLines(this))
        }
        val userBlock = prefs.getString(KEY_USER_BLOCK, "").orEmpty().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val userAllow = prefs.getString(KEY_USER_ALLOW, "").orEmpty().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.toList()
        DomainFilter.loadRules(FilterRuleComposer.compose(sources, userBlock, userAllow))
    }

    private fun reloadUserRules() {
        if (!running.get()) return
        try {
            loadConfiguredRules()
            publishState(true, "DNS 보호 실행 중 · 사용자 규칙을 적용했습니다.")
        } catch (_: Exception) {
            // Keep the previously loaded in-memory rules if the replacement is invalid.
            publishState(true, "DNS 보호 실행 중 · 새 규칙 적용에 실패해 기존 규칙을 유지합니다.")
        }
    }

    private fun publishState(active: Boolean, message: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_STATUS, message)
            .apply()
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    private fun configuredUpstreamDns(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_UPSTREAM_DNS, DEFAULT_UPSTREAM_DNS)
            ?.takeIf { it in setOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "94.140.14.14") }
            ?: DEFAULT_UPSTREAM_DNS

    private fun forwardDns(query: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) return null
            socket.soTimeout = 3500
            val address = InetAddress.getByName(configuredUpstreamDns())
            socket.send(DatagramPacket(query, query.size, address, 53))
            val response = ByteArray(4096)
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)
            // Do not accept a reply from another endpoint or a mismatched DNS transaction.
            if (packet.address != address || packet.port != 53) return null
            val received = response.copyOf(packet.length)
            if (received.size >= 4 && (received[2].toInt() and 0x02) != 0) {
                // RFC 7766: retry truncated UDP answers over TCP before returning SERVFAIL.
                forwardDnsTcp(query)
            } else if (DomainFilter.isMatchingResponse(query, received)) received else null
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun forwardDnsTcp(query: ByteArray): ByteArray? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            if (!protect(socket)) return null
            socket.connect(java.net.InetSocketAddress(configuredUpstreamDns(), 53), 3500)
            socket.soTimeout = 3500
            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(query.size)
            output.write(query)
            output.flush()
            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            if (length !in 17..4096) return null
            val answer = ByteArray(length)
            input.readFully(answer)
            if (DomainFilter.isMatchingResponse(query, answer)) answer else null
        } catch (_: Exception) {
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("Guardian AdBlock")
                .setContentText("DNS 광고·추적 도메인 필터링 실행 중")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Guardian AdBlock")
                .setContentText("DNS 필터링 실행 중")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        }
    }

    private fun stopProtection() {
        startCancelled = true
        tunnelActive = false
        val wasActive = running.getAndSet(false) || tun != null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wasActive) publishState(false, "DNS 보호가 중지되었습니다.")
        stopSelf()
    }

    override fun onRevoke() {
        stopProtection()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopProtection()
        super.onDestroy()
    }
}