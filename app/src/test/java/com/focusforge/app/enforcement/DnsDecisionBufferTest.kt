package com.focusforge.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsDecisionBufferTest {

    @Test
    fun `buffer does not record when disabled and masks private rules`() {
        val buf = DnsDecisionBuffer()
        buf.record("example.com", 1, DnsBufferDecision.ALLOWED, "DEFAULT_ALLOW", false, 12L)
        assertTrue(buf.entries.value.isEmpty())

        buf.setEnabled(true)
        buf.record("secret.example", 1, DnsBufferDecision.BLOCKED, "PRIVATE_RULE", true, 0L)
        assertEquals(1, buf.entries.value.size)
        val entry = buf.entries.value.first()
        assertEquals("Private rule", entry.displayDomain)
        assertEquals("Private rule", entry.reason)
        assertTrue(entry.isPrivateRule)
    }

    @Test
    fun `buffer caps at 300 entries and clears on disable or clear()`() {
        val buf = DnsDecisionBuffer()
        buf.setEnabled(true)
        repeat(350) { i ->
            buf.record("d$i.example", 1, DnsBufferDecision.ALLOWED, "DEFAULT_ALLOW", false, 5L)
        }
        assertEquals(DnsDecisionBuffer.MAX_ENTRIES, buf.entries.value.size)
        assertEquals("d349.example", buf.entries.value.first().displayDomain)

        buf.setEnabled(false)
        assertFalse(buf.isEnabled.value)
        assertTrue(buf.entries.value.isEmpty())
    }
}
