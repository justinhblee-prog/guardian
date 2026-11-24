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
    private data class OutstandingServerPacket(
        val endSequence: Long,
        val packet: ByteArray,
        var lastSentAtMs: Long,
        var retransmissionCount: Int = 0
    )
    private data class Session(
        val clientInitialSequence: Long,
        var clientNext: Long,
        var serverNext: Long,
        val serverInitial: Long,
        var serverAcknowledged: Long,
        val maxSegmentPayload: Int,
        var peerWindow: Int = 65535,
        var peerWindowSequence: Long,
        var peerWindowAcknowledgement: Long,
        val pendingServerData: ByteArrayOutputStream = ByteArrayOutputStream(),
        val received: ByteArrayOutputStream = ByteArrayOutputStream(),
        var lastActivity: Long,
        var duplicateAckSequence: Long = serverAcknowledged,
        var duplicateAckCount: Int = 0,
        var lastClientSegmentStart: Long? = null,
        var lastClientPayload: ByteArray = byteArrayOf(),
        var lastClientFin: Boolean = false,
        var clientFinReceived: Boolean = false,
        var serverFinSent: Boolean = false,
        val outstandingServerPackets: MutableList<OutstandingServerPacket> = mutableListOf()
    )

    private val sessions = LinkedHashMap<Key, Session>()
    private val maxSessions = 128
    private val idleTimeoutMs = 60_000L
    // DNS-over-TCP uses a 16-bit length prefix; retain one maximum-sized frame plus its prefix.
    private val maxDnsMessage = 65535
    private val maxBufferedBytes = 65537
    // Cap each synthesized packet below the IPv6 minimum MTU and bound work
    // per drain even if a peer advertises an unusually small MSS.
    private val maxSegmentPayloadLimit = 1200
    private val maxSegmentsPerDrain = 64
    // Bound per-session packet objects even when a peer advertises a tiny MSS.
    private val maxOutstandingServerPackets = 256

    @Synchronized
    fun handle(packet: ByteArray, length: Int, resolve: (ByteArray) -> ByteArray?): List<ByteArray> {
        val tcp = DnsTcpPacket.parse(packet, length) ?: return emptyList()
        val now = clock()
        sessions.entries.removeAll { now - it.value.lastActivity > idleTimeoutMs }
        val key = Key(tcp.version, tcp.source.joinToString("") { "%02x".format(it) },
            tcp.destination.joinToString("") { "%02x".format(it) }, tcp.sourcePort)
        if (tcp.rst) {
            val existing = sessions[key] ?: return emptyList()
            // A reset must belong to the current receive sequence. A delayed or
            // forged stale RST must not tear down a live DNS TCP stream.
            if (tcp.sequence == existing.clientNext) sessions.remove(key)
            return emptyList()
        }

        if (tcp.syn && !tcp.ack) {
            // TCP Fast Open data is not implemented by this state machine.
            // Do not establish a session or acknowledge a SYN carrying bytes
            // that would otherwise be silently discarded.
            if (tcp.payload.isNotEmpty()) return emptyList()
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
                (serverInitial + 1L) and MASK, serverInitial,
                serverAcknowledged = (serverInitial + 1L) and MASK,
                maxSegmentPayload = minOf(
                    tcp.maximumSegmentSize ?: if (tcp.version == 4) 536 else 1220,
                    maxSegmentPayloadLimit
                ),
                peerWindow = tcp.window, peerWindowSequence = tcp.sequence,
                peerWindowAcknowledgement = tcp.acknowledgement, lastActivity = now)
            sessions[key] = session
            return listOf(DnsTcpPacket.response(tcp, serverInitial, session.clientNext,
                DnsTcpPacket.SYN or DnsTcpPacket.ACK))
        }

        val session = sessions[key] ?: return emptyList()
        // A SYN is valid only when opening a session. Accepting SYN|ACK on an
        // established stream would let a malformed segment advance DNS framing
        // state while renegotiating sequence space that Guardian never supports.
        if (tcp.syn) return listOf(ack(tcp, session))
        // Every segment after the SYN handshake must carry ACK. Ignore
        // malformed ACK-less data/FIN so it cannot mutate the DNS stream.
        if (!tcp.ack) return emptyList()
        // This state machine accepts an in-order stream. Only the next expected
        // client sequence or an exact retransmission of the last accepted segment
        // may carry ACK state; arbitrary old/future segments must not discard
        // response packets that are still needed for retransmission.
        val isExactClientRetransmission =
            tcp.sequence == session.lastClientSegmentStart &&
                tcp.payload.contentEquals(session.lastClientPayload) &&
                tcp.fin == session.lastClientFin
        if (tcp.sequence != session.clientNext && !isExactClientRetransmission) {
            return listOf(ack(tcp, session))
        }
        // A client cannot acknowledge server sequence space Guardian has not
        // transmitted. Reject future ACKs before accepting any DNS payload;
        // otherwise an invalid segment can mutate the request stream state.
        if (sequenceAfter(tcp.acknowledgement, session.serverNext)) {
            return listOf(ack(tcp, session))
        }
        // Before the first server data byte is sent, the only valid ACK is
        // exactly SYN-ACK's next sequence. A stale ACK must not establish the
        // stream or allow DNS payload to reach the resolver.
        val initialServerNext = (session.serverInitial + 1L) and MASK
        if (session.serverNext == initialServerNext &&
            tcp.acknowledgement != initialServerNext) {
            return listOf(ack(tcp, session))
        }
        // Track cumulative acknowledgements and the peer's currently advertised
        // receive window. Window scaling is not negotiated, so this is the raw
        // 16-bit window from the TCP header.
        val ackDistance = (tcp.acknowledgement - session.serverAcknowledged) and MASK
        val outstanding = (session.serverNext - session.serverAcknowledged) and MASK
        var fastRetransmit: ByteArray? = null
        if (ackDistance in 1L..outstanding) {
            session.serverAcknowledged = tcp.acknowledgement
            session.outstandingServerPackets.removeAll {
                sequenceAtOrAfter(session.serverAcknowledged, it.endSequence)
            }
            // Restart the single oldest-outstanding-data timer when ACK makes
            // forward progress. All segments may have been sent together, so
            // retaining the next packet's original timestamp causes spurious
            // immediate retransmits after the previous segment is acknowledged.
            session.outstandingServerPackets.firstOrNull()?.lastSentAtMs = now
            session.duplicateAckSequence = tcp.acknowledgement
            session.duplicateAckCount = 0
        } else if (tcp.acknowledgement == session.serverAcknowledged && outstanding > 0L) {
            if (tcp.payload.isEmpty() && !tcp.fin && tcp.window == session.peerWindow) {
                if (session.duplicateAckSequence != tcp.acknowledgement) {
                    session.duplicateAckSequence = tcp.acknowledgement
                    session.duplicateAckCount = 1
                } else {
                    session.duplicateAckCount = (session.duplicateAckCount + 1).coerceAtMost(4)
                }
                // Fire fast retransmit once per ACK number; further duplicate ACKs
                // must not repeatedly flood the same oldest segment.
                if (session.duplicateAckCount == 3) {
                    fastRetransmit = markRetransmitted(session.outstandingServerPackets.firstOrNull(), now)
                }
            } else {
                // A payload-bearing ACK or a receive-window update is not a
                // duplicate ACK and breaks the consecutive duplicate sequence.
                session.duplicateAckSequence = tcp.acknowledgement
                session.duplicateAckCount = 0
            }
        }
        // RFC 7323-style window update ordering (without window scaling): an older
        // segment must not overwrite a newer advertised receive window. A segment
        // with the same sequence is accepted only when its ACK is not older.
        val windowSequenceIsNewer = sequenceAfter(tcp.sequence, session.peerWindowSequence)
        val windowAckIsCurrent = tcp.sequence == session.peerWindowSequence &&
            sequenceAtOrAfter(tcp.acknowledgement, session.peerWindowAcknowledgement)
        // Only a segment at the exact next client sequence position may
        // update the receive window. Future/out-of-order or duplicate old
        // segments must not pin the window ordering ahead of the live stream.
        if (tcp.sequence == session.clientNext &&
            (windowSequenceIsNewer || windowAckIsCurrent)) {
            session.peerWindow = tcp.window
            session.peerWindowSequence = tcp.sequence
            session.peerWindowAcknowledgement = tcp.acknowledgement
        }
        session.lastActivity = now
        // Three duplicate cumulative ACKs indicate a gap in the server response
        // stream. Retransmit the oldest unacknowledged segment without waiting
        // for the original request payload to be retransmitted by the client.
        fastRetransmit?.let { return listOf(it) }
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
                val outstanding = session.outstandingServerPackets
                if (outstanding.isNotEmpty()) {
                    // Replaying an answer is a real retransmission: advance the
                    // same retry budget and restart each segment's RTO timestamp.
                    // If any outstanding segment has exhausted its retry budget,
                    // expire the session rather than bypassing the RTO limit.
                    if (outstanding.any { it.retransmissionCount >= MAX_RETRANSMISSIONS }) {
                        sessions.remove(key)
                        return emptyList()
                    }
                    return outstanding.mapNotNull { markRetransmitted(it, now) }
                }
                // A delayed duplicate after cumulative ACK must not replay
                // data the peer has already confirmed; answer with current ACK.
                return listOf(ack(tcp, session))
            }
            return listOf(ack(tcp, session))
        }
        // ACK-only packets can reopen a zero/small receive window and drain
        // queued DNS response bytes without requiring another DNS request.
        if (tcp.payload.isEmpty() && !tcp.fin &&
            (session.pendingServerData.size() > 0 || session.clientFinReceived && !session.serverFinSent)) {
            val replies = drainServerData(tcp, session).toMutableList()
            appendPendingFin(tcp, session, replies)
            // Exact retransmissions replay only packets still awaiting ACK.
            return replies
        }
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
            session.clientNext = (session.clientNext + tcp.payload.size) and MASK
            // Parse the complete bounded input chunk before applying the
            // per-frame remainder limit. A nearly complete maximum-sized DNS
            // frame may finish in this segment with another query behind it.
            val previous = session.received.toByteArray()
            val buffered = ByteArray(previous.size + tcp.payload.size)
            System.arraycopy(previous, 0, buffered, 0, previous.size)
            System.arraycopy(tcp.payload, 0, buffered, previous.size, tcp.payload.size)
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
                // DNS-over-TCP's two-byte frame length cannot represent an
                // oversized upstream message. Return a bounded SERVFAIL instead
                // of silently ACKing the query without ever producing an answer.
                val answer = if (dnsAnswer == null || dnsAnswer.size > maxDnsMessage) {
                    DomainFilter.makeServfail(query)
                } else {
                    dnsAnswer
                }
                if (answer != null && answer.size <= maxDnsMessage) {
                    val framed = ByteArray(answer.size + 2)
                    framed[0] = (answer.size ushr 8).toByte()
                    framed[1] = answer.size.toByte()
                    System.arraycopy(answer, 0, framed, 2, answer.size)
                    // A single inbound TCP segment can contain thousands of
                    // minimum-sized DNS frames. Bound queued answers as well as
                    // the inbound stream so one client cannot grow memory without limit.
                    if (session.pendingServerData.size() > maxBufferedBytes - framed.size) {
                        sessions.remove(key)
                        return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                            session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
                    }
                    session.pendingServerData.write(framed)
                }
                offset += messageLength + 2
            }
            val remainder = buffered.copyOfRange(offset, buffered.size)
            if (remainder.size > maxBufferedBytes) {
                sessions.remove(key)
                return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
            }
            session.received.reset()
            session.received.write(remainder)
            if (tcp.fin && remainder.isNotEmpty()) {
                // FIN terminates the byte stream: an incomplete DNS length prefix
                // or message must not be silently accepted as a clean close.
                sessions.remove(key)
                return listOf(DnsTcpPacket.response(tcp, session.serverNext,
                    session.clientNext, DnsTcpPacket.RST or DnsTcpPacket.ACK))
            }
            replies.addAll(drainServerData(tcp, session))
            if (tcp.fin) {
                session.clientNext = (session.clientNext + 1L) and MASK
                session.clientFinReceived = true
                appendPendingFin(tcp, session, replies)
                return if (replies.isEmpty()) listOf(ack(tcp, session)) else replies
            }
            if (replies.isEmpty()) replies.add(ack(tcp, session))
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
            session.lastClientSegmentStart = tcp.sequence
            session.lastClientPayload = byteArrayOf()
            session.lastClientFin = true
            session.clientFinReceived = true
            val replies = drainServerData(tcp, session).toMutableList()
            appendPendingFin(tcp, session, replies)
            if (replies.isEmpty()) replies.add(ack(tcp, session))
            return replies
        }
        return emptyList()
    }

    private fun appendPendingFin(packet: DnsTcpPacket.Parsed, session: Session, replies: MutableList<ByteArray>) {
        if (!session.clientFinReceived || session.serverFinSent || session.pendingServerData.size() != 0) return
        val outstanding = (session.serverNext - session.serverAcknowledged) and MASK
        if (outstanding >= session.peerWindow.toLong() ||
            session.outstandingServerPackets.size >= maxOutstandingServerPackets) return
        val finPacket = DnsTcpPacket.response(packet, session.serverNext, session.clientNext,
            DnsTcpPacket.FIN or DnsTcpPacket.ACK)
        session.serverNext = (session.serverNext + 1L) and MASK
        session.outstandingServerPackets.add(
            OutstandingServerPacket(session.serverNext, finPacket.copyOf(), clock())
        )
        replies.add(finPacket)
        session.serverFinSent = true
    }

    private fun drainServerData(packet: DnsTcpPacket.Parsed, session: Session): List<ByteArray> {
        val outstanding = (session.serverNext - session.serverAcknowledged) and MASK
        val available = (session.peerWindow.toLong() - outstanding).coerceAtLeast(0L).toInt()
        if (available == 0 || session.pendingServerData.size() == 0) return emptyList()
        val pending = session.pendingServerData.toByteArray()
        val availablePacketSlots =
            (maxOutstandingServerPackets - session.outstandingServerPackets.size).coerceAtLeast(0)
        if (availablePacketSlots == 0) return emptyList()
        val segmentBudget = minOf(maxSegmentsPerDrain, availablePacketSlots)
        val sendBytes = minOf(
            available, pending.size, session.maxSegmentPayload * segmentBudget
        )
        val replies = ArrayList<ByteArray>()
        var offset = 0
        while (offset < sendBytes) {
            val count = minOf(session.maxSegmentPayload, sendBytes - offset)
            val segment = pending.copyOfRange(offset, offset + count)
            val responsePacket = DnsTcpPacket.response(packet, session.serverNext, session.clientNext,
                DnsTcpPacket.ACK or DnsTcpPacket.PSH, segment)
            session.serverNext = (session.serverNext + count) and MASK
            session.outstandingServerPackets.add(
                OutstandingServerPacket(session.serverNext, responsePacket.copyOf(), clock())
            )
            replies.add(responsePacket)
            offset += count
        }
        session.pendingServerData.reset()
        session.pendingServerData.write(pending, sendBytes, pending.size - sendBytes)
        return replies
    }

    /**
     * Returns TCP retransmissions that have reached their retransmission timeout.
     * Only the oldest unacknowledged segment per session is retried at a time.
     * Sessions are discarded after the bounded retry budget is exhausted.
     */
    @Synchronized
    fun retransmitDue(): List<ByteArray> {
        val now = clock()
        sessions.entries.removeAll { now - it.value.lastActivity > idleTimeoutMs }
        val expired = mutableListOf<Key>()
        val packets = mutableListOf<ByteArray>()
        sessions.forEach { (key, session) ->
            val oldest = session.outstandingServerPackets.firstOrNull() ?: return@forEach
            val timeout = retransmissionTimeoutMs(oldest.retransmissionCount)
            if (now - oldest.lastSentAtMs < timeout) return@forEach
            if (oldest.retransmissionCount >= MAX_RETRANSMISSIONS) {
                expired.add(key)
            } else {
                packets.add(markRetransmitted(oldest, now)!!)
            }
        }
        expired.forEach { key -> sessions.remove(key) }
        return packets
    }

    private fun markRetransmitted(packet: OutstandingServerPacket?, now: Long): ByteArray? {
        packet ?: return null
        if (packet.retransmissionCount >= MAX_RETRANSMISSIONS) return null
        packet.retransmissionCount++
        packet.lastSentAtMs = now
        return packet.packet.copyOf()
    }

    private fun retransmissionTimeoutMs(retryCount: Int): Long = when (retryCount) {
        0 -> INITIAL_RETRANSMISSION_TIMEOUT_MS
        1 -> INITIAL_RETRANSMISSION_TIMEOUT_MS * 2L
        2 -> INITIAL_RETRANSMISSION_TIMEOUT_MS * 4L
        else -> MAX_RETRANSMISSION_TIMEOUT_MS
    }

    @Synchronized
    fun activeSessionCount(): Int = sessions.size

    private fun sequenceAfter(candidate: Long, reference: Long): Boolean {
        val distance = (candidate - reference) and MASK
        return distance in 1L..0x7fffffffL
    }

    private fun sequenceAtOrAfter(candidate: Long, reference: Long): Boolean =
        candidate == reference || sequenceAfter(candidate, reference)

    private fun ack(packet: DnsTcpPacket.Parsed, session: Session) =
        DnsTcpPacket.response(packet, session.serverNext, session.clientNext, DnsTcpPacket.ACK)

    companion object {
        private const val MASK = 0xffffffffL
        private const val INITIAL_RETRANSMISSION_TIMEOUT_MS = 1_000L
        private const val MAX_RETRANSMISSION_TIMEOUT_MS = 8_000L
        private const val MAX_RETRANSMISSIONS = 5
    }
}
