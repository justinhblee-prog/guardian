package com.guardian.adblock

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection

/** Downloads and validates DNS-compatible community filters into app-private storage. */
object FilterListManager {
    const val FILTER_URL = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt"
    const val KEY_FILTER_PROFILE = "filter_profile"
    const val KEY_CACHED_PROFILE = "filter_cached_profile"
    const val SECURITY_FILTER_URL = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_44.txt"
    private const val CACHE_FILE = "adguard_dns_filter.txt"
    private const val SECURITY_CACHE_FILE = "hagezi_threat_intelligence.txt"
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MIN_SECURITY_BLOCKS = 100
    private const val PREFS = "guardian_settings"
    private const val KEY_UPDATED_AT = "filter_list_updated_at"
    private const val KEY_BLOCK_COUNT = "filter_list_block_count"
    private const val KEY_IGNORED_COUNT = "filter_list_ignored_count"
    private const val KEY_SECURITY_UPDATED_AT = "security_filter_updated_at"
    private const val KEY_SECURITY_BLOCK_COUNT = "security_filter_block_count"
    private const val KEY_SECURITY_IGNORED_COUNT = "security_filter_ignored_count"
    const val KEY_AUTO_UPDATE = "filter_auto_update"
    const val KEY_UPDATE_INTERVAL_MS = "filter_update_interval_ms"
    const val KEY_LAST_ERROR = "filter_list_last_error"
    const val KEY_SECURITY_LAST_ERROR = "security_filter_last_error"
    const val KEY_SECURITY_ENABLED = "security_filter_enabled"

    data class UpdateResult(val blocked: Int, val allowed: Int, val ignored: Int, val bytes: Int, val updatedAt: Long)
    data class FilterProfile(val key: String, val label: String, val url: String, val minimumRules: Int)

    private val profiles = listOf(
        FilterProfile("adguard", "AdGuard DNS Filter · 권장", FILTER_URL, 1000),
        FilterProfile("1hosts_lite", "1Hosts Lite · 균형", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_24.txt", 100),
        FilterProfile("oisd_big", "OISD Big · 강력", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_27.txt", 100),
        FilterProfile("hagezi_normal", "HaGeZi Normal · 강력", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_34.txt", 100),
        FilterProfile("hagezi_pro", "HaGeZi Pro · 매우 강력", "https://adguardteam.github.io/HostlistsRegistry/assets/filter_48.txt", 100)
    )

    fun availableProfiles(): List<FilterProfile> = profiles
    fun cachedProfile(context: Context): FilterProfile {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CACHED_PROFILE, null)
        return profiles.firstOrNull { it.key == key } ?: profiles.first()
    }
    fun selectedProfile(context: Context): FilterProfile {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FILTER_PROFILE, "adguard")
        return profiles.firstOrNull { it.key == key } ?: profiles.first()
    }

    fun hasCachedList(context: Context): Boolean = File(context.filesDir, CACHE_FILE).let { it.isFile && it.length() > 0L }
    fun hasCachedSecurityList(context: Context): Boolean = File(context.filesDir, SECURITY_CACHE_FILE).let { it.isFile && it.length() > 0L }
    fun cachedLines(context: Context): List<String> = readCache(context, CACHE_FILE)
    fun cachedSecurityLines(context: Context): List<String> = readCache(context, SECURITY_CACHE_FILE)

    fun update(context: Context): UpdateResult {
        val profile = selectedProfile(context)
        return download(context, profile.url, CACHE_FILE, profile.minimumRules, KEY_UPDATED_AT, KEY_BLOCK_COUNT, KEY_IGNORED_COUNT, profile.key)
    }

    fun updateSecurity(context: Context): UpdateResult =
        download(context, SECURITY_FILTER_URL, SECURITY_CACHE_FILE, MIN_SECURITY_BLOCKS, KEY_SECURITY_UPDATED_AT, KEY_SECURITY_BLOCK_COUNT, KEY_SECURITY_IGNORED_COUNT)

    private fun readCache(context: Context, name: String): List<String> {
        val file = File(context.filesDir, name)
        return if (file.isFile && file.length() > 0L) file.readLines(Charsets.UTF_8) else emptyList()
    }

    private fun download(
        context: Context,
        url: String,
        cacheName: String,
        minimumRules: Int,
        updatedKey: String,
        countKey: String,
        ignoredKey: String,
        cachedProfileKey: String? = null
    ): UpdateResult {
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            setRequestProperty("Accept", "text/plain")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "필터 서버 HTTP $status" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "필터 파일이 허용 크기(10MB)를 초과했습니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val lines = String(bytes, StandardCharsets.UTF_8).lineSequence().toList()
            val parsed = FilterRuleParser.parse(lines)
            require(parsed.blocked.size >= minimumRules) {
                "필터 검증 실패: 유효한 차단 도메인이 ${parsed.blocked.size}개뿐입니다."
            }
            val target = File(context.filesDir, cacheName)
            val temporary = File(context.filesDir, "$cacheName.tmp")
            temporary.writeBytes(bytes)
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                try {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    temporary.delete()
                    throw IllegalStateException("새 필터 캐시를 안전하게 교체하지 못했습니다.", e)
                }
            } catch (e: Exception) {
                temporary.delete()
                throw IllegalStateException("새 필터 캐시를 안전하게 교체하지 못했습니다.", e)
            }
            val now = System.currentTimeMillis()
            val metadata = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(updatedKey, now)
                .putInt(countKey, parsed.blocked.size)
                .putInt(ignoredKey, parsed.ignored)
            if (cachedProfileKey != null) metadata.putString(KEY_CACHED_PROFILE, cachedProfileKey)
            metadata.apply()
            return UpdateResult(parsed.blocked.size, parsed.allowed.size, parsed.ignored, bytes.size, now)
        } finally {
            connection.disconnect()
        }
    }

    fun lastUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED_AT, 0L)

    fun cachedBlockCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BLOCK_COUNT, 0)

    fun cachedIgnoredCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_IGNORED_COUNT, 0)

    fun lastSecurityUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SECURITY_UPDATED_AT, 0L)

    fun cachedSecurityBlockCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SECURITY_BLOCK_COUNT, 0)

    fun cachedSecurityIgnoredCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SECURITY_IGNORED_COUNT, 0)
}
