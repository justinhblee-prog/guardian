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
        const val KEY_DNS_RECENT = "dns_recent"
        private const val CHANNEL = "guardian_protection"
        private const val VIRTUAL_DNS = "10.13.37.2"
        private const val VIRTUAL_DNS_V6 = "fd13:37::2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private val DNS_STATS_LOCK = Any()
    }

    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopProtection()
            ACTION_START -> startProtection()
            ACTION_RELOAD_RULES -> reloadUserRules()
        }
        return START_NOT_STICKY
    }

    private fun startProtection() {
        if (running.get()) return
        try {
            loadConfiguredRules()
        } catch (_: Exception) {
            // Fail closed: do not start a VPN that silently has no shared filter rules.
            publishState(false, "필터 규칙을 읽지 못해 보호를 시작하지 않았습니다.")
            stopSelf()
            return
        }
        try {
            createNotificationChannel()
            startForeground(41, buildNotification())
        } catch (_: Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            publishState(false, "보호 알림을 시작하지 못했습니다.")
            stopSelf()
            return
        }
        val descriptor = try {
            Builder()
                .setSession("Guardian AdBlock")
                .setMtu(1500)
                .addAddress("10.13.37.1", 32)
                .addAddress("fd13:37::1", 128)
                .addDnsServer(VIRTUAL_DNS)
                .addDnsServer(VIRTUAL_DNS_V6)
                .addRoute(VIRTUAL_DNS, 32)
                .addRoute(VIRTUAL_DNS_V6, 128)
                .setBlocking(true)
                .establish()
        } catch (_: Exception) {
            null
        }
        if (descriptor == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            publishState(false, "VPN 연결을 만들지 못했습니다. VPN 권한과 다른 VPN 앱의 실행 여부를 확인하세요.")
            stopSelf()
            return
        }
        tun = descriptor
        running.set(true)
        publishState(true, "DNS 보호가 실행 중입니다. IPv4/IPv6 DNS 광고·추적 도메인 질의를 필터링합니다.")
        thread(name = "guardian-vpn-dns") { packetLoop(descriptor) }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        while (running.get()) {
            try {
                val length = input.read(buffer)
                if (length <= 0) continue
                val ipv4 = Ipv4Udp.parseDns(buffer, length)
                val ipv6 = if (ipv4 == null) Ipv6Udp.parseDns(buffer, length) else null
                val payload = ipv4?.payload ?: ipv6?.payload ?: continue
                val domain = DomainFilter.readQuestionName(payload) ?: continue
                val blocked = DomainFilter.isBlocked(domain)
                val forwarded = if (blocked) null else forwardDns(payload)
                val dnsResponse = if (blocked) DomainFilter.makeNxdomain(payload)
                    else forwarded ?: DomainFilter.makeServfail(payload)
                recordDnsActivity(domain, blocked, forwarded != null)
                val packet = if (ipv4 != null) Ipv4Udp.response(ipv4, dnsResponse)
                    else Ipv6Udp.response(ipv6!!, dnsResponse)
                synchronized(output) {
                    output.write(packet)
                    output.flush()
                }
            } catch (_: Exception) {
                if (!running.get()) break
            }
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
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    private fun loadConfiguredRules() {
        val rules = assets.open("filter_rules.txt").bufferedReader().use { it.readLines().toMutableList() }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.getString(KEY_USER_BLOCK, "").orEmpty().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.forEach { rules.add("block=$it") }
        prefs.getString(KEY_USER_ALLOW, "").orEmpty().lineSequence()
            .map { it.trim() }.filter { it.isNotEmpty() }.forEach { rules.add("allow=$it") }
        DomainFilter.loadRules(rules)
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

    private fun forwardDns(query: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) return null
            socket.soTimeout = 3500
            val address = InetAddress.getByName(UPSTREAM_DNS)
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
            socket.connect(java.net.InetSocketAddress(UPSTREAM_DNS, 53), 3500)
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