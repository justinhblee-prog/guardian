package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Resolve the version-bound sidecar used by static hosts without custom headers. */
internal fun detachedManifestSignatureUrl(
    manifestUrl: String,
    manifestBytes: ByteArray,
    expectedSourceId: String
): String {
    val manifestUri = URL(manifestUrl)
    require(manifestUri.protocol.equals("https", true) &&
        manifestUri.userInfo == null && manifestUri.query == null && manifestUri.ref == null) {
        "manifest URL 형식이 올바르지 않습니다."
    }
    val text = FilterManifestVerifier.decodeUtf8Strict(manifestBytes)
    fun field(name: String): String {
        val matches = text.lineSequence().filter { it.startsWith("$name=") }.toList()
        require(matches.size == 1) { "manifest $name 항목이 올바르지 않습니다." }
        return matches.single().substringAfter('=')
    }
    val sourceId = field("sourceId")
    val version = field("sourceVersion")
    val generatedAt = field("generatedAt")
    require(sourceId == expectedSourceId &&
        sourceId.matches(Regex("[a-zA-Z0-9_-]{1,64}")) &&
        version.matches(Regex("[a-zA-Z0-9._+-]{1,64}")) &&
        version != "." && version != ".." &&
        generatedAt.matches(Regex("[1-9][0-9]{0,15}"))) {
        "manifest 서명 파일 경로가 올바르지 않습니다."
    }
    val signatureUrl = URL(manifestUri, "$sourceId.$version.$generatedAt.sig")
    require(signatureUrl.protocol.equals("https", true) &&
        signatureUrl.host.equals(manifestUri.host, true) &&
        signatureUrl.port == manifestUri.port) {
        "manifest 서명 파일의 출처가 일치하지 않습니다."
    }
    return signatureUrl.toExternalForm()
}

internal const val MAX_FILTER_DOWNLOAD_BYTES = 32 * 1024 * 1024

/**
 * Retrieves a detached-signature manifest and its payload without trusting either until
 * FilterManifestVerifier and the caller's rule parser have accepted them.
 */
internal class SignedManifestHttpClient(
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxBytes: Int = MAX_FILTER_DOWNLOAD_BYTES
) {
    data class Download(val manifest: FilterManifestVerifier.Manifest, val content: ByteArray)

    fun download(
        manifestUrl: String,
        expectedSourceId: String,
        publicKeyX509Base64: String,
        minimumGeneratedAtExclusive: Long,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): Download {
        val manifestUri = URL(manifestUrl)
        require(manifestUri.query == null && manifestUri.ref == null) {
            "manifest URL에는 query 또는 fragment를 사용할 수 없습니다."
        }
        // GitHub raw CDN caches branch URLs. A harmless query parameter refreshes
        // only that mutable manifest pointer; the laptop API rejects query strings.
        val manifestRequestUrl = if (manifestUri.host.equals("raw.githubusercontent.com", true)) {
            "$manifestUrl?guardian_cache_bust=$nowEpochSeconds"
        } else manifestUrl
        val manifestResponse = get(manifestRequestUrl, 16 * 1024)
        // Static CDNs (GitHub raw, object storage, Pages) cannot attach our
        // custom response header. Prefer the authenticated server header, then
        // retrieve the version-bound detached signature beside the manifest.
        val signature = manifestResponse.signature
            ?: fetchDetachedSignature(manifestUrl, manifestResponse.bytes, expectedSourceId)
        val manifest = FilterManifestVerifier.parseAndVerify(
            manifestResponse.bytes, signature, publicKeyX509Base64,
            nowEpochSeconds, minimumGeneratedAtExclusive
        )
        require(manifest.sourceId == expectedSourceId) {
            "요청한 필터와 manifest sourceId가 일치하지 않습니다."
        }
        val content = get(manifest.url, maxBytes).bytes
        require(FilterManifestVerifier.matchesSha256(content, manifest.sha256)) {
            "필터 콘텐츠 SHA-256 검증에 실패했습니다."
        }
        return Download(manifest, content)
    }

    private data class Response(val bytes: ByteArray, val signature: String?)

    private fun fetchDetachedSignature(
        manifestUrl: String,
        manifestBytes: ByteArray,
        expectedSourceId: String
    ): String {
        val signatureUrl = detachedManifestSignatureUrl(manifestUrl, manifestBytes, expectedSourceId)
        val encoded = get(signatureUrl, 4096).bytes.toString(Charsets.US_ASCII).trim()
        require(encoded.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) {
            "manifest detached signature 형식이 올바르지 않습니다."
        }
        val decoded = try {
            java.util.Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("manifest detached signature 인코딩이 올바르지 않습니다.", error)
        }
        require(decoded.size in 256..512 && java.util.Base64.getEncoder().encodeToString(decoded) == encoded) {
            "manifest detached signature 크기 또는 인코딩이 올바르지 않습니다."
        }
        return encoded
    }

    private fun get(initialUrl: String, limit: Int): Response {
        var currentUrl = URL(initialUrl)
        require(currentUrl.protocol.equals("https", true) && currentUrl.userInfo == null) {
            "manifest 및 필터 URL은 사용자 정보가 없는 HTTPS 주소여야 합니다."
        }
        var redirects = 0
        while (true) {
            val connection = currentUrl.openConnection() as? HttpsURLConnection
                ?: throw IllegalStateException("HTTPS 연결을 만들 수 없습니다.")
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                connection.setRequestProperty("Pragma", "no-cache")
                connection.setRequestProperty("User-Agent", "Guardian-AdBlock-Android")
                connection.setRequestProperty("Accept", "application/octet-stream")
                val status = connection.responseCode
                if (status in 300..399) {
                    val target = FilterRedirectPolicy.resolve(
                        currentUrl.toExternalForm(), status,
                        connection.getHeaderField("Location"), redirects
                    ) ?: throw IllegalArgumentException("허용되지 않은 manifest/필터 리디렉션입니다.")
                    currentUrl = URL(target)
                    redirects++
                    continue
                }
                require(status in 200..299) { "서버 HTTP $status" }
                val signature = connection.getHeaderField("X-Guardian-Signature")
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= limit) { "다운로드 파일 크기 제한을 초과했습니다." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                return Response(bytes, signature)
            } finally {
                connection.disconnect()
            }
        }
    }
}
