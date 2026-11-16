package com.guardian.adblock

/** Merges independently parsed sources so badfilter rules remain source-local. */
object FilterRuleComposer {
    fun compose(
        sources: List<List<String>>,
        userBlockDomains: List<String> = emptyList(),
        userAllowDomains: List<String> = emptyList()
    ): List<String> {
        val parsed = composeParsed(
            sources.asSequence().map { FilterRuleParser.parse(it.asSequence()) },
            userBlockDomains,
            userAllowDomains
        )
        return buildList(parsed.blocked.size + parsed.allowed.size + parsed.importantBlocked.size) {
            parsed.blocked.forEach { add("block=$it") }
            parsed.importantBlocked.forEach { add("block-important=$it") }
            parsed.allowed.forEach { add("allow=$it") }
        }
    }

    fun composeParsed(
        sources: Sequence<FilterRuleParser.Parsed>,
        userBlockDomains: List<String> = emptyList(),
        userAllowDomains: List<String> = emptyList()
    ): FilterRuleParser.Parsed {
        val blocked = linkedSetOf<String>()
        val allowed = linkedSetOf<String>()
        val importantBlocked = linkedSetOf<String>()
        var ignored = 0
        sources.forEach { parsed ->
            blocked.addAll(parsed.blocked)
            allowed.addAll(parsed.allowed)
            importantBlocked.addAll(parsed.importantBlocked)
            ignored += parsed.ignored
        }
        val userRules = FilterRuleParser.parse(
            userBlockDomains.asSequence().map { "block=$it" } +
                userAllowDomains.asSequence().map { "allow=$it" }
        )
        blocked.addAll(userRules.blocked)
        allowed.addAll(userRules.allowed)
        importantBlocked.addAll(userRules.importantBlocked)
        ignored += userRules.ignored
        return FilterRuleParser.Parsed(blocked, allowed, importantBlocked, ignored)
    }
}
