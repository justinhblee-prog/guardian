package com.guardian.adblock

import java.io.ByteArrayOutputStream

/** In-process rule-engine probe. The VPN app's own sockets bypass its TUN by design,
 * so opening a DatagramSocket to the virtual DNS address would not be an end-to-end test. */
object DnsProbe {
    private const val TRANSACTION_ID = 0x4755

    data class Result(
        val blockedDomain: String,
        val blockedRuleMatched: Boolean,
        val blockedResponseCode: Int?,
        val normalDomain: String,
        val normalDomainBlocked: Boolean
    ) {
        val passed: Boolean
            get() = blockedRuleMatched && blockedResponseCode == 3 && !normalDomainBlocked
    }

    /** Verifies active rule matching and the NXDOMAIN response builder without a misleading loopback socket. */
    fun evaluateRules(): Result {
        val blockedQuery = buildQuery("doubleclick.net")
        val blockedDomain = DomainFilter.readQuestionName(blockedQuery)
            ?: throw IllegalStateException("차단 테스트 DNS 질문을 해석하지 못했습니다.")
        val matched = DomainFilter.isBlocked(blockedDomain)
        val response = if (matched) DomainFilter.makeNxdomain(blockedQuery) else null
        val responseCode = response?.let { u16(it, 2) and 0x000f }
        val normalDomain = "example.com"
        return Result(
            blockedDomain = blockedDomain,
            blockedRuleMatched = matched,
            blockedResponseCode = responseCode,
            normalDomain = normalDomain,
            normalDomainBlocked = DomainFilter.isBlocked(normalDomain)
        )
    }

    internal fun buildQuery(domain: String): ByteArray {
        val normalized = DomainFilter.normalizeRuleDomain(domain)
            ?: throw IllegalArgumentException("잘못된 테스트 도메인입니다.")
        val output = ByteArrayOutputStream()
        output.write(TRANSACTION_ID ushr 8)
        output.write(TRANSACTION_ID)
        output.write(0x01) // RD
        output.write(0)
        output.write(0)
        output.write(1) // QDCOUNT
        repeat(6) { output.write(0) }
        normalized.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.write(0)
        output.write(1) // A
        output.write(0)
        output.write(1) // IN
        return output.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
