package com.guardian.adblock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class DnsDohHttpCodecTest {
    private val dnsMessage = ByteArray(29).also {
        it[0] = 0x12
        it[1] = 0x34
        it[2] = 0x81.toByte()
        it[3] = 0x80.toByte()
    }

    @Test fun buildsBinaryPostRequestWithDnsMediaTypes() {
        val request = DnsDohHttpCodec.buildPostRequest("Cloudflare-DNS.com", "/dns-query", dnsMessage)!!
        val text = String(request, 0, request.size - dnsMessage.size, StandardCharsets.ISO_8859_1)
        assertTrue(text.startsWith("POST /dns-query HTTP/1.1\r\nHost: cloudflare-dns.com\r\n"))
        assertTrue(text.contains("Content-Type: application/dns-message"))
        assertTrue(text.contains("Content-Length: 29"))
        assertArrayEquals(dnsMessage, request.copyOfRange(request.size - dnsMessage.size, request.size))
    }

    @Test fun parsesContentLengthResponse() {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        assertArrayEquals(dnsMessage, DnsDohHttpCodec.readResponse(ByteArrayInputStream(response)))
    }

    @Test fun parsesChunkedResponse() {
        val hex = dnsMessage.size.toString(16)
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\n\r\n$hex\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage +
            "\r\n0\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        assertArrayEquals(dnsMessage, DnsDohHttpCodec.readResponse(ByteArrayInputStream(response)))
    }

    @Test fun supportsDnsMessagesLargerThanFourKilobytes() {
        val largeDnsMessage = ByteArray(5000) { (it and 0xff).toByte() }
        val request = DnsDohHttpCodec.buildPostRequest("cloudflare-dns.com", "/dns-query", largeDnsMessage)
        assertTrue(request != null)
        assertArrayEquals(largeDnsMessage, request!!.copyOfRange(request.size - largeDnsMessage.size, request.size))

        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 5000\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + largeDnsMessage
        assertArrayEquals(largeDnsMessage, DnsDohHttpCodec.readResponse(ByteArrayInputStream(response)))
    }

    @Test fun rejectsRedirectsAndWrongMediaTypes() {
        val redirect = "HTTP/1.1 302 Found\r\nContent-Type: application/dns-message\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val wrongType = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(redirect)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(wrongType)))
    }

    @Test fun rejectsAmbiguousOrMalformedResponseFraming() {
        val duplicateLength = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 29\r\nContent-Length: 30\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val conflictingFraming = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val unsupportedTransferCoding = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: gzip, chunked\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1)
        val malformedHeader = "HTTP/1.1 200 OK\r\nContent-Type application/dns-message\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(duplicateLength)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(conflictingFraming)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(unsupportedTransferCoding)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(malformedHeader)))
    }

    @Test fun rejectsOversizedAndTruncatedBodies() {
        val oversized = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 4097\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1)
        val truncated = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + byteArrayOf(1, 2, 3)
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(oversized)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(truncated)))
    }

    @Test fun rejectsUnsafeRequestHostsAndPaths() {
        assertNull(DnsDohHttpCodec.buildPostRequest("bad_host.example", "/dns-query", dnsMessage))
        assertNull(DnsDohHttpCodec.buildPostRequest("dns.google", "/dns-query\r\nHost: evil", dnsMessage))
    }

    @Test fun rejectsSignedContentLengthAndChunkSizes() {
        val signedLength = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: +29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val signedChunk = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\n\r\n+1d\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage + "\r\n0\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        val prefixedChunk = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\n\r\n0x1d\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage + "\r\n0\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(signedLength)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(signedChunk)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(prefixedChunk)))
    }

    @Test fun rejectsMalformedChunkTrailersAndControlCharacters() {
        val malformedTrailer = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\n\r\n1d\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage + "\r\n0\r\nNot-A-Header\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        val controlInChunkLine = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\n\r\n1d;bad\u0001x\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage + "\r\n0\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(malformedTrailer)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(controlInChunkLine)))
    }

    @Test fun rejectsFramingAndDuplicateChunkTrailersButAllowsBenignTrailer() {
        fun responseWithTrailer(trailer: String): ByteArray {
            val header = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nTransfer-Encoding: chunked\r\n\r\n1d\r\n"
            return header.toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage +
                "\r\n0\r\n$trailer\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1)
        }

        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(responseWithTrailer("Content-Length: 29"))))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(responseWithTrailer("Transfer-Encoding: chunked"))))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(responseWithTrailer("Host: evil.example"))))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(responseWithTrailer("X-Meta: one\r\nX-Meta: two"))))
        assertArrayEquals(dnsMessage, DnsDohHttpCodec.readResponse(
            ByteArrayInputStream(responseWithTrailer("X-Meta: verified"))
        ))
    }

    @Test fun invokesReadGuardForEveryHttpParserRead() {
        val response = "HTTP/1.1 200 OK\\r\\nContent-Type: application/dns-message\\r\\nContent-Length: 29\\r\\n\\r\\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        var guardedReads = 0
        assertArrayEquals(
            dnsMessage,
            DnsDohHttpCodec.readResponse(ByteArrayInputStream(response)) { guardedReads++ }
        )
        assertTrue(guardedReads >= response.size - dnsMessage.size + 1)
    }

    @Test(expected = SocketTimeoutException::class)
    fun abortsResponseParsingWhenReadDeadlineExpires() {
        val response = "HTTP/1.1 200 OK\\r\\nContent-Type: application/dns-message\\r\\nContent-Length: 29\\r\\n\\r\\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        DnsDohHttpCodec.readResponse(ByteArrayInputStream(response)) {
            throw SocketTimeoutException("deadline")
        }
    }

    @Test fun handlesStreamsThatTemporarilyReturnZeroForBulkReads() {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val headerEnd = response.indices.firstOrNull { index ->
            index + 3 < response.size &&
                response[index] == 13.toByte() && response[index + 1] == 10.toByte() &&
                response[index + 2] == 13.toByte() && response[index + 3] == 10.toByte()
        }?.plus(4) ?: error("HTTP header terminator missing")
        val intermittentZeroStream = object : java.io.InputStream() {
            private val delegate = ByteArrayInputStream(response)
            private var bytesConsumed = 0
            private var returnZero = true
            override fun read(): Int = delegate.read().also { if (it >= 0) bytesConsumed++ }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZero && bytesConsumed >= headerEnd) {
                    returnZero = false
                    return 0
                }
                return delegate.read(buffer, offset, length).also { if (it > 0) bytesConsumed += it }
            }
        }
        assertArrayEquals(dnsMessage, DnsDohHttpCodec.readResponse(intermittentZeroStream))
    }
}
