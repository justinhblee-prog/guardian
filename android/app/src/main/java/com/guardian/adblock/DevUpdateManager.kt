package com.guardian.adblock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
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
    private const val RELEASES_ATOM_URL =
        "https://github.com/justinhblee-prog/guardian/releases.atom"
    private const val APK_ASSET = "guardian-android-dev.apk"
    private const val MANIFEST_ASSET = "guardian-android-dev.json"
    private const val PACKAGE_NAME = "com.guardian.adblock"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L

    private data class ReleaseCandidate(
        val tagVersion: Int,
        val manifestUrl: String,
        val apkUrl: String,
        val notes: String
    )

    internal fun isNewerMatchingRelease(installedVersionCode: Int, tagVersion: Int, manifestVersionCode: Int): Boolean =
        tagVersion > installedVersionCode && manifestVersionCode == tagVersion

    /**
     * GitHub's public REST release-list endpoint is unauthenticated in installed apps and
     * can be rate-limited. The public Releases Atom feed avoids that API quota. It does not
     * expose a prerelease flag, so accept only this repository's strictly named dev tags,
     * then require the release manifest to match the tag and installed package policy.
     */
    internal fun developmentReleaseVersionsFromAtom(atomXml: String): List<Int> {
        val atomFeed = Regex(
            """<feed\b[^>]*xmlns=["']http://www\.w3\.org/2005/Atom["']""",
            RegexOption.IGNORE_CASE
        )
        if (!atomFeed.containsMatchIn(atomXml)) return emptyList()

        val releaseLink = Regex(
            """href=["']https://github\.com/justinhblee-prog/guardian/releases/tag/guardian-dev-v([0-9]+)["']""",
            RegexOption.IGNORE_CASE
        )
        return releaseLink.findAll(atomXml)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .distinct()
            .sortedDescending()
            .toList()
    }

    private fun releaseAssetUrl(version: Int, assetName: String): String =
        "https://github.com/justinhblee-prog/guardian/releases/download/guardian-dev-v$version/$assetName"

    fun checkForUpdate(): GuardianDevUpdate? {
        val atomFeed = readHttps(RELEASES_ATOM_URL, 512 * 1024)
        val candidates = developmentReleaseVersionsFromAtom(atomFeed).map { tagVersion ->
            ReleaseCandidate(
                tagVersion = tagVersion,
                manifestUrl = releaseAssetUrl(tagVersion, MANIFEST_ASSET),
                apkUrl = releaseAssetUrl(tagVersion, APK_ASSET),
                notes = ""
            )
        }

        for (candidate in candidates) {
            require(isGuardianReleaseAssetUrl(candidate.manifestUrl)) {
                "개발 업데이트 manifest 주소가 허용된 GitHub Release가 아닙니다."
            }
            require(isGuardianReleaseAssetUrl(candidate.apkUrl)) {
                "개발 APK 주소가 허용된 GitHub Release가 아닙니다."
            }
            val manifest = JSONObject(readHttps(candidate.manifestUrl, 64 * 1024))
            if (manifest.optString("packageName") != PACKAGE_NAME) continue
            val versionCode = manifest.optInt("versionCode", 0)
            // The release tag and signed-off manifest must identify the same build,
            // and that build must be newer than the installed application.
            if (!isNewerMatchingRelease(BuildConfig.VERSION_CODE, candidate.tagVersion, versionCode)) continue
            val versionName = manifest.optString("versionName")
            if (!versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+-dev"))) continue

            val digest = manifest.optString("sha256").lowercase()
            val signer = manifest.optString("signerSha256").lowercase()
            require(digest.matches(Regex("[a-f0-9]{64}"))) { "업데이트 SHA-256 형식이 올바르지 않습니다." }
            require(signer.matches(Regex("[a-f0-9]{64}"))) { "업데이트 서명 지문 형식이 올바르지 않습니다." }
            return GuardianDevUpdate(
                versionName,
                versionCode,
                digest,
                signer,
                candidate.apkUrl,
                candidate.notes
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
            check(isAllowedReleaseDownloadHost(connection.url)) { "APK 다운로드가 허용되지 않은 서버로 리디렉션되었습니다." }
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
        verifyDownloadedPackage(context, target, update)
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

    internal fun isGuardianReleaseAssetUrl(address: String): Boolean = runCatching {
        val uri = java.net.URI(address)
        uri.scheme.equals("https", true) &&
            uri.host.equals("github.com", true) &&
            uri.path.startsWith("/justinhblee-prog/guardian/releases/download/") &&
            uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)

    private fun isAllowedHttpsHost(url: URL): Boolean =
        url.protocol.equals("https", true) &&
            (url.host.equals("github.com", true) ||
                url.host.equals("release-assets.githubusercontent.com", true) ||
                url.host.equals("objects.githubusercontent.com", true))

    private fun isAllowedReleaseDownloadHost(url: URL): Boolean =
        isAllowedHttpsHost(url) && !url.host.equals("api.github.com", true)

    private fun verifyDownloadedPackage(context: Context, apk: File, update: GuardianDevUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("다운로드한 파일이 유효한 Android APK가 아닙니다.")
        check(info.packageName == PACKAGE_NAME) { "다운로드한 APK의 패키지 ID가 Guardian과 다릅니다." }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        check(archiveVersionCode == update.versionCode) { "다운로드한 APK의 versionCode가 manifest와 다릅니다." }
        check(info.versionName == update.versionName) { "다운로드한 APK의 versionName이 manifest와 다릅니다." }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION") info.signatures
        } ?: error("다운로드한 APK의 서명 정보를 읽을 수 없습니다.")
        val certificate = signatures.firstOrNull()?.toByteArray()
            ?: error("다운로드한 APK의 서명 인증서가 없습니다.")
        val actualSigner = MessageDigest.getInstance("SHA-256").digest(certificate)
            .joinToString("") { "%02x".format(it) }
        check(actualSigner.equals(update.signerSha256, true)) {
            "다운로드한 APK의 서명 인증서가 manifest와 일치하지 않습니다."
        }
        check(actualSigner == installedSignerSha256(context)) {
            "다운로드한 APK의 서명 인증서가 설치된 앱과 다릅니다."
        }
    }

    private fun installedSignerSha256(context: Context): String {
        val signingFlag = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(signingFlag.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, signingFlag)
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
            setRequestProperty("Accept", "application/atom+xml, application/json;q=0.9, */*;q=0.8")
        }
        try {
            connection.connect()
            check(isAllowedHttpsHost(connection.url)) { "업데이트 확인 요청이 허용되지 않은 서버로 리디렉션되었습니다." }
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
