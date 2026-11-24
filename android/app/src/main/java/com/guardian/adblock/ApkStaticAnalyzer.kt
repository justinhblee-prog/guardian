package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounded DEX string triage. API references are indicators, never a malware verdict. */
internal object ApkStaticAnalyzer {
    enum class Status { SCANNED, INVALID_APK, LIMIT_EXCEEDED }
    enum class Risk { LOW, REVIEW, HIGH }
    enum class Signal(val points: Int, val label: String, val tokens: List<String>) {
        DYNAMIC_CODE(25, "동적 코드 로딩 API 참조", listOf("Ldalvik/system/DexClassLoader;")),
        PROCESS_EXEC(30, "외부 프로세스 실행 API 참조", listOf("Ljava/lang/Runtime;", "exec")),
        SMS_SEND(25, "SMS 전송 API 참조", listOf("Landroid/telephony/SmsManager;", "send")),
        ACCESSIBILITY(20, "접근성 서비스 API 참조", listOf("Landroid/accessibilityservice/AccessibilityService;")),
        OVERLAY(15, "화면 오버레이 API 참조", listOf("Landroid/view/WindowManager\$LayoutParams;")),
        DEVICE_ADMIN(20, "기기 관리자 API 참조", listOf("Landroid/app/admin/DevicePolicyManager;")),
        CLIPBOARD(10, "클립보드 API 참조", listOf("Landroid/content/ClipboardManager;")),
        INSTALLER(20, "앱 설치 API 참조", listOf("Landroid/content/pm/PackageInstaller;")),
        WEBVIEW_BRIDGE(15, "WebView JavaScript 브리지 API 참조", listOf("Landroid/webkit/WebView;", "addJavascriptInterface"))
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
    private const val DEX_HEADER_SIZE = 0x70
    private const val DEX_041_HEADER_SIZE = 0x78
    private val supportedDexVersions = setOf("035", "037", "038", "039", "040", "041")
    private val dexName = Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")

    fun analyze(input: InputStream): Result {
        var total = 0L
        var dexBytes = 0L
        var dexCount = 0
        val signals = linkedSetOf<Signal>()
        return try {
            val boundedInput = BoundedInput(input, MAX_ARCHIVE)
            ZipInputStream(boundedInput).use { zip ->
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
                        val logicalDexCount = countValidDexFiles(bytes)
                            ?: return invalid(dexCount, dexBytes)
                        dexCount += logicalDexCount
                        val text = bytes.toString(Charsets.ISO_8859_1)
                        Signal.values().filterTo(signals) { signal -> signal.tokens.all(text::contains) }
                    }
                    zip.closeEntry()
                }
                // ZipInputStream parses local file headers but does not require a
                // valid central directory/EOCD. Drain the physical stream and verify
                // the final ZIP directory record before reporting a completed scan.
                boundedInput.drainToEof()
                if (!boundedInput.hasValidZipEndRecord(entries)) return invalid(dexCount, dexBytes)
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

    /**
     * Validate a standard DEX header or every logical header in a DEX 041
     * container. This is structural header triage, not a bytecode verifier.
     */
    private fun countValidDexFiles(bytes: ByteArray): Int? {
        if (bytes.size < DEX_HEADER_SIZE) return null
        if (!bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) return null
        val firstVersion = String(bytes, 4, 3, Charsets.US_ASCII)
        if (bytes[7].toInt() != 0 || firstVersion !in supportedDexVersions) return null
        if (firstVersion != "041") {
            val littleEndian = readDexEndianness(bytes, 0) ?: return null
            val declaredFileSize = headerInt(bytes, 32, littleEndian).toLong() and 0xffffffffL
            val headerSize = headerInt(bytes, 36, littleEndian)
            return if (headerSize == DEX_HEADER_SIZE && declaredFileSize == bytes.size.toLong()) 1 else null
        }

        val containerSize = bytes.size.toLong()
        var headerOffset = 0L
        var logicalCount = 0
        while (headerOffset < containerSize) {
            if (containerSize - headerOffset < DEX_041_HEADER_SIZE) return null
            val offset = headerOffset.toInt()
            if (!bytes.copyOfRange(offset, offset + 4).contentEquals(byteArrayOf(100, 101, 120, 10))) return null
            val version = String(bytes, offset + 4, 3, Charsets.US_ASCII)
            if (version != "041" || bytes[offset + 7].toInt() != 0) return null
            val littleEndian = readDexEndianness(bytes, offset) ?: return null
            val logicalFileSize = headerInt(bytes, offset + 32, littleEndian).toLong() and 0xffffffffL
            val headerSize = headerInt(bytes, offset + 36, littleEndian)
            val declaredContainerSize = headerInt(bytes, offset + 112, littleEndian).toLong() and 0xffffffffL
            val declaredHeaderOffset = headerInt(bytes, offset + 116, littleEndian).toLong() and 0xffffffffL
            if (headerSize != DEX_041_HEADER_SIZE ||
                logicalFileSize < DEX_041_HEADER_SIZE.toLong() ||
                declaredContainerSize != containerSize ||
                declaredHeaderOffset != headerOffset ||
                logicalFileSize > containerSize - headerOffset
            ) return null
            headerOffset += logicalFileSize
            logicalCount++
        }
        return logicalCount.takeIf { headerOffset == containerSize && it > 0 }
    }

    private fun readDexEndianness(bytes: ByteArray, offset: Int): Boolean? =
        when (littleEndianInt(bytes, offset + 40)) {
            0x12345678 -> true
            0x78563412 -> false
            else -> null
        }

    private fun headerInt(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        if (littleEndian) return littleEndianInt(bytes, offset)
        return ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

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
        private val tail = ByteArray(MAX_ZIP_END_RECORD_BYTES)
        private var tailPosition = 0
        private var tailSize = 0
        private var bytesRead = 0L

        private fun add(bytes: ByteArray, offset: Int, count: Int) {
            if (count <= 0) return
            bytesRead += count.toLong()
            if (bytesRead > max) throw ArchiveLimit()
            for (index in 0 until count) {
                tail[tailPosition] = bytes[offset + index]
                tailPosition = (tailPosition + 1) % tail.size
                if (tailSize < tail.size) tailSize++
            }
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) add(byteArrayOf(value.toByte()), 0, 1)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            add(b, off, count)
            return count
        }

        fun drainToEof() {
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                if (count == 0 && read() < 0) break
            }
        }

