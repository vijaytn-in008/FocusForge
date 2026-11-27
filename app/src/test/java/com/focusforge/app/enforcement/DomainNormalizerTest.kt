package com.focusforge.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainNormalizerTest {

    @Test
    fun `normalize lowercases and strips trailing dots and whitespace`() {
        assertEquals("example.com", DomainNormalizer.normalize("  EXAMPLE.COM.  "))
        assertEquals("sub.example.com", DomainNormalizer.normalize("Sub.Example.Com..."))
        assertEquals("www.example.com", DomainNormalizer.normalize("WWW.EXAMPLE.COM."))
    }

    @Test
    fun `normalize strips URL scheme path query fragment and port`() {
        assertEquals("example.com", DomainNormalizer.normalize("https://EXAMPLE.COM/path?a=1#frag"))
        assertEquals("example.com", DomainNormalizer.normalize("http://example.com:8080/login"))
        assertEquals("sub.example.org", DomainNormalizer.normalize("https://user@SUB.EXAMPLE.ORG.:443/"))
    }

    @Test
    fun `normalize converts IDN unicode domains to punycode`() {
        assertEquals("xn--mnchen-3ya.de", DomainNormalizer.normalize("München.de."))
        assertEquals("xn--mnchen-3ya.de", DomainNormalizer.normalize("XN--MNCHEN-3YA.DE"))
        assertEquals("xn--fsqu00a.xn--0zwm56d", DomainNormalizer.normalize("例子.测试"))
    }

    @Test
    fun `normalize returns empty string for blank or dot-only input`() {
        assertEquals("", DomainNormalizer.normalize(""))
        assertEquals("", DomainNormalizer.normalize("   "))
        assertEquals("", DomainNormalizer.normalize("..."))
        assertEquals("", DomainNormalizer.normalize("https:///path"))
    }

    @Test
    fun `fnv1a64 is deterministic and matches standard offset basis on empty input`() {
        assertEquals(DomainNormalizer.FNV_OFFSET_BASIS, DomainNormalizer.fnv1a64(""))
        val h1 = DomainNormalizer.fnv1a64("example.com")
        val h2 = DomainNormalizer.fnv1a64("example.com")
        val h3 = DomainNormalizer.fnv1a64("notexample.com")
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
    }

    @Test
    fun `normalizeAndHash produces identical hashes for equivalent domain forms`() {
        val h1 = DomainNormalizer.normalizeAndHash("example.com")
        val h2 = DomainNormalizer.normalizeAndHash("  EXAMPLE.COM. ")
        val h3 = DomainNormalizer.normalizeAndHash("https://Example.Com:443/page")
        assertEquals(h1, h2)
        assertEquals(h1, h3)
        assertNull(DomainNormalizer.normalizeAndHash("   "))
    }
}
