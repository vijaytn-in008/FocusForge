package com.focusforge.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsPacketParserTest {

    @Test
    fun testParseDnsQueryAndBuildNxdomain() {
        val bos = ByteArrayOutputStream()

        // 1. Synthetic IPv4 Header (20 bytes)
        bos.write(0x45) // Version 4, IHL 5
        bos.write(0x00) // DSCP/ECN
        bos.write(0x00); bos.write(0x3B) // Total length 59
        bos.write(0x12); bos.write(0x34) // ID
        bos.write(0x00); bos.write(0x00) // Flags/Frag
        bos.write(64)   // TTL
        bos.write(17)   // Protocol 17 = UDP
        bos.write(0x00); bos.write(0x00) // Checksum placeholder
        bos.write(byteArrayOf(10, 0, 0, 2)) // Src: 10.0.0.2
        bos.write(byteArrayOf(10, 0, 0, 1)) // Dst: 10.0.0.1

        // 2. Synthetic UDP Header (8 bytes)
        bos.write(0xC0); bos.write(0x00) // Src port 49152
        bos.write(0x00); bos.write(0x35) // Dst port 53 (DNS)
        bos.write(0x00); bos.write(0x27) // Length 39
        bos.write(0x00); bos.write(0x00) // Checksum

        // 3. DNS Query (pornhub.com)
        bos.write(0xAB); bos.write(0xCD) // TxID 0xABCD
        bos.write(0x01); bos.write(0x00) // Standard query
        bos.write(0x00); bos.write(0x01) // QDCount: 1
        bos.write(0x00); bos.write(0x00) // ANCount: 0
        bos.write(0x00); bos.write(0x00) // NSCount: 0
        bos.write(0x00); bos.write(0x00) // ARCount: 0

        // QNAME: 7pornhub3com0
        bos.write(7); bos.write("pornhub".toByteArray())
        bos.write(3); bos.write("com".toByteArray())
        bos.write(0)

        // QTYPE: A (1), QCLASS: IN (1)
        bos.write(0x00); bos.write(0x01)
        bos.write(0x00); bos.write(0x01)

        val packet = bos.toByteArray()
        val query = DnsPacketParser.parseDnsQuery(packet, packet.size)

        assertNotNull("Query must be parsed successfully", query)
        assertEquals("pornhub.com", query!!.domain)
        assertEquals(0xABCD, query.transactionId)
        assertEquals(49152, query.sourcePort)
        assertEquals(53, query.destPort)

        // Test NXDOMAIN response construction
        val nxdomain = DnsPacketParser.buildNxdomainResponse(packet, packet.size, query)
        assertNotNull(nxdomain)

        // Dst IP in response should be 10.0.0.2
        assertEquals(10.toByte(), nxdomain[16])
        assertEquals(2.toByte(), nxdomain[19])

        // Src port in response should be 53
        val respSrcPort = ((nxdomain[20].toInt() and 0xFF) shl 8) or (nxdomain[21].toInt() and 0xFF)
        val respDstPort = ((nxdomain[22].toInt() and 0xFF) shl 8) or (nxdomain[23].toInt() and 0xFF)
        assertEquals(53, respSrcPort)
        assertEquals(49152, respDstPort)

        // RCODE in response must be 3 (NXDOMAIN) -> flags byte: 0x83
        val flagsHigh = nxdomain[28 + 2].toInt() and 0xFF
        val flagsLow = nxdomain[28 + 3].toInt() and 0xFF
        assertEquals(0x81, flagsHigh) // QR=1, RD=1
        assertEquals(0x83, flagsLow)  // RA=1, RCODE=3 (NXDOMAIN)
    }
}
