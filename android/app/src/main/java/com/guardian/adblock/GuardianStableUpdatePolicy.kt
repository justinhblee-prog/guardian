package com.guardian.adblock

internal object GuardianStableUpdatePolicy {
    private val stableTagPattern = Regex("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")

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

    /** Accept only positive integral manifest/PackageInfo version codes representable by Android's versionCode. */
    fun parseSupportedVersionCode(value: Any?): Int? {
        val number = value as? Number ?: return null
        val numeric = number.toDouble()
        if (!numeric.isFinite()) return null
        val longValue = number.toLong()
        if (numeric != longValue.toDouble() || longValue !in 1L..Int.MAX_VALUE.toLong()) return null
        return longValue.toInt()
    }

    fun parseArchiveVersionCode(longVersionCode: Long): Int? =
        if (longVersionCode in 1L..Int.MAX_VALUE.toLong()) longVersionCode.toInt() else null

    /** Content-Length -1 means a valid streaming response with unknown length. */
    fun isAcceptableDownloadLength(contentLength: Long, maximumBytes: Long): Boolean =
        maximumBytes > 0L && (contentLength == -1L || contentLength in 1L..maximumBytes)

    fun matchesDownloadedLength(expectedLength: Long, actualLength: Long, maximumBytes: Long): Boolean =
        actualLength in 1L..maximumBytes && (expectedLength == -1L || actualLength == expectedLength)

    fun isSha256(value: String): Boolean = Regex("^[a-fA-F0-9]{64}$").matches(value)

    fun isOfficialReleaseAssetUrl(address: String, tag: String, assetName: String): Boolean = runCatching {
        require(isStableRelease(draft = false, prerelease = false, tag = tag))
        require(assetName == "guardian-android.json" || assetName == "guardian-android.apk")
        val uri = java.net.URI(address)
        uri.scheme.equals("https", true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host.equals("github.com", true) &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path == "/justinhblee-prog/guardian/releases/download/$tag/$assetName"
    }.getOrDefault(false)
}
