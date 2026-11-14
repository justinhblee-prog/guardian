package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Retrieves a detached-signature manifest and its payload without trusting either until
 * FilterManifestVerifier and the caller's rule parser have accepted them.
 */
internal class SignedManifestHttpClient(
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxBytes: Int = 10 * 1024 * 1024
) {
    data class Download(val manifest: FilterManifestVerifier.Manifest, val content: ByteArray)

    fun download(
        manifestUrl: String,
        expectedSourceId: String,
        publicKeyX509Base64: String,
        minimumGeneratedAtExclusive: Long,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): Download {
        val manifestResponse = get(manifestUrl, 16 * 1024)
        val signature = manifestResponse.signature
            ?: throw IllegalArgumentException("manifest 서명 헤더가 없습니다.")
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
