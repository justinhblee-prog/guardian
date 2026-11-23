package com.guardian.adblock

internal object GuardianStableUpdatePolicy {
    private val stableTagPattern = Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$")

    fun isStableRelease(draft: Boolean, prerelease: Boolean, tag: String): Boolean =
        !draft && !prerelease && runCatching { compareVersionTags(tag, tag) }.isSuccess

    fun compareVersionTags(left: String, right: String): Int {
        fun parts(tag: String): List<Int> {
            require(stableTagPattern.matches(tag)) { "Invalid stable version tag" }
            return tag.substring(1).split('.').map { component ->
                require(component.length <= 9) { "Stable version component is too large" }
                component.toIntOrNull() ?: error("Invalid stable version component")
            }
        }
        val a = parts(left)
        val b = parts(right)
        for (index in 0..2) {
            val comparison = a[index].compareTo(b[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun isNewerVersion(installedVersionCode: Int, candidateVersionCode: Int): Boolean =
        candidateVersionCode > installedVersionCode
    fun isSha256(value: String): Boolean = Regex("^[a-fA-F0-9]{64}$").matches(value)
}
