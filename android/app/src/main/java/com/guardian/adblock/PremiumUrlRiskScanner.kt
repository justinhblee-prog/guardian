package com.guardian.adblock

import java.net.URI
import java.util.Locale

internal enum class UrlRiskLevel { LOW, REVIEW, HIGH }

internal data class UrlRiskResult(
    val level: UrlRiskLevel,
    val host: String?,
    val indicators: List<String>,
    val valid: Boolean
)

/**
 * Offline URL triage only. This is a transparent heuristic, not a reputation,
 * malware, phishing, certificate, or app-code verdict.
 */
internal object PremiumUrlRiskScanner {
    private val shorteners = setOf("bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd")
    private val suspiciousTerms = listOf(
        "login", "verify", "wallet", "password", "secure", "account", "payment",
        "signin", "update", "confirm", "crypto"
    )

    /** Browser URL parsers accept several non-canonical IPv4 spellings. */
    private fun isCanonicalIpv4(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4) return false
        return octets.all { octet ->
            octet.isNotEmpty() &&
                octet.length <= 3 &&
                (octet == "0" || !octet.startsWith('0')) &&
                octet.all { it in '0'..'9' } &&
                (octet.toIntOrNull()?.let { it in 0..255 } == true)
        }
    }

    /**
     * WHATWG-style URL parsing treats one-to-four decimal or 0x-prefixed
     * hexadecimal labels as IPv4 candidates (including legacy octal-like
     * decimal forms). Flag them for review even when java.net.URI reports a
     * syntactically valid hostname rather than a canonical dotted quad.
     */
    private fun isBrowserIpv4Notation(host: String): Boolean {
        val labels = host.split('.')
        if (labels.size !in 1..4) return false
        return labels.all { label ->
            label.isNotEmpty() && (
                label.all { it in '0'..'9' } ||
                    (label.startsWith("0x") && label.length > 2 &&
                        label.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
                )
        }
    }

    fun scan(input: String): UrlRiskResult {
        val raw = input.trim()
        if (raw.isEmpty() || raw.length > 2048 || raw.any { it.isWhitespace() }) {
            return UrlRiskResult(UrlRiskLevel.HIGH, null, listOf("URL 형식이 비어 있거나 길이·공백 제한을 벗어났습니다."), false)
        }
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        val uri = try { URI(candidate) } catch (_: Exception) { null }
            ?: return UrlRiskResult(UrlRiskLevel.HIGH, null, listOf("URL 구문을 해석할 수 없습니다."), false)
        val host = uri.host?.lowercase(Locale.ROOT)
            ?: return UrlRiskResult(UrlRiskLevel.HIGH, null, listOf("호스트 이름이 없거나 올바르지 않습니다."), false)
        if (uri.userInfo != null) {
            return UrlRiskResult(UrlRiskLevel.HIGH, host, listOf("주소에 사용자 정보(@)가 포함되어 실제 호스트를 혼동시킬 수 있습니다."), true)
        }
        val indicators = mutableListOf<String>()
        if (!uri.scheme.equals("https", true)) indicators += "HTTPS가 아닌 연결입니다."
        if (host.startsWith("xn--") || host.split('.').any { it.startsWith("xn--") }) indicators += "국제화 도메인(Punycode)이 포함되어 있습니다."
        if (isCanonicalIpv4(host)) {
            indicators += "도메인 대신 IPv4 주소를 직접 사용합니다."
        } else if (isBrowserIpv4Notation(host)) {
            indicators += "IPv4 주소로 해석될 수 있는 비표준 숫자 호스트 표기입니다."
        }
        if (host.count { it == '.' } >= 4) indicators += "하위 도메인이 비정상적으로 많습니다."
        if (host in shorteners) indicators += "목적지를 숨길 수 있는 URL 단축 서비스입니다."
        val pathAndQuery = (uri.rawPath.orEmpty() + "?" + uri.rawQuery.orEmpty()).lowercase(Locale.ROOT)
        if (suspiciousTerms.any { pathAndQuery.contains(it) }) indicators += "로그인·인증·결제 관련 단어가 경로 또는 쿼리에 포함되어 있습니다."
        if (raw.count { it == '%' } >= 5) indicators += "퍼센트 인코딩이 과도하게 포함되어 있습니다."
        val level = when {
            indicators.size >= 3 || indicators.any { it.startsWith("HTTPS가 아닌") || it.startsWith("국제화") } -> UrlRiskLevel.HIGH
            indicators.isNotEmpty() -> UrlRiskLevel.REVIEW
            else -> UrlRiskLevel.LOW
        }
        return UrlRiskResult(level, host, indicators, true)
    }
}
