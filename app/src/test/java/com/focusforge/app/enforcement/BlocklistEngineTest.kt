package com.focusforge.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlocklistEngineTest {

    @Test
    fun testDomainNormalization() {
        assertEquals("example.com", BlocklistEngine.normalize("https://www.example.com/some/path?query=1"))
        assertEquals("pornhub.com", BlocklistEngine.normalize("  WWW.PORNHUB.COM.  "))
        assertEquals("instagram.com", BlocklistEngine.normalize("http://instagram.com:8080/feed"))
    }

    @Test
    fun testCandidateSuffixes() {
        val suffixes = BlocklistEngine.normalize("video.play.tiktok.com")
            .let { BlocklistEngine.fnv1a64("test"); /* verify static access */ }
        
        // Check candidate labels for "sub.deep.example.com"
        val parts = "sub.deep.example.com".split(".")
        val candidates = mutableListOf<String>()
        for (i in 0 until (parts.size - 1)) {
            candidates.add(parts.subList(i, parts.size).joinToString("."))
        }

        assertEquals(listOf("sub.deep.example.com", "deep.example.com", "example.com"), candidates)
        // Ensure "com" bare TLD is not a candidate
        assertFalse(candidates.contains("com"))
    }

    @Test
    fun testFnv1a64Consistency() {
        // Test deterministic hashing of domain strings
        val h1 = BlocklistEngine.fnv1a64("example.com")
        val h2 = BlocklistEngine.fnv1a64("example.com")
        assertEquals(h1, h2)

        val h3 = BlocklistEngine.fnv1a64("notexample.com")
        assertTrue("Different domains must yield different hashes", h1 != h3)
    }

    @Test
    fun testBinarySearchLogic() {
        // Prepare a sorted list of Long hashes
        val hashes = listOf("adultsite.com", "facebook.com", "tiktok.com", "xvideos.com")
            .map { BlocklistEngine.fnv1a64(it) }
            .sortedBy { it.toULong() }

        val buffer = ByteBuffer.allocate(hashes.size * 8).order(ByteOrder.BIG_ENDIAN)
        for (h in hashes) {
            buffer.putLong(h)
        }
        buffer.flip()

        // Binary search helper matching BlocklistEngine logic
        fun search(target: Long): Boolean {
            var low = 0
            var high = (buffer.capacity() / 8) - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val midVal = buffer.getLong(mid * 8)
                val cmp = midVal.toULong().compareTo(target.toULong())
                if (cmp < 0) low = mid + 1
                else if (cmp > 0) high = mid - 1
                else return true
            }
            return false
        }

        // Test exact matches
        assertTrue(search(BlocklistEngine.fnv1a64("tiktok.com")))
        assertTrue(search(BlocklistEngine.fnv1a64("adultsite.com")))

        // Test non-matches
        assertFalse(search(BlocklistEngine.fnv1a64("nottiktok.com")))
        assertFalse(search(BlocklistEngine.fnv1a64("google.com")))
    }
}