        fun hasValidZipEndRecord(entriesSeen: Int): Boolean {
            val tailStart = if (tailSize == tail.size) tailPosition else 0
            val bytes = ByteArray(tailSize) { index -> tail[(tailStart + index) % tail.size] }
            if (bytes.size < ZIP_END_RECORD_MIN_BYTES) return false
            for (offset in bytes.size - ZIP_END_RECORD_MIN_BYTES downTo 0) {
                if (unsignedInt(bytes, offset) != ZIP_END_SIGNATURE) continue
                val commentLength = unsignedShort(bytes, offset + 20)
                if (offset + ZIP_END_RECORD_MIN_BYTES + commentLength != bytes.size) continue

                val diskNumber = unsignedShort(bytes, offset + 4)
                val directoryDisk = unsignedShort(bytes, offset + 6)
                val entriesOnDisk = unsignedShort(bytes, offset + 8)
                val totalEntries = unsignedShort(bytes, offset + 10)
                val directorySize = unsignedInt(bytes, offset + 12)
                val directoryOffset = unsignedInt(bytes, offset + 16)
                val endRecordAbsoluteOffset = bytesRead - bytes.size + offset

                if (diskNumber != 0 || directoryDisk != 0) continue
                if (entriesOnDisk != entriesSeen || totalEntries != entriesSeen) continue
                if (directorySize == ZIP64_SENTINEL || directoryOffset == ZIP64_SENTINEL) continue
                if (directoryOffset + directorySize != endRecordAbsoluteOffset) continue
                return true
            }
            return false
        }

        private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

        private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
            (bytes[offset].toLong() and 0xffL) or
                ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
                ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
                ((bytes[offset + 3].toLong() and 0xffL) shl 24)
    }

    private const val ZIP_END_RECORD_MIN_BYTES = 22
    private const val MAX_ZIP_END_RECORD_BYTES = ZIP_END_RECORD_MIN_BYTES + 65535
    private const val ZIP_END_SIGNATURE = 0x06054b50L
    private const val ZIP64_SENTINEL = 0xffffffffL
}
