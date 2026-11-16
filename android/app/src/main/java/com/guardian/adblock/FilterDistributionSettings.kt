package com.guardian.adblock

import android.content.Context
import java.net.URI

/** Runtime-selectable HTTPS origin for signed filter manifests; signing trust stays in the APK. */
internal object FilterDistributionSettings {
    private const val PREFS = "guardian_server_settings"
    private const val KEY_FILTER_MANIFEST_BASE_URL = "filter_manifest_base_url"

    fun configuredBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FILTER_MANIFEST_BASE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.FILTER_MANIFEST_BASE_URL

    fun saveBaseUrl(context: Context, value: String) {
        val normalized = parseBaseUrl(value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FILTER_MANIFEST_BASE_URL, normalized).apply()
    }

    fun resetBaseUrl(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_FILTER_MANIFEST_BASE_URL).apply()
    }

    internal fun parseBaseUrl(value: String): String {
        val raw = value.trim().trimEnd('/')
        require(raw.isNotBlank()) { "필터 배포 서버 주소를 입력하세요." }
        val uri = try { URI(raw) } catch (error: Exception) {
            throw IllegalArgumentException("필터 서버 주소 형식이 올바르지 않습니다.", error)
        }
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.query == null && uri.fragment == null &&
                (uri.port == -1 || uri.port in 1..65535)
        ) {
            "필터 배포 서버 주소는 인증 정보·쿼리·fragment가 없는 HTTPS 주소여야 합니다."
        }
        return uri.toASCIIString()
    }
}
