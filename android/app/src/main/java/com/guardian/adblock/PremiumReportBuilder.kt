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
    val protectionActive: Boolean,
    val dailyStats: List<DailyDnsStats> = emptyList()
)

internal object PremiumReportBuilder {
    fun build(snapshot: PremiumReportSnapshot, generatedAtEpochMillis: Long): String {
        fun safe(value: Long): Long = value.coerceAtLeast(0L)
        fun count(value: Long): String = String.format(Locale.getDefault(), "%,d", safe(value))
        fun percentage(value: Long, total: Long): Double =
            if (total <= 0L) 0.0 else safe(value).coerceAtMost(total) * 100.0 / total

        val total = safe(snapshot.totalQueries)
        val blocked = safe(snapshot.blockedQueries)
        val rate = percentage(blocked, total)
        val forwardedRate = percentage(snapshot.forwardedQueries, total)
        val failedRate = percentage(snapshot.failedQueries, total)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(generatedAtEpochMillis))
        val health = when {
            !snapshot.protectionActive -> "보호가 비활성 상태입니다. Guardian 보호를 시작한 뒤 다시 확인하세요."
            total == 0L -> "아직 관측된 DNS 질의가 없습니다. 보호 연결과 기기 네트워크 상태를 확인하세요."
            failedRate >= 5.0 -> "DNS 응답 실패 비율이 높습니다. 업스트림 연결과 비공개 DNS 설정을 점검하세요."
            else -> "관측된 DNS 처리 통계에 중대한 응답 실패 신호가 없습니다."
        }
        return buildString {
            appendLine("Guardian Premium · DNS 보호 리포트")
            appendLine("생성 시각(UTC): $timestamp")
            appendLine("분석 범위: 이 앱 설치 이후 누적")
            appendLine("보호 상태: ${if (snapshot.protectionActive) "활성" else "비활성"}")
            appendLine("관측 DNS 질의: ${count(total)}")
            appendLine("차단 질의: ${count(blocked)}")
            appendLine("차단 비율: ${String.format(Locale.ROOT, "%.1f%%", rate)}")
            appendLine("업스트림 전달: ${count(snapshot.forwardedQueries)}")
            appendLine("업스트림 전달 비율: ${String.format(Locale.ROOT, "%.1f%%", forwardedRate)}")
            appendLine("응답 실패: ${count(snapshot.failedQueries)}")
            appendLine("응답 실패 비율: ${String.format(Locale.ROOT, "%.1f%%", failedRate)}")
            appendLine("광고 차단: ${count(snapshot.adBlocked)}")
            appendLine("추적 차단: ${count(snapshot.trackerBlocked)}")
            appendLine("미분류 차단: ${count(snapshot.otherBlocked)}")
            appendLine()
            appendLine("최근 7일 일별 DNS 요약")
            val recentDays = snapshot.dailyStats.sortedBy { it.date }.takeLast(7)
            if (recentDays.isEmpty()) {
                appendLine("일별 데이터가 아직 없습니다.")
            } else {
                recentDays.forEach { day ->
                    appendLine("${day.date}: 질의 ${count(day.totalQueries)} · 차단 ${count(day.blockedQueries)} · 전달 ${count(day.forwardedQueries)} · 실패 ${count(day.failedQueries)}")
                }
            }
            appendLine()
            appendLine("상태 점검: $health")
            appendLine()
            append("참고: 이 리포트는 이 기기에 저장된 누적 DNS 통계입니다. 광고·추적 분류는 도메인 이름 기반 참고용 추정이며, 미분류 차단은 유형을 확인할 수 없는 요청으로 보안 위협 판정이 아닙니다. 방문 도메인, 앱 사용 기록, 사용자 규칙은 포함하지 않습니다.")
        }
    }
}
