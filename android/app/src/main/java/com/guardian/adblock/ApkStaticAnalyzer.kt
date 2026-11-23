package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounded DEX string triage. API references are indicators, never a malware verdict. */
internal object ApkStaticAnalyzer {
    enum class Status { SCANNED, INVALID_APK, LIMIT_EXCEEDED }
    enum class Risk { LOW, REVIEW, HIGH }
    enum class Signal(val points: Int, val label: String, val token: String) {
        DYNAMIC_CODE(25, "동적 코드 로딩 API 참조", "Ldalvik/system/DexClassLoader;"),
        PROCESS_EXEC(30, "외부 프로세스 실행 API 참조", "Ljava/lang/Runtime;->exec"),
        SMS_SEND(25, "SMS 전송 API 참조", "Landroid/telephony/SmsManager;->send"),
        ACCESSIBILITY(20, "접근성 서비스 API 참조", "Landroid/accessibilityservice/AccessibilityService;"),
        OVERLAY(15, "화면 오버레이 API 참조", "Landroid/view/WindowManager\$LayoutParams;"),
        DEVICE_ADMIN(20, "기기 관리자 API 참조", "Landroid/app/admin/DevicePolicyManager;"),
        CLIPBOARD(10, "클립보드 API 참조", "Landroid/content/ClipboardManager;"),
        INSTALLER(20, "앱 설치 API 참조", "Landroid/content/pm/PackageInstaller;"),
        WEBVIEW_BRIDGE(15, "WebView JavaScript 브리지 API 참조", "Landroid/webkit/WebView;->addJavascriptInterface")
    }
    data class Result(
        val status: Status, val risk: Risk?, val score: Int,
        val dexCount: Int, val dexBytes: Long, val signals: List<Signal>,
        val explanation: String
    )

    private const val MAX_ARCHIVE = 200L * 1024 * 1024
    private const val MAX_UNCOMPRESSED = 128L * 1024 * 1024
    private const val MAX_DEX = 64L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val dexName = Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")

    fun analyze(input: InputStream): Result {
        var total = 0L
        var dexBytes = 0L
        var dexCount = 0
        val signals = linkedSetOf<Signal>()
        return try {
            ZipInputStream(BoundedInput(input, MAX_ARCHIVE)).use { zip ->
                var entries = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) return limit(dexCount, dexBytes)
                    val capture = if (dexName.matches(entry.name)) ByteArrayOutputStream() else null
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_UNCOMPRESSED) return limit(dexCount, dexBytes)
                        if (capture != null) {
                            dexBytes += n
                            if (dexBytes > MAX_DEX) return limit(dexCount, dexBytes)
                            capture.write(buffer, 0, n)
                        }
                    }
                    if (capture != null) {
                        val bytes = capture.toByteArray()
                        if (bytes.size < 8 || !bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) {
                            return invalid(dexCount, dexBytes)
                        }
                        dexCount++
                        val text = bytes.toString(Charsets.ISO_8859_1)
                        Signal.values().filterTo(signals) { text.contains(it.token) }
                    }
                    zip.closeEntry()
                }
            }
            if (dexCount == 0) return invalid(0, 0)
            val score = signals.sumOf { it.points }.coerceIn(0, 100)
            val risk = when { score >= 50 -> Risk.HIGH; score >= 20 -> Risk.REVIEW; else -> Risk.LOW }
            val note = when (risk) {
                Risk.HIGH -> "여러 민감 API 참조가 발견되었습니다. 권한·설치 출처와 앱 동작을 추가 확인하세요."
                Risk.REVIEW -> "일부 민감 API 참조가 발견되었습니다. 정상 앱도 사용하는 API이므로 단독 악성 판정은 불가능합니다."
                Risk.LOW -> "검사한 DEX에서 지정된 민감 API 문자열을 찾지 못했습니다. 전체 악성코드 검사를 의미하지 않습니다."
            }
            Result(Status.SCANNED, risk, score, dexCount, dexBytes, signals.toList(), note)
        } catch (_: ArchiveLimit) {
            limit(dexCount, dexBytes)
        } catch (_: Exception) {
            invalid(dexCount, dexBytes)
        }
    }

    private fun invalid(count: Int, bytes: Long) = Result(
        Status.INVALID_APK, null, 0, count, bytes, emptyList(),
        "유효한 DEX 파일이 포함된 APK인지 확인해 주세요."
    )
    private fun limit(count: Int, bytes: Long) = Result(
        Status.LIMIT_EXCEEDED, null, 0, count, bytes, emptyList(),
        "APK가 검사 용량 또는 항목 수 제한을 초과했습니다."
    )
    private class ArchiveLimit : Exception()
    private class BoundedInput(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var used = 0L
        private fun add(n: Int) {
            if (n > 0) { used += n; if (used > max) throw ArchiveLimit() }
        }
        override fun read(): Int { val v = super.read(); if (v >= 0) add(1); return v }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len); add(n); return n
        }
    }
}
