package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object DnsDohHttpCodec {
    private const val MAX_HEADER_BYTES = 8192
    private const val MAX_DNS_BYTES = 4096

    fun buildPostRequest(host: String, path: String, query: ByteArray): ByteArray? {
        val normalizedHost = DnsTlsHostValidator.normalize(host) ?: return null
        if (path.isEmpty() || !path.startsWith("/") || path.contains("..") ||
            path.any { it == '\r' || it == '\n' || it == ' ' }) return null
        if (query.size !in 17..MAX_DNS_BYTES) return null
        val header = "POST $path HTTP/1.1\r\n" +
            "Host: $normalizedHost\r\n" +
            "Accept: application/dns-message\r\n" +
            "Content-Type: application/dns-message\r\n" +
            "Content-Length: ${query.size}\r\n" +
            "Connection: close\r\n\r\n"
        return header.toByteArray(StandardCharsets.ISO_8859_1) + query
    }

    fun readResponse(input: InputStream): ByteArray? {
        val headerBytes = readHeaders(input) ?: return null
        val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val statusLine = lines.firstOrNull() ?: return null
        if (!statusLine.matches(Regex("HTTP/1\\.[01] 200(?: .*)?"))) return null
        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            if (line.isEmpty()) return@forEach
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            val name = line.substring(0, separator)
            if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) return null
            val key = name.lowercase()
            // Reject duplicate fields instead of silently trusting the last
            // Content-Length/Transfer-Encoding value supplied by the peer.
            if (headers.containsKey(key)) return null
            val value = line.substring(separator + 1).trim()
            if (value.any { it.code == 13 || it.code == 10 || (it.code < 0x20 && it.code != 9) }) return null
            headers[key] = value
        }
        val contentType = headers["content-type"]?.substringBefore(';')?.trim()
        if (!contentType.equals("application/dns-message", ignoreCase = true)) return null
        val transferEncoding = headers["transfer-encoding"]
        val contentLengthHeader = headers["content-length"]
        // RFC 9112 forbids combining these framing mechanisms. This client
        // accepts only a single, explicitly supported chunked coding.
        if (transferEncoding != null && contentLengthHeader != null) return null
        if (transferEncoding != null && !transferEncoding.equals("chunked", ignoreCase = true)) return null
        val body = if (transferEncoding != null) {
            readChunked(input) ?: return null
        } else {
            val contentLength = contentLengthHeader?.toIntOrNull()
            if (contentLengthHeader != null && contentLength == null) return null
            if (contentLength != null) {
                if (contentLength !in 17..MAX_DNS_BYTES) return null
                readExactly(input, contentLength) ?: return null
            } else {
                readUntilEof(input) ?: return null
            }
        }
        return body.takeIf { it.size in 17..MAX_DNS_BYTES }
    }

    private fun readHeaders(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        var matched = 0
        val terminator = byteArrayOf(13, 10, 13, 10)
        while (output.size() < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) return null
            output.write(value)
            matched = when {
                value == (terminator[matched].toInt() and 255) -> matched + 1
                value == 13 -> 1
                else -> 0
            }
            if (matched == terminator.size) {
                val bytes = output.toByteArray()
                return bytes.copyOf(bytes.size - terminator.size)
            }
        }
        return null
    }

    private fun readChunked(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        while (output.size() <= MAX_DNS_BYTES) {
            val line = readAsciiLine(input, 128) ?: return null
            val token = line.substringBefore(';').trim()
            val size = token.toLongOrNull(16) ?: return null
            if (size < 0 || size > MAX_DNS_BYTES || output.size() + size > MAX_DNS_BYTES) return null
            if (size == 0L) {
                var trailerBytes = 0
                while (true) {
                    val trailer = readAsciiLine(input, 1024) ?: return null
                    trailerBytes += trailer.length + 2
                    if (trailerBytes > MAX_HEADER_BYTES) return null
                    if (trailer.isEmpty()) break
                }
                return output.toByteArray()
            }
            val chunk = readExactly(input, size.toInt()) ?: return null
            output.write(chunk)
            if (input.read() != 13 || input.read() != 10) return null
        }
        return null
    }

    private fun readAsciiLine(input: InputStream, maxLength: Int): String? {
        val bytes = ByteArrayOutputStream()
        var previous = -1
        while (bytes.size() <= maxLength) {
            val value = input.read()
            if (value < 0) return null
            if (previous == 13 && value == 10) {
                val data = bytes.toByteArray()
                return String(data.copyOf(data.size - 1), StandardCharsets.ISO_8859_1)
            }
            bytes.write(value)
            previous = value
        }
        return null
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray? {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(result, offset, length - offset)
            if (count < 0) return null
            if (count == 0) continue
            offset += count
        }
        return result
    }

    private fun readUntilEof(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_DNS_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
