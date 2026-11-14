package com.guardian.adblock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
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

    @Test fun rejectsRedirectsAndWrongMediaTypes() {
        val redirect = "HTTP/1.1 302 Found\r\nContent-Type: application/dns-message\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val wrongType = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 29\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(redirect)))
        assertNull(DnsDohHttpCodec.readResponse(ByteArrayInputStream(wrongType)))
    }

    @Test fun rejectsAmbiguousOrMalformedResponseFraming() {
        val duplicateLength = "HTTP/1.1 200 OK\\r\\nContent-Type: application/dns-message\\r\\nContent-Length: 29\\r\\nContent-Length: 30\\r\\n\\r\\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val conflictingFraming = "HTTP/1.1 200 OK\\r\\nContent-Type: application/dns-message\\r\\nTransfer-Encoding: chunked\\r\\nContent-Length: 29\\r\\n\\r\\n"
            .toByteArray(StandardCharsets.ISO_8859_1) + dnsMessage
        val unsupportedTransferCoding = "HTTP/1.1 200 OK\\r\\nContent-Type: application/dns-message\\r\\nTransfer-Encoding: gzip, chunked\\r\\n\\r\\n"
            .toByteArray(StandardCharsets.ISO_8859_1)
        val malformedHeader = "HTTP/1.1 200 OK\\r\\nContent-Type application/dns-message\\r\\nContent-Length: 29\\r\\n\\r\\n"
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
}
