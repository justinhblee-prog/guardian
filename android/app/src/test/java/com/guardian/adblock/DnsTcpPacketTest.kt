package com.guardian.adblock

import org.junit.Assert.*
import org.junit.Test

class DnsTcpPacketTest {
    @Test fun parsesAndBuildsIpv4TcpDnsPackets() {
        val request = ipv4Request()
        val parsed = DnsTcpPacket.parse(request, request.size)
        assertNotNull(parsed)
        assertEquals(53000, parsed!!.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertEquals(0x12345678L, parsed.sequence)
        assertEquals(DnsTcpPacket.SYN, parsed.flags)

        val response = DnsTcpPacket.response(parsed, 0x87654321L, 0x12345679L,
            DnsTcpPacket.SYN or DnsTcpPacket.ACK)
        val reply = DnsTcpPacket.parse(response, response.size, expectedDestinationPort = 53000)
        assertNotNull(reply)
        assertEquals(53, reply!!.sourcePort)
        assertEquals(53000, reply.destinationPort)
        assertEquals(0x87654321L, reply.sequence)
        assertEquals(0x12345679L, reply.acknowledgement)
        assertEquals(DnsTcpPacket.SYN or DnsTcpPacket.ACK, reply.flags)
    }

    @Test fun rejectsCorruptTcpChecksum() {
        val packet = ipv4Request()
        packet[packet.size - 1] = 1
        assertNull(DnsTcpPacket.parse(packet, packet.size))
    }

    @Test fun rejectsNonDnsDestinationAndFragments() {
        val packet = ipv4Request()
        packet[23] = 54
        sealTcp4(packet)
        assertNull(DnsTcpPacket.parse(packet, packet.size))
        val fragmented = ipv4Request()
        fragmented[6] = 0x20
        sealIp4(fragmented)
        assertNull(DnsTcpPacket.parse(fragmented, fragmented.size))
    }

    private fun ipv4Request(): ByteArray {
        val p = ByteArray(40)
        p[0] = 0x45; p[2] = 0; p[3] = 40; p[9] = 6
        p[12] = 10; p[15] = 1; p[16] = 10; p[19] = 2
        p[20] = 0xcf.toByte(); p[21] = 0x08; p[22] = 0; p[23] = 53
        p[24] = 0x12; p[25] = 0x34; p[26] = 0x56; p[27] = 0x78
        p[32] = 0x50; p[33] = DnsTcpPacket.SYN.toByte()
        p[34] = 0x20; p[35] = 0
        sealTcp4(p); sealIp4(p)
        return p
    }

    private fun sealTcp4(p: ByteArray) {
        p[36] = 0; p[37] = 0
        val sum = checksumSum(p, 12, 8, 20, p.size - 20) 
        val value = sum.inv() and 0xffff
        p[36] = (value ushr 8).toByte(); p[37] = value.toByte()
    }
    private fun checksumSum(p: ByteArray, addr: Int, addrLen: Int, data: Int, dataLen: Int): Int {
        var sum = 0L
        fun add(start: Int, len: Int) {
            var i=start
            while (i+1 < start+len) { sum += (((p[i].toInt() and 255) shl 8) or (p[i+1].toInt() and 255)).toLong(); i+=2 }
            if (i < start+len) sum += ((p[i].toInt() and 255) shl 8).toLong()
        }
        add(addr,addrLen); sum += 6; sum += dataLen
        add(data,dataLen)
        while ((sum ushr 16)!=0L) sum=(sum and 65535)+(sum ushr 16)
        return sum.toInt() and 65535
    }
    private fun sealIp4(p: ByteArray) {
        p[10]=0; p[11]=0
        var sum=0L; var i=0
        while(i<20){ sum += (((p[i].toInt() and 255) shl 8) or (p[i+1].toInt() and 255)).toLong(); i+=2 }
        while((sum ushr 16)!=0L) sum=(sum and 65535)+(sum ushr 16)
        val v=sum.inv().toInt() and 65535
        p[10]=(v ushr 8).toByte(); p[11]=v.toByte()
    }
}
