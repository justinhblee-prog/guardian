package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/**
 * Reads one regular cache file through a no-follow descriptor and never allocates
 * more than the configured limit, even if the file grows after its initial stat.
 */
internal fun readBoundedFile(file: File, maxBytes: Int): ByteArray? {
    require(maxBytes > 0) { "maxBytes must be positive" }
    return try {
        FileChannel.open(
            file.toPath(),
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS
        ).use { channel ->
            val initialSize = channel.size()
            if (initialSize !in 1..maxBytes.toLong()) return null
            val output = ByteArrayOutputStream(initialSize.toInt())
            val buffer = ByteBuffer.allocate(8192)
            var total = 0
            while (true) {
                buffer.clear()
                val count = channel.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > maxBytes) return null
                output.write(buffer.array(), 0, count)
            }
            if (total.toLong() != initialSize) null else output.toByteArray()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
