package com.guardian.adblock

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies server-issued Pro entitlement tokens. The exact UTF-8 payload bytes are signed;
 * the app contains only the server public key. This class does not issue or persist licenses.
 */
internal object LicenseEntitlementVerifier {
    private val REQUIRED_KEYS = setOf(
        "tokenVersion", "product", "entitlement", "issuedAt", "expiresAt",
        "licenseRef", "deviceBinding"
    )
    private const val MAX_TOKEN_BYTES = 4096
    private const val CLOCK_SKEW_SECONDS = 300L

    data class Entitlement(
        val product: String,
        val entitlement: String,
        val issuedAt: Long,
        val expiresAt: Long,
        val licenseRef: String,
        val deviceBinding: String
    )

    fun verify(
        payload: ByteArray,
        signatureBase64: String,
        publicKeyX509Base64: String,
        expectedDeviceBinding: String,
        nowEpochSeconds: Long,
        allowExpired: Boolean = false
    ): Entitlement {
        require(payload.isNotEmpty() && payload.size <= MAX_TOKEN_BYTES) {
            "라이선스 토큰 크기가 올바르지 않습니다."
        }
        require(expectedDeviceBinding.matches(Regex("[a-fA-F0-9]{64}"))) {
            "기기 바인딩 값이 올바르지 않습니다."
        }
        val text = String(payload, StandardCharsets.UTF_8)
        require(!text.contains('\u0000')) { "토큰에 허용되지 않는 문자가 있습니다." }
        val values = LinkedHashMap<String, String>()
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val separator = line.indexOf('=')
            require(separator in 1 until line.lastIndex) { "토큰 항목 형식이 올바르지 않습니다." }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            require(key in REQUIRED_KEYS && value.isNotEmpty()) { "토큰에 알 수 없거나 비어 있는 항목이 있습니다." }
            require(values.put(key, value) == null) { "토큰에 중복 항목이 있습니다." }
        }
        require(values.keys == REQUIRED_KEYS) { "토큰 필수 항목이 누락되었습니다." }

        val keyBytes = try {
            Base64.getDecoder().decode(publicKeyX509Base64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("라이선스 공개키 인코딩이 올바르지 않습니다.", e)
        }
        val signatureBytes = try {
            Base64.getDecoder().decode(signatureBase64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("라이선스 서명 인코딩이 올바르지 않습니다.", e)
        }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes)) as? RSAPublicKey
            ?: throw IllegalArgumentException("라이선스 공개키 형식이 올바르지 않습니다.")
        require(publicKey.modulus.bitLength() >= 2048) { "라이선스 공개키가 너무 짧습니다." }
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        require(verifier.verify(signatureBytes)) { "라이선스 토큰 서명이 유효하지 않습니다." }

        val tokenVersion = values.getValue("tokenVersion").toIntOrNull()
            ?: throw IllegalArgumentException("tokenVersion 형식이 올바르지 않습니다.")
        val issuedAt = values.getValue("issuedAt").toLongOrNull()
            ?: throw IllegalArgumentException("issuedAt 형식이 올바르지 않습니다.")
        val expiresAt = values.getValue("expiresAt").toLongOrNull()
            ?: throw IllegalArgumentException("expiresAt 형식이 올바르지 않습니다.")
        val deviceBinding = values.getValue("deviceBinding")
        val entitlement = Entitlement(
            product = values.getValue("product"),
            entitlement = values.getValue("entitlement"),
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            licenseRef = values.getValue("licenseRef"),
            deviceBinding = deviceBinding
        )
        require(tokenVersion == 1) { "지원하지 않는 라이선스 토큰 버전입니다." }
        require(entitlement.product == "guardian-android" && entitlement.entitlement == "pro") {
            "이 앱에서 사용할 수 없는 라이선스 권한입니다."
        }
        require(issuedAt > 0L && expiresAt > issuedAt) { "라이선스 유효 기간이 올바르지 않습니다." }
        require(issuedAt <= nowEpochSeconds + CLOCK_SKEW_SECONDS) { "라이선스 토큰 발급 시각이 미래입니다." }
        if (!allowExpired) require(expiresAt > nowEpochSeconds) { "라이선스가 만료되었습니다." }
        require(entitlement.licenseRef.matches(Regex("[a-zA-Z0-9._-]{8,128}"))) {
            "라이선스 참조 값이 올바르지 않습니다."
        }
        require(deviceBinding.matches(Regex("[a-fA-F0-9]{64}")) &&
            deviceBinding.equals(expectedDeviceBinding, ignoreCase = true)) {
            "이 기기에 등록된 라이선스가 아닙니다."
        }
        return entitlement
    }
}
