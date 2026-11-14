package com.guardian.adblock

/** Builds truthful protection copy from observed tunnel traffic, not VPN state alone. */
object ProtectionDiagnostics {
    fun statusDetail(
        active: Boolean,
        queries: Long,
        blocked: Long,
        forwarded: Long,
        failed: Long,
        privateDnsMode: String?
    ): String {
        if (!active) return "보호를 시작하면 시스템 가상 DNS를 통해 필터링합니다."
        val observed = "Guardian 관측 DNS 질의 ${queries.coerceAtLeast(0)}건 · 차단 ${blocked.coerceAtLeast(0)} · 업스트림 전달 ${forwarded.coerceAtLeast(0)} · 응답 실패 ${failed.coerceAtLeast(0)}."
        return when (privateDnsMode) {
            "hostname" -> "VPN 터널은 실행 중이지만 Android 비공개 DNS 공급자가 암호화 DNS를 Guardian 필터 밖으로 보낼 수 있습니다. $observed"
            "opportunistic" -> "VPN 터널은 실행 중입니다. Android 비공개 DNS 자동 모드가 네트워크에 따라 필터를 우회할 수 있습니다. $observed"
            else -> if (queries <= 0L) {
                "VPN 터널은 실행 중이지만 아직 DNS 질의를 관측하지 못했습니다. 앱이나 웹사이트를 열어 요청을 발생시켜 주세요. 실제 광고 제거 여부는 별도 확인이 필요합니다."
            } else {
                "$observed 이 수치는 Guardian이 처리한 DNS 요청이며, 앱 내부 광고나 암호화 DNS까지 차단한다는 뜻은 아닙니다."
            }
        }
    }
}