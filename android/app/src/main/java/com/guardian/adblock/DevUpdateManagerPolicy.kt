package com.guardian.adblock

import java.net.URI

/** Pure policy helpers used by the development release updater. */
internal object DevUpdateManagerPolicy {
    private const val OWNER = "justinhblee-prog"
    private const val REPOSITORY = "guardian"
    private const val APK_ASSET = "guardian-android-dev.apk"
    private const val MANIFEST_ASSET = "guardian-android-dev.json"
    private val tagPattern = Regex("^guardian-dev-v([1-9][0-9]{0,8})$")
    private val allowedHttpsHosts = setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    fun isNewerMatchingRelease(
        installedVersionCode: Int,
        tagVersion: Int,
        manifestVersionCode: Int
    ): Boolean = tagVersion > installedVersionCode && manifestVersionCode == tagVersion

    fun isGuardianReleaseAssetUrl(
        address: String,
        expectedAssetName: String? = null,
        expectedTagVersion: Int? = null
    ): Boolean = runCatching {
        val uri = URI(address)
        val parts = uri.path.split('/').filter { it.isNotEmpty() }
        val tagVersion = tagPattern.matchEntire(parts.getOrNull(4).orEmpty())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        val assetName = parts.getOrNull(5)
        uri.scheme.equals("https", true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host.equals("github.com", true) &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            parts.size == 6 &&
            parts.take(4) == listOf(OWNER, REPOSITORY, "releases", "download") &&
            tagVersion != null &&
            assetName in setOf(MANIFEST_ASSET, APK_ASSET) &&
            (expectedAssetName == null || assetName == expectedAssetName) &&
            (expectedTagVersion == null || tagVersion == expectedTagVersion)
    }.getOrDefault(false)

    fun isAllowedHttpsUrl(address: String): Boolean = runCatching {
        val uri = URI(address)
        uri.scheme.equals("https", true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.lowercase() in allowedHttpsHosts &&
            uri.userInfo == null
    }.getOrDefault(false)

    fun isAllowedReleaseDownloadUrl(address: String): Boolean =
        isAllowedHttpsUrl(address) && runCatching {
            !URI(address).host.equals("api.github.com", true)
        }.getOrDefault(false)

    fun hasSingleExpectedSigner(signers: List<String>, expectedSha256: String): Boolean =
        signers.size == 1 &&
            Regex("^[a-fA-F0-9]{64}$").matches(expectedSha256) &&
            signers.single().equals(expectedSha256, ignoreCase = true)
}
