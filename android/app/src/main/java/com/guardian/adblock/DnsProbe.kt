package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** End-to-end UDP DNS probe sent through the app's virtual VPN DNS endpoint. */
object DnsProbe {
    private const val VIRTUAL_DNS = "10.13.37.2"
    private const val DNS_PORT = 53
    private const val TRANSACTION_ID = 0x4755

    data class Result(val domain: String, val responseCode: Int)

    fun query(domain: String, timeoutMs: Int = 5000): Result {
        val query = buildQuery(domain)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val target = InetAddress.getByName(VIRTUAL_DNS)
            socket.send(DatagramPacket(query, query.size, target, DNS_PORT))
            val buffer = ByteArray(4096)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            require(responsePacket.port == DNS_PORT && responsePacket.address == target) {
                "응답 DNS 엔드포인트가 요청과 일치하지 않습니다."
            }
            val response = buffer.copyOf(responsePacket.length)
            require(response.size >= 12 && u16(response, 0) == TRANSACTION_ID) {
                "DNS 응답 transaction ID가 일치하지 않습니다."
            }
            val flags = u16(response, 2)
            require((flags and 0x8000) != 0) { "DNS 응답에 QR 플래그가 없습니다." }
            return Result(domain, flags and 0x000f)
        }
    }

    private fun buildQuery(domain: String): ByteArray {
        val normalized = DomainFilter.normalizeRuleDomain(domain)
            ?: throw IllegalArgumentException("잘못된 테스트 도메인입니다.")
        val output = ByteArrayOutputStream()
        output.write(TRANSACTION_ID ushr 8)
        output.write(TRANSACTION_ID)
        output.write(0x01) // RD
        output.write(0)
        output.write(0)
        output.write(1) // QDCOUNT
        repeat(6) { output.write(0) }
        normalized.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.write(0)
        output.write(1) // A
        output.write(0)
        output.write(1) // IN
        return output.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}
