package com.guardian.adblock

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.util.UUID

/**
 * Device-bound storage for server-signed Pro entitlements.
 *
 * The token is never trusted merely because it is present in preferences:
 * every read re-verifies the signature, expiry and installation binding.
 * DNS protection is deliberately independent from this store.
 */
internal object LicenseEntitlementStore {
    private const val PREFS = "guardian_license_v1"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_PAYLOAD = "signed_payload"
    private const val KEY_SIGNATURE = "signature"

    fun deviceBinding(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var installId = prefs.getString(KEY_INSTALL_ID, null)
        if (installId.isNullOrBlank()) {
            installId = UUID.randomUUID().toString()
            check(prefs.edit().putString(KEY_INSTALL_ID, installId).commit()) {
                "기기 등록 식별자를 저장하지 못했습니다."
            }
        }
        return sha256("guardian-android:$installId")
    }

    @Synchronized
    fun save(
        context: Context,
        payload: ByteArray,
        signatureBase64: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): LicenseEntitlementVerifier.Entitlement {
        val publicKey = BuildConfig.LICENSE_PUBLIC_KEY
        require(publicKey.isNotBlank()) { "라이선스 검증 공개키가 앱에 설정되지 않았습니다." }
        val binding = deviceBinding(context)
        val entitlement = LicenseEntitlementVerifier.verify(
            payload = payload,
            signatureBase64 = signatureBase64,
            publicKeyX509Base64 = publicKey,
            expectedDeviceBinding = binding,
            nowEpochSeconds = nowEpochSeconds
        )
        val encodedPayload = Base64.encodeToString(payload, Base64.NO_WRAP)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putString(KEY_PAYLOAD, encodedPayload)
            .putString(KEY_SIGNATURE, signatureBase64)
            .commit()) { "라이선스 정보를 안전하게 저장하지 못했습니다." }
        return entitlement
    }

    fun current(
        context: Context,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): LicenseEntitlementVerifier.Entitlement? {
        val publicKey = BuildConfig.LICENSE_PUBLIC_KEY
        if (publicKey.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encodedPayload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val signature = prefs.getString(KEY_SIGNATURE, null) ?: return null
        return try {
            val payload = Base64.decode(encodedPayload, Base64.DEFAULT)
            LicenseEntitlementVerifier.verify(
                payload = payload,
                signatureBase64 = signature,
                publicKeyX509Base64 = publicKey,
                expectedDeviceBinding = deviceBinding(context),
                nowEpochSeconds = nowEpochSeconds,
                allowExpired = true
            ).takeIf { it.expiresAt > nowEpochSeconds }
        } catch (_: Exception) {
            // Invalid signatures or device bindings are removed; a valid expired token is retained for server renewal.
            prefs.edit().remove(KEY_PAYLOAD).remove(KEY_SIGNATURE).commit()
            null
        }
    }

    /** Returns a verified device-bound reference even after expiry, solely so the server can renew it. */
    fun refreshableLicenseRef(context: Context): String? {
        val publicKey = BuildConfig.LICENSE_PUBLIC_KEY
        if (publicKey.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encodedPayload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val signature = prefs.getString(KEY_SIGNATURE, null) ?: return null
        return try {
            val payload = Base64.decode(encodedPayload, Base64.DEFAULT)
            LicenseEntitlementVerifier.verify(
                payload = payload,
                signatureBase64 = signature,
                publicKeyX509Base64 = publicKey,
                expectedDeviceBinding = deviceBinding(context),
                nowEpochSeconds = System.currentTimeMillis() / 1000L,
                allowExpired = true
            ).licenseRef
        } catch (_: Exception) {
            prefs.edit().remove(KEY_PAYLOAD).remove(KEY_SIGNATURE).commit()
            null
        }
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.edit().remove(KEY_PAYLOAD).remove(KEY_SIGNATURE).commit()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
