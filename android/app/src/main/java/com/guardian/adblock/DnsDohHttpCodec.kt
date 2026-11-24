package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object DnsDohHttpCodec {
    private const val MAX_HEADER_BYTES = 8192
    private const val MAX_DNS_BYTES = 65535

    fun readDnsTcpResponse(input: InputStream, beforeRead: () -> Unit = {}): ByteArray? {
        fun readExactly(length: Int): ByteArray? {
            val result = ByteArray(length)
            var offset = 0
            while (offset < length) {
                beforeRead()
                val count = input.read(result, offset, length - offset)
                if (count < 0) return null
                if (count == 0) {
                    beforeRead()
                    val value = input.read()
                    if (value < 0) return null
                    result[offset++] = value.toByte()
                } else {
                    offset += count
                }
            }
            return result
        }

        val prefix = readExactly(2) ?: return null
        val length = ((prefix[0].toInt() and 0xff) shl 8) or (prefix[1].toInt() and 0xff)
        if (length !in 17..MAX_DNS_BYTES) return null
        return readExactly(length)
    }

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

    fun readResponse(input: InputStream, beforeRead: () -> Unit = {}): ByteArray? {
        // HTTP parsing performs many small reads (including one-byte header/chunk lines).
        // Give the caller a chance to enforce one absolute deadline across all of them.
        val guardedInput = object : java.io.FilterInputStream(input) {
            override fun read(): Int {
                beforeRead()
                return super.read()
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                beforeRead()
                return super.read(buffer, offset, length)
            }
        }
        val headerBytes = readHeaders(guardedInput) ?: return null
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
            if (headers.containsKey(key)) return null
            val value = line.substring(separator + 1).trim()
            if (value.any { it.code == 13 || it.code == 10 || (it.code < 0x20 && it.code != 9) }) return null
            headers[key] = value
        }
        val contentType = headers["content-type"]?.substringBefore(';')?.trim()
        if (!contentType.equals("application/dns-message", ignoreCase = true)) return null
        val transferEncoding = headers["transfer-encoding"]
        val contentLengthHeader = headers["content-length"]
        if (transferEncoding != null && contentLengthHeader != null) return null
        if (transferEncoding != null && !transferEncoding.equals("chunked", ignoreCase = true)) return null
        val body = if (transferEncoding != null) {
            readChunked(guardedInput) ?: return null
        } else {
            // Content-Length is an unsigned decimal field, not a signed integer.
            if (contentLengthHeader != null && !contentLengthHeader.matches(Regex("[0-9]+"))) return null
            val contentLength = contentLengthHeader?.toIntOrNull()
            if (contentLengthHeader != null && contentLength == null) return null
            if (contentLength != null) {
                if (contentLength !in 17..MAX_DNS_BYTES) return null
                readExactly(guardedInput, contentLength) ?: return null
            } else {
                readUntilEof(guardedInput) ?: return null
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
            // A chunk-size is one or more hexadecimal digits; signs and prefixes are invalid.
            if (!token.matches(Regex("[0-9A-Fa-f]+"))) return null
            val size = token.toLongOrNull(16) ?: return null
            if (size > MAX_DNS_BYTES || output.size() + size > MAX_DNS_BYTES) return null
            if (size == 0L) {
                var trailerBytes = 0
                val trailerNames = mutableSetOf<String>()
                while (true) {
                    val trailer = readAsciiLine(input, 1024) ?: return null
                    trailerBytes += trailer.length + 2
                    if (trailerBytes > MAX_HEADER_BYTES) return null
                    if (trailer.isEmpty()) break
                    val separator = trailer.indexOf(':')
                    if (separator <= 0) return null
                    val name = trailer.substring(0, separator)
                    if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) return null
                    val key = name.lowercase()
                    // Trailer fields must not redefine message framing or routing metadata.
                    if (key in setOf("content-length", "transfer-encoding", "host")) return null
                    if (!trailerNames.add(key)) return null
                    val value = trailer.substring(separator + 1)
                    if (value.any { it.code == 13 || it.code == 10 || (it.code < 0x20 && it.code != 9) }) return null
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
                val line = data.copyOf(data.size - 1)
                if (line.any { (it.toInt() and 255) < 0x20 && it.toInt() != 9 }) return null
                if (line.any { (it.toInt() and 255) > 0x7e }) return null
                return String(line, StandardCharsets.ISO_8859_1)
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
            if (count == 0) {
                val value = input.read()
                if (value < 0) return null
                result[offset++] = value.toByte()
            } else {
                offset += count
            }
        }
        return result
    }

    private fun readUntilEof(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val value = input.read()
                if (value < 0) break
                if (output.size() >= MAX_DNS_BYTES) return null
                output.write(value)
                continue
            }
            if (output.size() + count > MAX_DNS_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
