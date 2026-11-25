package com.guardian.adblock

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A privacy-preserving snapshot built only from counters already collected by the local DNS engine. */
internal data class PremiumReportSnapshot(
    val totalQueries: Long,
    val blockedQueries: Long,
    val forwardedQueries: Long,
    val failedQueries: Long,
    val adBlocked: Long,
    val trackerBlocked: Long,
    val otherBlocked: Long,
    val protectionActive: Boolean
)

internal object PremiumReportBuilder {
    fun build(snapshot: PremiumReportSnapshot, generatedAtEpochMillis: Long): String {
        fun count(value: Long): Long = value.coerceAtLeast(0L)
        val total = count(snapshot.totalQueries)
        val blocked = count(snapshot.blockedQueries)
        val rate = if (total == 0L) 0.0 else count(snapshot.blockedQueries).coerceAtMost(total) * 100.0 / total
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(generatedAtEpochMillis))
        return buildString {
            appendLine("Guardian Premium · DNS 보호 리포트")
            appendLine("생성 시각(UTC): $timestamp")
            appendLine("보호 상태: ${if (snapshot.protectionActive) "활성" else "비활성"}")
            appendLine("관측 DNS 질의: $total")
            appendLine("차단 질의: ${count(snapshot.blockedQueries)}")
            appendLine("차단 비율: ${String.format(Locale.ROOT, "%.1f%%", rate)}")
            appendLine("업스트림 전달: ${count(snapshot.forwardedQueries)}")
            appendLine("응답 실패: ${count(snapshot.failedQueries)}")
            appendLine("광고 차단: ${count(snapshot.adBlocked)}")
            appendLine("추적 차단: ${count(snapshot.trackerBlocked)}")
            appendLine("기타 차단: ${count(snapshot.otherBlocked)}")
            appendLine()
            append("참고: 이 리포트는 이 기기에 저장된 누적 DNS 통계입니다. 광고·추적·기타 분류는 도메인 이름 기반 참고용 추정이며 보안 위협 판정이 아닙니다. 방문 도메인, 앱 사용 기록, 사용자 규칙은 포함하지 않습니다.")
        }
    }
}
