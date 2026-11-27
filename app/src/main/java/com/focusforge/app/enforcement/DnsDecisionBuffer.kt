package com.focusforge.app.enforcement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DnsBufferDecision {
    BLOCKED,
    ALLOWED,
    UPSTREAM_ERROR,
    TIMEOUT
}

data class DnsDecisionEntry(
    val id: Long,
    val displayDomain: String, // "Private rule" for private matches — never exposes private domain names
    val isPrivateRule: Boolean,
    val queryType: String,
    val decision: DnsBufferDecision,
    val reason: String,
    val upstreamLatencyMs: Long,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Debug-only, opt-in in-memory ring buffer (last 300 DNS queries).
 * Cleared when VPN stops or app closes. Never persisted to disk or backups.
 */
@Singleton
class DnsDecisionBuffer @Inject constructor() {

    private val lock = Any()
    private val buffer = ArrayDeque<DnsDecisionEntry>(MAX_ENTRIES)
    private var nextId = 1L

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _entries = MutableStateFlow<List<DnsDecisionEntry>>(emptyList())
    val entries: StateFlow<List<DnsDecisionEntry>> = _entries.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            clear()
        }
    }

    fun record(
        rawDomain: String,
        qType: Int,
        decision: DnsBufferDecision,
        reason: String,
        isPrivateRule: Boolean,
        upstreamLatencyMs: Long
    ) {
        if (!_isEnabled.value) return
        val entry = DnsDecisionEntry(
            id = nextId++,
            displayDomain = if (isPrivateRule) "Private rule" else rawDomain,
            isPrivateRule = isPrivateRule,
            queryType = qTypeName(qType),
            decision = decision,
            reason = if (isPrivateRule) "Private rule" else reason,
            upstreamLatencyMs = upstreamLatencyMs
        )
        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) {
                buffer.removeLast()
            }
            buffer.addFirst(entry)
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    companion object {
        const val MAX_ENTRIES = 300

        fun qTypeName(qType: Int): String = when (qType) {
            1 -> "A"
            28 -> "AAAA"
            64 -> "SVCB"
            65 -> "HTTPS"
            16 -> "TXT"
            5 -> "CNAME"
            12 -> "PTR"
            else -> "TYPE$qType"
        }
    }
}
