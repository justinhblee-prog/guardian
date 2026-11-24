package com.guardian.adblock

import java.net.InetAddress

/** Orders resolver candidates without ever feeding Guardian's virtual DNS back into itself. */
internal object DnsUpstreamCandidatePolicy {
    data class Attempt(val address: String, val transport: String)

    fun udpCandidates(
        configured: String,
        systemDns: List<String>,
        publicFallback: List<String>,
        virtualDns: Set<String>
    ): List<String> = (listOf(configured) + systemDns + publicFallback)
        .mapNotNull(::usableResolverAddress)
        .filterNot { it in virtualDns }
        .distinct()

    fun udpAttempts(
        configured: String,
        systemDns: List<String>,
        publicFallback: List<String>,
        virtualDns: Set<String>
    ): List<Attempt> {
        val candidates = udpCandidates(configured, systemDns, publicFallback, virtualDns)
        return buildList {
            candidates.forEachIndexed { index, address ->
                add(Attempt(address, "udp"))
                // Only the user's configured resolver gets an immediate TCP retry.
                // Device DNS and public fallbacks remain bounded UDP attempts.
                if (index == 0) add(Attempt(address, "tcp"))
            }
        }
    }

    /** Explicitly selected transports must never be silently downgraded. */
    fun transports(primary: String): List<String> =
        if (primary == "udp") listOf("udp", "tcp") else listOf(primary)

    private fun usableResolverAddress(raw: String): String? {
        val normalized = DnsAddressValidator.normalize(raw) ?: return null
        return try {
            val address = InetAddress.getByName(normalized)
            if (address.isAnyLocalAddress || address.isLoopbackAddress ||
                address.isMulticastAddress || address.isLinkLocalAddress
            ) null else normalized
        } catch (_: Exception) {
            null
        }
    }
}
