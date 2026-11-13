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
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: return null
        if (status != 200) return null
        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] =
                line.substring(separator + 1).trim()
        }
        val contentType = headers["content-type"]?.substringBefore(';')?.trim()
        if (!contentType.equals("application/dns-message", ignoreCase = true)) return null
        val transferEncoding = headers["transfer-encoding"].orEmpty()
        val body = if (transferEncoding.split(',').any { it.trim().equals("chunked", true) }) {
            readChunked(input) ?: return null
        } else {
            val contentLength = headers["content-length"]?.toIntOrNull()
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
