package com.guardian.adblock

import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * Small, bounded TCP state machine for DNS-over-TCP on the VPN's virtual
 * resolver. Supports ordered, unfragmented client streams and keeps sessions
 * isolated by the complete source/destination tuple.
 */
class DnsTcpSessionManager(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val random: SecureRandom = SecureRandom()
) {
    private data class Key(
        val version: Int, val source: String, val destination: String, val sourcePort: Int
    )
    private data class Session(
        var clientNext: Long,
        var serverNext: Long,
        val received: ByteArrayOutputStream = ByteArrayOutputStream(),
        var lastActivity: Long
    )

    private val sessions = LinkedHashMap<Key, Session>()
    private val maxSessions = 128
    private val idleTimeoutMs = 60_000L
    private val maxDnsMessage = 4096
    private val maxBufferedBytes = 8192
    // Conservative default MSS when the client did not negotiate TCP options.
    private val maxSegmentPayload = 536

    @Synchronized
    fun handle(packet: ByteArray, length: Int, resolve: (ByteArray) -> ByteArray?): List<ByteArray> {
        val tcp = DnsTcpPacket.parse(packet, length) ?: return emptyList()
        val now = clock()
        sessions.entries.removeAll { now - it.value.lastActivity > idleTimeoutMs }
        val key = Key(tcp.version, tcp.source.joinToString("") { "%02x".format(it) },
            tcp.destination.joinToString("") { "%02x".format(it) }, tcp.sourcePort)
        if (tcp.rst) {
            sessions.remove(key)
            return emptyList()
        }

        if (tcp.syn && !tcp.ack) {
            if (sessions.size >= maxSessions) {
                val oldest = sessions.minByOrNull { it.value.lastActivity }?.key
                if (oldest != null) sessions.remove(oldest)
            }
            val serverInitial = random.nextInt().toLong() and 0xffffffffL
            val session = Session((tcp.sequence + 1L) and MASK,
                (serverInitial + 1L) and MASK, lastActivity = now)
            sessions[key] = session
            return listOf(DnsTcpPacket.response(tcp, serverInitial, session.clientNext,
                DnsTcpPacket.SYN or DnsTcpPacket.ACK))
        }

        val session = sessions[key] ?: return emptyList()
        session.lastActivity = now
        if (tcp.sequence != session.clientNext) {
            // Duplicate or out-of-order segments are not re-applied to the DNS stream.
            return listOf(ack(tcp, session))
        }
        if (tcp.payload.isNotEmpty()) {
            if (session.received.size() + tcp.payload.size > maxBufferedBytes) {
                sessions.remove(key)
                return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
            }
            session.clientNext = (session.clientNext + tcp.payload.size) and MASK
            session.received.write(tcp.payload)
            val buffered = session.received.toByteArray()
            var offset = 0
            val replies = mutableListOf<ByteArray>()
            while (buffered.size - offset >= 2) {
                val messageLength = ((buffered[offset].toInt() and 0xff) shl 8) or
                    (buffered[offset + 1].toInt() and 0xff)
                if (messageLength !in 17..maxDnsMessage) {
                    sessions.remove(key)
                    return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                        session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
                }
                if (buffered.size - offset < messageLength + 2) break
                val query = buffered.copyOfRange(offset + 2, offset + 2 + messageLength)
                val dnsAnswer = resolve(query)
                val answer = dnsAnswer ?: DomainFilter.makeServfail(query)
                if (answer != null && answer.size <= maxDnsMessage) {
                    val framed = ByteArray(answer.size + 2)
                    framed[0] = (answer.size ushr 8).toByte()
                    framed[1] = answer.size.toByte()
                    System.arraycopy(answer, 0, framed, 2, answer.size)
                    var sent = 0
                    while (sent < framed.size) {
                        val count = minOf(maxSegmentPayload, framed.size - sent)
                        val segment = framed.copyOfRange(sent, sent + count)
                        replies.add(DnsTcpPacket.response(tcp, session.serverNext,
                            session.clientNext, DnsTcpPacket.ACK or DnsTcpPacket.PSH, segment))
                        session.serverNext = (session.serverNext + count) and MASK
                        sent += count
                    }
                }
                offset += messageLength + 2
            }
            val remainder = buffered.copyOfRange(offset, buffered.size)
            session.received.reset()
            session.received.write(remainder)
            if (tcp.fin) {
                session.clientNext = (session.clientNext + 1L) and MASK
                replies.add(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.FIN or DnsTcpPacket.ACK))
                session.serverNext = (session.serverNext + 1L) and MASK
                sessions.remove(key)
                return replies
            }
            if (replies.isEmpty()) replies.add(ack(tcp, session))
            return replies
        }

        if (tcp.fin) {
            session.clientNext = (session.clientNext + 1L) and MASK
            val fin = DnsTcpPacket.response(tcp, session.serverNext, session.clientNext,
                DnsTcpPacket.FIN or DnsTcpPacket.ACK)
            session.serverNext = (session.serverNext + 1L) and MASK
            sessions.remove(key)
            return listOf(fin)
        }
        return emptyList()
    }

    @Synchronized
    fun activeSessionCount(): Int = sessions.size

    private fun ack(packet: DnsTcpPacket.Parsed, session: Session) =
        DnsTcpPacket.response(packet, session.serverNext, session.clientNext, DnsTcpPacket.ACK)

    companion object {
        private const val MASK = 0xffffffffL
    }
}
