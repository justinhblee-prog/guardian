package com.guardian.adblock

/** Orders resolver candidates without ever feeding Guardian's virtual DNS back into itself. */
internal object DnsUpstreamCandidatePolicy {
    fun udpCandidates(
        configured: String,
        systemDns: List<String>,
        publicFallback: List<String>,
        virtualDns: Set<String>
    ): List<String> = (listOf(configured) + systemDns + publicFallback)
        .mapNotNull(DnsAddressValidator::normalize)
        .filterNot { it in virtualDns }
        .distinct()

    /** UDP mode may retry TCP/53; explicitly selected transports must remain explicit. */
    fun transports(primary: String): List<String> =
        if (primary == "udp") listOf("udp", "tcp") else listOf(primary)
}
