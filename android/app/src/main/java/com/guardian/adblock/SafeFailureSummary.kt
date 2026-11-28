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
                is GuardianHttpStatusException -> "HTTP_${current.statusCode}"
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

    /** Exception type chain for CI diagnostics; never emits messages, URLs, paths, or resolver data. */
    fun diagnosticTypeChain(error: Throwable): String {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val types = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && types.size < 8 && seen.add(current)) {
            types += current.javaClass.simpleName.ifBlank { "Throwable" }
            current = current.cause
        }
        return types.joinToString(" -> ")
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
        "HTTP_400" -> "업데이트 서버 요청 형식 오류(HTTP 400)"
        "HTTP_401", "HTTP_403" -> "GitHub 접근 인증 또는 권한 제한(HTTP ${code.removePrefix("HTTP_")})"
        "HTTP_404" -> "업데이트 목록 또는 파일을 찾을 수 없음(HTTP 404)"
        "HTTP_408" -> "업데이트 서버 응답 시간 초과(HTTP 408)"
        "HTTP_409" -> "업데이트 요청 충돌(HTTP 409)"
        "HTTP_413" -> "업데이트 응답 크기 제한 초과(HTTP 413)"
        "HTTP_422" -> "업데이트 서버가 요청을 처리할 수 없음(HTTP 422)"
        "HTTP_429" -> "GitHub 요청 한도 초과(HTTP 429)"
        else -> if (code.matches(Regex("HTTP_5[0-9]{2}"))) {
            "GitHub 서버 일시 오류(${code.removePrefix("HTTP_")})"
        } else {
            "원인을 안전하게 분류할 수 없는 오류"
        }
    }
}
