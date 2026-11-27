package com.focusforge.app.enforcement

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OnDeviceContentInspectorTest {

    private lateinit var contentInspector: OnDeviceContentInspector

    private class TestContext : ContextWrapper(null) {
        override fun getFilesDir(): File {
            return File("/tmp")
        }
    }

    @Before
    fun setUp() {
        contentInspector = OnDeviceContentInspector(
            blocklistEngine = BlocklistEngine(context = TestContext())
        )
    }

    @Test
    fun allowlist_overridesEverything() {
        val result = contentInspector.inspectContent(
            rawText = "https://www.example-sex-education.org/video",
            pageTitle = "Sex Education Course",
            customAllowedDomains = setOf("example-sex-education.org")
        )
        assertFalse(result.isBlocked)
        assertEquals("ALLOWED_BY_RULE", result.reason)
    }

    @Test
    fun strictlyExplicit_isBlockedImmediately() {
        val result = contentInspector.inspectContent(
            rawText = "https://www.google.com/search?q=pornhub+free+videos",
            pageTitle = "Google Search"
        )
        assertTrue(result.isBlocked)
        assertEquals("EXPLICIT_KEYWORD", result.reason)
    }

    @Test
    fun educationalMedicalContext_isExemptFromBlocking() {
        val result = contentInspector.inspectContent(
            rawText = "https://www.google.com/search?q=breast+cancer+anatomy+definition+symptoms",
            pageTitle = "Medical Studies Search"
        )
        assertFalse(result.isBlocked)
    }

    @Test
    fun nonEducationalExplicitSearch_isBlocked() {
        val result = contentInspector.inspectContent(
            rawText = "https://www.google.com/search?q=hot+naked+girls+boobs",
            pageTitle = "Search"
        )
        assertTrue(result.isBlocked)
        assertEquals("CONTEXTUAL_ADULT_KEYWORD", result.reason)
    }

    @Test
    fun generalSearch_isAllowed() {
        val result = contentInspector.inspectContent(
            rawText = "https://gemini.google.com/app",
            pageTitle = "Gemini"
        )
        assertFalse(result.isBlocked)
    }
}
