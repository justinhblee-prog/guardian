package com.guardian.adblock

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Stores only stable error codes for filter refresh failures.
 *
 * Throwable messages can contain URLs, local paths, server response bodies, or other
 * request-specific data. They must never be persisted in preferences or shown in UI.
 */
internal object FilterUpdateErrorPolicy {
    fun codeFor(error: Throwable): String {
        var current: Throwable? = error
        var fallback = "UPDATE_FAILED"
        var depth = 0
        while (current != null && depth < 8) {
            val code = when (current) {
                is SocketTimeoutException -> "NETWORK_TIMEOUT"
                is UnknownHostException -> "DNS_RESOLUTION_FAILED"
                is SSLException -> "TLS_FAILURE"
                is ConnectException -> "CONNECTION_FAILED"
                is SecurityException -> "ACCESS_DENIED"
                is IllegalArgumentException -> "INVALID_FILTER_RESPONSE"
                is IllegalStateException -> "FILTER_INTEGRITY_OR_STORAGE_FAILURE"
                is IOException -> "IO_FAILURE"
                else -> null
            }
            if (code != null) {
                fallback = code
                if (code in setOf(
                        "NETWORK_TIMEOUT",
                        "DNS_RESOLUTION_FAILED",
                        "TLS_FAILURE",
                        "CONNECTION_FAILED",
                        "ACCESS_DENIED",
                        "INVALID_FILTER_RESPONSE",
                        "IO_FAILURE"
                    )) return code
            }
            current = current.cause
            depth++
        }
        return fallback
    }

    fun displayMessage(code: String): String = when (code) {
        "NETWORK_TIMEOUT" -> "네트워크 연결 시간 초과"
        "DNS_RESOLUTION_FAILED" -> "서버 주소 확인 실패"
        "TLS_FAILURE" -> "보안 연결 검증 실패"
        "CONNECTION_FAILED" -> "서버 연결 실패"
        "ACCESS_DENIED" -> "접근 권한 거부"
        "INVALID_FILTER_RESPONSE" -> "필터 응답 형식 또는 규칙 검증 실패"
        "FILTER_INTEGRITY_OR_STORAGE_FAILURE" -> "필터 무결성 또는 저장 오류"
        "IO_FAILURE" -> "네트워크 또는 파일 입출력 오류"
        "UPDATE_FAILED" -> "필터 업데이트 실패"
        else -> "기존 오류 정보(개인정보 보호를 위해 상세 내용 숨김)"
    }
}
