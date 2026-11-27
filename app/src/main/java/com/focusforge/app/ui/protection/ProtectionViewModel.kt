package com.focusforge.app.ui.protection

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.local.dao.CustomDomainDao
import com.focusforge.app.data.local.entity.CustomDomainEntity
import com.focusforge.app.data.local.entity.DnsBlockLogEntity
import com.focusforge.app.data.model.DomainCategory
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.repository.PolicyRepository
import com.focusforge.app.enforcement.BlocklistEngine
import com.focusforge.app.enforcement.DnsVpnService
import com.focusforge.app.enforcement.DomainCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProtectionUiState(
    val policySnapshot: PolicySnapshot = PolicySnapshot(),
    val categoryPreferences: Map<DomainCategory, Boolean> = emptyMap(),
    val isVpnRunning: Boolean = false,
    val todayBlockedCount: Int = 0,
    val isPrivateDnsActive: Boolean = false,
    val customDomains: List<CustomDomainEntity> = emptyList(),
    val recentLogs: List<DnsBlockLogEntity> = emptyList(),
    val testDomainInput: String = "",
    val testDomainResult: DomainCheckResult? = null,
    val newCustomDomainInput: String = "",
    val newCustomDomainType: String = "ALLOW" // Default to ALLOW for whitelist focus
)

@HiltViewModel
class ProtectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyRepository: PolicyRepository,
    private val blocklistEngine: BlocklistEngine,
    private val customDomainDao: CustomDomainDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProtectionUiState())
    val uiState: StateFlow<ProtectionUiState> = _uiState.asStateFlow()

    init {
        checkPrivateDnsMode()
        observePolicy()
        observeVpnServiceState()
        observeTodayBlockedCount()
        observeCustomDomains()
        observeRecentLogs()
    }

    fun checkPrivateDnsMode() {
        try {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
            // ONLY "hostname" (strict manual DNS provider) bypasses local VPN DNS.
            // "opportunistic" (Automatic) and "off" (Off) are fully compatible with local VPN DNS!
            val isStrict = mode.equals("hostname", ignoreCase = true)
            _uiState.value = _uiState.value.copy(isPrivateDnsActive = isStrict)
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(isPrivateDnsActive = false)
        }
    }

    private fun observePolicy() {
        viewModelScope.launch {
            policyRepository.policySnapshot.collect { snapshot ->
                _uiState.value = _uiState.value.copy(policySnapshot = snapshot)
            }
        }
        viewModelScope.launch {
            policyRepository.categoryPreferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(categoryPreferences = prefs)
            }
        }
    }

    private fun observeVpnServiceState() {
        viewModelScope.launch {
            DnsVpnService.isVpnRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isVpnRunning = running)
            }
        }
    }

    private fun observeTodayBlockedCount() {
        viewModelScope.launch {
            policyRepository.getTodayBlockedCount().collect { count ->
                _uiState.value = _uiState.value.copy(todayBlockedCount = count)
            }
        }
    }

    private fun observeCustomDomains() {
        viewModelScope.launch {
            customDomainDao.getAllCustomDomains().collect { list ->
                _uiState.value = _uiState.value.copy(customDomains = list)
            }
        }
    }

    private fun observeRecentLogs() {
        viewModelScope.launch {
            policyRepository.getRecentDnsLogs(30).collect { logs ->
                _uiState.value = _uiState.value.copy(recentLogs = logs)
            }
        }
    }

    fun toggleCategory(category: DomainCategory) {
        viewModelScope.launch {
            val current = _uiState.value.categoryPreferences[category] ?: true
            policyRepository.setCategoryEnabled(category, !current)
        }
    }

    fun onTestDomainInputChanged(domain: String) {
        _uiState.value = _uiState.value.copy(testDomainInput = domain)
    }

    fun testDomain() {
        val domain = _uiState.value.testDomainInput.trim()
        if (domain.isBlank()) return

        val snapshot = _uiState.value.policySnapshot
        val result = blocklistEngine.checkDomain(
            rawDomain = domain,
            enabledCategories = snapshot.enabledDomainCategories,
            customBlockedDomains = snapshot.customBlockedDomains,
            customAllowedDomains = snapshot.customAllowedDomains
        )
        _uiState.value = _uiState.value.copy(testDomainResult = result)
    }

    fun onNewCustomDomainInputChanged(domain: String) {
        _uiState.value = _uiState.value.copy(newCustomDomainInput = domain)
    }

    fun setNewCustomDomainType(type: String) {
        _uiState.value = _uiState.value.copy(newCustomDomainType = type)
    }

    fun addCustomDomain() {
        val domain = _uiState.value.newCustomDomainInput.trim()
        if (domain.isBlank()) return

        val type = _uiState.value.newCustomDomainType
        viewModelScope.launch {
            policyRepository.addCustomDomain(domain, type)
            _uiState.value = _uiState.value.copy(newCustomDomainInput = "")
        }
    }

    fun removeCustomDomain(domain: String) {
        viewModelScope.launch {
            policyRepository.removeCustomDomain(domain)
        }
    }

    fun clearDnsActivityLogs() {
        viewModelScope.launch {
            policyRepository.clearAllDnsLogs()
        }
    }

    fun toggleVpnService() {
        val isRunning = _uiState.value.isVpnRunning
        val prefs = context.getSharedPreferences("focusforge_prefs", Context.MODE_PRIVATE)
        if (isRunning) {
            DnsVpnService.stop(context)
            prefs.edit().putBoolean("dns_protection_enabled", false).apply()
        } else {
            DnsVpnService.start(context)
            prefs.edit().putBoolean("dns_protection_enabled", true).apply()
        }
    }
}
