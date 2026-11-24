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
import java.net.URL
import javax.net.ssl.HttpsURLConnection
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
        "https://api.github.com/repos/justinhblee-prog/guardian/releases?per_page=100"
    private const val APK_ASSET = "guardian-android-dev.apk"
    private const val MANIFEST_ASSET = "guardian-android-dev.json"
    private const val PACKAGE_NAME = "com.guardian.adblock"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private const val MAX_REDIRECTS = 5

    private data class ReleaseCandidate(
        val tagVersion: Int,
        val manifestUrl: String,
        val apkUrl: String,
        val notes: String
    )

    internal fun isNewerMatchingRelease(installedVersionCode: Int, tagVersion: Int, manifestVersionCode: Int): Boolean =
        tagVersion > installedVersionCode && manifestVersionCode == tagVersion

    fun checkForUpdate(): GuardianDevUpdate? {
        val releases = JSONArray(readHttps(RELEASES_URL, 4 * 1024 * 1024))
        val candidates = ArrayList<ReleaseCandidate>()
        val tagPattern = Regex("^guardian-dev-v(\\d+)$")

        // GitHub sorts releases by commit-created time, not semantic/version order.
        // Collect all valid development releases and select by the numeric tag version.
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", true) || !release.optBoolean("prerelease", false)) continue
            val tagVersion = tagPattern.matchEntire(release.optString("tag_name"))
                ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            val assets = release.optJSONArray("assets") ?: continue
            var manifestUrl: String? = null
            var apkUrl: String? = null
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                when (asset.optString("name")) {
                    MANIFEST_ASSET -> manifestUrl = asset.optString("browser_download_url")
                    APK_ASSET -> apkUrl = asset.optString("browser_download_url")
                }
            }
            if (manifestUrl.isNullOrBlank() || apkUrl.isNullOrBlank()) continue
            candidates += ReleaseCandidate(
                tagVersion = tagVersion,
                manifestUrl = manifestUrl,
                apkUrl = apkUrl,
                notes = release.optString("body").take(2000)
            )
        }

        for (candidate in candidates.sortedByDescending { it.tagVersion }) {
            require(isGuardianReleaseAssetUrl(candidate.manifestUrl, MANIFEST_ASSET, candidate.tagVersion)) {
                "개발 업데이트 manifest 주소가 허용된 GitHub Release가 아닙니다."
            }
            require(isGuardianReleaseAssetUrl(candidate.apkUrl, APK_ASSET, candidate.tagVersion)) {
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
        require(isGuardianReleaseAssetUrl(update.apkUrl, APK_ASSET, update.versionCode)) {
            "개발 APK 주소의 저장소·태그·자산 이름이 업데이트 버전과 일치하지 않습니다."
        }
        val directory = File(context.cacheDir, GuardianUpdateCachePaths.DEVELOPMENT_DIRECTORY).apply { mkdirs() }
        val target = File(directory, "guardian-${update.versionCode}.apk")
        val connection = openAllowedHttpsConnection(
            update.apkUrl, 15000, 30000, "application/octet-stream", ::isAllowedReleaseDownloadUrl
        )
        try {
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
        try {
            check(sha256(target).equals(update.sha256, ignoreCase = true)) {
                "다운로드한 APK의 SHA-256 검증에 실패했습니다."
            }
            verifyDownloadedPackage(context, target, update)
        } catch (error: Exception) {
            target.delete()
            throw error
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

    internal fun isGuardianReleaseAssetUrl(
        address: String,
        expectedAssetName: String? = null,
        expectedTagVersion: Int? = null
    ): Boolean = runCatching {
        val uri = java.net.URI(address)
        val parts = uri.path.split('/').filter { it.isNotEmpty() }
        val tag = parts.getOrNull(4).orEmpty()
        val tagVersion = Regex("^guardian-dev-v([1-9][0-9]{0,8})$")
            .matchEntire(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val assetName = parts.getOrNull(5)
        uri.scheme.equals("https", true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host.equals("github.com", true) &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            parts.size == 6 &&
            parts.take(4) == listOf("justinhblee-prog", "guardian", "releases", "download") &&
            tagVersion != null &&
            assetName in setOf(MANIFEST_ASSET, APK_ASSET) &&
            (expectedAssetName == null || assetName == expectedAssetName) &&
            (expectedTagVersion == null || tagVersion == expectedTagVersion)
    }.getOrDefault(false)

    internal fun isAllowedHttpsUrl(address: String): Boolean = runCatching {
        val uri = java.net.URI(address)
        uri.scheme.equals("https", true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.lowercase() in setOf(
                "api.github.com", "github.com",
                "release-assets.githubusercontent.com", "objects.githubusercontent.com"
            ) && uri.userInfo == null
    }.getOrDefault(false)

    internal fun isAllowedReleaseDownloadUrl(address: String): Boolean =
        isAllowedHttpsUrl(address) && runCatching {
            !java.net.URI(address).host.equals("api.github.com", true)
        }.getOrDefault(false)

    /** Check every redirect destination before connecting to it; automatic redirects are unsafe here. */
    private fun openAllowedHttpsConnection(
        address: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        accept: String,
        isAllowed: (String) -> Boolean
    ): HttpsURLConnection {
        var current = URL(address)
        var redirects = 0
        while (true) {
            check(isAllowed(current.toString())) { "업데이트 요청 주소가 허용되지 않은 HTTPS 서버입니다." }
            val connection = current.openConnection() as? HttpsURLConnection
                ?: error("HTTPS 업데이트 연결을 만들 수 없습니다.")
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            connection.setRequestProperty("Accept", accept)
            try {
                connection.connect()
                val response = connection.responseCode
                if (response in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    check(redirects < MAX_REDIRECTS) { "업데이트 서버 리디렉션 횟수가 너무 많습니다." }
                    require(!location.isNullOrBlank()) { "업데이트 서버 리디렉션 주소가 없습니다." }
                    val next = URL(current, location)
                    check(isAllowed(next.toString())) {
                        "업데이트 서버가 허용되지 않은 호스트로 리디렉션하려 했습니다."
                    }
                    current = next
                    redirects += 1
                    continue
                }
                check(response in 200..299) { "업데이트 서버 요청 실패: HTTP $response" }
                check(isAllowed(connection.url.toString())) {
                    "업데이트 요청의 최종 주소가 허용된 HTTPS 서버가 아닙니다."
                }
                return connection
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
    }

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
        val signerFingerprints = signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        check(DevUpdateManagerPolicy.hasSingleExpectedSigner(signerFingerprints, update.signerSha256)) {
            "다운로드한 APK는 manifest에 선언된 단일 서명 인증서와 일치하지 않습니다."
        }
        check(signerFingerprints.single() == installedSignerSha256(context)) {
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
        check(signatures.size == 1) {
            "현재 설치된 앱이 단일 서명 인증서를 사용하지 않아 자동 업데이트를 진행할 수 없습니다."
        }
        val cert = signatures.single().toByteArray()
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
        val connection = openAllowedHttpsConnection(
            address, 12000, 15000, "application/vnd.github+json", ::isAllowedHttpsUrl
        )
        try {
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
