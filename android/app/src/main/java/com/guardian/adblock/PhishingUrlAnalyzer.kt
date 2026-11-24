package com.guardian.adblock

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Offline URL triage. These heuristics identify observable warning signals only;
 * they are not a malware verdict or a substitute for live reputation intelligence.
 */
internal object PhishingUrlAnalyzer {
    enum class Risk { LOW, CAUTION, HIGH, INVALID }

    enum class Signal(val points: Int, val label: String) {
        INSECURE_HTTP(15, "암호화되지 않은 HTTP 연결"),
        IP_LITERAL_HOST(25, "도메인 대신 IP 주소 사용"),
        PUNYCODE_HOST(20, "국제화 도메인의 퓨니코드 사용"),
        MIXED_SCRIPT_HOST(30, "도메인에 서로 다른 문자 체계 혼합"),
        EMBEDDED_CREDENTIALS(25, "주소에 사용자 정보가 포함됨"),
        DECEPTIVE_HOST_WORDS(15, "로그인·인증 등 민감한 문구가 도메인에 포함됨"),
        MANY_SUBDOMAINS(10, "하위 도메인 단계가 많음"),
        URL_SHORTENER(15, "리디렉션 단축 도메인 사용"),
        UNUSUAL_PORT(10, "일반적이지 않은 웹 포트 사용"),
        EXECUTABLE_DOWNLOAD(20, "실행 가능한 파일 형식으로 연결")
    }

    data class Result(
        val inputAccepted: Boolean,
        val normalizedHost: String?,
        val risk: Risk,
        val score: Int,
        val signals: List<Signal>,
        val explanation: String
    )

    private val deceptiveTerms = setOf(
        "account", "auth", "bank", "billing", "confirm", "login",
        "password", "payment", "secure", "signin", "support", "verify", "wallet"
    )
    private val shorteners = setOf(
        "bit.ly", "t.co", "tinyurl.com", "is.gd", "ow.ly", "buff.ly",
        "rebrand.ly", "cutt.ly", "shorturl.at"
    )
    private val executableExtensions = setOf(".apk", ".app", ".bat", ".cmd", ".com", ".exe", ".js", ".msi", ".scr")

