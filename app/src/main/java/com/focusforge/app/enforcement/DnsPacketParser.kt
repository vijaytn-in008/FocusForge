package com.focusforge.app.enforcement

import java.nio.ByteBuffer

/**
 * Parsed DNS query representation.
 */
data class DnsQuery(
    val domain: String,
    val qType: Int,
    val qClass: Int,
    val transactionId: Int,
    val dnsPayloadOffset: Int,
    val dnsPayloadLength: Int,
    val isIpv6: Boolean,
    val sourcePort: Int,
    val destPort: Int
)

/**
 * Parses and builds raw IPv4/IPv6 UDP DNS packets.
 * Strictly avoids heap fragmentation by working with direct ByteBuffers or reusable arrays.
 */
object DnsPacketParser {

    /**
     * Parses a raw IP packet from TUN interface. Returns DnsQuery if packet is a valid DNS query on port 53.
     */
    fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null // Minimum IPv4 (20) + UDP (8)

        val version = (packet[0].toInt() shr 4) and 0x0F
        val isIpv6 = (version == 6)

        val (ipHeaderLen, protocol) = if (isIpv6) {
            if (length < 48) return null // Minimum IPv6 (40) + UDP (8)
            Pair(40, packet[6].toInt() and 0xFF) // Next header
        } else {
            val ihl = (packet[0].toInt() and 0x0F) * 4
            Pair(ihl, packet[9].toInt() and 0xFF)
        }

        // Protocol 17 = UDP
        if (protocol != 17) return null

        val udpOffset = ipHeaderLen
        if (length < udpOffset + 8) return null

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)

        // Only process queries directed to DNS port 53
        if (dstPort != 53) return null

        val dnsOffset = udpOffset + 8
        if (length < dnsOffset + 12) return null // DNS header is 12 bytes

        val txId = ((packet[dnsOffset].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 1].toInt() and 0xFF)
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return null // Only process incoming queries

        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 5].toInt() and 0xFF)
        if (qdCount < 1) return null

        // Parse Question section (QNAME)
        var cursor = dnsOffset + 12
        val domainLabels = mutableListOf<String>()

        while (cursor < length) {
            val labelLen = packet[cursor].toInt() and 0xFF
            cursor++

            if (labelLen == 0) break // End of domain
            if ((labelLen and 0xC0) == 0xC0) {
                // Pointer / compression in query header (uncommon in standard queries)
                cursor++
                break
            }

            if (cursor + labelLen > length) return null // Malformed packet
            val label = String(packet, cursor, labelLen, Charsets.US_ASCII)
            domainLabels.add(label)
            cursor += labelLen
        }

        if (cursor + 4 > length) return null
        val qType = ((packet[cursor].toInt() and 0xFF) shl 8) or (packet[cursor + 1].toInt() and 0xFF)
        val qClass = ((packet[cursor + 2].toInt() and 0xFF) shl 8) or (packet[cursor + 3].toInt() and 0xFF)

        val domain = domainLabels.joinToString(".")

        return DnsQuery(
            domain = domain,
            qType = qType,
            qClass = qClass,
            transactionId = txId,
            dnsPayloadOffset = dnsOffset,
            dnsPayloadLength = length - dnsOffset,
            isIpv6 = isIpv6,
            sourcePort = srcPort,
            destPort = dstPort
        )
    }

    /**
     * Builds an NXDOMAIN response packet matching the query packet.
     * Swaps IP src/dst, UDP src/dst, sets QR=1, RCODE=3 (NXDOMAIN), AA=1, RA=1.
     */
    fun buildNxdomainResponse(queryPacket: ByteArray, queryLength: Int, query: DnsQuery): ByteArray {
        val response = queryPacket.copyOf(queryLength)
        val isIpv6 = query.isIpv6
        val ipHeaderLen = if (isIpv6) 40 else ((queryPacket[0].toInt() and 0x0F) * 4)
        val udpOffset = ipHeaderLen
        val dnsOffset = query.dnsPayloadOffset

        // 1. Swap IP Source & Destination addresses
        if (isIpv6) {
            for (i in 0 until 16) {
                val tmp = response[8 + i]
                response[8 + i] = response[24 + i]
                response[24 + i] = tmp
            }
        } else {
            for (i in 0 until 4) {
                val tmp = response[12 + i]
                response[12 + i] = response[16 + i]
                response[16 + i] = tmp
            }
        }

        // 2. Swap UDP Source & Destination Ports
        response[udpOffset] = ((query.destPort shr 8) and 0xFF).toByte()
        response[udpOffset + 1] = (query.destPort and 0xFF).toByte()
        response[udpOffset + 2] = ((query.sourcePort shr 8) and 0xFF).toByte()
        response[udpOffset + 3] = (query.sourcePort and 0xFF).toByte()

        // 3. Set DNS Header Flags: QR=1 (response), AA=1, RD=1, RA=1, RCODE=3 (NXDOMAIN)
        // 0x8183 = 1000 0001 1000 0011
        response[dnsOffset + 2] = 0x81.toByte()
        response[dnsOffset + 3] = 0x83.toByte()

        // Answer Count = 0, Authority Count = 0, Additional Count = 0
        response[dnsOffset + 6] = 0
        response[dnsOffset + 7] = 0
        response[dnsOffset + 8] = 0
        response[dnsOffset + 9] = 0
        response[dnsOffset + 10] = 0
        response[dnsOffset + 11] = 0

        // 4. Recalculate IP and UDP Checksums
        if (!isIpv6) {
            computeIpv4Checksum(response, ipHeaderLen)
        }
        // Zero UDP checksum (valid in IPv4; for simplicity and performance)
        response[udpOffset + 6] = 0
        response[udpOffset + 7] = 0

        return response
    }

    private fun computeIpv4Checksum(packet: ByteArray, headerLen: Int) {
        // Zero existing checksum
        packet[10] = 0
        packet[11] = 0

        var sum = 0
        var i = 0
        while (i < headerLen) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val checksum = (sum.inv()) and 0xFFFF
        packet[10] = ((checksum shr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }
}
