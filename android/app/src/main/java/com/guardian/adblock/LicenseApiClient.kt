package com.guardian.adblock

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.nio.charset.CodingErrorAction


/**
 * Validate the pinned verifier key before consuming a server-side device slot.
 * If the key is missing or malformed, activation must fail locally before HTTP.
 */
internal fun validateLicensePublicKeyConfiguration(encoded: String) {
    require(encoded.isNotBlank()) { "라이선스 검증 공개키가 앱에 설정되지 않았습니다. 운영 공개키를 포함해 앱을 다시 빌드하세요." }
    val bytes = try {
        java.util.Base64.getDecoder().decode(encoded)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("라이선스 검증 공개키 인코딩이 올바르지 않습니다.", error)
    }
    val key = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes)) as? RSAPublicKey
    } catch (error: Exception) {
        throw IllegalArgumentException("라이선스 검증 공개키 형식이 올바르지 않습니다.", error)
    } ?: throw IllegalArgumentException("라이선스 검증 공개키는 RSA 키여야 합니다.")
    require(key.modulus.bitLength() >= 2048) { "라이선스 검증 공개키는 RSA 2048비트 이상이어야 합니다." }
}

internal fun isLicensePublicKeyConfigurationValid(encoded: String): Boolean =
    try {
        validateLicensePublicKeyConfiguration(encoded)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

/** Match the server's UUID v4 contract for durable activation retries. */
internal fun isValidActivationRequestId(value: String): Boolean =
    value.matches(Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))

/** The status endpoint returns HTTP 200 with status=free for revoked devices. */
internal fun isActiveLicenseStatus(status: String): Boolean =
    status.equals("active", ignoreCase = true)

/** Only a bounded opaque reference may be used for compensating release after a failed activation save. */
internal fun isSafeLicenseRefForActivationCleanup(value: String): Boolean =
    value.matches(Regex("[a-zA-Z0-9._-]{8,128}"))

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

    /** A 403 from refresh means the server no longer grants this device an entitlement. */
    val invalidatesEntitlement: Boolean
        get() = statusCode == 403
}

/** HTTPS-only client for the Guardian license service. Call from a worker thread. */
internal object LicenseApiClient {
    private const val PREFS = "guardian_server_settings"
    private const val KEY_LICENSE_API_BASE_URL = "license_api_base_url"
    private const val KEY_PENDING_ACTIVATION_ID = "pending_activation_request_id"
    private const val KEY_PENDING_ACTIVATION_FINGERPRINT = "pending_activation_fingerprint"
    private const val MAX_RESPONSE_BYTES = 8192

    fun configuredBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LICENSE_API_BASE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.LICENSE_API_BASE_URL

