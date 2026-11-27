package com.focusforge.app.ui.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.repository.PolicyStore
import com.focusforge.app.enforcement.DnsDecisionBuffer
import com.focusforge.app.enforcement.DnsDecisionEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhyBlockedUiState(
    val isEnabled: Boolean = false,
    val entries: List<DnsDecisionEntry> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class WhyBlockedViewModel @Inject constructor(
    private val decisionBuffer: DnsDecisionBuffer,
    private val policyStore: PolicyStore
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WhyBlockedUiState> = combine(
        decisionBuffer.isEnabled,
        decisionBuffer.entries,
        _statusMessage
    ) { enabled, entries, msg ->
        WhyBlockedUiState(
            isEnabled = enabled,
            entries = entries,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WhyBlockedUiState()
    )

    fun reportFalsePositive(entry: DnsDecisionEntry) {
        viewModelScope.launch {
            if (entry.isPrivateRule) {
                // Report without exposing or logging the private domain name
                _statusMessage.value = "Reported without exposing private rule."
            } else {
                val added = policyStore.addCustomAllowDomain(entry.displayDomain)
                _statusMessage.value = if (added) {
                    "Added ${entry.displayDomain} to user allowlist."
                } else {
                    "Could not add to allowlist."
                }
            }
        }
    }

    fun clearBuffer() {
        decisionBuffer.clear()
        _statusMessage.value = "Decision buffer cleared."
    }
}
