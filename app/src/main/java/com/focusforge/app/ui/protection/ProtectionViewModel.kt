package com.focusforge.app.ui.protection

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.model.BlockResponseMode
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.PolicyTier
import com.focusforge.app.data.model.UpstreamResolverProvider
import com.focusforge.app.data.repository.PolicyStore
import com.focusforge.app.enforcement.BlocklistEngine
import com.focusforge.app.enforcement.DnsDecisionBuffer
import com.focusforge.app.enforcement.DnsVpnService
import com.focusforge.app.enforcement.DomainCheckResult
import com.focusforge.app.enforcement.DomainNormalizer
import com.focusforge.app.enforcement.RuleChangeGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProtectionUiState(
    val policySnapshot: PolicySnapshot = PolicySnapshot(),
    val tierPreferences: Map<PolicyTier, Boolean> = emptyMap(),
    val upstreamProvider: UpstreamResolverProvider = UpstreamResolverProvider.SYSTEM_DEFAULT,
    val blockResponseMode: BlockResponseMode = BlockResponseMode.NXDOMAIN,
    val isVpnRunning: Boolean = false,
    val isDnsProtectionEnabled: Boolean = false,
    val isPrivateDnsStrict: Boolean = false,
    val isFailOpenPaused: Boolean = false,
    val failOpenReason: String? = null,
    val todayBlockedCount: Int = 0,
    val pauseEventsCount: Int = 0,
    val allowDomainInput: String = "",
    val allowActionMessage: String? = null,
    val testDomainInput: String = "",
    val testDomainResult: DomainCheckResult? = null,
    val isDecisionBufferEnabled: Boolean = false,
    val decisionBufferCount: Int = 0
)

