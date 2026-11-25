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

internal data class GuardianStableUpdate(
    val versionName: String, val versionCode: Int, val sha256: String,
    val signerSha256: String, val apkUrl: String, val notes: String
)

internal object GuardianStableUpdateManager {
    private const val RELEASES_URL = "https://api.github.com/repos/justinhblee-prog/guardian/releases?per_page=30"
    private const val APK_ASSET = "guardian-android.apk"
    private const val MANIFEST_ASSET = "guardian-android.json"
    private const val PACKAGE_NAME = "com.guardian.adblock"
    private const val MAX_APK_BYTES = 150L * 1024L * 1024L

    fun checkForUpdate(): GuardianStableUpdate? {
        val releases = JSONArray(readHttps(RELEASES_URL, 2 * 1024 * 1024))
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            val tag = release.optString("tag_name")
            if (!GuardianStableUpdatePolicy.isStableRelease(
                    release.optBoolean("draft", true), release.optBoolean("prerelease", false), tag
                )) continue
            val assets = release.optJSONArray("assets")
                ?: error("최신 정식 Release에 업데이트 자산 목록이 없습니다.")
            var manifestUrl: String? = null
            var apkUrl: String? = null
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                when (asset.optString("name")) {
                    MANIFEST_ASSET -> manifestUrl = asset.optString("browser_download_url")
                    APK_ASSET -> apkUrl = asset.optString("browser_download_url")
                }
            }
            require(!manifestUrl.isNullOrBlank() && !apkUrl.isNullOrBlank()) {
                "최신 정식 Release에 guardian-android.json 또는 guardian-android.apk가 없습니다."
            }
            require(isGuardianReleaseAssetUrl(manifestUrl, tag, MANIFEST_ASSET)) { "정식 업데이트 manifest 주소가 Guardian 공식 GitHub Release가 아닙니다." }
            require(isGuardianReleaseAssetUrl(apkUrl, tag, APK_ASSET)) { "정식 APK 주소가 Guardian 공식 GitHub Release가 아닙니다." }

