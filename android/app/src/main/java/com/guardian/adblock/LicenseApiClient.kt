package com.guardian.adblock

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction


/** Parse only an HTTPS origin; API paths are appended from fixed internal constants. */
internal fun parseLicenseApiBaseUrl(value: String): URL {
    val base = value.trim()
    require(base.isNotBlank()) { "라이선스 API 주소가 앱 빌드에 설정되지 않았습니다." }
    val parsed = URL(base)
    require(
        parsed.protocol.equals("https", ignoreCase = true) &&
            parsed.host.isNotBlank() &&
            parsed.userInfo == null &&
            parsed.query == null &&
            parsed.ref == null &&
            (parsed.path.isEmpty() || parsed.path == "/") &&
            (parsed.port == -1 || parsed.port in 1..65535)
    ) {
        "라이선스 API 주소는 경로·인증 정보·쿼리·fragment가 없는 HTTPS origin이어야 합니다."
    }
    return URL(parsed.protocol, parsed.host, parsed.port, "")
}


internal fun decodeLicenseApiResponseUtf8(bytes: ByteArray): String =
    try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("라이선스 API 응답이 올바른 UTF-8이 아닙니다.")
    }

/** HTTP failures retain their status so background work can distinguish retryable outages. */
internal class LicenseApiException(
    val statusCode: Int,
    message: String
) : IOException(message) {
    val isRetryable: Boolean
        get() = statusCode == 408 || statusCode == 429 || statusCode >= 500
}

/** HTTPS-only client for the Guardian license service. Call from a worker thread. */
internal object LicenseApiClient {
    private const val MAX_RESPONSE_BYTES = 8192
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    // Serialize entitlement mutations with refresh/deactivation so a late refresh response
    // cannot re-save a Pro token after the device activation has been released.
    @Synchronized
    fun activate(context: Context, licenseKey: String, appVersion: String): LicenseEntitlementVerifier.Entitlement {
        require(licenseKey.isNotBlank() && licenseKey.length <= 100) { "라이선스 키를 확인하세요." }
        val response = post(
            "/v1/licenses/activate",
            JSONObject()
                .put("licenseKey", licenseKey.trim().uppercase())
                .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                .put("appVersion", appVersion.take(40))
        )
        return persistToken(context, response)
    }

    @Synchronized
    fun refresh(context: Context): LicenseEntitlementVerifier.Entitlement {
        val current = LicenseEntitlementStore.currentForRefresh(context)
            ?: throw IllegalStateException("갱신할 라이선스 정보가 없습니다.")
        val response = post(
            "/v1/licenses/refresh",
            JSONObject()
                .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                .put("licenseRef", current.licenseRef)
        )
        return persistToken(context, response)
    }

    @Synchronized
    fun deactivate(context: Context): Boolean {
        val current = LicenseEntitlementStore.current(context)
            ?: return LicenseEntitlementStore.clear(context)
        post(
            "/v1/licenses/deactivate",
            JSONObject()
                .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                .put("licenseRef", current.licenseRef)
        )
        return LicenseEntitlementStore.clear(context)
    }

    fun status(context: Context): JSONObject {
        val current = LicenseEntitlementStore.current(context)
            ?: return JSONObject().put("status", "free")
        return post(
            "/v1/licenses/status",
            JSONObject()
                .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                .put("licenseRef", current.licenseRef)
        )
    }

    private fun persistToken(
        context: Context,
        response: JSONObject
    ): LicenseEntitlementVerifier.Entitlement {
        val payload = response.optString("payload")
        val signature = response.optString("signature")
        require(payload.isNotBlank() && signature.isNotBlank()) {
            "서버 응답에 서명된 라이선스 토큰이 없습니다."
        }
        return LicenseEntitlementStore.save(
            context,
            payload.toByteArray(Charsets.UTF_8),
            signature
        )
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val baseUrl = parseLicenseApiBaseUrl(BuildConfig.LICENSE_API_BASE_URL)
        val endpoint = URL(baseUrl, path)
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
        }
        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readBounded(it) } ?: ByteArray(0)
            val responseText = decodeLicenseApiResponseUtf8(bytes)
            if (code !in 200..299) {
                val message = try {
                    JSONObject(responseText).optString("error", "라이선스 서버 요청에 실패했습니다.")
                } catch (_: Exception) {
                    "라이선스 서버 요청에 실패했습니다. (HTTP $code)"
                }
                throw LicenseApiException(code, message)
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_RESPONSE_BYTES) {
                "라이선스 서버 응답 크기가 제한을 초과했습니다."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
