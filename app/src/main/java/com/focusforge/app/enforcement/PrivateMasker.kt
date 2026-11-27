package com.focusforge.app.enforcement

/**
 * Pure masking helpers for discreet Private List display.
 * Never reveals full embarrassing domain or app names in overview lists.
 */
object PrivateMasker {

    /**
     * Masks a domain name such as "pornhub.com" -> "p•••••.com".
     */
    fun maskDomain(rawDomain: String): String {
        val d = DomainNormalizer.normalize(rawDomain)
        if (d.isEmpty()) return "•••••"
        val dotIdx = d.indexOf('.')
        if (dotIdx <= 0) {
            return maskToken(d)
        }
        val firstLabel = d.substring(0, dotIdx)
        val suffix = d.substring(dotIdx)
        return "${maskToken(firstLabel)}$suffix"
    }

    /**
     * Masks an app label such as "Reddit" -> "R•••••".
     */
    fun maskLabel(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return "•••••"
        return maskToken(trimmed)
    }

    private fun maskToken(token: String): String {
        return when {
            token.length == 1 -> "•"
            token.length == 2 -> "${token[0]}•"
            else -> "${token[0]}•••••"
        }
    }
}