    fun analyze(rawInput: String): Result {
        val value = rawInput.trim()
        if (value.isEmpty() || value.length > 2048) return invalid()
        val uri = runCatching { URI(value) }.getOrNull() ?: return invalid()
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https")) return invalid()
        val authority = uri.rawAuthority?.takeIf { it.isNotBlank() } ?: return invalid()

        val signals = linkedSetOf<Signal>()
        val hostPort = authority.substringAfterLast('@').takeIf { it.isNotBlank() } ?: return invalid()
        if (authority.contains('@')) signals.add(Signal.EMBEDDED_CREDENTIALS)

        val host: String
        val port: Int?
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close <= 1) return invalid()
            host = hostPort.substring(1, close)
            if (!isValidIpv6Literal(host)) return invalid()
            val suffix = hostPort.substring(close + 1)
            if (suffix.isNotEmpty() && !suffix.startsWith(":")) return invalid()
            port = if (suffix.length > 1) suffix.substring(1).toIntOrNull() else null
            if (suffix == ":" || (suffix.isNotEmpty() && port == null)) return invalid()
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) {
                if (hostPort.indexOf(':') != colon) return invalid()
                host = hostPort.substring(0, colon)
                port = hostPort.substring(colon + 1).toIntOrNull() ?: return invalid()
            } else {
                host = hostPort
                port = null
            }
        }
        if (host.isBlank() || host.length > 253) return invalid()
        if (port != null && port !in 1..65535) return invalid()
        // A single terminal dot is the valid absolute-DNS-name form. Trimming
        // every trailing dot would silently turn malformed `example.com..`
        // into a different, apparently valid hostname.
        if (!host.contains(':') && host.endsWith("..")) return invalid()
        val normalizedHostInput = if (!host.contains(':') && host.endsWith('.')) {
            host.dropLast(1)
        } else host
        val asciiHost = runCatching {
            if (normalizedHostInput.contains(':')) normalizedHostInput.lowercase(Locale.ROOT)
            else IDN.toASCII(normalizedHostInput, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return invalid()
        if (asciiHost.split('.').any { it.isBlank() || it.length > 63 }) return invalid()
        if (asciiHost.length > 253) return invalid()
        if (isAmbiguousNumericHost(asciiHost) && !isIpLiteral(asciiHost)) return invalid()

        if (scheme == "http") signals.add(Signal.INSECURE_HTTP)
        if (isIpLiteral(asciiHost)) signals.add(Signal.IP_LITERAL_HOST)
        if (asciiHost.split('.').any { it.startsWith("xn--") }) signals.add(Signal.PUNYCODE_HOST)
        if (hasMixedScripts(host)) signals.add(Signal.MIXED_SCRIPT_HOST)
        if (asciiHost.split('.').size >= 5) signals.add(Signal.MANY_SUBDOMAINS)
        if (shorteners.any { asciiHost == it || asciiHost.endsWith(".$it") }) signals.add(Signal.URL_SHORTENER)
        val labels = asciiHost.split('.')
        if (labels.any { label -> deceptiveTerms.any { term -> label == term || label.contains("$term-") || label.contains("-$term") } }) {
            signals.add(Signal.DECEPTIVE_HOST_WORDS)
        }
        val schemeDefaultPort = if (scheme == "https") 443 else 80
        if (port != null && port != schemeDefaultPort) signals.add(Signal.UNUSUAL_PORT)
        if (hasExecutableDownloadPath(uri.rawPath.orEmpty())) {
            signals.add(Signal.EXECUTABLE_DOWNLOAD)
        }

        val score = signals.sumOf { it.points }.coerceIn(0, 100)
        val risk = when {
            score >= 60 -> Risk.HIGH
            score >= 25 -> Risk.CAUTION
            else -> Risk.LOW
        }
        val explanation = when (risk) {
            Risk.HIGH -> "여러 주의 신호가 확인되었습니다. 링크를 열지 말고 발신자와 공식 사이트를 별도로 확인하세요."
            Risk.CAUTION -> "주의 신호가 확인되었습니다. 링크의 실제 목적지와 발신자를 확인한 뒤 이용하세요."
            Risk.LOW -> "높은 위험을 나타내는 구조적 신호는 확인되지 않았습니다. 안전한 사이트임을 보증하지는 않습니다."
            Risk.INVALID -> "HTTP 또는 HTTPS 주소 형식인지 확인해 주세요."
        }
        return Result(true, asciiHost, risk, score, signals.toList(), explanation)
    }

    /**
     * Inspect the raw URL path and a small, bounded number of percent-decoding
     * passes. Browsers/servers commonly decode escaped path bytes before routing,
     * so `%2Eapk` and double-escaped `%252Eapk` must not bypass suffix triage.
     * Query text is intentionally excluded because it is not the URL path.
     */
    private fun hasExecutableDownloadPath(rawPath: String): Boolean {
        var candidate = rawPath
        repeat(3) {
            val normalized = candidate.lowercase(Locale.ROOT)
            if (executableExtensions.any(normalized::endsWith)) return true
            val decoded = runCatching {
                URLDecoder.decode(candidate.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            }.getOrNull() ?: return false
            if (decoded == candidate) return false
            candidate = decoded
        }
        return executableExtensions.any(candidate.lowercase(Locale.ROOT)::endsWith)
    }

    /** A bracketed URL host must be a syntactically valid IPv6 literal, not a DNS name. */
    /** A bracketed URL host must be a syntactically valid IPv6 literal, not a DNS name. */
    private fun isValidIpv6Literal(host: String): Boolean {
        if (!host.contains(':') || host.contains('%')) return false
        return runCatching { InetAddress.getByName(host) }.isSuccess
    }

    /** Reject non-canonical numeric forms that browser URL parsers may treat as IPv4. */
    private fun isAmbiguousNumericHost(host: String): Boolean {
        val labels = host.trimEnd('.').lowercase(Locale.ROOT).split('.')
        if (labels.isEmpty()) return false
        return labels.all { label ->
            label.isNotEmpty() && (
                label.all { it in '0'..'9' } ||
                    (label.startsWith("0x") && label.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
            )
        }
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.contains(':')) return true
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun hasMixedScripts(host: String): Boolean {
        val scripts = host.codePoints().toArray().asIterable().mapNotNull { codePoint ->
            val script = Character.UnicodeScript.of(codePoint)
            script.takeIf {
                it == Character.UnicodeScript.LATIN ||
                    it == Character.UnicodeScript.CYRILLIC ||
                    it == Character.UnicodeScript.GREEK
            }
        }.toSet()
        return scripts.size > 1
    }

    private fun invalid() = Result(
        inputAccepted = false,
        normalizedHost = null,
        risk = Risk.INVALID,
        score = 0,
        signals = emptyList(),
        explanation = "HTTP 또는 HTTPS 주소 형식인지 확인해 주세요."
    )
}
