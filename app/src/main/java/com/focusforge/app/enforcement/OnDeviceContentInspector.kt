package com.focusforge.app.enforcement

import android.net.Uri
import com.focusforge.app.data.model.DomainCategory
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of on-device content analysis (search query, URL, and page title).
 */
data class ContentInspectionResult(
    val isBlocked: Boolean,
    val matchedTerm: String? = null,
    val reason: String? = null,
    val isEducationalExempt: Boolean = false
)

/**
 * Local-only, privacy-preserving content inspector.
 * Inspects browser URLs, query parameters, and page titles on-device.
 * Zero data leaves the phone. Highly optimized for memory & CPU efficiency.
 *
 * Implements context-aware classification to avoid blocking educational/scientific/medical searches
 * (e.g. "breast cancer anatomy", "reproductive biology textbook", "history of pornography law").
 */
@Singleton
class OnDeviceContentInspector @Inject constructor(
    private val blocklistEngine: BlocklistEngine
) {

    // Common search engine query parameters
    private val queryParamKeys = listOf("q", "query", "p", "search_query", "text", "wd")

    // Context words indicating educational, medical, biological, or scientific intent
    private val educationalExemptWords = hashSetOf(
        "biology", "biological", "anatomy", "medical", "medicine", "cancer", "disease",
        "health", "science", "scientific", "study", "studies", "textbook", "wikipedia",
        "definition", "meaning", "symptoms", "treatment", "clinical", "research",
        "psychology", "disorder", "therapy", "prevention", "syndrome", "pathology",
        "physiology", "exam", "neet", "ncert", "lecture", "course", "paper", "journal",
        "history", "law", "legal", "essay", "article", "curriculum", "university"
    )

    // Unambiguous explicit keywords (always blocked regardless of context)
    private val strictlyExplicitKeywords = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "brazzers",
        "chaturbate", "onlyfans leaks", "camwhores", "beeg", "spankbang", "eporner",
        "hqporner", "motherless", "bangbros", "naughtyamerica", "heavy-r",
        "hardcore sex", "porn video", "free porn", "porno", "xxx video", "sex video",
        "hentai video", "erotic cam", "live sex cam", "nude cam", "blowjob video",
        "gangbang video", "milf porn", "anal porn", "incest porn", "shemale porn"
    )

    // Ambiguous explicit terms that are blocked UNLESS educational context words are present
    private val contextualExplicitKeywords = listOf(
        "porn", "xxx", "sex video", "nude", "naked girls", "naked women",
        "boobs", "pussy", "dick pic", "penis enlargement", "vagina pic", "erotic",
        "masturbation", "orgasm", "escort service", "call girls", "erotica"
    )

    // Fast word boundary token pattern
    private val wordBoundaryRegex = Pattern.compile("[\\s+._,\\-;:/?&!]+")

    /**
     * Inspects a browser URL or search text string on-device.
     *
     * @param rawText Can be a full URL (https://www.google.com/search?q=biology) or URL bar text.
     * @param pageTitle Optional page title or heading text if available from accessibility.
     * @param customAllowedDomains User-configured allowlist (highest priority).
     * @param customBlockedDomains User-configured blocklist.
     */
    fun inspectContent(
        rawText: String,
        pageTitle: String? = null,
        customAllowedDomains: Set<String> = emptySet(),
        customBlockedDomains: Set<String> = emptySet()
    ): ContentInspectionResult {
        val trimmedText = rawText.trim()
        if (trimmedText.isBlank()) {
            return ContentInspectionResult(isBlocked = false)
        }

        // 1. Check Allowlist first (Highest Priority)
        val extractedDomain = extractDomain(trimmedText)
        if (extractedDomain != null) {
            for (allowed in customAllowedDomains) {
                if (extractedDomain == allowed || extractedDomain.endsWith(".$allowed")) {
                    return ContentInspectionResult(isBlocked = false, reason = "ALLOWED_BY_RULE")
                }
            }
        }

        // 2. Check Custom Blocklist
        if (extractedDomain != null) {
            for (blocked in customBlockedDomains) {
                if (extractedDomain == blocked || extractedDomain.endsWith(".$blocked")) {
                    return ContentInspectionResult(
                        isBlocked = true,
                        matchedTerm = extractedDomain,
                        reason = "CUSTOM_BLOCK_RULE"
                    )
                }
            }
        }

        // 3. Check Domain against 121,000+ Adult Blocklist (Binary Search, 0ms)
        if (extractedDomain != null) {
            val domainCheck = blocklistEngine.checkDomain(
                rawDomain = extractedDomain,
                enabledCategories = setOf(DomainCategory.ADULT),
                customBlockedDomains = customBlockedDomains,
                customAllowedDomains = customAllowedDomains
            )
            if (domainCheck.isBlocked) {
                return ContentInspectionResult(
                    isBlocked = true,
                    matchedTerm = extractedDomain,
                    reason = "ADULT_DOMAIN_DATABASE"
                )
            }
        }

        // 4. Extract search query text if this is a search engine URL
        val searchQuery = extractSearchQuery(trimmedText)
        val fullTextToAnalyze = buildString {
            if (searchQuery != null) append(searchQuery).append(" ")
            else append(trimmedText).append(" ")
            if (!pageTitle.isNullOrBlank()) append(pageTitle)
        }.lowercase(Locale.ROOT)

        // 5. Check Strictly Explicit Keywords (unambiguous, never educational)
        for (keyword in strictlyExplicitKeywords) {
            if (containsKeyword(fullTextToAnalyze, keyword)) {
                return ContentInspectionResult(
                    isBlocked = true,
                    matchedTerm = keyword,
                    reason = "EXPLICIT_KEYWORD"
                )
            }
        }

        // 6. Check Contextual Explicit Terms with Educational Disambiguation
        val tokens = wordBoundaryRegex.split(fullTextToAnalyze).toSet()
        val hasEducationalContext = tokens.any { educationalExemptWords.contains(it) }

        for (keyword in contextualExplicitKeywords) {
            if (containsKeyword(fullTextToAnalyze, keyword)) {
                if (hasEducationalContext) {
                    // Educational intent detected! Allow without blocking
                    return ContentInspectionResult(
                        isBlocked = false,
                        matchedTerm = keyword,
                        reason = "EDUCATIONAL_EXEMPT",
                        isEducationalExempt = true
                    )
                } else {
                    return ContentInspectionResult(
                        isBlocked = true,
                        matchedTerm = keyword,
                        reason = "CONTEXTUAL_ADULT_KEYWORD"
                    )
                }
            }
        }

        return ContentInspectionResult(isBlocked = false)
    }

    /**
     * Checks if target text contains the keyword as a distinct phrase or token.
     */
    private fun containsKeyword(text: String, keyword: String): Boolean {
        if (!text.contains(keyword)) return false
        val idx = text.indexOf(keyword)
        val beforeChar = if (idx > 0) text[idx - 1] else ' '
        val afterChar = if (idx + keyword.length < text.length) text[idx + keyword.length] else ' '
        val isWordBoundaryBefore = !beforeChar.isLetterOrDigit()
        val isWordBoundaryAfter = !afterChar.isLetterOrDigit()
        return isWordBoundaryBefore && isWordBoundaryAfter
    }

    /**
     * Extracts search query parameters (e.g. ?q=example+search) from URL.
     */
    fun extractSearchQuery(urlOrText: String): String? {
        if (!urlOrText.contains("?") && !urlOrText.contains("&")) return null
        try {
            val uri = Uri.parse(if (urlOrText.startsWith("http")) urlOrText else "https://$urlOrText")
            for (key in queryParamKeys) {
                val param = uri.getQueryParameter(key)
                if (!param.isNullOrBlank()) {
                    return param.replace('+', ' ')
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Extracts the host domain name from URL or raw domain string.
     */
    fun extractDomain(urlOrText: String): String? {
        val lower = urlOrText.trim().lowercase(Locale.ROOT)
        // Strip scheme if present
        val stripped = lower.removePrefix("https://").removePrefix("http://")
        val hostPart = stripped.split("/")[0].split("?")[0].split("#")[0].split(":")[0].removePrefix("www.")
        if (hostPart.contains(".") && hostPart.matches(Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$"))) {
            return hostPart
        }
        try {
            val uri = Uri.parse(if (lower.startsWith("http://") || lower.startsWith("https://")) lower else "https://$lower")
            val host = uri.host ?: return null
            return host.removePrefix("www.")
        } catch (_: Exception) {
            return null
        }
    }
}
