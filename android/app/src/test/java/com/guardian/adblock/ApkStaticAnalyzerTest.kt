package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkStaticAnalyzerTest {
    private fun apk(vararg contents: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            contents.forEachIndexed { i, text ->
                zip.putNextEntry(ZipEntry(if (i == 0) "classes.dex" else "classes" + (i + 1) + ".dex"))
                zip.write(byteArrayOf(100, 101, 120, 10, 48, 51, 53, 0))
                zip.write(text.toByteArray(Charsets.ISO_8859_1))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun cleanDexDoesNotClaimThatApkIsSafe() {
        val r = ApkStaticAnalyzer.analyze(ByteArrayInputStream(apk("Lcom/example/NormalActivity;")))
        assertEquals(ApkStaticAnalyzer.Status.SCANNED, r.status)
        assertEquals(ApkStaticAnalyzer.Risk.LOW, r.risk)
        assertTrue(r.explanation.contains("전체 악성코드 검사를 의미하지 않습니다"))
    }

    @Test fun multipleSensitiveApiReferencesRequireManualReview() {
        val r = ApkStaticAnalyzer.analyze(ByteArrayInputStream(apk(
            "Ldalvik/system/DexClassLoader; Ljava/lang/Runtime;->exec Landroid/telephony/SmsManager;->send"
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

    @Test fun rejectsMalformedDexHeader() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write("not dex".toByteArray())
            zip.closeEntry()
        }
        assertEquals(ApkStaticAnalyzer.Status.INVALID_APK,
            ApkStaticAnalyzer.analyze(ByteArrayInputStream(out.toByteArray())).status)
    }
}