            val manifest = JSONObject(readHttps(manifestUrl, 64 * 1024))
            require(manifest.optInt("manifestVersion", 0) == 1) { "지원하지 않는 정식 업데이트 manifest 버전입니다." }
            require(manifest.optString("packageName") == PACKAGE_NAME) { "정식 업데이트 manifest의 패키지 ID가 Guardian과 다릅니다." }
            val versionName = manifest.optString("versionName")
            val versionCode = manifest.optInt("versionCode", 0)
            require(versionName.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")) && versionCode > 0) {
                "정식 업데이트 버전 정보가 올바르지 않습니다."
            }
            if (!GuardianStableUpdatePolicy.isNewerVersion(BuildConfig.VERSION_CODE, versionCode)) return null
            require(tag == "v$versionName") { "정식 Release 태그와 manifest 버전이 일치하지 않습니다." }
            val digest = manifest.optString("sha256").lowercase()
            val signer = manifest.optString("signerSha256").lowercase()
            require(GuardianStableUpdatePolicy.isSha256(digest)) { "정식 업데이트 SHA-256 형식이 올바르지 않습니다." }
            require(GuardianStableUpdatePolicy.isSha256(signer)) { "정식 업데이트 서명 지문 형식이 올바르지 않습니다." }
            return GuardianStableUpdate(versionName, versionCode, digest, signer, apkUrl, release.optString("body").take(3000))
        }
        return null
    }

    fun download(context: Context, update: GuardianStableUpdate): File {
        require(GuardianStableUpdatePolicy.isNewerVersion(BuildConfig.VERSION_CODE, update.versionCode)) {
            "정식 업데이트 버전이 현재 설치된 앱보다 최신이 아닙니다."
        }
        require(GuardianStableUpdatePolicy.isSha256(update.sha256) && GuardianStableUpdatePolicy.isSha256(update.signerSha256)) {
            "정식 업데이트 검증 정보의 SHA-256 형식이 올바르지 않습니다."
        }
        require(isGuardianReleaseAssetUrl(update.apkUrl, "v${update.versionName}", APK_ASSET)) {
            "정식 APK 주소가 허용된 Guardian Release 자산이 아닙니다."
        }
        require(update.signerSha256 == installedSignerSha256(context)) {
            "정식 업데이트 서명 인증서가 현재 설치된 앱과 다릅니다. 기존 개발판에서 정식판으로 전환하려면 백업 후 정식 APK를 새로 설치해야 합니다."
        }
        val directory = File(context.cacheDir, "stable-updates").apply { mkdirs() }
        val target = File(directory, "guardian-${update.versionCode}.apk")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 45000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            connection.connect()
            check(isAllowedReleaseDownloadHost(connection.url)) { "정식 APK 다운로드가 허용되지 않은 서버로 리디렉션되었습니다." }
            check(connection.responseCode in 200..299) { "정식 APK 다운로드 실패: HTTP ${connection.responseCode}" }
            val expectedLength = connection.contentLengthLong
            check(expectedLength in 1..MAX_APK_BYTES) { "정식 APK 파일 크기가 허용 범위를 벗어났습니다." }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        check(total <= MAX_APK_BYTES) { "정식 APK 파일이 너무 큽니다." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                    check(total == expectedLength) { "정식 APK 다운로드 크기가 일치하지 않습니다." }
                }
            }
        } catch (error: Exception) {
            target.delete(); throw error
        } finally { connection.disconnect() }

        if (!sha256(target).equals(update.sha256, ignoreCase = true)) {
            target.delete(); error("다운로드한 정식 APK의 SHA-256 검증에 실패했습니다.")
        }
        verifyDownloadedPackage(context, target, update)
        return target
    }

    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings); return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent); return true
    }

    private fun verifyDownloadedPackage(context: Context, apk: File, update: GuardianStableUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("다운로드한 파일이 유효한 Android APK가 아닙니다.")
        check(info.packageName == PACKAGE_NAME) { "다운로드한 APK의 패키지 ID가 Guardian과 다릅니다." }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        check(archiveVersionCode == update.versionCode) { "다운로드한 APK의 versionCode가 업데이트 manifest와 다릅니다." }
        check(info.versionName == update.versionName) { "다운로드한 APK의 versionName이 업데이트 manifest와 다릅니다." }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION") info.signatures
        } ?: error("다운로드한 APK의 서명 정보를 읽을 수 없습니다.")
        val certificate = signatures.firstOrNull()?.toByteArray() ?: error("다운로드한 APK의 서명 인증서가 없습니다.")
        val actualSigner = fingerprint(certificate)
        check(actualSigner.equals(update.signerSha256, true)) { "다운로드한 APK의 서명 인증서가 manifest와 일치하지 않습니다." }
        check(actualSigner == installedSignerSha256(context)) { "다운로드한 APK의 서명 인증서가 설치된 앱과 다릅니다." }
    }

    private fun installedSignerSha256(context: Context): String {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, flags)
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION") info.signatures
        } ?: error("현재 설치된 앱의 서명 정보를 읽을 수 없습니다.")
        val certificate = signatures.firstOrNull()?.toByteArray() ?: error("현재 설치된 앱의 서명 인증서가 없습니다.")
        return fingerprint(certificate)
    }

    private fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun isGuardianReleaseAssetUrl(address: String, tag: String, assetName: String): Boolean = runCatching {
        if (!GuardianStableUpdatePolicy.isStableRelease(false, false, tag)) return@runCatching false
        if (assetName !in setOf(APK_ASSET, MANIFEST_ASSET)) return@runCatching false
        val uri = java.net.URI(address)
        uri.scheme.equals("https", true) &&
            uri.host?.equals("github.com", true) == true &&
            uri.port in setOf(-1, 443) &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath == "/justinhblee-prog/guardian/releases/download/$tag/$assetName"
    }.getOrDefault(false)

    internal fun isAllowedHttpsHost(url: URL): Boolean =
        url.protocol.equals("https", true) &&
            url.port in setOf(-1, 443) &&
            url.userInfo == null &&
            setOf(
                "api.github.com",
                "github.com",
                "release-assets.githubusercontent.com",
                "objects.githubusercontent.com"
            ).any { url.host.equals(it, true) }

    internal fun isAllowedReleaseDownloadHost(url: URL): Boolean =
        isAllowedHttpsHost(url) && !url.host.equals("api.github.com", true)

    private fun readHttps(address: String, maxBytes: Int): String {
        require(address.startsWith("https://", true)) { "업데이트 서버는 HTTPS만 허용합니다." }
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            connection.connect()
            check(isAllowedHttpsHost(connection.url)) { "정식 업데이트 확인 요청이 허용되지 않은 서버로 리디렉션되었습니다." }
            check(connection.responseCode in 200..299) { "정식 업데이트 확인 실패: HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    check(output.size() + count <= maxBytes) { "정식 업데이트 응답이 너무 큽니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
}
