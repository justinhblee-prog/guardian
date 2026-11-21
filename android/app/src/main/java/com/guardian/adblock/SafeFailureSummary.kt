package com.guardian.adblock

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import javax.net.ssl.SSLException

/**
 * Produces user-safe failure descriptions for VPN startup and DNS transport paths.
 * Throwable messages can contain resolver addresses, URLs, local paths, or response text.
 */
internal object SafeFailureSummary {
    fun codeFor(error: Throwable): String {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = error
        var fallback = "UNKNOWN_FAILURE"
        var depth = 0
        while (current != null && depth < 8 && seen.add(current)) {
            val code = when (current) {
                is SocketTimeoutException -> "NETWORK_TIMEOUT"
                is UnknownHostException -> "DNS_RESOLUTION_FAILED"
                is SSLException -> "TLS_FAILURE"
                is ConnectException -> "CONNECTION_FAILED"
                is SecurityException -> "ACCESS_DENIED"
                is IllegalArgumentException -> "INVALID_CONFIGURATION_OR_RESPONSE"
                is IllegalStateException -> "INVALID_RUNTIME_STATE"
                is IOException -> "IO_FAILURE"
                else -> null
            }
            if (code != null) {
                fallback = code
                if (code != "INVALID_RUNTIME_STATE") return code
            }
            current = current.cause
            depth++
        }
        return fallback
    }

    fun describe(error: Throwable): String = describeCode(codeFor(error))

    internal fun describeCode(code: String): String = when (code) {
        "NETWORK_TIMEOUT" -> "네트워크 연결 시간 초과"
        "DNS_RESOLUTION_FAILED" -> "DNS 서버 주소 확인 실패"
        "TLS_FAILURE" -> "보안 연결 검증 실패"
        "CONNECTION_FAILED" -> "서버 연결 실패"
        "ACCESS_DENIED" -> "접근 권한 거부"
        "INVALID_CONFIGURATION_OR_RESPONSE" -> "설정 또는 응답 형식 확인 필요"
        "INVALID_RUNTIME_STATE" -> "보호 엔진 내부 상태 오류"
        "IO_FAILURE" -> "네트워크 또는 입출력 오류"
        else -> "원인을 안전하게 분류할 수 없는 오류"
    }
}
