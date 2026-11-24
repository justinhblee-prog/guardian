package com.guardian.adblock

/**
 * Shared decision path for DNS-over-UDP and DNS-over-TCP.
 * Keeps allow/block precedence and upstream-failure behavior identical for both transports.
 */
internal object DnsQueryPolicy {
    data class Result(
        val domain: String,
        val response: ByteArray,
        val blocked: Boolean,
        val forwarded: Boolean
    )

    fun resolve(query: ByteArray, upstream: (ByteArray) -> ByteArray?): Result? {
        val domain = DomainFilter.readQuestionName(query) ?: return null
        if (DomainFilter.isBlocked(domain)) {
            val response = DomainFilter.makeNxdomain(query) ?: return null
            return Result(domain, response, blocked = true, forwarded = false)
        }

        val forwardedResponse = try {
            upstream(query)?.takeIf { DomainFilter.isMatchingResponse(query, it) }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            // A missing optional TLS/provider runtime must fail this DNS request
            // closed with SERVFAIL, not escape and terminate the VPN packet loop.
            null
        }
        val response = forwardedResponse ?: DomainFilter.makeServfail(query) ?: return null
        return Result(domain, response, blocked = false, forwarded = forwardedResponse != null)
    }
}