    fun saveBaseUrl(context: Context, value: String) {
        val normalized = parseLicenseApiBaseUrl(value).toExternalForm().trimEnd('/')
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LICENSE_API_BASE_URL, normalized).apply()
    }

    fun resetBaseUrl(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_LICENSE_API_BASE_URL).apply()
    }
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    @Synchronized
    fun activate(context: Context, licenseKey: String, appVersion: String): LicenseEntitlementVerifier.Entitlement {
        require(licenseKey.isNotBlank() && licenseKey.length <= 100) { "라이선스 키를 확인하세요." }
        check(LicenseEntitlementStore.currentForRefresh(context) == null) {
            "이 기기에 기존 라이선스 등록 정보가 남아 있습니다. 만료된 등록도 포함되므로 기존 라이선스를 먼저 해제한 뒤 새 키를 활성화하세요."
        }
        // Fail locally before the server allocates a device activation slot.
        validateLicensePublicKeyConfiguration(BuildConfig.LICENSE_PUBLIC_KEY)
        val normalizedKey = licenseKey.trim().uppercase()
        val deviceBinding = LicenseEntitlementStore.deviceBinding(context)
        val requestId = pendingActivationRequestId(context, normalizedKey, deviceBinding)
        // Keep the same durable request ID until a signed token is verified and
        // committed locally. A retry can then replay the exact server response.
        val response = post(context,
            "/v1/licenses/activate",
            JSONObject()
                .put("requestId", requestId)
                .put("licenseKey", normalizedKey)
                .put("deviceBinding", deviceBinding)
                .put("appVersion", appVersion.take(40))
        )
        val entitlement = persistToken(context, response)
        clearPendingActivationRequest(context)
        return entitlement
    }

    @Synchronized
    fun refresh(context: Context): LicenseEntitlementVerifier.Entitlement {
        val current = LicenseEntitlementStore.currentForRefresh(context)
            ?: throw IllegalStateException("갱신할 라이선스 정보가 없습니다.")
        val response = try {
            post(context,
                "/v1/licenses/refresh",
                JSONObject()
                    .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                    .put("licenseRef", current.licenseRef)
            )
        } catch (error: LicenseApiException) {
            if (error.invalidatesEntitlement) {
                // A server-side revoke/deactivation/product disable must not leave
                // an offline Pro token active until its local expiry timestamp.
                if (LicenseEntitlementStore.clear(context)) {
                    clearPendingActivationRequest(context)
                }
            }
            throw error
        }
        return persistToken(context, response)
    }

    @Synchronized
    fun deactivate(context: Context): Boolean {
        // Never discard the local renewal credential merely because the app's
        // pinned verifier key is missing or malformed.
        validateLicensePublicKeyConfiguration(BuildConfig.LICENSE_PUBLIC_KEY)
        // Expired signed tokens do not grant Pro access, but their verified licenseRef
        // is still required to release the server-side device activation slot.
        val current = LicenseEntitlementStore.currentForRefresh(context)
            ?: return LicenseEntitlementStore.clear(context)
        post(context,
            "/v1/licenses/deactivate",
            JSONObject()
                .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                .put("licenseRef", current.licenseRef)
        )
        val entitlementCleared = LicenseEntitlementStore.clear(context)
        val pendingRequestCleared = entitlementCleared && clearPendingActivationRequest(context)
        return entitlementCleared && pendingRequestCleared
    }

    @Synchronized
    fun status(context: Context): JSONObject {
        val current = LicenseEntitlementStore.current(context)
            ?: return JSONObject().put("status", "free")
        val response = try {
            post(context,
                "/v1/licenses/status",
                JSONObject()
                    .put("deviceBinding", LicenseEntitlementStore.deviceBinding(context))
                    .put("licenseRef", current.licenseRef)
            )
        } catch (error: LicenseApiException) {
            if (error.invalidatesEntitlement) {
                if (LicenseEntitlementStore.clear(context)) {
                    clearPendingActivationRequest(context)
                }
            }
            throw error
        }
        // The API intentionally returns HTTP 200 + status=free when the
        // device activation was revoked or deactivated; treat that as loss
        // of entitlement, not as a successful Pro status check.
        if (!isActiveLicenseStatus(response.optString("status"))) {
            if (LicenseEntitlementStore.clear(context)) {
                clearPendingActivationRequest(context)
            }
        }
        return response
    }

    fun submitSupportTicket(
        context: Context,
        category: String,
        subject: String,
        message: String,
        email: String = ""
    ): JSONObject = post(
        context,
        "/v1/support/tickets",
        JSONObject()
            .put("category", category)
            .put("subject", subject)
            .put("message", message)
            .put("email", email)
    )

    fun lookupSupportTicket(
        context: Context,
        ticketRef: String,
        accessToken: String
    ): JSONObject {
        val normalizedRef = SupportInputPolicy.normalizeTicketReference(ticketRef)
            ?: throw IllegalArgumentException("문의 접수 번호 형식이 올바르지 않습니다.")
        val normalizedToken = accessToken.trim()
        require(SupportInputPolicy.isValidAccessToken(normalizedToken)) {
            "문의 조회 코드 형식이 올바르지 않습니다."
        }
        return post(
            context,
            "/v1/support/tickets/lookup",
            JSONObject()
                .put("ticketRef", normalizedRef)
                .put("accessToken", normalizedToken)
        )
    }

    private fun pendingActivationRequestId(context: Context, licenseKey: String, deviceBinding: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest("$licenseKey:$deviceBinding".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val savedFingerprint = prefs.getString(KEY_PENDING_ACTIVATION_FINGERPRINT, null)
        val savedRequestId = prefs.getString(KEY_PENDING_ACTIVATION_ID, null)
        if (savedFingerprint == fingerprint && savedRequestId != null &&
            isValidActivationRequestId(savedRequestId)) return savedRequestId
        val requestId = UUID.randomUUID().toString()
        check(prefs.edit()
            .putString(KEY_PENDING_ACTIVATION_FINGERPRINT, fingerprint)
            .putString(KEY_PENDING_ACTIVATION_ID, requestId)
            .commit()) { "활성화 요청을 안전하게 저장하지 못했습니다. 저장 공간을 확인한 뒤 다시 시도하세요." }
        return requestId
    }

    private fun clearPendingActivationRequest(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENDING_ACTIVATION_ID)
            .remove(KEY_PENDING_ACTIVATION_FINGERPRINT)
            .commit()

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

    private fun post(context: Context, path: String, body: JSONObject): JSONObject {
        val baseUrl = parseLicenseApiBaseUrl(configuredBaseUrl(context))
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
