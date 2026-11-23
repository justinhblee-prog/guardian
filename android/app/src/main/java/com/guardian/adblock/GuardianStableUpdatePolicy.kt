package com.guardian.adblock

import java.math.BigInteger

internal object GuardianStableUpdatePolicy {
    fun isStableRelease(draft: Boolean, prerelease: Boolean, tag: String): Boolean =
        !draft && !prerelease && Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$").matches(tag)
    fun compareVersionTags(left: String, right: String): Int {
        fun parts(tag: String): List<BigInteger> {
            require(Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$").matches(tag)) {
                "Invalid stable version tag"
            }
            return tag.substring(1).split('.').map(::BigInteger)
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
