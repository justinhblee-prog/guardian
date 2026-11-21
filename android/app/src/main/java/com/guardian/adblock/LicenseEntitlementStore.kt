package com.guardian.adblock

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.util.UUID

/**
 * Device-bound storage for server-signed Pro entitlements.
 * Expired but correctly signed tokens are retained only as renewal credentials;
 * they never grant Pro access.
 */
internal fun shouldClearEntitlementForLicenseRef(currentLicenseRef: String?, revokedLicenseRef: String): Boolean =
    !currentLicenseRef.isNullOrBlank() && revokedLicenseRef.isNotBlank() && currentLicenseRef == revokedLicenseRef

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
        val entitlement = LicenseEntitlementVerifier.verify(
            payload = payload,
            signatureBase64 = signatureBase64,
            publicKeyX509Base64 = publicKey,
            expectedDeviceBinding = deviceBinding(context),
            nowEpochSeconds = nowEpochSeconds
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP))
            .putString(KEY_SIGNATURE, signatureBase64)
            .commit()) { "라이선스 정보를 안전하게 저장하지 못했습니다." }
        return entitlement
    }

    fun current(
        context: Context,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): LicenseEntitlementVerifier.Entitlement? {
        val verified = readVerified(context, nowEpochSeconds) ?: return null
        // An expired token is never an active entitlement.
        return verified.takeIf { it.expiresAt > nowEpochSeconds }
    }

    /**
     * Returns the last correctly signed device-bound token even after expiry.
     * Callers may use its licenseRef only to request server-side renewal.
     */
    fun currentForRefresh(
        context: Context,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): LicenseEntitlementVerifier.Entitlement? = readVerified(context, nowEpochSeconds)

    @Synchronized
    private fun readVerified(
        context: Context,
        nowEpochSeconds: Long
    ): LicenseEntitlementVerifier.Entitlement? {
        val publicKey = BuildConfig.LICENSE_PUBLIC_KEY
        if (!isLicensePublicKeyConfigurationValid(publicKey)) {
            // A broken app configuration is not evidence that the stored token is corrupt.
            // Preserve it so repairing the pinned key does not strand the server device slot.
            return null
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encodedPayload = prefs.getString(KEY_PAYLOAD, null) ?: return null
        val signature = prefs.getString(KEY_SIGNATURE, null) ?: return null
        return try {
            LicenseEntitlementVerifier.verify(
                payload = Base64.decode(encodedPayload, Base64.DEFAULT),
                signatureBase64 = signature,
                publicKeyX509Base64 = publicKey,
                expectedDeviceBinding = deviceBinding(context),
                nowEpochSeconds = nowEpochSeconds,
                allowExpired = true
            )
        } catch (_: Exception) {
            // Invalid signatures, malformed tokens and wrong-device tokens are discarded.
            prefs.edit().remove(KEY_PAYLOAD).remove(KEY_SIGNATURE).commit()
            null
        }
    }

    @Synchronized
    fun clearIfLicenseRef(context: Context, expectedLicenseRef: String): Boolean {
        if (expectedLicenseRef.isBlank()) return false
        val current = currentForRefresh(context) ?: return false
        if (!shouldClearEntitlementForLicenseRef(current.licenseRef, expectedLicenseRef)) return false
        return clear(context)
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
