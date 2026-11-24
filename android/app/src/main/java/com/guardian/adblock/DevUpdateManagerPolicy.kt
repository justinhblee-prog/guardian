package com.guardian.adblock

/** Pure policy helpers used by the development release updater. */
internal object DevUpdateManagerPolicy {
    fun hasSingleExpectedSigner(signers: List<String>, expectedSha256: String): Boolean =
        signers.size == 1 && Regex("^[a-fA-F0-9]{64}$").matches(expectedSha256) &&
            signers.single().equals(expectedSha256, ignoreCase = true)
}
