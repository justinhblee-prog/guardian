package com.guardian.adblock

import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * Offline URL risk signals for the Premium phishing scanner.
 *
 * This policy is deliberately heuristic: it identifies suspicious URL
 * construction, not whether a destination is known malicious. A clean result
 * must never be presented as a guarantee that a site is safe.
 */
internal object PremiumPhishingScanPolicy {
    enum class Risk { INVALID, LOW, ELEVATED, HIGH }

    enum class Signal {
        NON_HTTP_SCHEME,
        EMBEDDED_CREDENTIALS,
        IP_LITERAL_HOST,
        PUNYCODE_HOST,
        MIXED_SCRIPT_HOST,
        DEEP_SUBDOMAIN_CHAIN,
        MISLEADING_HOST_IN_PATH
    }

    data class Result(
        val risk: Risk,
        val normalizedHost: String?,
        val signals: Set<Signal>,
        val explanation: String
    )

    fun inspect(rawUrl: String): Result {
        val input = rawUrl.trim()
        if (input.isEmpty() || input.length > MAX_URL_LENGTH ||
            input.any { it.isISOControl() || it.isWhitespace() }
        ) return invalid()

        val candidate = if (SCHEME_PATTERN.containsMatchIn(input)) input else "https://$input"
        val uri = try { URI(candidate) } catch (_: Exception) { return invalid() }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return invalid()
        if (scheme !in setOf("http", "https")) {
            return Result(Risk.HIGH, null, setOf(Signal.NON_HTTP_SCHEME),
                "웹 주소가 아닌 위험할 수 있는 URI 스킴입니다.")
        }
        if (uri.rawAuthority.isNullOrBlank() || uri.host.isNullOrBlank() ||
            uri.port !in -1..65535 || ((uri.fragment?.length ?: 0) > MAX_COMPONENT_LENGTH) ||
            ((uri.rawPath?.length ?: 0) > MAX_COMPONENT_LENGTH) ||
            ((uri.rawQuery?.length ?: 0) > MAX_COMPONENT_LENGTH)
        ) return invalid()

        val host = uri.host.trimEnd('.').lowercase(Locale.ROOT)
        if (host.isBlank() || host.length > 253 || host.split('.').any { it.isBlank() || it.length > 63 }) {
            return invalid()
        }

        val signals = linkedSetOf<Signal>()
        if (uri.rawUserInfo != null) signals += Signal.EMBEDDED_CREDENTIALS
        if (isIpLiteral(host)) signals += Signal.IP_LITERAL_HOST
        if (host.split('.').any { it.startsWith("xn--") }) signals += Signal.PUNYCODE_HOST
        if (containsMixedScripts(host)) signals += Signal.MIXED_SCRIPT_HOST
        if (host.split('.').size >= 5) signals += Signal.DEEP_SUBDOMAIN_CHAIN

        val decodedPath = try { uri.path.orEmpty().lowercase(Locale.ROOT) } catch (_: Exception) { "" }
        if (decodedPath.contains("http://") || decodedPath.contains("https://")) {
            signals += Signal.MISLEADING_HOST_IN_PATH
        }

        val highRisk = Signal.EMBEDDED_CREDENTIALS in signals ||
            Signal.IP_LITERAL_HOST in signals ||
            Signal.MIXED_SCRIPT_HOST in signals
        val elevated = signals.isNotEmpty()
        val risk = when {
            highRisk -> Risk.HIGH
            elevated -> Risk.ELEVATED
            else -> Risk.LOW
        }
        val explanation = when (risk) {
            Risk.HIGH -> "주소에 피싱에 악용될 수 있는 강한 위험 신호가 있습니다."
            Risk.ELEVATED -> "주소 구조에 추가 확인이 필요한 의심 신호가 있습니다."
            Risk.LOW -> "검사한 주소 구조에서 알려진 의심 패턴을 찾지 못했습니다. 안전을 보장하지는 않습니다."
            Risk.INVALID -> "올바른 HTTP 또는 HTTPS 웹 주소를 입력해 주세요."
        }
        return Result(risk, host, signals, explanation)
    }

    private fun invalid() = Result(
        Risk.INVALID, null, emptySet(), "올바른 HTTP 또는 HTTPS 웹 주소를 입력해 주세요."
    )

    private fun isIpLiteral(host: String): Boolean {
        if (host.startsWith("[") && host.endsWith("]")) return true
        if (host.matches(Regex("[0-9.]+"))) {
            val parts = host.split('.')
            return parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        }
        return false
    }

    private fun containsMixedScripts(host: String): Boolean {
        val unicode = try { IDN.toUnicode(host) } catch (_: Exception) { host }
        val scripts = unicode.codePoints().toArray().mapNotNull { codePoint ->
            val character = String(Character.toChars(codePoint))
            Character.UnicodeScript.of(codePoint).takeIf {
                Character.isLetter(codePoint) && it !in setOf(
                    Character.UnicodeScript.COMMON,
                    Character.UnicodeScript.INHERITED,
                    Character.UnicodeScript.UNKNOWN
                )
            }
        }.toSet()
        return scripts.size > 1
    }

    private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private const val MAX_URL_LENGTH = 4096
    private const val MAX_COMPONENT_LENGTH = 2048
}
