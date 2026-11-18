package com.guardian.adblock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class GuardianDevUpdate(
    val versionName: String,
    val versionCode: Int,
    val sha256: String,
    val signerSha256: String,
    val apkUrl: String,
    val notes: String
)

internal object DevUpdateManager {
    private const val RELEASES_URL =
        "https://api.github.com/repos/justinhblee-prog/guardian/releases?per_page=30"
    private const val APK_ASSET = "guardian-android-dev.apk"
    private const val MANIFEST_ASSET = "guardian-android-dev.json"
    private const val PACKAGE_NAME = "com.guardian.adblock"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L

    fun checkForUpdate(): GuardianDevUpdate? {
        val releases = JSONArray(readHttps(RELEASES_URL, 2 * 1024 * 1024))
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft", true) || !release.optBoolean("prerelease", false)) continue
            if (!release.optString("tag_name").startsWith("guardian-dev-v")) continue
            val assets = release.optJSONArray("assets") ?: continue
            var manifestUrl: String? = null
            var apkUrl: String? = null
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                when (asset.optString("name")) {
                    MANIFEST_ASSET -> manifestUrl = asset.optString("browser_download_url")
                    APK_ASSET -> apkUrl = asset.optString("browser_download_url")
                }
            }
            if (manifestUrl.isNullOrBlank() || apkUrl.isNullOrBlank()) continue
            val manifest = JSONObject(readHttps(manifestUrl, 64 * 1024))
            if (manifest.optString("packageName") != PACKAGE_NAME) continue
            val versionCode = manifest.optInt("versionCode", 0)
            if (versionCode <= BuildConfig.VERSION_CODE) return null
            val digest = manifest.optString("sha256").lowercase()
            val signer = manifest.optString("signerSha256").lowercase()
            require(digest.matches(Regex("[a-f0-9]{64}"))) { "업데이트 SHA-256 형식이 올바르지 않습니다." }
            require(signer.matches(Regex("[a-f0-9]{64}"))) { "업데이트 서명 지문 형식이 올바르지 않습니다." }
            return GuardianDevUpdate(
                manifest.optString("versionName"),
                versionCode,
                digest,
                signer,
                apkUrl,
                release.optString("body").take(2000)
            )
        }
        return null
    }

    fun download(context: Context, update: GuardianDevUpdate): File {
        require(update.signerSha256 == installedSignerSha256(context)) {
            "업데이트 서명 인증서가 현재 설치된 개발 버전과 다릅니다. 설치를 중단했습니다."
        }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "guardian-${update.versionCode}.apk")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "APK 다운로드 실패: HTTP ${connection.responseCode}" }
            val expectedLength = connection.contentLengthLong
            check(expectedLength in 1..MAX_APK_BYTES) { "APK 파일 크기가 허용 범위를 벗어났습니다." }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_APK_BYTES) { "APK 파일이 너무 큽니다." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                    check(total == expectedLength) { "APK 다운로드 크기가 일치하지 않습니다." }
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        val actual = sha256(target)
        if (!actual.equals(update.sha256, ignoreCase = true)) {
            target.delete()
            error("다운로드한 APK의 SHA-256 검증에 실패했습니다.")
        }
        return target
    }

    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun installedSignerSha256(context: Context): String {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: error("현재 설치된 앱의 서명 정보를 읽을 수 없습니다.")
        val cert = signatures.firstOrNull()?.toByteArray()
            ?: error("현재 설치된 앱의 서명 인증서가 없습니다.")
        return MessageDigest.getInstance("SHA-256").digest(cert)
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readHttps(address: String, maxBytes: Int): String {
        require(address.startsWith("https://", ignoreCase = true)) { "업데이트 서버는 HTTPS만 허용합니다." }
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "업데이트 확인 실패: HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= maxBytes) { "업데이트 응답이 너무 큽니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
