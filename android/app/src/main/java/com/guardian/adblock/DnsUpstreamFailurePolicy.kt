package com.guardian.adblock

/** Bounds the diagnostic text retained for failed DNS upstream fallback candidates. */
internal object DnsUpstreamFailurePolicy {
    const val MAX_FAILURES = 6
    const val MAX_ENTRY_CHARS = 240
    const val MAX_SUMMARY_CHARS = 1200

    fun summarize(failures: List<String>): String? {
        val entries = failures.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_FAILURES)
            .map { it.take(MAX_ENTRY_CHARS) }
            .toList()
        return entries.joinToString(" · ")
            .take(MAX_SUMMARY_CHARS)
            .takeIf { it.isNotBlank() }
    }
}
