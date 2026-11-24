package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class DnsTcpSessionManagerTest {
    @Test fun completesHandshakeAndAcknowledgesClientData() {
        var now = 1000L
        val manager = DnsTcpSessionManager({ now }, deterministicRandom())
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 100)
        val synAck = manager.handle(syn, syn.size) { null }.single()
        val parsedSynAck = DnsTcpPacket.parse(synAck, synAck.size, 53000)
        assertNotNull(parsedSynAck)
        assertEquals(DnsTcpPacket.SYN or DnsTcpPacket.ACK, parsedSynAck!!.flags)
        assertEquals(101L, parsedSynAck.acknowledgement)

        val ack = packet(flags = DnsTcpPacket.ACK, seq = 101)
        assertTrue(manager.handle(ack, ack.size) { null }.isEmpty())
        assertEquals(1, manager.activeSessionCount())
        now += 61_000
        manager.handle(packet(flags = DnsTcpPacket.ACK, seq = 101), 40) { null }
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun boundsBufferedTcpDataAndResetsConnection() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 300)
        manager.handle(syn, syn.size) { null }
        val oversized = ByteArray(8200)
        val segment = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 301, payload = oversized)
        val reset = manager.handle(segment, segment.size) { null }.single()
        val parsed = DnsTcpPacket.parse(reset, reset.size, 53000)
        assertNotNull(parsed)
        assertTrue(parsed!!.rst)
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun acceptsNextDnsFrameWhenMaximumFrameCompletesInSameSegment() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 7800)
        manager.handle(syn, syn.size) { null }

        // A maximum-length DNS message needs 65,537 TCP stream bytes including
        // its length prefix. The final 42 bytes and a second complete query are
        // coalesced into one segment, briefly exceeding the single-frame cap.
        val maximumFrame = byteArrayOf(0xff.toByte(), 0xff.toByte()) + ByteArray(65535)
        val secondQuery = dnsQuery()
        val secondFrame = byteArrayOf((secondQuery.size ushr 8).toByte(), secondQuery.size.toByte()) + secondQuery
        val firstPayload = maximumFrame.copyOfRange(0, 65495)
        val finalPayload = maximumFrame.copyOfRange(65495, maximumFrame.size) + secondFrame
        val resolvedMessages = mutableListOf<ByteArray>()

        val firstSegment = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 7801, payload = firstPayload)
        val firstReply = manager.handle(firstSegment, firstSegment.size) {
            resolvedMessages.add(it.copyOf())
            secondQuery
        }.single()
        assertTrue(DnsTcpPacket.parse(firstReply, firstReply.size, 53000)!!.payload.isEmpty())

        val secondSegment = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 7801L + firstPayload.size, payload = finalPayload)
        val replies = manager.handle(secondSegment, secondSegment.size) {
            resolvedMessages.add(it.copyOf())
            secondQuery
        }
        assertEquals(2, resolvedMessages.size)
        assertEquals(65535, resolvedMessages.first().size)
        assertArrayEquals(secondQuery, resolvedMessages.last())
        assertTrue(replies.any { DnsTcpPacket.parse(it, it.size, 53000)!!.payload.isNotEmpty() })
        assertEquals(1, manager.activeSessionCount())
    }

    @Test fun rejectsMalformedDnsLengthAndResetsConnection() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 200)
        manager.handle(syn, syn.size) { null }
        val data = byteArrayOf(0, 1, 0x12)
        val segment = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 201, payload = data)
        val reset = manager.handle(segment, segment.size) { null }.single()
        val parsed = DnsTcpPacket.parse(reset, reset.size, 53000)
        assertNotNull(parsed)
        assertTrue(parsed!!.rst)
        assertEquals(0, manager.activeSessionCount())
    }


    @Test fun forwardsCompleteDnsOverTcpQueryAndFramesTheAnswer() {
        val manager = newManager()
        val query = dnsQuery()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1000)
        val synAckBytes = manager.handle(syn, syn.size) { null }.single()
        val synAck = DnsTcpPacket.parse(synAckBytes, synAckBytes.size, 53000)!!
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        var calls = 0
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 1001, payload = framed)
        val replies = manager.handle(request, request.size) {
            calls++
            assertArrayEquals(query, it)
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, calls)
        assertEquals(1, replies.size)
        val segment = DnsTcpPacket.parse(replies.single(), replies.single().size, 53000)!!
        assertTrue(segment.ack)
        assertEquals(1001L + framed.size, segment.acknowledgement)
        assertEquals((synAck.sequence + 1L) and 0xffffffffL, segment.sequence)
        val answer = segment.payload
        val answerLength = ((answer[0].toInt() and 255) shl 8) or (answer[1].toInt() and 255)
        assertEquals(answerLength, answer.size - 2)
        assertEquals(2, answer[5].toInt() and 0x0f)
    }

    @Test fun reassemblesPartialDnsFrameAcrossTcpSegments() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 700)
        manager.handle(syn, syn.size) { null }
        var calls = 0
        val first = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 701,
            payload = framed.copyOfRange(0, 9))
        val firstReply = manager.handle(first, first.size) { calls++; null }.single()
        assertTrue(DnsTcpPacket.parse(firstReply, firstReply.size, 53000)!!.ack)
        assertEquals(0, calls)
        val second = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 710,
            payload = framed.copyOfRange(9, framed.size))
        val replies = manager.handle(second, second.size) {
            calls++
            assertArrayEquals(query, it)
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, calls)
        assertEquals(1, replies.size)
        assertTrue(DnsTcpPacket.parse(replies.single(), replies.single().size, 53000)!!.payload.isNotEmpty())
    }





    @Test fun rejectsOutOfOrderTcpDataWithoutConsumingDnsStream() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1500)
        manager.handle(syn, syn.size) { null }

        var calls = 0
        val outOfOrder = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 1502,
            payload = framed
        )
        val gapAck = manager.handle(outOfOrder, outOfOrder.size) {
            calls++
            DomainFilter.makeServfail(it)
        }.single()
        assertEquals(1501L, DnsTcpPacket.parse(gapAck, gapAck.size, 53000)!!.acknowledgement)
        assertEquals(0, calls)

        val inOrder = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 1501,
            payload = framed
        )
        val reply = manager.handle(inOrder, inOrder.size) {
            calls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, calls)
        assertTrue(reply.isNotEmpty())
    }

    @Test fun rejectsOverlappingTcpPayloadWithoutDuplicatingDnsBytes() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 2500)
        manager.handle(syn, syn.size) { null }

        var calls = 0
        val firstPart = framed.copyOfRange(0, 9)
        val first = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 2501,
            payload = firstPart
        )
        val firstAck = manager.handle(first, first.size) { calls++; null }.single()
        assertEquals(2510L, DnsTcpPacket.parse(firstAck, firstAck.size, 53000)!!.acknowledgement)

        val overlapping = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 2505,
            payload = framed.copyOfRange(4, framed.size)
        )
        val overlapAck = manager.handle(overlapping, overlapping.size) {
            calls++
            DomainFilter.makeServfail(it)
        }.single()
        assertEquals(2510L, DnsTcpPacket.parse(overlapAck, overlapAck.size, 53000)!!.acknowledgement)
        assertEquals(0, calls)

        val remainder = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 2510,
            payload = framed.copyOfRange(9, framed.size)
        )
        val reply = manager.handle(remainder, remainder.size) {
            calls++
            assertArrayEquals(query, it)
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, calls)
        assertTrue(reply.isNotEmpty())
    }

    @Test fun handlesDnsOverTcpSessionWithIpv6Packets() {
        val manager = newManager()
        val syn = ipv6Packet(DnsTcpPacket.SYN, 11000)
        val synAckBytes = manager.handle(syn, syn.size) { null }.single()
        val synAck = DnsTcpPacket.parse(synAckBytes, synAckBytes.size, 53000)!!
        assertEquals(6, synAck.version)
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val request = ipv6Packet(DnsTcpPacket.ACK or DnsTcpPacket.PSH, 11001, framed)
        val replies = manager.handle(request, request.size) { DomainFilter.makeServfail(it) }
        assertEquals(1, replies.size)
        val response = DnsTcpPacket.parse(replies.single(), replies.single().size, 53000)!!
        assertEquals(6, response.version)
        assertTrue(response.ack)
        assertEquals(11001L + framed.size, response.acknowledgement)
        assertTrue(response.payload.isNotEmpty())
    }

    private fun ipv6Packet(flags: Int, seq: Long, payload: ByteArray = byteArrayOf()): ByteArray {
        val tcpLength = 20 + payload.size
        val p = ByteArray(40 + tcpLength)
        p[0] = 0x60
        p[4] = (tcpLength ushr 8).toByte()
        p[5] = tcpLength.toByte()
        p[6] = 6
        p[7] = 64
        p[8] = 0x20; p[9] = 0x01; p[10] = 0x0d; p[11] = 0xb8.toByte(); p[23] = 1
        p[24] = 0xfd.toByte(); p[25] = 0x13; p[26] = 0; p[27] = 0x37; p[39] = 2
        p[40] = 0xcf.toByte(); p[41] = 0x08; p[42] = 0; p[43] = 53
        p[44] = (seq ushr 24).toByte(); p[45] = (seq ushr 16).toByte()
        p[46] = (seq ushr 8).toByte(); p[47] = seq.toByte()
        p[52] = 0x50; p[53] = flags.toByte()
        p[54] = 0xff.toByte(); p[55] = 0xff.toByte()
        payload.copyInto(p, 60)
        sealTcp6(p, tcpLength)
        return p
    }

    private fun sealTcp6(p: ByteArray, tcpLength: Int) {
        p[56] = 0; p[57] = 0
        var sum = 0L
        fun add(start: Int, length: Int) {
            var i = start
            while (i + 1 < start + length) {
                sum += (((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)).toLong()
                i += 2
            }
            if (i < start + length) sum += ((p[i].toInt() and 255) shl 8).toLong()
        }
        add(8, 32)
        sum += tcpLength.toLong()
        sum += 6L
        add(40, tcpLength)
        while ((sum ushr 16) != 0L) sum = (sum and 65535) + (sum ushr 16)
        val value = sum.inv().toInt() and 65535
        p[56] = (value ushr 8).toByte()
        p[57] = value.toByte()
    }

    @Test fun processesMultipleDnsMessagesFromOneTcpPayload() {
        val manager = newManager()
        val query = dnsQuery()
        val frame = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val payload = frame + frame
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 4000)
        manager.handle(syn, syn.size) { null }
        var calls = 0
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 4001, payload = payload)
        val replies = manager.handle(request, request.size) {
            calls++
            assertArrayEquals(query, it)
            DomainFilter.makeServfail(it)
        }
        assertEquals(2, calls)
        assertEquals(2, replies.size)
        val first = DnsTcpPacket.parse(replies[0], replies[0].size, 53000)!!
        val second = DnsTcpPacket.parse(replies[1], replies[1].size, 53000)!!
        assertEquals((first.sequence + first.payload.size) and 0xffffffffL, second.sequence)
        assertEquals(4001L + payload.size, second.acknowledgement)
    }

    @Test fun boundsQueuedDnsTcpResponsesAcrossManyQueries() {
        val manager = newManager()
        val query = dnsQuery()
        val frame = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val payload = frame + frame
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 8750, window = 0)
        manager.handle(syn, syn.size) { null }
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8751, payload = payload, window = 0)
        var calls = 0
        val reset = manager.handle(request, request.size) {
            calls++
            ByteArray(40000).also {
                it[0] = 0x12
                it[1] = 0x34
                it[2] = 0x81.toByte()
                it[3] = 0x80.toByte()
            }
        }.single()
        val parsed = DnsTcpPacket.parse(reset, reset.size, 53000)!!
        assertTrue(parsed.rst)
        assertEquals(2, calls)
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun segmentsLargeDnsAnswersWithinConservativeTcpMss() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val largeAnswer = ByteArray(1200).also {
            it[0] = 0x12
            it[1] = 0x34
            it[2] = 0x81.toByte()
            it[3] = 0x80.toByte()
        }
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 8000)
        manager.handle(syn, syn.size) { null }
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8001, payload = framedQuery)
        val replies = manager.handle(request, request.size) { largeAnswer }
        assertEquals(3, replies.size)
        var expectedSequence: Long? = null
        val assembled = java.io.ByteArrayOutputStream()
        replies.forEach { bytes ->
            val segment = DnsTcpPacket.parse(bytes, bytes.size, 53000)!!
            assertTrue(segment.payload.size <= 1200)
            if (expectedSequence != null) assertEquals(expectedSequence!!.toLong(), segment.sequence)
            expectedSequence = (segment.sequence + segment.payload.size) and 0xffffffffL
            assertEquals(8001L + framedQuery.size, segment.acknowledgement)
            assembled.write(segment.payload)
        }
        val framedAnswer = assembled.toByteArray()
        val declaredLength = ((framedAnswer[0].toInt() and 255) shl 8) or
            (framedAnswer[1].toInt() and 255)
        assertEquals(1200, declaredLength)
        assertEquals(1202, framedAnswer.size)
    }

    @Test fun retransmittedSynReceivesTheSameServerSequence() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 5000)
        val firstBytes = manager.handle(syn, syn.size) { null }.single()
        val first = DnsTcpPacket.parse(firstBytes, firstBytes.size, 53000)!!
        val secondBytes = manager.handle(syn, syn.size) { null }.single()
        val second = DnsTcpPacket.parse(secondBytes, secondBytes.size, 53000)!!
        assertEquals(first.sequence, second.sequence)
        assertEquals(first.acknowledgement, second.acknowledgement)
        assertEquals(1, manager.activeSessionCount())
    }

    @Test fun delayedSynRetransmissionDoesNotResetAnEstablishedDnsSession() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 5100)
        val initialSynAckBytes = manager.handle(syn, syn.size) { null }.single()
        val initialSynAck = DnsTcpPacket.parse(initialSynAckBytes, initialSynAckBytes.size, 53000)!!
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 5101, payload = framed)
        manager.handle(request, request.size) { DomainFilter.makeServfail(it) }

        val delayedSynAckBytes = manager.handle(syn, syn.size) { null }.single()
        val delayedSynAck = DnsTcpPacket.parse(delayedSynAckBytes, delayedSynAckBytes.size, 53000)!!
        assertEquals(initialSynAck.sequence, delayedSynAck.sequence)
        assertEquals(5101L, delayedSynAck.acknowledgement)
        assertEquals(1, manager.activeSessionCount())

        var calls = 0
        val nextRequest = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 5101L + framed.size, payload = framed)
        val replies = manager.handle(nextRequest, nextRequest.size) {
            calls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, calls)
        assertTrue(replies.isNotEmpty())
    }

    @Test fun outOfOrderFutureAckCannotPinReceiveWindowAndBlockDnsResponse() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 7200, window = 0)
        val synAckBytes = manager.handle(syn, syn.size) { null }.single()
        val synAck = DnsTcpPacket.parse(synAckBytes, synAckBytes.size, 53000)!!
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 7201, acknowledgment = (synAck.sequence + 1L) and 0xffffffffL,
            payload = framed, window = 0)
        val queuedAck = manager.handle(request, request.size) {
            DomainFilter.makeServfail(it)
        }.single()
        assertTrue(DnsTcpPacket.parse(queuedAck, queuedAck.size, 53000)!!.payload.isEmpty())

        // This future-sequence segment is outside RCV.NXT and must not
        // replace the live receive-window update ordering.
        val future = packet(flags = DnsTcpPacket.ACK,
            seq = 7202L + framed.size, acknowledgment = synAck.sequence + 1L,
            window = 0)
        assertTrue(manager.handle(future, future.size) { null }.isNotEmpty())

        val validWindowUpdate = packet(flags = DnsTcpPacket.ACK,
            seq = 7201L + framed.size, acknowledgment = synAck.sequence + 1L,
            window = 4096)
        val replies = manager.handle(validWindowUpdate, validWindowUpdate.size) { null }
        assertTrue(replies.isNotEmpty())
        val parsed = replies.map { DnsTcpPacket.parse(it, it.size, 53000)!! }
        assertTrue(parsed.any { it.payload.isNotEmpty() })
    }

    @Test fun retransmittedDnsRequestReplaysTheCachedAnswerWithoutResolvingAgain() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 6000)
        manager.handle(syn, syn.size) { null }
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 6001, payload = framed)
        var calls = 0
        val first = manager.handle(request, request.size) {
            calls++
            DomainFilter.makeServfail(it)
        }.single()
        val replay = manager.handle(request, request.size) { calls++; null }.single()
        assertEquals(1, calls)
        assertArrayEquals(first, replay)
    }

    @Test fun exactDuplicateRequestRestartsRtoAndConsumesRetransmissionBudget() {
        var now = 0L
        val manager = DnsTcpSessionManager(clock = { now }, random = deterministicRandom())
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 7700)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 7701, acknowledgment = serverStart, payload = framedQuery)
        val answer = ByteArray(5000) { (it and 0xff).toByte() }
        val sent = manager.handle(request, request.size) { answer }
        assertTrue(sent.size > 1)

        now = 500L
        val replayed = manager.handle(request, request.size) { error("Exact retransmission must not resolve DNS again") }
        assertEquals(sent.size, replayed.size)
        replayed.zip(sent).forEach { (actual, expected) -> assertArrayEquals(expected, actual) }

        // The duplicate-request replay consumed retry #1 and reset the timer:
        // initial 1s RTO is replaced by the 2s backoff measured from t=500ms.
        now = 1_499L
        assertTrue(manager.retransmitDue().isEmpty())
        now = 2_499L
        assertTrue(manager.retransmitDue().isEmpty())
        now = 2_500L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
    }

    @Test fun delayedDuplicateRequestDoesNotReplayAlreadyAcknowledgedDnsAnswer() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 6100)
        val synAckBytes = manager.handle(syn, syn.size) { null }.single()
        val synAck = DnsTcpPacket.parse(synAckBytes, synAckBytes.size, 53000)!!
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 6101, acknowledgment = (synAck.sequence + 1L) and 0xffffffffL,
            payload = framed)
        var calls = 0
        val answerBytes = manager.handle(request, request.size) {
            calls++
            DomainFilter.makeServfail(it)
        }.single()
        val answer = DnsTcpPacket.parse(answerBytes, answerBytes.size, 53000)!!
        assertTrue(answer.payload.isNotEmpty())

        val responseAck = packet(flags = DnsTcpPacket.ACK,
            seq = 6101L + framed.size,
            acknowledgment = (answer.sequence + answer.payload.size) and 0xffffffffL)
        assertTrue(manager.handle(responseAck, responseAck.size) { null }.isEmpty())

        val delayedDuplicate = manager.handle(request, request.size) { calls++; null }.single()
        val duplicateAck = DnsTcpPacket.parse(delayedDuplicate, delayedDuplicate.size, 53000)!!
        assertTrue(duplicateAck.ack)
        assertTrue(duplicateAck.payload.isEmpty())
        assertEquals(6101L + framed.size, duplicateAck.acknowledgement)
        assertEquals(1, calls)
    }

    @Test fun retransmittedDataAndFinReplaysRepliesUntilClientAcknowledgesServerFin() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 900)
        manager.handle(syn, syn.size) { null }
        val segment = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH or DnsTcpPacket.FIN,
            seq = 901, payload = framed)
        var resolveCalls = 0
        val replies = manager.handle(segment, segment.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(2, replies.size)
        val dataReply = DnsTcpPacket.parse(replies.first(), replies.first().size, 53000)!!
        assertTrue(dataReply.payload.isNotEmpty())
        val finReply = DnsTcpPacket.parse(replies.last(), replies.last().size, 53000)!!
        assertTrue(finReply.fin)
        assertEquals(902L + framed.size, finReply.acknowledgement)
        assertEquals(1, manager.activeSessionCount())

        val replay = manager.handle(segment, segment.size) {
            resolveCalls++
            null
        }
        assertEquals(2, replay.size)
        replay.zip(replies).forEach { (actual, expected) -> assertArrayEquals(expected, actual) }
        assertEquals(1, resolveCalls)

        val finalAck = packet(
            flags = DnsTcpPacket.ACK,
            seq = 902L + framed.size,
            acknowledgment = (finReply.sequence + 1L) and 0xffffffffL
        )
        assertTrue(manager.handle(finalAck, finalAck.size) { null }.isEmpty())
        assertEquals(0, manager.activeSessionCount())
    }


    @Test fun staleAcknowledgementCannotCompleteHandshakeOrConsumeDnsPayload() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 3150)
        val synAck = DnsTcpPacket.parse(
            manager.handle(syn, syn.size) { null }.single(), 40, 53000
        )!!

        var resolveCalls = 0
        val stale = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3151,
            acknowledgment = (synAck.sequence + 0L) and 0xffffffffL,
            payload = framed
        )
        val rejected = manager.handle(stale, stale.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(0, resolveCalls)
        assertEquals(1, manager.activeSessionCount())
        assertTrue(rejected.isNotEmpty())
        assertTrue(DnsTcpPacket.parse(rejected.single(), rejected.single().size, 53000)!!.ack)

        val valid = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3151,
            acknowledgment = (synAck.sequence + 1L) and 0xffffffffL,
            payload = framed
        )
        val accepted = manager.handle(valid, valid.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, resolveCalls)
        assertTrue(accepted.isNotEmpty())
    }

    @Test fun futureAcknowledgementCannotConsumeDnsRequestPayload() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 3200)
        val synAck = manager.handle(syn, syn.size) { null }.single()
        val parsedSynAck = DnsTcpPacket.parse(synAck, synAck.size, 53000)!!

        var resolveCalls = 0
        val forgedFutureAck = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3201,
            acknowledgment = (parsedSynAck.sequence + 100L) and 0xffffffffL,
            payload = framed
        )
        val rejected = manager.handle(forgedFutureAck, forgedFutureAck.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }.single()
        assertTrue(DnsTcpPacket.parse(rejected, rejected.size, 53000)!!.ack)
        assertEquals(0, resolveCalls)
        assertEquals(1, manager.activeSessionCount())

        val valid = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3201,
            acknowledgment = (parsedSynAck.sequence + 1L) and 0xffffffffL,
            payload = framed
        )
        val accepted = manager.handle(valid, valid.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }
        assertTrue(accepted.isNotEmpty())
        assertEquals(1, resolveCalls)
        assertEquals(1, manager.activeSessionCount())
    }


    @Test fun futureAcknowledgementAcrossSequenceWraparoundCannotConsumeDnsRequestPayload() {
        val wrapRandom = object : SecureRandom() {
            override fun nextInt(): Int = 0xfffffff0.toInt()
        }
        val manager = DnsTcpSessionManager(random = wrapRandom)
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 3200)
        val synAck = manager.handle(syn, syn.size) { null }.single()
        val parsedSynAck = DnsTcpPacket.parse(synAck, synAck.size, 53000)!!
        assertEquals(0xfffffff0L, parsedSynAck.sequence)
        val serverNext = (parsedSynAck.sequence + 1L) and 0xffffffffL

        var resolveCalls = 0
        val forged = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3201,
            acknowledgment = 0x00000010L,
            payload = framed
        )
        val rejected = manager.handle(forged, forged.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }.single()
        val rejectedSegment = DnsTcpPacket.parse(rejected, rejected.size, 53000)!!
        assertTrue(rejectedSegment.ack)
        assertEquals(3201L, rejectedSegment.acknowledgement)
        assertEquals(0, resolveCalls)
        assertEquals(1, manager.activeSessionCount())

        val valid = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3201,
            acknowledgment = serverNext,
            payload = framed
        )
        val accepted = manager.handle(valid, valid.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }
        assertTrue(accepted.isNotEmpty())
        assertEquals(1, resolveCalls)
        assertEquals(1, manager.activeSessionCount())
    }

    @Test fun rejectsFutureAcknowledgementAcrossSequenceNumberWrap() {
        val wrapRandom = object : SecureRandom() {
            override fun nextInt(): Int = -2
        }
        val manager = DnsTcpSessionManager(random = wrapRandom)
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 3300)
        val synAck = DnsTcpPacket.parse(
            manager.handle(syn, syn.size) { null }.single(), 40, 53000
        )!!
        assertEquals(0xfffffffeL, synAck.sequence)

        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val wrappedFutureAck = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 3301,
            acknowledgment = 0L,
            payload = framed
        )
        var resolveCalls = 0
        manager.handle(wrappedFutureAck, wrappedFutureAck.size) {
            resolveCalls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(0, resolveCalls)
        assertEquals(1, manager.activeSessionCount())
    }

    @Test fun ignoresPayloadWithoutAckAndAcceptsValidRetransmissionAtSameSequence() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1600)
        manager.handle(syn, syn.size) { null }

        val malformed = packet(flags = DnsTcpPacket.PSH, seq = 1601, payload = framed)
        var calls = 0
        assertTrue(manager.handle(malformed, malformed.size) { calls++; null }.isEmpty())
        assertEquals(0, calls)
        assertEquals(1, manager.activeSessionCount())

        val valid = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 1601, payload = framed)
        val replies = manager.handle(valid, valid.size) {
            calls++
            DomainFilter.makeServfail(it)
        }
        assertEquals(1, calls)
        assertTrue(replies.isNotEmpty())
        assertTrue(DnsTcpPacket.parse(replies.single(), replies.single().size, 53000)!!.payload.isNotEmpty())
    }

    @Test fun doesNotTreatAckWithPayloadAsFinalFinAcknowledgement() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1500)
        manager.handle(syn, syn.size) { null }
        val fin = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.FIN, seq = 1501)
        val finReply = manager.handle(fin, fin.size) { null }.single()
        val serverFin = DnsTcpPacket.parse(finReply, finReply.size, 53000)!!
        val strayPayloadAck = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 1502, acknowledgment = (serverFin.sequence + 1L) and 0xffffffffL,
            payload = byteArrayOf(0x55))
        manager.handle(strayPayloadAck, strayPayloadAck.size) { null }
        assertEquals(1, manager.activeSessionCount())
        val finalAck = packet(flags = DnsTcpPacket.ACK, seq = 1502,
            acknowledgment = (serverFin.sequence + 1L) and 0xffffffffL)
        manager.handle(finalAck, finalAck.size) { null }
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun rejectsFinWhenDnsTcpFrameIsTruncated() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1300)
        manager.handle(syn, syn.size) { null }
        val partial = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 1301, payload = framed.copyOfRange(0, 10))
        val ack = manager.handle(partial, partial.size) { null }.single()
        assertTrue(DnsTcpPacket.parse(ack, ack.size, 53000)!!.ack)
        val fin = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.FIN,
            seq = 1311)
        val reset = manager.handle(fin, fin.size) { null }.single()
        assertTrue(DnsTcpPacket.parse(reset, reset.size, 53000)!!.rst)
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun rejectsFinPayloadWithTrailingIncompleteDnsFrame() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1400)
        manager.handle(syn, syn.size) { null }
        val partial = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH or DnsTcpPacket.FIN,
            seq = 1401, payload = framed.copyOfRange(0, 10))
        val reset = manager.handle(partial, partial.size) { null }.single()
        assertTrue(DnsTcpPacket.parse(reset, reset.size, 53000)!!.rst)
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun retransmittedClientFinReplaysServerFinUntilAcknowledged() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 1200)
        manager.handle(syn, syn.size) { null }
        val fin = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.FIN, seq = 1201)
        val first = manager.handle(fin, fin.size) { null }.single()
        val replay = manager.handle(fin, fin.size) { null }.single()
        assertArrayEquals(first, replay)
        assertEquals(1, manager.activeSessionCount())
        val serverFin = DnsTcpPacket.parse(first, first.size, 53000)!!
        val staleAck = packet(
            flags = DnsTcpPacket.ACK,
            seq = 1201, // Wrong client sequence: must not close the session.
            acknowledgment = (serverFin.sequence + 1L) and 0xffffffffL
        )
        manager.handle(staleAck, staleAck.size) { null }
        assertEquals(1, manager.activeSessionCount())

        val ack = packet(
            flags = DnsTcpPacket.ACK,
            seq = 1202,
            acknowledgment = (serverFin.sequence + 1L) and 0xffffffffL
        )
        manager.handle(ack, ack.size) { null }
        assertEquals(0, manager.activeSessionCount())
    }


    @Test fun resolverExceptionReturnsServfailAndKeepsTcpSessionAlive() {
        val manager = newManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 2400)
        manager.handle(syn, syn.size) { null }

        val first = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH, seq = 2401, payload = framed)
        val firstReply = manager.handle(first, first.size) {
            throw IllegalStateException("simulated resolver failure")
        }.single()
        val firstSegment = DnsTcpPacket.parse(firstReply, firstReply.size, 53000)!!
        val firstLength = ((firstSegment.payload[0].toInt() and 255) shl 8) or
            (firstSegment.payload[1].toInt() and 255)
        assertEquals(firstLength, firstSegment.payload.size - 2)
        assertEquals(2, firstSegment.payload[5].toInt() and 0x0f) // SERVFAIL
        assertEquals(1, manager.activeSessionCount())

        val next = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 2401L + framed.size, payload = framed)
        val nextReply = manager.handle(next, next.size) { DomainFilter.makeServfail(it) }
        assertTrue(nextReply.isNotEmpty())
        assertEquals(1, manager.activeSessionCount())
    }


    @Test fun returnsDnsTcpResponsesLargerThanFourKilobytesInMtuSafeSegments() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 4100)
        manager.handle(syn, syn.size) { null }

        val largeAnswer = ByteArray(5000) { (it and 0xff).toByte() }
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 4101, payload = framedQuery)
        val replies = manager.handle(request, request.size) { largeAnswer }

        assertTrue(replies.size > 1)
        val reconstructed = java.io.ByteArrayOutputStream()
        var expectedSequence: Long? = null
        replies.forEach { bytes ->
            val segment = DnsTcpPacket.parse(bytes, bytes.size, 53000)!!
            assertTrue(segment.payload.size <= 1200)
            if (expectedSequence != null) assertEquals(expectedSequence, segment.sequence)
            expectedSequence = (segment.sequence + segment.payload.size) and 0xffffffffL
            reconstructed.write(segment.payload)
        }
        val framedAnswer = reconstructed.toByteArray()
        assertEquals(largeAnswer.size + 2, framedAnswer.size)
        assertEquals(largeAnswer.size, ((framedAnswer[0].toInt() and 255) shl 8) or
            (framedAnswer[1].toInt() and 255))
        assertArrayEquals(largeAnswer, framedAnswer.copyOfRange(2, framedAnswer.size))
    }

    @Test fun retransmittedDnsRequestReplaysUnacknowledgedWindowedResponseSegments() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 8600, window = 0)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8601, acknowledgment = serverStart, payload = framedQuery, window = 0)
        val answer = ByteArray(5000) { (it and 0xff).toByte() }
        var calls = 0
        assertTrue(manager.handle(request, request.size) { calls++; answer }.isEmpty())

        val windowUpdate = packet(flags = DnsTcpPacket.ACK, seq = 8601L + framedQuery.size,
            acknowledgment = serverStart, window = 2048)
        val firstTransmission = manager.handle(windowUpdate, windowUpdate.size) { null }
        assertEquals(2048, firstTransmission.sumOf {
            DnsTcpPacket.parse(it, it.size, 53000)!!.payload.size
        })

        // Simulate loss: the client retransmits its original DNS request without
        // acknowledging the first response window. Guardian must replay the
        // outstanding response packets, not silently ACK and lose the DNS answer.
        val retransmission = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8601, acknowledgment = serverStart, payload = framedQuery, window = 2048)
        val replayed = manager.handle(retransmission, retransmission.size) { calls++; null }
        assertEquals(1, calls)
        assertEquals(firstTransmission.size, replayed.size)
        replayed.zip(firstTransmission).forEach { (actual, expected) -> assertArrayEquals(expected, actual) }
    }

    @Test fun outOfOrderFutureSequenceCannotAcknowledgeDnsResponseData() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 8700)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(),
            40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8701, acknowledgment = serverStart, payload = framedQuery)
        val answer = ByteArray(5000) { (it and 0xff).toByte() }
        val firstTransmission = manager.handle(request, request.size) { answer }
        assertTrue(firstTransmission.isNotEmpty())
        val firstSegments = firstTransmission.map { DnsTcpPacket.parse(it, it.size, 53000)!! }
        val firstAcknowledgement = (serverStart + firstSegments.first().payload.size) and 0xffffffffL
        val clientNext = 8701L + framedQuery.size

        // A future client sequence is invalid even when its server ACK is
        // otherwise plausible. It must not discard the first outstanding packet.
        val futureAck = packet(flags = DnsTcpPacket.ACK,
            seq = clientNext + 10L, acknowledgment = firstAcknowledgement, window = 0)
        val rejected = manager.handle(futureAck, futureAck.size) { null }.single()
        assertTrue(DnsTcpPacket.parse(rejected, rejected.size, 53000)!!.payload.isEmpty())

        // A stale sequence that is not the exact last request retransmission
        // must not advance the server ACK either.
        val staleAck = packet(flags = DnsTcpPacket.ACK,
            seq = clientNext - 1L, acknowledgment = firstAcknowledgement, window = 0)
        val staleRejected = manager.handle(staleAck, staleAck.size) { null }.single()
        assertTrue(DnsTcpPacket.parse(staleRejected, staleRejected.size, 53000)!!.payload.isEmpty())

        val retransmission = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8701, acknowledgment = serverStart, payload = framedQuery)
        val replayed = manager.handle(retransmission, retransmission.size) { null }
        assertEquals(firstTransmission.size, replayed.size)
        replayed.zip(firstTransmission).forEach { (actual, expected) ->
            assertArrayEquals(expected, actual)
        }
    }

    @Test fun receiveWindowUpdatesDoNotCountAsDuplicateAcks() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 9200)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 9201, acknowledgment = serverStart, payload = framedQuery)
        val sent = manager.handle(request, request.size) { ByteArray(5000) }
        assertTrue(sent.size > 1)
        val clientNext = (9201L + framedQuery.size) and 0xffffffffL

        // First change the advertised window, then keep it stable. Only the
        // three subsequent identical ACK/window pairs count toward fast retransmit.
        val windowChange = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = serverStart, window = 32768)
        assertTrue(manager.handle(windowChange, windowChange.size) { null }.isEmpty())
        repeat(2) {
            val duplicate = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
                acknowledgment = serverStart, window = 32768)
            assertTrue(manager.handle(duplicate, duplicate.size) { null }.isEmpty())
        }
        val thirdStableDuplicate = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = serverStart, window = 32768)
        assertArrayEquals(sent.first(), manager.handle(
            thirdStableDuplicate, thirdStableDuplicate.size
        ) { null }.single())
    }

    @Test fun retransmitsDnsTcpSegmentOnExponentialTimeoutAndDropsAfterRetryLimit() {
        var now = 0L
        val manager = DnsTcpSessionManager(clock = { now }, random = deterministicRandom())
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 9300)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 9301, acknowledgment = serverStart, payload = framedQuery)
        val sent = manager.handle(request, request.size) { ByteArray(5000) }
        assertTrue(sent.isNotEmpty())

        now = 999L
        assertTrue(manager.retransmitDue().isEmpty())
        now = 1_000L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
        now = 2_999L
        assertTrue(manager.retransmitDue().isEmpty())
        now = 3_000L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
        now = 7_000L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
        now = 15_000L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
        now = 23_000L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
        assertEquals(1, manager.activeSessionCount())
        now = 31_000L
        assertTrue(manager.retransmitDue().isEmpty())
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun cumulativeAckCancelsTimerForAcknowledgedDnsTcpData() {
        var now = 0L
        val manager = DnsTcpSessionManager(clock = { now }, random = deterministicRandom())
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 9400)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 9401, acknowledgment = serverStart, payload = framedQuery)
        val sent = manager.handle(request, request.size) { ByteArray(5000) }
        val firstSize = DnsTcpPacket.parse(sent.first(), sent.first().size, 53000)!!.payload.size
        now = 1_000L
        assertArrayEquals(sent.first(), manager.retransmitDue().single())
        val clientNext = (9401L + framedQuery.size) and 0xffffffffL
        val ack = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = (serverStart + firstSize) and 0xffffffffL)
        assertTrue(manager.handle(ack, ack.size) { null }.isEmpty())
        now = 1_999L
        assertTrue(manager.retransmitDue().isEmpty())
        now = 2_000L
        assertArrayEquals(sent[1], manager.retransmitDue().single())
    }

    @Test fun clientAndServerSequenceNumbersWrapAcrossZeroWithoutLosingDnsAckState() {
        val clientInitial = 0xfffffffCL
        val clientNext = (clientInitial + 1L + dnsQuery().size + 2L) and 0xffffffffL
        val manager = DnsTcpSessionManager(random = deterministicRandom(-2))
        val syn = packet(flags = DnsTcpPacket.SYN, seq = clientInitial)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        assertEquals(0xfffffffEL, synAck.sequence)
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        assertEquals(0xffffffffL, serverStart)

        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val request = packet(
            flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = (clientInitial + 1L) and 0xffffffffL,
            acknowledgment = serverStart,
            payload = framedQuery
        )
        val sent = manager.handle(request, request.size) { ByteArray(3000) }
        assertEquals(3, sent.size)
        val first = DnsTcpPacket.parse(sent[0], sent[0].size, 53000)!!
        val second = DnsTcpPacket.parse(sent[1], sent[1].size, 53000)!!
        assertEquals(0xffffffffL, first.sequence)
        assertEquals(1199L, second.sequence)
        assertTrue(clientNext < clientInitial)

        val wrappedAck = (serverStart + first.payload.size) and 0xffffffffL
        assertEquals(1199L, wrappedAck)
        val partialAck = packet(
            flags = DnsTcpPacket.ACK,
            seq = clientNext,
            acknowledgment = wrappedAck
        )
        assertTrue(manager.handle(partialAck, partialAck.size) { null }.isEmpty())

        repeat(3) { index ->
            val duplicateAck = packet(
                flags = DnsTcpPacket.ACK,
                seq = clientNext,
                acknowledgment = wrappedAck
            )
            val result = manager.handle(duplicateAck, duplicateAck.size) { null }
            if (index < 2) assertTrue(result.isEmpty())
            else assertArrayEquals(sent[1], result.single())
        }
    }

    @Test fun retransmitsOldestOutstandingDnsAnswerAfterThreeDuplicateAcks() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 9100)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 9101, acknowledgment = serverStart, payload = framedQuery)
        val answer = ByteArray(5000) { (it and 0xff).toByte() }
        val sent = manager.handle(request, request.size) { answer }
        assertTrue(sent.size > 1)

        val clientNext = (9101L + framedQuery.size) and 0xffffffffL
        repeat(2) {
            val duplicateAck = packet(flags = DnsTcpPacket.ACK,
                seq = clientNext, acknowledgment = serverStart)
            assertTrue(manager.handle(duplicateAck, duplicateAck.size) { null }.isEmpty())
        }
        val thirdDuplicateAck = packet(flags = DnsTcpPacket.ACK,
            seq = clientNext, acknowledgment = serverStart)
        val retransmitted = manager.handle(thirdDuplicateAck, thirdDuplicateAck.size) { null }
        assertEquals(1, retransmitted.size)
        assertArrayEquals(sent.first(), retransmitted.single())
        val fourthDuplicateAck = packet(flags = DnsTcpPacket.ACK,
            seq = clientNext, acknowledgment = serverStart)
        assertTrue(manager.handle(fourthDuplicateAck, fourthDuplicateAck.size) { null }.isEmpty())

        val firstPayloadSize = DnsTcpPacket.parse(sent.first(), sent.first().size, 53000)!!.payload.size
        val partialAckNumber = (serverStart + firstPayloadSize) and 0xffffffffL
        val partialAck = packet(flags = DnsTcpPacket.ACK,
            seq = clientNext, acknowledgment = partialAckNumber)
        assertTrue(manager.handle(partialAck, partialAck.size) { null }.isEmpty())
        repeat(3) {
            val duplicateAck = packet(flags = DnsTcpPacket.ACK,
                seq = clientNext, acknowledgment = partialAckNumber)
            val result = manager.handle(duplicateAck, duplicateAck.size) { null }
            if (it < 2) assertTrue(result.isEmpty())
            else assertArrayEquals(sent[1], result.single())
        }
    }

    @Test fun staleResetSegmentCannotDestroyActiveDnsTcpSession() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 9500)
        manager.handle(syn, syn.size) { null }
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 9501, acknowledgment = 1L, payload = framedQuery)
        val sent = manager.handle(request, request.size) { ByteArray(2000) }
        assertTrue(sent.isNotEmpty())
        val clientNext = (9501L + framedQuery.size) and 0xffffffffL
        val staleReset = packet(flags = DnsTcpPacket.RST, seq = clientNext - 1L)
        assertTrue(manager.handle(staleReset, staleReset.size) { null }.isEmpty())
        assertEquals(1, manager.activeSessionCount())
        val exactRetransmission = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 9501, acknowledgment = 1L, payload = framedQuery)
        assertEquals(sent.size, manager.handle(exactRetransmission, exactRetransmission.size) { null }.size)
    }

    @Test fun resetAtCurrentReceiveSequenceClosesDnsTcpSession() {
        val manager = newManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 9600)
        manager.handle(syn, syn.size) { null }
        val currentReset = packet(flags = DnsTcpPacket.RST, seq = 9601)
        assertTrue(manager.handle(currentReset, currentReset.size) { null }.isEmpty())
        assertEquals(0, manager.activeSessionCount())
    }

    @Test fun staleTcpSegmentsCannotOverwriteNewerPeerReceiveWindow() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 8500, window = 0)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val clientNext = 8501L + framedQuery.size
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8501, acknowledgment = serverStart, payload = framedQuery, window = 0)
        val answer = ByteArray(5000) { (it and 0xff).toByte() }
        assertTrue(manager.handle(request, request.size) { answer }.isEmpty())

        val firstWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = serverStart, window = 2048)
        val first = manager.handle(firstWindow, firstWindow.size) { null }
        assertEquals(2048, first.sumOf { DnsTcpPacket.parse(it, it.size, 53000)!!.payload.size })

        // This packet is older in both sequence and ACK space. Its zero window
        // must not replace the more recent 2048-byte advertisement.
        val staleWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext - 1L,
            acknowledgment = serverStart, window = 0)
        manager.handle(staleWindow, staleWindow.size) { null }

        val secondWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = (serverStart + 2048L) and 0xffffffffL, window = 2048)
        val second = manager.handle(secondWindow, secondWindow.size) { null }
        assertEquals(2048, second.sumOf { DnsTcpPacket.parse(it, it.size, 53000)!!.payload.size })

        // Same SEG.SEQ with an older SEG.ACK is also not allowed to shrink
        // the window after the newer ACK/window pair has been accepted.
        val staleAckWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = serverStart, window = 0)
        manager.handle(staleAckWindow, staleAckWindow.size) { null }

        val finalWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = (serverStart + 4096L) and 0xffffffffL, window = 2048)
        val last = manager.handle(finalWindow, finalWindow.size) { null }
        assertEquals(906, last.sumOf { DnsTcpPacket.parse(it, it.size, 53000)!!.payload.size })
    }

    @Test fun largeDnsResponseWaitsForAndFollowsPeerReceiveWindowUpdates() {
        val manager = newManager()
        val query = dnsQuery()
        val framedQuery = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 8000, window = 0)
        val synAck = DnsTcpPacket.parse(manager.handle(syn, syn.size) { null }.single(), 40, 53000)!!
        val serverStart = (synAck.sequence + 1L) and 0xffffffffL
        val clientNext = 8001L + framedQuery.size
        val request = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH,
            seq = 8001, acknowledgment = serverStart, payload = framedQuery, window = 0)
        val largeAnswer = ByteArray(5000) { (it and 0xff).toByte() }
        assertTrue(manager.handle(request, request.size) { largeAnswer }.isEmpty())

        val firstWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = serverStart, window = 2048)
        val first = manager.handle(firstWindow, firstWindow.size) { null }
        assertEquals(2, first.size)
        val firstSegments = first.map { DnsTcpPacket.parse(it, it.size, 53000)!! }
        assertEquals(2048, firstSegments.sumOf { it.payload.size })
        assertEquals(serverStart, firstSegments.first().sequence)

        val secondWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = (serverStart + 2048L) and 0xffffffffL, window = 2048)
        val second = manager.handle(secondWindow, secondWindow.size) { null }
        assertEquals(2, second.size)
        assertEquals(2048, second.sumOf { DnsTcpPacket.parse(it, it.size, 53000)!!.payload.size })

        val finalWindow = packet(flags = DnsTcpPacket.ACK, seq = clientNext,
            acknowledgment = (serverStart + 4096L) and 0xffffffffL, window = 2048)
        val last = manager.handle(finalWindow, finalWindow.size) { null }
        assertEquals(1, last.size)
        assertEquals(906, DnsTcpPacket.parse(last.single(), last.single().size, 53000)!!.payload.size)
    }

    private fun newManager(): DnsTcpSessionManager =
        DnsTcpSessionManager(random = deterministicRandom())

    private fun deterministicRandom(value: Int = 0): SecureRandom = object : SecureRandom() {
        override fun nextInt(): Int = value
    }

    private fun dnsQuery(): ByteArray = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
        0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x01, 0x00, 0x01
    )

    private fun packet(flags: Int, seq: Long, payload: ByteArray = byteArrayOf(),
                       acknowledgment: Long = 0L, window: Int = 8192): ByteArray {
        val tcpLength = 20 + payload.size
        val p = ByteArray(20 + tcpLength)
        p[0] = 0x45; p[2] = (p.size ushr 8).toByte(); p[3] = p.size.toByte(); p[9] = 6
        p[12] = 10; p[15] = 1; p[16] = 10; p[19] = 2
        p[20] = 0xcf.toByte(); p[21] = 0x08; p[22] = 0; p[23] = 53
        p[24] = (seq ushr 24).toByte(); p[25] = (seq ushr 16).toByte()
        p[26] = (seq ushr 8).toByte(); p[27] = seq.toByte()
        p[28] = (acknowledgment ushr 24).toByte(); p[29] = (acknowledgment ushr 16).toByte()
        p[30] = (acknowledgment ushr 8).toByte(); p[31] = acknowledgment.toByte()
        p[32] = 0x50; p[33] = flags.toByte()
        p[34] = (window ushr 8).toByte(); p[35] = window.toByte()
        System.arraycopy(payload, 0, p, 40, payload.size)
        sealTcp4(p); sealIp4(p)
        return p
    }
    private fun sealTcp4(p: ByteArray) {
        p[36] = 0; p[37] = 0
        var sum = 0L
        fun add(start: Int, len: Int) {
            var i = start
            while (i + 1 < start + len) {
                sum += (((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)).toLong(); i += 2
            }
            if (i < start + len) sum += ((p[i].toInt() and 255) shl 8).toLong()
        }
        add(12, 8); sum += 6; sum += p.size - 20; add(20, p.size - 20)
        while ((sum ushr 16) != 0L) sum = (sum and 65535) + (sum ushr 16)
        val v = sum.inv().toInt() and 65535
        p[36] = (v ushr 8).toByte(); p[37] = v.toByte()
    }
    private fun sealIp4(p: ByteArray) {
        p[10] = 0; p[11] = 0
        var sum = 0L; var i = 0
        while (i < 20) { sum += (((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)).toLong(); i += 2 }
        while ((sum ushr 16) != 0L) sum = (sum and 65535) + (sum ushr 16)
        val v = sum.inv().toInt() and 65535
        p[10] = (v ushr 8).toByte(); p[11] = v.toByte()
    }
}
