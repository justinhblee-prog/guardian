package com.guardian.adblock

internal object GuardianStableUpdatePolicy {
    fun isStableRelease(draft: Boolean, prerelease: Boolean, tag: String): Boolean =
        !draft && !prerelease && Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$").matches(tag)
    fun isNewerVersion(installedVersionCode: Int, candidateVersionCode: Int): Boolean =
        candidateVersionCode > installedVersionCode
    fun isMissingStableReleaseStatus(statusCode: Int): Boolean = statusCode == 404
    fun isSha256(value: String): Boolean = Regex("^[a-fA-F0-9]{64}$").matches(value)
}
