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
        val clientInitialSequence: Long,
        var clientNext: Long,
        var serverNext: Long,
        val serverInitial: Long,
        val received: ByteArrayOutputStream = ByteArrayOutputStream(),
        var lastActivity: Long,
        var lastClientSegmentStart: Long? = null,
        var lastClientPayload: ByteArray = byteArrayOf(),
        var lastClientFin: Boolean = false,
        var clientFinReceived: Boolean = false,
        var serverFinSent: Boolean = false,
        var lastServerReplies: List<ByteArray> = emptyList()
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
            val existing = sessions[key]
            val expectedClientNext = (tcp.sequence + 1L) and MASK
            if (existing != null && existing.clientInitialSequence == tcp.sequence) {
                existing.lastActivity = now
                return listOf(DnsTcpPacket.response(tcp, existing.serverInitial,
                    (existing.clientInitialSequence + 1L) and MASK,
                    DnsTcpPacket.SYN or DnsTcpPacket.ACK))
            }
            if (sessions.size >= maxSessions && existing == null) {
                val oldest = sessions.minByOrNull { it.value.lastActivity }?.key
                if (oldest != null) sessions.remove(oldest)
            }
            val serverInitial = random.nextInt().toLong() and 0xffffffffL
            val session = Session(tcp.sequence, expectedClientNext,
                (serverInitial + 1L) and MASK, serverInitial, lastActivity = now)
            sessions[key] = session
            return listOf(DnsTcpPacket.response(tcp, serverInitial, session.clientNext,
                DnsTcpPacket.SYN or DnsTcpPacket.ACK))
        }

        val session = sessions[key] ?: return emptyList()
        // Every segment after the SYN handshake must carry ACK. Ignore
        // malformed ACK-less data/FIN so it cannot mutate the DNS stream.
        if (!tcp.ack) return emptyList()
        // SYN is valid only when opening a session. A second SYN (including
        // SYN+ACK) inside an established flow must not consume client sequence
        // space or append attacker-controlled bytes to the DNS stream.
        if (tcp.syn) return listOf(ack(tcp, session))
        // A client cannot acknowledge server sequence space that Guardian has
        // not transmitted. Reject future ACKs before accepting any DNS payload;
        // otherwise an invalid segment can mutate the request stream state.
        if (sequenceAfter(tcp.acknowledgement, session.serverNext)) {
            return listOf(ack(tcp, session))
        }
        // Keep a half-closed session until the client acknowledges our FIN.
        // This lets a lost FIN/data response be replayed on an exact retransmission.
        // A FIN is complete only when the ACK belongs to the current client stream.
        // Ignore stale/out-of-window ACKs so they cannot evict a live half-closed session.
        if (tcp.ack && tcp.payload.isEmpty() && !tcp.fin &&
            tcp.sequence == session.clientNext && session.serverFinSent &&
            tcp.acknowledgement == session.serverNext) {
            sessions.remove(key)
            return emptyList()
        }
        if (tcp.sequence != session.clientNext) {
            // Re-serve the last reply when the client retransmits the exact
            // request/FIN segment after losing the corresponding response.
            if (tcp.sequence == session.lastClientSegmentStart &&
                tcp.payload.contentEquals(session.lastClientPayload) &&
                tcp.fin == session.lastClientFin) {
                session.lastActivity = now
                return if (session.lastServerReplies.isNotEmpty()) session.lastServerReplies
                    else listOf(ack(tcp, session))
            }
            return listOf(ack(tcp, session))
        }
        // Only an in-order segment (or exact retransmission above) is valid activity.
        // Out-of-window packets must not keep bounded sessions alive indefinitely.
        session.lastActivity = now
        // Once the client has sent FIN, its byte stream is closed.
        // Do not accept new payload at the FIN sequence boundary; otherwise a
        // stray ACK+data can advance clientNext and prevent the final ACK closing
        // the half-closed session.
        if (session.clientFinReceived && tcp.payload.isNotEmpty()) {
            return listOf(ack(tcp, session))
        }
        if (tcp.payload.isNotEmpty()) {
            session.lastClientSegmentStart = tcp.sequence
            session.lastClientPayload = tcp.payload.copyOf()
            session.lastClientFin = tcp.fin
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
                // Resolver failures must not escape the packet loop and tear down the VPN.
                val dnsAnswer = try { resolve(query) } catch (_: Exception) { null }
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
            if (tcp.fin && remainder.isNotEmpty()) {
                // FIN terminates the byte stream: an incomplete DNS length prefix
                // or message must not be silently accepted as a clean close.
                sessions.remove(key)
                return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
            }
            if (tcp.fin) {
                session.clientNext = (session.clientNext + 1L) and MASK
                replies.add(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.FIN or DnsTcpPacket.ACK))
                session.serverNext = (session.serverNext + 1L) and MASK
                session.clientFinReceived = true
                session.serverFinSent = true
                session.lastServerReplies = replies.map { it.copyOf() }
                return replies
            }
            if (replies.isEmpty()) replies.add(ack(tcp, session))
            session.lastServerReplies = replies.map { it.copyOf() }
            return replies
        }

        if (tcp.fin) {
            if (session.received.size() != 0) {
                // A FIN without the remainder of a framed DNS message is a truncated stream.
                sessions.remove(key)
                return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
            }
            session.clientNext = (session.clientNext + 1L) and MASK
            val fin = DnsTcpPacket.response(tcp, session.serverNext, session.clientNext,
                DnsTcpPacket.FIN or DnsTcpPacket.ACK)
            session.serverNext = (session.serverNext + 1L) and MASK
            session.lastClientSegmentStart = tcp.sequence
            session.lastClientPayload = byteArrayOf()
            session.lastClientFin = true
            session.clientFinReceived = true
            session.serverFinSent = true
            session.lastServerReplies = listOf(fin.copyOf())
            return listOf(fin)
        }
        return emptyList()
    }

    @Synchronized
    fun activeSessionCount(): Int = sessions.size

    private fun sequenceAfter(candidate: Long, reference: Long): Boolean {
        val distance = (candidate - reference) and MASK
        return distance in 1L..0x7fffffffL
    }

    private fun ack(packet: DnsTcpPacket.Parsed, session: Session) =
        DnsTcpPacket.response(packet, session.serverNext, session.clientNext, DnsTcpPacket.ACK)

    companion object {
        private const val MASK = 0xffffffffL
    }
}
