package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkStaticAnalyzerTest {
    private fun dexFixture(text: String, reverseEndian: Boolean = false): ByteArray {
        val payload = text.toByteArray(Charsets.ISO_8859_1)
        val bytes = ByteArray(0x70 + payload.size)
        byteArrayOf(100, 101, 120, 10, 48, 51, 53, 0).copyInto(bytes)
        fun putInt(offset: Int, value: Int) {
            repeat(4) { index ->
                val shift = if (reverseEndian) (3 - index) * 8 else index * 8
                bytes[offset + index] = (value ushr shift).toByte()
            }
        }
        putInt(32, bytes.size)
        putInt(36, 0x70)
        // The marker has the same numeric value in the file's byte order;
        // reading its bytes as little-endian distinguishes standard vs reverse.
        putInt(40, 0x12345678)
        payload.copyInto(bytes, 0x70)
        return bytes
    }

    private fun apk(vararg contents: String): ByteArray =
        apkWithRawDex(*contents.map(::dexFixture).toTypedArray())

    private fun apkWithRawDex(vararg dexFiles: ByteArray, comment: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setComment(comment)
            dexFiles.forEachIndexed { i, bytes ->
                zip.putNextEntry(ZipEntry(if (i == 0) "classes.dex" else "classes" + (i + 1) + ".dex"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun rejectsArchivesThatExceedTheEntryCountLimit() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            repeat(10_001) { index ->
                zip.putNextEntry(ZipEntry("assets/entry-$index"))
                zip.closeEntry()
            }
        }
        val result = ApkStaticAnalyzer.analyze(ByteArrayInputStream(out.toByteArray()))
        assertEquals(ApkStaticAnalyzer.Status.LIMIT_EXCEEDED, result.status)
    }

    @Test fun cleanDexDoesNotClaimThatApkIsSafe() {
        val r = ApkStaticAnalyzer.analyze(ByteArrayInputStream(apk("Lcom/example/NormalActivity;")))
        assertEquals(ApkStaticAnalyzer.Status.SCANNED, r.status)
        assertEquals(ApkStaticAnalyzer.Risk.LOW, r.risk)
        assertTrue(r.explanation.contains("전체 악성코드 검사를 의미하지 않습니다"))
    }

    @Test fun multipleSensitiveApiReferencesRequireManualReview() {
        val r = ApkStaticAnalyzer.analyze(ByteArrayInputStream(apk(
            "Ldalvik/system/DexClassLoader; Ljava/lang/Runtime; exec Landroid/telephony/SmsManager; send"
        )))
        assertEquals(ApkStaticAnalyzer.Risk.HIGH, r.risk)
        assertEquals(3, r.signals.size)
    }

    @Test fun scansMultipleDexFiles() {
        val r = ApkStaticAnalyzer.analyze(ByteArrayInputStream(apk(
            "Landroid/accessibilityservice/AccessibilityService;",
            "Landroid/app/admin/DevicePolicyManager; Landroid/content/pm/PackageInstaller;"
        )))
        assertEquals(2, r.dexCount)
        assertTrue(r.signals.contains(ApkStaticAnalyzer.Signal.ACCESSIBILITY))
        assertTrue(r.signals.contains(ApkStaticAnalyzer.Signal.DEVICE_ADMIN))
        assertTrue(r.signals.contains(ApkStaticAnalyzer.Signal.INSTALLER))
    }

    @Test fun rejectsInvalidZipAndArchivesWithoutDex() {
        assertEquals(ApkStaticAnalyzer.Status.INVALID_APK,
            ApkStaticAnalyzer.analyze(ByteArrayInputStream("not an apk".toByteArray())).status)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("assets/readme.txt"))
            zip.write("no dex".toByteArray())
            zip.closeEntry()
        }
        assertEquals(ApkStaticAnalyzer.Status.INVALID_APK,
            ApkStaticAnalyzer.analyze(ByteArrayInputStream(out.toByteArray())).status)
    }

    @Test fun acceptsMaximumLengthZipCommentAcrossTailBufferWrapAround() {
        val archive = apkWithRawDex(
            dexFixture("Lcom/example/Commented;") ,
            comment = "c".repeat(65535)
        )
        val result = ApkStaticAnalyzer.analyze(ByteArrayInputStream(archive))
        assertEquals(ApkStaticAnalyzer.Status.SCANNED, result.status)
        assertEquals(1, result.dexCount)
    }

    @Test fun rejectsMissingMalformedOrTrailingZipEndRecord() {
        val validArchive = apk("Lcom/example/Valid;" )
        val withoutEndRecord = validArchive.copyOf(validArchive.size - 22)
        val invalidCommentLength = validArchive.copyOf().also { bytes ->
            bytes[bytes.size - 2] = 1
            bytes[bytes.size - 1] = 0
        }
        val trailingBytes = validArchive + byteArrayOf(0x41, 0x42)

        listOf(withoutEndRecord, invalidCommentLength, trailingBytes).forEach { malformed ->
            assertEquals(
                ApkStaticAnalyzer.Status.INVALID_APK,
                ApkStaticAnalyzer.analyze(ByteArrayInputStream(malformed)).status
            )
        }
    }

    @Test fun rejectsMalformedDexHeader() {
        assertEquals(
            ApkStaticAnalyzer.Status.INVALID_APK,
            ApkStaticAnalyzer.analyze(ByteArrayInputStream(apkWithRawDex("not dex".toByteArray()))).status
        )
        assertEquals(
            ApkStaticAnalyzer.Status.INVALID_APK,
            ApkStaticAnalyzer.analyze(ByteArrayInputStream(
                apkWithRawDex(byteArrayOf(100, 101, 120, 10, 48, 51, 53, 0))
            )).status
        )
    }

    @Test fun acceptsLittleEndianAndReverseEndianDexHeaderValues() {
        val littleEndian = ApkStaticAnalyzer.analyze(
            ByteArrayInputStream(apkWithRawDex(dexFixture("Lcom/example/Normal;")))
        )
        val reverseEndian = ApkStaticAnalyzer.analyze(
            ByteArrayInputStream(apkWithRawDex(dexFixture("Lcom/example/Reverse;", reverseEndian = true)))
        )
        assertEquals(ApkStaticAnalyzer.Status.SCANNED, littleEndian.status)
        assertEquals(ApkStaticAnalyzer.Status.SCANNED, reverseEndian.status)
    }

    @Test fun rejectsDexHeaderWithWrongDeclaredFileSizeOrHeaderSize() {
        val wrongFileSize = dexFixture("Lcom/example/App;").also { bytes ->
            val value = bytes.size - 1
            repeat(4) { index -> bytes[32 + index] = (value ushr (index * 8)).toByte() }
        }
        val wrongHeaderSize = dexFixture("Lcom/example/App;").also { bytes ->
            bytes[36] = 0x68
        }
        listOf(wrongFileSize, wrongHeaderSize).forEach { malformed ->
            assertEquals(
                ApkStaticAnalyzer.Status.INVALID_APK,
                ApkStaticAnalyzer.analyze(ByteArrayInputStream(apkWithRawDex(malformed))).status
            )
        }
    }
}
