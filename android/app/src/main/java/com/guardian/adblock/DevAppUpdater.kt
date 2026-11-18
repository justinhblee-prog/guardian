package com.guardian.adblock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class DevUpdateResult(
    val message: String,
    val readyToInstall: Boolean = false,
    val versionName: String = ""
)

internal object DevUpdatePolicy {
    fun isNewer(candidateVersionCode: Long, installedVersionCode: Long): Boolean =
        candidateVersionCode > installedVersionCode

    fun isValidSha256(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{64}"))
}

/**
 * Pre-release updater. APK installation is delegated to Android's package installer;
 * Android still requires the user to approve the installation.
 */
internal object DevAppUpdater {
    private const val FEED_URL =
        "https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-dev-updates/latest.json"
    private const val PREFS = "guardian_dev_app_updates"
    private const val LAST_CHECK = "last_check_at"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 64 * 1024

    fun checkForUpdate(
        context: Context,
        force: Boolean,
        callback: (DevUpdateResult) -> Unit
    ) {
        val app = context.applicationContext
        Thread({
            val result = try {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                if (!force && now - prefs.getLong(LAST_CHECK, 0L) < 6L * 60L * 60L * 1000L) {
                    return@Thread
                }
                prefs.edit().putLong(LAST_CHECK, now).apply()
                downloadIfAvailable(app)
            } catch (error: Exception) {
                DevUpdateResult("업데이트 확인 실패: ${error.message ?: error.javaClass.simpleName}")
            }
            (context as? android.app.Activity)?.runOnUiThread { callback(result) }
        }, "guardian-dev-app-update").start()
    }

    private fun downloadIfAvailable(context: Context): DevUpdateResult {
        val manifestBytes = readHttps(FEED_URL, MAX_MANIFEST_BYTES.toInt())
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        val packageName = manifest.getString("packageName")
        require(packageName == context.packageName) { "다른 앱의 업데이트 패키지입니다." }
        val versionName = manifest.getString("versionName")
        val versionCode = manifest.getLong("versionCode")
        val installed = installedVersionCode(context)
        if (!DevUpdatePolicy.isNewer(versionCode, installed)) {
            return DevUpdateResult("현재 최신 개발 버전입니다.")
        }

        val apkUrl = manifest.getString("apkUrl")
        val apkUri = Uri.parse(apkUrl)
        require(apkUri.scheme == "https" && apkUri.host == "raw.githubusercontent.com") {
            "허용되지 않은 APK 다운로드 주소입니다."
        }
        val expectedHash = manifest.getString("sha256").lowercase()
        require(DevUpdatePolicy.isValidSha256(expectedHash)) { "업데이트 SHA-256 형식이 올바르지 않습니다." }

        val bytes = readHttps(apkUrl, MAX_APK_BYTES.toInt())
        val actualHash = sha256(bytes)
        require(actualHash == expectedHash) { "APK SHA-256 검증에 실패했습니다." }

        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(directory, "guardian-$versionName.apk")
        FileOutputStream(apkFile).use { it.write(bytes) }
        verifyApk(context, apkFile, versionCode, packageName)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("prepared_apk", apkFile.absolutePath)
            .putString("prepared_version", versionName)
            .apply()
        return DevUpdateResult("새 개발 버전 $versionName 다운로드 및 검증을 완료했습니다.", true, versionName)
    }

    fun installPrepared(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString("prepared_apk", null) ?: return "설치할 업데이트 파일이 없습니다. 먼저 업데이트를 확인하세요."
        val apk = File(path)
        if (!apk.isFile || apk.length() == 0L) return "다운로드된 업데이트 파일이 없습니다. 다시 확인하세요."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Guardian의 알 수 없는 앱 설치 권한을 허용한 뒤 앱으로 돌아와 업데이트 확인을 다시 눌러주세요."
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return "Android 설치 화면을 열었습니다. 설치를 승인하면 기존 Guardian이 업데이트됩니다."
    }

    private fun verifyApk(context: Context, apk: File, expectedVersion: Long, expectedPackage: String) {
        @Suppress("DEPRECATION")
        val parsed = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        ) ?: error("APK 패키지 정보를 읽을 수 없습니다.")
        require(parsed.packageName == expectedPackage) { "APK 패키지명이 일치하지 않습니다." }
        require(parsed.longVersionCode == expectedVersion) { "APK 버전 코드가 manifest와 다릅니다." }
        val installed = installedSigners(context)
        val candidate = packageSigners(parsed)
        require(installed.isNotEmpty() && candidate == installed) {
            "설치된 앱과 APK의 서명 인증서가 다릅니다. 기존 앱을 덮어쓰지 않습니다."
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    @Suppress("DEPRECATION")
    private fun installedSigners(context: Context): Set<String> {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        )
        return if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            val signatures = if (info.signingInfo.hasMultipleSigners()) {
                info.signingInfo.apkContentsSigners
            } else {
                info.signingInfo.signingCertificateHistory
            }
            signatures.map { sha256(it.toByteArray()) }.toSet()
        } else {
            info.signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        }
    }

    @Suppress("DEPRECATION")
    private fun packageSigners(info: android.content.pm.PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            val signatures = if (info.signingInfo.hasMultipleSigners()) {
                info.signingInfo.apkContentsSigners
            } else {
                info.signingInfo.signingCertificateHistory
            }
            signatures.map { sha256(it.toByteArray()) }.toSet()
        } else {
            info.signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        }
    }

    private fun readHttps(address: String, maxBytes: Int): ByteArray {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            requestMethod = "GET"
        }
        try {
            require(connection.url.protocol == "https") { "HTTPS 연결만 허용합니다." }
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "업데이트 서버 응답 오류: HTTP ${connection.responseCode}"
            }
            val length = connection.contentLengthLong
            require(length <= maxBytes) { "업데이트 파일 크기가 제한을 초과합니다." }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxBytes) { "다운로드 크기가 제한을 초과합니다." }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
