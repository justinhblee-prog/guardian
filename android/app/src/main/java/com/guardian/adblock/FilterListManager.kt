package com.guardian.adblock

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import javax.net.ssl.HttpsURLConnection

/** Downloads and validates the official AdGuard DNS filter into app-private storage. */
object FilterListManager {
    const val FILTER_URL = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt"
    private const val CACHE_FILE = "adguard_dns_filter.txt"
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MIN_VALID_BLOCKS = 1000
    private const val PREFS = "guardian_settings"
    private const val KEY_UPDATED_AT = "filter_list_updated_at"
    private const val KEY_BLOCK_COUNT = "filter_list_block_count"

    data class UpdateResult(val blocked: Int, val allowed: Int, val bytes: Int, val updatedAt: Long)

    fun cachedLines(context: Context): List<String> {
        val file = File(context.filesDir, CACHE_FILE)
        return if (file.isFile && file.length() > 0L) file.readLines(Charsets.UTF_8) else emptyList()
    }

    fun update(context: Context): UpdateResult {
        val connection = (URL(FILTER_URL).openConnection() as HttpsURLConnection).apply {
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
            val text = String(bytes, StandardCharsets.UTF_8)
            val lines = text.lineSequence().toList()
            val parsed = FilterRuleParser.parse(lines)
            require(parsed.blocked.size >= MIN_VALID_BLOCKS) {
                "필터 검증 실패: 유효한 차단 도메인이 ${parsed.blocked.size}개뿐입니다."
            }
            val target = File(context.filesDir, CACHE_FILE)
            val temporary = File(context.filesDir, "$CACHE_FILE.tmp")
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
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_UPDATED_AT, now)
                .putInt(KEY_BLOCK_COUNT, parsed.blocked.size)
                .apply()
            return UpdateResult(parsed.blocked.size, parsed.allowed.size, bytes.size, now)
        } finally {
            connection.disconnect()
        }
    }

    fun lastUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED_AT, 0L)

    fun cachedBlockCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BLOCK_COUNT, 0)
}
