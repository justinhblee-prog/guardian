package com.guardian.adblock

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies a compact, detached-signature filter manifest before a filter download is trusted.
 *
 * The signed payload is the exact UTF-8 manifest byte sequence (not a re-serialized map),
 * avoiding ambiguous canonicalization. The production RSA public key is supplied by the
 * application; private signing material must remain on the release server.
 */
internal object FilterManifestVerifier {
    private const val MAX_MANIFEST_BYTES = 16 * 1024
    private const val MAX_FUTURE_SKEW_SECONDS = 10 * 60L
    private val REQUIRED_KEYS = setOf(
        "manifestVersion", "generatedAt", "sourceId", "sourceVersion",
        "url", "sha256", "minimumRules"
    )

    data class Manifest(
        val manifestVersion: Int,
        val generatedAt: Long,
        val sourceId: String,
        val sourceVersion: String,
        val url: String,
        val sha256: String,
        val minimumRules: Int
    )

    fun parseAndVerify(
        manifestBytes: ByteArray,
        signatureBase64: String,
        publicKeyX509Base64: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        minimumGeneratedAtExclusive: Long = 0L
    ): Manifest {
        require(manifestBytes.isNotEmpty() && manifestBytes.size <= MAX_MANIFEST_BYTES) {
            "필터 manifest 크기가 올바르지 않습니다."
        }
        val values = LinkedHashMap<String, String>()
        val text = decodeUtf8Strict(manifestBytes)
        require(!text.contains('\u0000')) { "manifest에 허용되지 않는 문자가 있습니다." }
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val separator = line.indexOf('=')
            require(separator in 1 until line.lastIndex) { "manifest 항목 형식이 올바르지 않습니다." }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            require(key in REQUIRED_KEYS && value.isNotEmpty()) { "manifest에 알 수 없거나 비어 있는 항목이 있습니다." }
            require(values.put(key, value) == null) { "manifest에 중복 항목이 있습니다." }
        }
        require(values.keys == REQUIRED_KEYS) { "manifest 필수 항목이 누락되었습니다." }

        val publicKeyBytes = try {
            Base64.getDecoder().decode(publicKeyX509Base64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("필터 서명 공개키 인코딩이 올바르지 않습니다.", e)
        }
        val signatureBytes = try {
            Base64.getDecoder().decode(signatureBase64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("필터 서명 인코딩이 올바르지 않습니다.", e)
        }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes)) as? RSAPublicKey
            ?: throw IllegalArgumentException("필터 서명 공개키 형식이 올바르지 않습니다.")
        require(publicKey.modulus.bitLength() >= 2048) { "필터 서명 공개키가 너무 짧습니다." }
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(manifestBytes)
        require(verifier.verify(signatureBytes)) { "필터 manifest 서명이 유효하지 않습니다." }

        val manifest = Manifest(
            manifestVersion = values.getValue("manifestVersion").toIntOrNull()
                ?: throw IllegalArgumentException("manifestVersion 형식이 올바르지 않습니다."),
            generatedAt = values.getValue("generatedAt").toLongOrNull()
                ?: throw IllegalArgumentException("generatedAt 형식이 올바르지 않습니다."),
            sourceId = values.getValue("sourceId"),
            sourceVersion = values.getValue("sourceVersion"),
            url = values.getValue("url"),
            sha256 = values.getValue("sha256").lowercase(),
            minimumRules = values.getValue("minimumRules").toIntOrNull()
                ?: throw IllegalArgumentException("minimumRules 형식이 올바르지 않습니다.")
        )
        require(manifest.manifestVersion == 1) { "지원하지 않는 manifest 버전입니다." }
        require(manifest.generatedAt > 0L && manifest.minimumRules > 0) { "manifest 숫자 값이 범위를 벗어났습니다." }
        require(minimumGeneratedAtExclusive >= 0L && manifest.generatedAt > minimumGeneratedAtExclusive) {
            "이전 manifest보다 오래되었거나 동일한 manifest는 적용할 수 없습니다."
        }
        require(nowEpochSeconds > 0L && manifest.generatedAt <= nowEpochSeconds + MAX_FUTURE_SKEW_SECONDS) {
            "manifest 생성 시각이 현재보다 지나치게 미래입니다."
        }
        require(manifest.sourceId.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "sourceId 형식이 올바르지 않습니다." }
        require(manifest.sourceVersion.matches(Regex("[a-zA-Z0-9._+-]{1,64}")) &&
            manifest.sourceVersion != "." && manifest.sourceVersion != "..") {
            "sourceVersion 형식이 올바르지 않습니다."
        }
        require(manifest.sha256.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 해시 형식이 올바르지 않습니다." }
        val uri = URI(manifest.url)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.port in listOf(-1, 443)) {
            "필터 URL은 기본 HTTPS 포트(443)를 사용하는 주소여야 합니다."
        }
        return manifest
    }

    /** Decodes public filter feeds while replacing isolated malformed bytes in comments. */
    fun decodeUtf8Lenient(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun decodeUtf8Strict(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        throw IllegalArgumentException("UTF-8 인코딩이 올바르지 않습니다.", e)
    }

    /**
     * A published sourceVersion is immutable. A newer signed manifest timestamp must not
     * silently replace the bytes already accepted under the same version label.
     */
    fun requireImmutableSourceVersion(
        previousVersion: String?,
        previousSha256: String?,
        nextVersion: String,
        nextSha256: String
    ) {
        if (previousVersion == null || previousVersion != nextVersion) return
        require(
            previousSha256 != null &&
                previousSha256.matches(Regex("[a-fA-F0-9]{64}")) &&
                previousSha256.equals(nextSha256, ignoreCase = true)
        ) {
            "동일한 sourceVersion에 다른 필터 콘텐츠가 연결되어 있습니다."
        }
    }

    fun matchesSha256(content: ByteArray, expectedHex: String): Boolean {
        if (!expectedHex.matches(Regex("[a-fA-F0-9]{64}"))) return false
        val actual = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.US_ASCII),
            expectedHex.lowercase().toByteArray(StandardCharsets.US_ASCII)
        )
    }
}
