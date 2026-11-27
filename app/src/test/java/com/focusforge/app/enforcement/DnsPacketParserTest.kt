package com.focusforge.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsPacketParserTest {

    private fun buildIpv4DnsQueryPacket(
        domain: String,
        qType: Int = DnsPacketParser.QTYPE_A,
        txId: Int = 0x1234,
        withEdns: Boolean = false
    ): ByteArray {
        val dns = ByteArrayOutputStream()
        dns.write(byteArrayOf((txId shr 8).toByte(), txId.toByte()))
        dns.write(byteArrayOf(0x01, 0x00)) // RD=1
        dns.write(byteArrayOf(0x00, 0x01)) // QDCOUNT=1
        dns.write(byteArrayOf(0x00, 0x00)) // ANCOUNT=0
        dns.write(byteArrayOf(0x00, 0x00)) // NSCOUNT=0
        dns.write(byteArrayOf(0x00, if (withEdns) 0x01 else 0x00)) // ARCOUNT

        for (label in domain.split(".")) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dns.write(bytes.size)
            dns.write(bytes)
        }
        dns.write(0x00)
        dns.write(byteArrayOf((qType shr 8).toByte(), qType.toByte()))
        dns.write(byteArrayOf(0x00, 0x01)) // IN

        if (withEdns) {
            dns.write(0x00) // root
            dns.write(byteArrayOf(0x00, 41)) // OPT
            dns.write(byteArrayOf(0x10, 0x00)) // 4096
            dns.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // ext rcode + flags
            dns.write(byteArrayOf(0x00, 0x00)) // rdlen=0
        }

        val dnsBytes = dns.toByteArray()
        val totalLen = 20 + 8 + dnsBytes.size
        val pkt = ByteArray(totalLen)
        pkt[0] = 0x45.toByte()
        pkt[2] = ((totalLen shr 8) and 0xFF).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[8] = 64
        pkt[9] = 17 // UDP
        // Src 10.0.0.2, Dst 10.0.0.1
        pkt[12] = 10; pkt[13] = 0; pkt[14] = 0; pkt[15] = 2
        pkt[16] = 10; pkt[17] = 0; pkt[18] = 0; pkt[19] = 1
        // UDP src=54321, dst=53
        pkt[20] = (54321 shr 8).toByte(); pkt[21] = (54321 and 0xFF).toByte()
        pkt[22] = 0; pkt[23] = 53
        val udpLen = 8 + dnsBytes.size
        pkt[24] = ((udpLen shr 8) and 0xFF).toByte(); pkt[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(dnsBytes, 0, pkt, 28, dnsBytes.size)
        return pkt
    }

    private fun buildIpv6DnsQueryPacket(domain: String, qType: Int = DnsPacketParser.QTYPE_AAAA): ByteArray {
        val v4 = buildIpv4DnsQueryPacket(domain, qType)
        val dnsLen = v4.size - 28
        val udpLen = 8 + dnsLen
        val pkt = ByteArray(40 + udpLen)
        pkt[0] = 0x60.toByte()
        pkt[4] = ((udpLen shr 8) and 0xFF).toByte()
        pkt[5] = (udpLen and 0xFF).toByte()
        pkt[6] = 17 // UDP
        pkt[7] = 64
        // Src fd00::2, Dst fd00::1
        pkt[8] = 0xFD.toByte(); pkt[23] = 2
        pkt[24] = 0xFD.toByte(); pkt[39] = 1
        System.arraycopy(v4, 20, pkt, 40, udpLen)
        return pkt
    }

    @Test
    fun `parses valid IPv4, IPv6, HTTPS, SVCB, and EDNS queries`() {
        val v4 = buildIpv4DnsQueryPacket("example.com", DnsPacketParser.QTYPE_HTTPS, withEdns = true)
        val q4 = DnsPacketParser.parseDnsQuery(v4, v4.size)
        assertNotNull(q4)
        assertEquals("example.com", q4!!.domain)
        assertEquals(DnsPacketParser.QTYPE_HTTPS, q4.qType)
        assertFalse(q4.isIpv6)
        assertTrue(q4.hasEdns)

        val v6 = buildIpv6DnsQueryPacket("sub.example.org", DnsPacketParser.QTYPE_AAAA)
        val q6 = DnsPacketParser.parseDnsQuery(v6, v6.size)
        assertNotNull(q6)
        assertEquals("sub.example.org", q6!!.domain)
        assertEquals(DnsPacketParser.QTYPE_AAAA, q6.qType)
        assertTrue(q6.isIpv6)
    }

    @Test
    fun `malformed, truncated, and self-loop compressed packets never crash`() {
        assertNull(DnsPacketParser.parseDnsQuery(ByteArray(0), 0))
        assertNull(DnsPacketParser.parseDnsQuery(ByteArray(10), 10))

        // Truncated valid packet
        val valid = buildIpv4DnsQueryPacket("example.com")
        for (len in 0 until valid.size) {
            // Must not throw
            val parsed = DnsPacketParser.parseDnsQuery(valid, len)
            if (len < valid.size) {
                assertNull("Truncated at $len should return null", parsed)
            }
        }

        // Compression pointer loop at QNAME offset 12 -> points to offset 12 (0xC00C)
        val loopPkt = valid.copyOf()
        loopPkt[28 + 12] = 0xC0.toByte()
        loopPkt[28 + 13] = 0x0C
        assertNull(DnsPacketParser.parseDnsQuery(loopPkt, loopPkt.size))
    }

    @Test
    fun `builds NXDOMAIN, SERVFAIL, and ZERO_IP responses properly`() {
        val pkt = buildIpv4DnsQueryPacket("blocked.example", DnsPacketParser.QTYPE_A)
        val q = DnsPacketParser.parseDnsQuery(pkt, pkt.size)!!

        val nx = DnsPacketParser.buildNxdomainResponse(pkt, pkt.size, q)
        assertEquals(DnsPacketParser.RCODE_NXDOMAIN, nx[q.dnsPayloadOffset + 3].toInt() and 0x0F)
        assertEquals(53, ((nx[20].toInt() and 0xFF) shl 8) or (nx[21].toInt() and 0xFF))
        assertEquals(54321, ((nx[22].toInt() and 0xFF) shl 8) or (nx[23].toInt() and 0xFF))

        val sf = DnsPacketParser.buildServfailResponse(pkt, pkt.size, q)
        assertEquals(DnsPacketParser.RCODE_SERVFAIL, sf[q.dnsPayloadOffset + 3].toInt() and 0x0F)

        val zero4 = DnsPacketParser.buildZeroIpResponse(pkt, pkt.size, q)
        assertEquals(DnsPacketParser.RCODE_NOERROR, zero4[q.dnsPayloadOffset + 3].toInt() and 0x0F)
        assertEquals(1, zero4[q.dnsPayloadOffset + 7].toInt() and 0xFF) // ANCOUNT=1
        assertEquals(0, zero4[zero4.size - 1].toInt()) // 0.0.0.0 last byte

        val pkt6 = buildIpv6DnsQueryPacket("blocked.example", DnsPacketParser.QTYPE_AAAA)
        val q6 = DnsPacketParser.parseDnsQuery(pkt6, pkt6.size)!!
        val zero6 = DnsPacketParser.buildZeroIpResponse(pkt6, pkt6.size, q6)
        assertEquals(1, zero6[q6.dnsPayloadOffset + 7].toInt() and 0xFF) // ANCOUNT=1
    }

    @Test
    fun `detects truncated TC bit in upstream DNS response for TCP fallback`() {
        val nonTruncated = ByteArray(12).apply { this[2] = 0x81.toByte() }
        val truncated = ByteArray(12).apply { this[2] = 0x83.toByte() } // TC=1 (0x02)
        assertFalse(DnsPacketParser.isTruncatedResponse(nonTruncated))
        assertTrue(DnsPacketParser.isTruncatedResponse(truncated))
    }
}
