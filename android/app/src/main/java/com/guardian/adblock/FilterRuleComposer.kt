package com.guardian.adblock

/** Combines independently parsed sources so one list's badfilter cannot cancel another list. */
object FilterRuleComposer {
    fun compose(
        sources: List<List<String>>,
        userBlockDomains: List<String> = emptyList(),
        userAllowDomains: List<String> = emptyList()
    ): List<String> {
        val combined = mutableListOf<String>()
        sources.forEach { source ->
            val parsed = FilterRuleParser.parse(source)
            parsed.blocked.forEach { combined.add("block=$it") }
            parsed.allowed.forEach { combined.add("allow=$it") }
        }
        userBlockDomains.forEach { combined.add("block=$it") }
        userAllowDomains.forEach { combined.add("allow=$it") }
        return combined
    }
}