@HiltViewModel
class ProtectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: PolicyStore,
    private val blocklistEngine: BlocklistEngine,
    private val ruleChangeGate: RuleChangeGate,
    private val decisionBuffer: DnsDecisionBuffer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProtectionUiState())
    val uiState: StateFlow<ProtectionUiState> = _uiState.asStateFlow()

    init {
        refreshPrivateDnsStatus()
        observeState()
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                policyStore.policySnapshot,
                policyStore.tierPreferences,
                policyStore.upstreamProvider,
                policyStore.blockResponseMode,
                policyStore.isDnsProtectionEnabled
            ) { snapshot, tiers, provider, mode, enabled ->
                ConfigBundle(snapshot, tiers, provider, mode, enabled)
            }.collect { cfg ->
                _uiState.value = _uiState.value.copy(
                    policySnapshot = cfg.snapshot,
                    tierPreferences = cfg.tiers,
                    upstreamProvider = cfg.provider,
                    blockResponseMode = cfg.mode,
                    isDnsProtectionEnabled = cfg.enabled
                )
            }
        }

        viewModelScope.launch {
            combine(
                DnsVpnService.isVpnRunning,
                DnsVpnService.isPrivateDnsStrict,
                DnsVpnService.isFailOpenPaused,
                DnsVpnService.lastFailOpenReason
            ) { running, strict, failOpen, reason ->
                ServiceBundle(running, strict, failOpen, reason)
            }.collect { svc ->
                _uiState.value = _uiState.value.copy(
                    isVpnRunning = svc.running,
                    isPrivateDnsStrict = svc.strict || isSystemPrivateDnsHostnameMode(),
                    isFailOpenPaused = svc.failOpen,
                    failOpenReason = svc.reason
                )
            }
        }

        viewModelScope.launch {
            policyStore.todayBlockedCount.collect { count ->
                _uiState.value = _uiState.value.copy(todayBlockedCount = count)
            }
        }

        viewModelScope.launch {
            policyStore.pauseEventsCount.collect { count ->
                _uiState.value = _uiState.value.copy(pauseEventsCount = count)
            }
        }

        viewModelScope.launch {
            decisionBuffer.isEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isDecisionBufferEnabled = enabled)
            }
        }

        viewModelScope.launch {
            decisionBuffer.entries.collect { entries ->
                _uiState.value = _uiState.value.copy(decisionBufferCount = entries.size)
            }
        }
    }

    fun refreshPrivateDnsStatus() {
        val strict = DnsVpnService.isPrivateDnsStrict.value || isSystemPrivateDnsHostnameMode()
        _uiState.value = _uiState.value.copy(isPrivateDnsStrict = strict)
    }

    private fun isSystemPrivateDnsHostnameMode(): Boolean {
        return try {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
            mode.equals("hostname", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun enableVpn() {
        viewModelScope.launch {
            policyStore.setDnsProtectionEnabled(true)
            ruleChangeGate.resumeProtectionNow()
            DnsVpnService.start(context)
        }
    }

    fun disableVpn() {
        viewModelScope.launch {
            policyStore.setDnsProtectionEnabled(false)
            DnsVpnService.stop(context)
        }
    }

    fun setDecisionBufferEnabled(enabled: Boolean) {
        decisionBuffer.setEnabled(enabled)
    }

    fun pauseFor5Minutes() {
        viewModelScope.launch {
            ruleChangeGate.pauseProtectionFor5Minutes()
        }
    }

    fun resumeNow() {
        viewModelScope.launch {
            ruleChangeGate.resumeProtectionNow()
            if (_uiState.value.isDnsProtectionEnabled) {
                DnsVpnService.start(context)
            }
        }
    }

    fun toggleTier(tier: PolicyTier) {
        viewModelScope.launch {
            val current = _uiState.value.tierPreferences[tier] ?: false
            if (current) {
                ruleChangeGate.requestPermanentWeakening("Disable ${tier.name}") {
                    policyStore.setTierEnabled(tier, false)
                }
            } else {
                policyStore.setTierEnabled(tier, true)
            }
        }
    }

    fun setUpstreamProvider(provider: UpstreamResolverProvider) {
        viewModelScope.launch {
            policyStore.setUpstreamProvider(provider)
        }
    }

    fun setBlockResponseMode(mode: BlockResponseMode) {
        viewModelScope.launch {
            policyStore.setBlockResponseMode(mode)
        }
    }

    fun onAllowDomainInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(allowDomainInput = value, allowActionMessage = null)
    }

    fun addAllowDomain() {
        val raw = _uiState.value.allowDomainInput
        viewModelScope.launch {
            val added = policyStore.addCustomAllowDomain(raw)
            _uiState.value = if (added) {
                _uiState.value.copy(
                    allowDomainInput = "",
                    allowActionMessage = "Allow rule saved (stored as hash only; applies to distraction rules, not adult)."
                )
            } else {
                _uiState.value.copy(allowActionMessage = "Enter a valid domain name.")
            }
        }
    }

    fun removeAllowDomain() {
        val raw = _uiState.value.allowDomainInput
        viewModelScope.launch {
            val removed = policyStore.removeCustomAllowDomain(raw)
            _uiState.value = if (removed) {
                _uiState.value.copy(
                    allowDomainInput = "",
                    allowActionMessage = "Matching allow rule removed."
                )
            } else {
                _uiState.value.copy(allowActionMessage = "No matching allow rule found for that domain.")
            }
        }
    }

    fun onTestDomainInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(testDomainInput = value)
    }

    fun testDomain() {
        val domain = _uiState.value.testDomainInput.trim()
        if (domain.isBlank()) return
        val upstreamHashes = _uiState.value.upstreamProvider.bootstrapHostnames
            .mapNotNull { DomainNormalizer.normalizeAndHash(it) }
            .toSet()
        val res = blocklistEngine.checkDomain(
            rawDomain = domain,
            snapshot = _uiState.value.policySnapshot,
            configuredUpstreamHashes = upstreamHashes,
            allowlistOverridesAdult = false
        )
        _uiState.value = _uiState.value.copy(testDomainResult = res)
    }

    private data class ConfigBundle(
        val snapshot: PolicySnapshot,
        val tiers: Map<PolicyTier, Boolean>,
        val provider: UpstreamResolverProvider,
        val mode: BlockResponseMode,
        val enabled: Boolean
    )

    private data class ServiceBundle(
        val running: Boolean,
        val strict: Boolean,
        val failOpen: Boolean,
        val reason: String?
    )
}
