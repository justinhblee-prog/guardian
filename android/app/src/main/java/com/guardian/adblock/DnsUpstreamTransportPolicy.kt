package com.guardian.adblock

/**
 * Selects safe transport attempts for the configured upstream.
 * UDP may fall back to TCP on the same resolver; explicit TCP/DoT/DoH
 * selections remain strict and are never silently downgraded.
 */
object DnsUpstreamTransportPolicy {
    fun attempts(configuredTransport: String): List<String> =
        if (configuredTransport == "udp") listOf("udp", "tcp")
        else listOf(configuredTransport)

    fun resolverCandidates(
        configured: String,
        systemResolvers: List<String>,
        publicFallbacks: List<String>
    ): List<String> = (listOf(configured) + systemResolvers + publicFallbacks).distinct()
}
