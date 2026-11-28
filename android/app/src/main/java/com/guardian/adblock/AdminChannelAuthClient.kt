package com.guardian.adblock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Admin bearer credentials are encrypted at rest with a per-install Android Keystore key. */
internal object AdminChannelAuthClient {
    private const val PREFS = "guardian_admin_channel_auth"
    private const val TOKEN = "encrypted_admin_token"
    private const val KEY_ALIAS = "guardian_admin_channel_aes_v1"
    private const val MAX_TOKEN_LENGTH = 512

    fun hasStoredToken(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(TOKEN)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).apply()
    }

    fun verifyAndStore(context: Context, token: String) {
        require(token.length in 32..MAX_TOKEN_LENGTH && token.none { it.isWhitespace() }) {
            "관리자 토큰 형식이 올바르지 않습니다."
        }
        val base = LicenseApiClient.configuredBaseUrl(context)
        require(base.isNotBlank()) {
            "관리자 인증 서버 주소가 아직 앱에 설정되지 않았습니다. 운영 라이선스 API 배포와 주소 설정이 필요합니다."
        }
        val endpoint = URL(parseLicenseApiBaseUrl(base).toExternalForm().trimEnd('/') + "/v1/admin/auth/verify")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
        }
        try {
            val status = connection.responseCode
            if (status != 200) {
                val message = when (status) {
                    401, 403 -> "관리자 토큰이 유효하지 않거나 권한이 없습니다."
                    429 -> "인증 요청이 많습니다. 잠시 후 다시 시도하세요."
                    in 500..599 -> "관리자 인증 서버를 사용할 수 없습니다. 잠시 후 다시 시도하세요."
                    else -> "관리자 인증 실패: HTTP $status"
                }
                throw IllegalStateException(message)
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 4096) { "관리자 인증 서버 응답이 너무 큽니다." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val response = JSONObject(decodeLicenseApiResponseUtf8(bytes))
            require(response.optBoolean("authenticated", false) &&
                response.optJSONArray("channels")?.let { array ->
                    (0 until array.length()).any { array.optString(it) == "admin" }
                } == true) {
                "서버가 관리자 업데이트 채널 권한을 확인하지 못했습니다."
            }
            saveEncrypted(context, token)
        } finally {
            connection.disconnect()
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun saveEncrypted(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN, encoded).commit()) {
            "관리자 인증 정보를 안전하게 저장하지 못했습니다."
        }
    }
}
