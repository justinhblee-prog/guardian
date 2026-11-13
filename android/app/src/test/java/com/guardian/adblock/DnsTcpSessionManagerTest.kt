package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class DnsTcpSessionManagerTest {
    @Test fun completesHandshakeAndAcknowledgesClientData() {
        var now = 1000L
        val manager = DnsTcpSessionManager({ now }, SecureRandom())
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
        val manager = DnsTcpSessionManager()
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

    @Test fun rejectsMalformedDnsLengthAndResetsConnection() {
        val manager = DnsTcpSessionManager()
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
        val manager = DnsTcpSessionManager()
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
        val manager = DnsTcpSessionManager()
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





    @Test fun handlesDnsOverTcpSessionWithIpv6Packets() {
        val manager = DnsTcpSessionManager()
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
        val manager = DnsTcpSessionManager()
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

    @Test fun segmentsLargeDnsAnswersWithinConservativeTcpMss() {
        val manager = DnsTcpSessionManager()
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
            assertTrue(segment.payload.size <= 536)
            if (expectedSequence != null) assertEquals(expectedSequence, segment.sequence)
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
        val manager = DnsTcpSessionManager()
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 5000)
        val firstBytes = manager.handle(syn, syn.size) { null }.single()
        val first = DnsTcpPacket.parse(firstBytes, firstBytes.size, 53000)!!
        val secondBytes = manager.handle(syn, syn.size) { null }.single()
        val second = DnsTcpPacket.parse(secondBytes, secondBytes.size, 53000)!!
        assertEquals(first.sequence, second.sequence)
        assertEquals(first.acknowledgement, second.acknowledgement)
        assertEquals(1, manager.activeSessionCount())
    }

    @Test fun retransmittedDnsRequestReplaysTheCachedAnswerWithoutResolvingAgain() {
        val manager = DnsTcpSessionManager()
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

    @Test fun processesDnsPayloadAndFinInTheSameTcpSegment() {
        val manager = DnsTcpSessionManager()
        val query = dnsQuery()
        val framed = byteArrayOf((query.size ushr 8).toByte(), query.size.toByte()) + query
        val syn = packet(flags = DnsTcpPacket.SYN, seq = 900)
        manager.handle(syn, syn.size) { null }
        val segment = packet(flags = DnsTcpPacket.ACK or DnsTcpPacket.PSH or DnsTcpPacket.FIN,
            seq = 901, payload = framed)
        val replies = manager.handle(segment, segment.size) { DomainFilter.makeServfail(it) }
        assertEquals(2, replies.size)
        val dataReply = DnsTcpPacket.parse(replies.first(), replies.first().size, 53000)!!
        assertTrue(dataReply.payload.isNotEmpty())
        val finReply = DnsTcpPacket.parse(replies.last(), replies.last().size, 53000)!!
        assertTrue(finReply.fin)
        assertEquals(902L + framed.size, finReply.acknowledgement)
        assertEquals(0, manager.activeSessionCount())
    }

    private fun dnsQuery(): ByteArray = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65,
        0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x01, 0x00, 0x01
    )

    private fun packet(flags: Int, seq: Long, payload: ByteArray = byteArrayOf()): ByteArray {
        val tcpLength = 20 + payload.size
        val p = ByteArray(20 + tcpLength)
        p[0] = 0x45; p[2] = (p.size ushr 8).toByte(); p[3] = p.size.toByte(); p[9] = 6
        p[12] = 10; p[15] = 1; p[16] = 10; p[19] = 2
        p[20] = 0xcf.toByte(); p[21] = 0x08; p[22] = 0; p[23] = 53
        p[24] = (seq ushr 24).toByte(); p[25] = (seq ushr 16).toByte()
        p[26] = (seq ushr 8).toByte(); p[27] = seq.toByte()
        p[32] = 0x50; p[33] = flags.toByte()
        p[34] = 0x20; p[35] = 0
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
