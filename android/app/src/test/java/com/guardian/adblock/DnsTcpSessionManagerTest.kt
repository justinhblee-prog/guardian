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
