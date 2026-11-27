package com.guardian.adblock

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object GuardianUpdateAdminCredential {
    private const val PREFS = "guardian_update_admin_credential_v1"
    private const val KEY_ALIAS = "guardian_update_admin_aes_v1"
    private const val CIPHER = "AES/GCM/NoPadding"

    fun save(context: Context, token: String) {
        require(token.length in 32..512 && token.none { it.isWhitespace() }) {
            "관리자 인증 토큰 형식이 올바르지 않습니다."
        }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("value", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val encrypted = Base64.decode(prefs.getString("value", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

internal data class GuardianPrivateUpdate(
    val channel: String,
    val versionName: String,
    val versionCode: Int,
    val sha256: String,
    val signerSha256: String,
    val sizeBytes: Long,
    val apkUrl: String,
    val notes: String
)

internal object GuardianPrivateUpdateManager {
    private const val PACKAGE_NAME = "com.guardian.adblock"
    private const val STORAGE_HOST_SUFFIX = ".storage.c-5.ap-southeast-1.aws.neon.tech"
    private const val STORAGE_BUCKET_PATH = "/guardian-private-updates/android/"
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L

    fun authorize(context: Context, token: String) {
        request(context, "/v1/updates/dev", token.trim())
        GuardianUpdateAdminCredential.save(context, token.trim())
    }

    fun checkForUpdate(context: Context, channel: String, onProgress: ((String) -> Unit)? = null): GuardianPrivateUpdate? {
        require(channel == "beta" || channel == "dev") { "비공개 업데이트 채널이 올바르지 않습니다." }
        val token = GuardianUpdateAdminCredential.load(context)
            ?: error("관리자 인증이 필요합니다. 관리자 채널 인증을 먼저 완료하세요.")
        onProgress?.invoke("${channel.uppercase()} 채널 인증 및 업데이트 목록 확인 중…")
        val response = request(context, "/v1/updates/$channel", token)
        val update = response.optJSONObject("update") ?: return null
        val versionCode = update.optInt("versionCode", 0)
        val versionName = update.optString("versionName")
        val sha256 = update.optString("sha256").lowercase()
        val signer = update.optString("signerSha256").lowercase()
        val size = update.optLong("sizeBytes", 0)
        val apkUrl = update.optString("apkUrl")
        require(versionCode > BuildConfig.VERSION_CODE) { "현재 설치 버전보다 새로운 업데이트가 없습니다." }
        require(update.optString("channel") == channel) { "서버 업데이트 채널 정보가 일치하지 않습니다." }
        require(update.optInt("minSupportedVersionCode", 1) <= BuildConfig.VERSION_CODE) {
            "이 업데이트는 현재 설치 버전에서 바로 적용할 수 없습니다."
        }
        val versionPattern = if (channel == "beta") {
            Regex("^[0-9]+\\.[0-9]+\\.[0-9]+-beta(?:\\.[0-9]+)?$")
        } else {
            Regex("^[0-9]+\\.[0-9]+\\.[0-9]+-dev$")
        }
        require(versionPattern.matches(versionName)) { "업데이트 버전 이름과 채널이 일치하지 않습니다." }
        require(sha256.matches(Regex("^[a-f0-9]{64}$")) && signer.matches(Regex("^[a-f0-9]{64}$"))) {
            "업데이트 무결성 정보가 올바르지 않습니다."
        }
        require(size in 1..MAX_APK_BYTES) { "업데이트 APK 크기가 허용 범위를 벗어났습니다." }
        require(isPrivateStorageUrl(apkUrl)) { "APK 다운로드 주소가 Guardian 비공개 저장소가 아닙니다." }
        return GuardianPrivateUpdate(channel, versionName, versionCode, sha256, signer, size, apkUrl, update.optString("releaseNotes").take(4000))
    }

    fun download(context: Context, update: GuardianPrivateUpdate, onProgress: ((String) -> Unit)? = null): File {
        require(isPrivateStorageUrl(update.apkUrl)) { "APK 다운로드 주소가 Guardian 비공개 저장소가 아닙니다." }
        require(update.signerSha256 == installedSignerSha256(context)) {
            "업데이트 서명 인증서가 현재 설치된 앱과 다릅니다. 설치를 중단했습니다."
        }
        val target = File(File(context.cacheDir, "private-updates").apply { mkdirs() }, "guardian-${update.channel}-${update.versionCode}.apk")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 45000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }
        try {
            connection.connect()
            check(isPrivateStorageUrl(connection.url.toString())) { "APK 다운로드가 허용되지 않은 서버로 이동했습니다." }
            check(connection.responseCode in 200..299) { "비공개 APK 다운로드 실패: HTTP ${connection.responseCode}" }
            val expectedLength = connection.contentLengthLong
            check(expectedLength == update.sizeBytes && expectedLength in 1..MAX_APK_BYTES) {
                "비공개 APK 다운로드 크기가 manifest와 일치하지 않습니다."
            }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_APK_BYTES) { "APK 파일이 너무 큽니다." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (total % (1024 * 1024) < count) onProgress?.invoke("APK 다운로드 중… ${total / 1024 / 1024} MB")
                    }
                    output.fd.sync()
                    check(total == update.sizeBytes) { "APK 다운로드 크기가 일치하지 않습니다." }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    check(actualHash.equals(update.sha256, true)) { "비공개 APK SHA-256 검증에 실패했습니다." }
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        verifyPackage(context, target, update)
        return target
    }

    private fun verifyPackage(context: Context, apk: File, update: GuardianPrivateUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("다운로드한 파일이 유효한 Android APK가 아닙니다.")
        check(info.packageName == PACKAGE_NAME) { "APK 패키지 ID가 Guardian과 다릅니다." }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        check(versionCode == update.versionCode && info.versionName == update.versionName) {
            "APK 버전 정보가 업데이트 manifest와 일치하지 않습니다."
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION") info.signatures
        } ?: error("APK 서명 정보를 읽을 수 없습니다.")
        val cert = signatures.firstOrNull()?.toByteArray() ?: error("APK 서명 인증서가 없습니다.")
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
        check(fingerprint.equals(update.signerSha256, true) && fingerprint == installedSignerSha256(context)) {
            "APK 서명 인증서가 현재 설치된 앱 또는 manifest와 다릅니다."
        }
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
        val cert = signatures.firstOrNull()?.toByteArray() ?: error("현재 설치된 앱의 서명 인증서가 없습니다.")
        return MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
    }

    private fun isPrivateStorageUrl(address: String): Boolean = runCatching {
        val uri = java.net.URI(address)
        uri.scheme.equals("https", true) &&
            uri.host.endsWith(STORAGE_HOST_SUFFIX, true) &&
            uri.rawPath.startsWith(STORAGE_BUCKET_PATH) &&
            uri.rawUserInfo == null && uri.rawFragment == null
    }.getOrDefault(false)

    private fun request(context: Context, path: String, token: String): JSONObject {
        val base = BuildConfig.LICENSE_API_BASE_URL.trimEnd('/')
        require(base.startsWith("https://", true)) { "업데이트 API는 HTTPS만 허용합니다." }
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            // Never forward the administrator bearer token through an HTTP redirect.
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.connect()
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 64 * 1024) { "업데이트 API 응답이 너무 큽니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: ByteArray(0)
            if (status == 401) error("관리자 인증에 실패했습니다. 토큰을 확인하세요.")
            check(status in 200..299) { "업데이트 API 요청 실패: HTTP $status" }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}
