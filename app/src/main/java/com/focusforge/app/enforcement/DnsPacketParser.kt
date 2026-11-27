package com.focusforge.app.enforcement

import java.io.ByteArrayOutputStream

/**
 * Parsed IPv4/IPv6 UDP DNS query representation.
 */
data class DnsQuery(
    val domain: String,
    val qType: Int,
    val qClass: Int,
    val transactionId: Int,
    val dnsPayloadOffset: Int,
    val dnsPayloadLength: Int,
    val questionEndDnsOffset: Int,
    val isIpv6: Boolean,
    val sourcePort: Int,
    val destPort: Int,
    val hasEdns: Boolean
)

/**
 * Pure JVM IPv4/IPv6 + UDP + DNS packet parser and response builder.
 * Handles compressed names, EDNS, A/AAAA/HTTPS/SVCB, NXDOMAIN, 0.0.0.0/::, SERVFAIL,
 * and truncated (TC) response detection. Malformed packets never throw or crash.
 */
object DnsPacketParser {

    const val QTYPE_A = 1
    const val QTYPE_AAAA = 28
    const val QTYPE_OPT = 41
    const val QTYPE_SVCB = 64
    const val QTYPE_HTTPS = 65

    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    /**
     * Parses a raw IP packet from TUN interface. Returns null for non-DNS, non-UDP,
     * response packets, or any malformed/truncated packet.
     */
    fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28 || length > packet.size) return null

        val version = (packet[0].toInt() ushr 4) and 0x0F
        val isIpv6 = when (version) {
            4 -> false
            6 -> true
            else -> return null
        }

        val (ipHeaderLen, protocol) = if (isIpv6) {
            if (length < 48) return null
            40 to (packet[6].toInt() and 0xFF)
        } else {
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (ihl < 20) return null
            ihl to (packet[9].toInt() and 0xFF)
        }

        if (protocol != 17) return null // UDP only

        val udpOffset = ipHeaderLen
        if (length < udpOffset + 8) return null

        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val dnsOffset = udpOffset + 8
        val dnsLength = length - dnsOffset
        if (dnsLength < 12) return null

        val txId = ((packet[dnsOffset].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 1].toInt() and 0xFF)
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return null

        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 5].toInt() and 0xFF)
        val arCount = ((packet[dnsOffset + 10].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 11].toInt() and 0xFF)
        if (qdCount < 1) return null

        val parsedName = parseQName(packet, dnsOffset, length, dnsOffset + 12) ?: return null
        val (domain, afterNameOffset) = parsedName

        if (afterNameOffset + 4 > length) return null
        val qType = ((packet[afterNameOffset].toInt() and 0xFF) shl 8) or (packet[afterNameOffset + 1].toInt() and 0xFF)
        val qClass = ((packet[afterNameOffset + 2].toInt() and 0xFF) shl 8) or (packet[afterNameOffset + 3].toInt() and 0xFF)
        val questionEndDnsOffset = (afterNameOffset + 4) - dnsOffset

        return DnsQuery(
            domain = domain,
            qType = qType,
            qClass = qClass,
            transactionId = txId,
            dnsPayloadOffset = dnsOffset,
            dnsPayloadLength = dnsLength,
            questionEndDnsOffset = questionEndDnsOffset,
            isIpv6 = isIpv6,
            sourcePort = srcPort,
            destPort = dstPort,
            hasEdns = arCount > 0
        )
    }

    /**
     * Parses a DNS QNAME supporting RFC 1035 label compression pointers with loop protection.
     */
    private fun parseQName(
        packet: ByteArray,
        dnsOffset: Int,
        packetLength: Int,
        startOffset: Int
    ): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var cursor = startOffset
        var endAfterFirstPointer = -1
        var jumps = 0

        while (cursor < packetLength) {
            val lenByte = packet[cursor].toInt() and 0xFF
            if (lenByte == 0) {
                val endOffset = if (endAfterFirstPointer >= 0) endAfterFirstPointer else cursor + 1
                return labels.joinToString(".") to endOffset
            }

            if ((lenByte and 0xC0) == 0xC0) {
                if (cursor + 1 >= packetLength) return null
                val ptr = ((lenByte and 0x3F) shl 8) or (packet[cursor + 1].toInt() and 0xFF)
                val target = dnsOffset + ptr
                if (target < dnsOffset + 12 || target >= packetLength) return null
                if (endAfterFirstPointer < 0) {
                    endAfterFirstPointer = cursor + 2
                }
                cursor = target
                jumps++
                if (jumps > 16) return null // Prevent compression pointer loops
                continue
            }

            if ((lenByte and 0xC0) != 0) return null // Reserved label type
            cursor++
            if (cursor + lenByte > packetLength) return null
            labels.add(String(packet, cursor, lenByte, Charsets.US_ASCII))
            cursor += lenByte
            if (labels.size > 128) return null
        }
        return null
    }

    /**
     * Checks if an upstream DNS response payload has the TC (Truncated) bit set.
     */
    fun isTruncatedResponse(dnsResponse: ByteArray, length: Int = dnsResponse.size): Boolean {
        if (length < 4 || length > dnsResponse.size) return false
        return (dnsResponse[2].toInt() and 0x02) != 0
    }

    /**
     * Builds an NXDOMAIN (RCODE=3) IP response packet for a blocked query.
     */
    fun buildNxdomainResponse(queryPacket: ByteArray, queryLength: Int, query: DnsQuery): ByteArray {
        val dnsPayload = buildErrorDnsPayload(queryPacket, query, RCODE_NXDOMAIN)
        return buildResponseIpPacket(queryPacket, query, dnsPayload, dnsPayload.size)
    }

    /**
     * Builds a SERVFAIL (RCODE=2) IP response packet when upstream forwarding fails.
     */
    fun buildServfailResponse(queryPacket: ByteArray, queryLength: Int, query: DnsQuery): ByteArray {
        val dnsPayload = buildErrorDnsPayload(queryPacket, query, RCODE_SERVFAIL)
        return buildResponseIpPacket(queryPacket, query, dnsPayload, dnsPayload.size)
    }

    /**
     * Builds a null-route response (0.0.0.0 for A, :: for AAAA, NODATA for other types).
     */
    fun buildZeroIpResponse(queryPacket: ByteArray, queryLength: Int, query: DnsQuery): ByteArray {
        if (query.qType != QTYPE_A && query.qType != QTYPE_AAAA) {
            val dnsPayload = buildErrorDnsPayload(queryPacket, query, RCODE_NOERROR)
            return buildResponseIpPacket(queryPacket, query, dnsPayload, dnsPayload.size)
        }

        val qSectionLen = query.questionEndDnsOffset
        val out = ByteArrayOutputStream(qSectionLen + 32)

        // Copy header + question section
        out.write(queryPacket, query.dnsPayloadOffset, qSectionLen)
        val headerAndQuestion = out.toByteArray()

        // QR=1, AA=1, RD=1, RA=1, RCODE=0
        headerAndQuestion[2] = 0x85.toByte()
        headerAndQuestion[3] = 0x80.toByte()
        // QDCOUNT = 1, ANCOUNT = 1, NSCOUNT = 0, ARCOUNT = 0
        headerAndQuestion[4] = 0; headerAndQuestion[5] = 1
        headerAndQuestion[6] = 0; headerAndQuestion[7] = 1
        headerAndQuestion[8] = 0; headerAndQuestion[9] = 0
        headerAndQuestion[10] = 0; headerAndQuestion[11] = 0

        val rdataLen = if (query.qType == QTYPE_A) 4 else 16
        val answer = ByteArray(12 + rdataLen)
        // Name pointer to offset 12 (0xC00C)
        answer[0] = 0xC0.toByte(); answer[1] = 0x0C
        // Type
        answer[2] = ((query.qType shr 8) and 0xFF).toByte()
        answer[3] = (query.qType and 0xFF).toByte()
        // Class IN (1)
        answer[4] = 0; answer[5] = 1
        // TTL = 60s
        answer[6] = 0; answer[7] = 0; answer[8] = 0; answer[9] = 60
        // RDLENGTH
        answer[10] = 0; answer[11] = rdataLen.toByte()
        // RDATA is already all zeros (0.0.0.0 or ::)

        val combined = ByteArray(headerAndQuestion.size + answer.size)
        System.arraycopy(headerAndQuestion, 0, combined, 0, headerAndQuestion.size)
        System.arraycopy(answer, 0, combined, headerAndQuestion.size, answer.size)
        return buildResponseIpPacket(queryPacket, query, combined, combined.size)
    }

    private fun buildErrorDnsPayload(queryPacket: ByteArray, query: DnsQuery, rcode: Int): ByteArray {
        val len = query.questionEndDnsOffset
        val payload = queryPacket.copyOfRange(query.dnsPayloadOffset, query.dnsPayloadOffset + len)
        // QR=1, AA=1, RD=1 (preserve RD=1), RA=1, RCODE=rcode
        payload[2] = 0x85.toByte()
        payload[3] = (0x80 or (rcode and 0x0F)).toByte()
        // QDCOUNT=1, ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
        payload[4] = 0; payload[5] = 1
        payload[6] = 0; payload[7] = 0
        payload[8] = 0; payload[9] = 0
        payload[10] = 0; payload[11] = 0
        return payload
    }

    /**
     * Wraps a DNS response payload in an IPv4 or IPv6 UDP response packet matching [originalQueryPacket].
     * Computes valid IPv4 header checksum and IPv6/IPv4 UDP checksum.
     */
    fun buildResponseIpPacket(
        originalQueryPacket: ByteArray,
        query: DnsQuery,
        dnsResponsePayload: ByteArray,
        dnsResponseLength: Int
    ): ByteArray {
        val isIpv6 = query.isIpv6
        val ipHeaderLen = if (isIpv6) 40 else 20
        val udpLen = 8 + dnsResponseLength
        val totalLen = ipHeaderLen + udpLen
        val resp = ByteArray(totalLen)

        if (!isIpv6) {
            resp[0] = 0x45.toByte()
            resp[1] = 0
            resp[2] = ((totalLen shr 8) and 0xFF).toByte()
            resp[3] = (totalLen and 0xFF).toByte()
            resp[4] = 0; resp[5] = 0
            resp[6] = 0x40.toByte(); resp[7] = 0
            resp[8] = 64
            resp[9] = 17
            // Swap IPv4 src/dst
            System.arraycopy(originalQueryPacket, 16, resp, 12, 4)
            System.arraycopy(originalQueryPacket, 12, resp, 16, 4)
            computeIpv4Checksum(resp, ipHeaderLen)
        } else {
            resp[0] = 0x60.toByte()
            resp[4] = ((udpLen shr 8) and 0xFF).toByte()
            resp[5] = (udpLen and 0xFF).toByte()
            resp[6] = 17
            resp[7] = 64
            // Swap IPv6 src/dst
            System.arraycopy(originalQueryPacket, 24, resp, 8, 16)
            System.arraycopy(originalQueryPacket, 8, resp, 24, 16)
        }

        val udpOffset = ipHeaderLen
        val origUdpOffset = if (isIpv6) 40 else ((originalQueryPacket[0].toInt() and 0x0F) * 4)
        // Src Port = original Dst Port (53), Dst Port = original Src Port
        resp[udpOffset] = originalQueryPacket[origUdpOffset + 2]
        resp[udpOffset + 1] = originalQueryPacket[origUdpOffset + 3]
        resp[udpOffset + 2] = originalQueryPacket[origUdpOffset]
        resp[udpOffset + 3] = originalQueryPacket[origUdpOffset + 1]
        resp[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        resp[udpOffset + 5] = (udpLen and 0xFF).toByte()
        resp[udpOffset + 6] = 0; resp[udpOffset + 7] = 0

        System.arraycopy(dnsResponsePayload, 0, resp, udpOffset + 8, dnsResponseLength)
        computeUdpChecksum(resp, ipHeaderLen, udpLen, isIpv6)
        return resp
    }

    private fun computeIpv4Checksum(packet: ByteArray, headerLen: Int) {
        packet[10] = 0; packet[11] = 0
        var sum = 0
        var i = 0
        while (i < headerLen) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum ushr 16) > 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        val cs = sum.inv() and 0xFFFF
        packet[10] = ((cs shr 8) and 0xFF).toByte()
        packet[11] = (cs and 0xFF).toByte()
    }

    private fun computeUdpChecksum(packet: ByteArray, ipHeaderLen: Int, udpLen: Int, isIpv6: Boolean) {
        packet[ipHeaderLen + 6] = 0
        packet[ipHeaderLen + 7] = 0

        var sum = 0L
        if (isIpv6) {
            // IPv6 pseudo-header: src (16) + dst (16) + udpLen (4) + nextHeader=17 (4)
            for (i in 8 until 40 step 2) {
                sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            }
            sum += (udpLen ushr 16) and 0xFFFF
            sum += udpLen and 0xFFFF
            sum += 17L
        } else {
            // IPv4 pseudo-header: src (4) + dst (4) + proto=17 + udpLen
            for (i in 12 until 20 step 2) {
                sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            }
            sum += 17L
            sum += udpLen.toLong()
        }

        // UDP header + payload
        var i = ipHeaderLen
        val end = ipHeaderLen + udpLen
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        var cs = (sum.inv() and 0xFFFF).toInt()
        if (cs == 0) cs = 0xFFFF
        packet[ipHeaderLen + 6] = ((cs shr 8) and 0xFF).toByte()
        packet[ipHeaderLen + 7] = (cs and 0xFF).toByte()
    }
}
