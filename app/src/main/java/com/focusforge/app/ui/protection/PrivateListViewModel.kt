package com.focusforge.app.ui.protection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.repository.PolicyStore
import com.focusforge.app.data.repository.PrivateListStore
import com.focusforge.app.data.repository.PrivateVault
import com.focusforge.app.enforcement.BlocklistEngine
import com.focusforge.app.enforcement.DomainNormalizer
import com.focusforge.app.enforcement.PendingPrivateChange
import com.focusforge.app.enforcement.PrivateMasker
import com.focusforge.app.enforcement.RuleChangeGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MaskedPrivateDomainItem(
    val hash: Long,
    val maskedName: String,
    val realName: String?
)

data class MaskedPrivateAppItem(
    val packageName: String,
    val maskedLabel: String,
    val realLabel: String?
)

data class InstalledAppChoice(
    val packageName: String,
    val label: String
)

data class PrivateListUiState(
    val isAuthenticated: Boolean = false,
    val lastAuthTimeMillis: Long = 0L,
    val isHashOnlyMode: Boolean = false,
    val stealthLabelsEnabled: Boolean = false,
    val discreetAppIconEnabled: Boolean = false,
    val authGraceSeconds: Int = PrivateVault.DEFAULT_GRACE_SECONDS,
    val totalDomainCount: Int = 0,
    val totalAppCount: Int = 0,
    val maskedDomains: List<MaskedPrivateDomainItem> = emptyList(),
    val maskedApps: List<MaskedPrivateAppItem> = emptyList(),
    val revealedItemKey: String? = null,
    val domainInput: String = "",
    val pendingPopularWarningDomain: String? = null,
    val statusMessage: String? = null,
    val pendingPrivateChange: PendingPrivateChange? = null,
    val installedApps: List<InstalledAppChoice> = emptyList()
)

@HiltViewModel
class PrivateListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privateListStore: PrivateListStore,
    private val policyStore: PolicyStore,
    private val blocklistEngine: BlocklistEngine,
    private val ruleChangeGate: RuleChangeGate
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateListUiState())
    val uiState: StateFlow<PrivateListUiState> = _uiState.asStateFlow()

    private var autoHideJob: Job? = null
    // In-memory only while screen is authenticated; cleared on lock/pause/background
    private val sessionDomainNames = mutableMapOf<Long, String>()
    private val sessionAppNames = mutableMapOf<String, String>()

    init {
        observeStore()
        loadInstalledApps()
    }

    private fun observeStore() {
        viewModelScope.launch {
            combine(
                privateListStore.privateDomainHashes,
                privateListStore.privateBlockedApps,
                privateListStore.isHashOnlyMode,
                privateListStore.stealthLabelsEnabled,
                privateListStore.discreetAppIconEnabled
            ) { hashes, apps, hashOnly, stealth, discreetIcon ->
                StoreBundle(hashes, apps, hashOnly, stealth, discreetIcon)
            }.collect { b ->
                _uiState.value = _uiState.value.copy(
                    isHashOnlyMode = b.hashOnly,
                    stealthLabelsEnabled = b.stealth,
                    discreetAppIconEnabled = b.discreetIcon,
                    totalDomainCount = b.hashes.size,
                    totalAppCount = b.apps.size
                )
                if (_uiState.value.isAuthenticated) {
                    refreshMaskedItems()
                }
            }
        }

        viewModelScope.launch {
            privateListStore.authGraceSeconds.collect { sec ->
                _uiState.value = _uiState.value.copy(authGraceSeconds = sec)
            }
        }

        viewModelScope.launch {
            ruleChangeGate.pendingPrivateChange.collect { pending ->
                _uiState.value = _uiState.value.copy(pendingPrivateChange = pending)
            }
        }
    }

    fun isWithinAuthGrace(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val state = _uiState.value
        if (!state.isAuthenticated || state.lastAuthTimeMillis == 0L) return false
        return (nowMillis - state.lastAuthTimeMillis) < (state.authGraceSeconds * 1000L)
    }

    fun onAuthenticated(nowMillis: Long = System.currentTimeMillis()) {
        _uiState.value = _uiState.value.copy(
            isAuthenticated = true,
            lastAuthTimeMillis = nowMillis
        )
        refreshMaskedItems()
    }

    fun lockAndHide() {
        autoHideJob?.cancel()
        sessionDomainNames.clear()
        sessionAppNames.clear()
        _uiState.value = _uiState.value.copy(
            isAuthenticated = false,
            revealedItemKey = null,
            maskedDomains = emptyList(),
            maskedApps = emptyList()
        )
    }

    fun hideRevealedOnly() {
        autoHideJob?.cancel()
        _uiState.value = _uiState.value.copy(revealedItemKey = null)
    }

    fun revealItem(key: String) {
        autoHideJob?.cancel()
        if (_uiState.value.revealedItemKey == key) {
            _uiState.value = _uiState.value.copy(revealedItemKey = null)
            return
        }
        _uiState.value = _uiState.value.copy(revealedItemKey = key)
        autoHideJob = viewModelScope.launch {
            delay(10_000L)
            _uiState.value = _uiState.value.copy(revealedItemKey = null)
        }
    }

    private fun refreshMaskedItems() {
        val hashes = privateListStore.privateDomainHashes.value
        val apps = privateListStore.privateBlockedApps.value
        val hashOnly = privateListStore.isHashOnlyMode.value

        if (hashOnly) {
            _uiState.value = _uiState.value.copy(
                maskedDomains = emptyList(),
                maskedApps = emptyList()
            )
            return
        }

        when (val res = privateListStore.readVaultNames()) {
            is PrivateVault.VaultReadResult.Success -> {
                sessionDomainNames.putAll(res.domainNames)
                sessionAppNames.putAll(res.appNames)
                val domainItems = hashes.map { h ->
                    val real = sessionDomainNames[h]
                    MaskedPrivateDomainItem(
                        hash = h,
                        maskedName = real?.let { PrivateMasker.maskDomain(it) } ?: "•••••",
                        realName = real
                    )
                }
                val appItems = apps.map { pkg ->
                    val label = sessionAppNames[pkg] ?: pkg
                    MaskedPrivateAppItem(
                        packageName = pkg,
                        maskedLabel = PrivateMasker.maskLabel(label),
                        realLabel = label
                    )
                }
                _uiState.value = _uiState.value.copy(
                    maskedDomains = domainItems,
                    maskedApps = appItems
                )
            }
            is PrivateVault.VaultReadResult.AuthRequired -> {
                lockAndHide()
            }
            is PrivateVault.VaultReadResult.Error -> {
                val domainItems = hashes.map { h ->
                    val real = sessionDomainNames[h]
                    MaskedPrivateDomainItem(
                        hash = h,
                        maskedName = real?.let { PrivateMasker.maskDomain(it) } ?: "•••••",
                        realName = real
                    )
                }
                _uiState.value = _uiState.value.copy(
                    maskedDomains = domainItems,
                    statusMessage = "Vault unavailable"
                )
            }
        }
    }

    fun onDomainInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(domainInput = value, statusMessage = null)
    }

    fun requestAddDomain() {
        val raw = _uiState.value.domainInput.trim()
        val normalized = DomainNormalizer.normalize(raw)
        if (normalized.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Enter a valid domain.")
            return
        }
        if (blocklistEngine.isPopularDomain(normalized)) {
            _uiState.value = _uiState.value.copy(pendingPopularWarningDomain = normalized)
        } else {
            confirmAddDomain(normalized)
        }
    }

    fun confirmPopularDomainWarning() {
        val d = _uiState.value.pendingPopularWarningDomain ?: return
        _uiState.value = _uiState.value.copy(pendingPopularWarningDomain = null)
        confirmAddDomain(d)
    }

    fun dismissPopularDomainWarning() {
        _uiState.value = _uiState.value.copy(pendingPopularWarningDomain = null)
    }

    private fun confirmAddDomain(domain: String) {
        val hash = DomainNormalizer.normalizeAndHash(domain)
        if (hash == null) {
            _uiState.value = _uiState.value.copy(statusMessage = "Invalid domain.")
            return
        }
        val snapshot = policyStore.policySnapshot.value
        if (snapshot.privateBlockedDomainHashes.contains(hash)) {
            _uiState.value = _uiState.value.copy(statusMessage = "Already in Custom Rules.")
            return
        }
        if (snapshot.customAllowedDomainHashes.contains(hash)) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Already in Allowlist. Remove it from Allowlist first."
            )
            return
        }
        val addedHash = privateListStore.addPrivateDomain(domain)
        if (addedHash != null && !privateListStore.isHashOnlyMode.value) {
            sessionDomainNames[addedHash] = domain
        }
        _uiState.value = if (addedHash != null) {
            _uiState.value.copy(
                domainInput = "",
                statusMessage = "Rule added."
            )
        } else {
            _uiState.value.copy(statusMessage = "Invalid domain.")
        }
        refreshMaskedItems()
    }

    fun importFromPaste(pastedText: String) {
        val tokens = pastedText.split('\n', ',', ';', ' ')
            .map { DomainNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        val snapshot = policyStore.policySnapshot.value
        var added = 0
        var skipped = 0
        for (d in tokens) {
            val hCandidate = DomainNormalizer.fnv1a64(d)
            if (snapshot.privateBlockedDomainHashes.contains(hCandidate) ||
                snapshot.customAllowedDomainHashes.contains(hCandidate)
            ) {
                skipped++
                continue
            }
            val h = privateListStore.addPrivateDomain(d)
            if (h != null) {
                if (!privateListStore.isHashOnlyMode.value) {
                    sessionDomainNames[h] = d
                }
                added++
            }
        }
        _uiState.value = _uiState.value.copy(
            statusMessage = when {
                added > 0 && skipped > 0 -> "Added $added rule(s); skipped $skipped duplicate/allowlisted."
                added > 0 -> "Added $added rule(s)."
                skipped > 0 -> "Skipped $skipped duplicate or allowlisted domain(s)."
                else -> "No valid domains found in clipboard."
            }
        )
        refreshMaskedItems()
    }

    fun requestRemoveByHash(hash: Long) {
        ruleChangeGate.schedulePrivateRuleRemoval {
            privateListStore.removePrivateDomainByHash(hash)
            withContext(Dispatchers.Main) {
                refreshMaskedItems()
            }
        }
    }

    fun requestRemoveByRetyping() {
        val raw = _uiState.value.domainInput.trim()
        val hash = DomainNormalizer.normalizeAndHash(raw)
        if (hash == null || !privateListStore.privateDomainHashes.value.contains(hash)) {
            _uiState.value = _uiState.value.copy(statusMessage = "No matching rule found.")
            return
        }
        _uiState.value = _uiState.value.copy(domainInput = "")
        requestRemoveByHash(hash)
    }

    fun addPrivateApp(packageName: String, label: String) {
        privateListStore.addPrivateApp(packageName, label)
        if (!privateListStore.isHashOnlyMode.value) {
            sessionAppNames[packageName] = label
        }
        _uiState.value = _uiState.value.copy(statusMessage = "App rule added.")
        refreshMaskedItems()
    }

    fun requestRemovePrivateApp(packageName: String) {
        ruleChangeGate.schedulePrivateRuleRemoval {
            privateListStore.removePrivateApp(packageName)
            withContext(Dispatchers.Main) {
                refreshMaskedItems()
            }
        }
    }

    fun cancelPendingPrivateChange() {
        ruleChangeGate.cancelPendingPrivateChange()
    }

    fun setHashOnlyMode(enabled: Boolean) {
        privateListStore.setHashOnlyMode(enabled)
        refreshMaskedItems()
    }

    fun setStealthLabelsEnabled(enabled: Boolean) {
        privateListStore.setStealthLabelsEnabled(enabled)
    }

    fun setDiscreetAppIconEnabled(enabled: Boolean) {
        privateListStore.setDiscreetAppIconEnabled(enabled)
        val pm = context.packageManager
        val normalMain = ComponentName(context, "com.focusforge.app.MainActivity")
        val discreetAlias = ComponentName(context, "com.focusforge.app.DiscreetLauncherAlias")
        pm.setComponentEnabledSetting(
            normalMain,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            discreetAlias,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            val seen = HashSet<String>()
            val list = mutableListOf<InstalledAppChoice>()
            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName && seen.add(pkg)) {
                    list.add(InstalledAppChoice(pkg, info.loadLabel(pm).toString()))
                }
            }
            list.sortBy { it.label.lowercase() }
            _uiState.value = _uiState.value.copy(installedApps = list)
        }
    }

    private data class StoreBundle(
        val hashes: Set<Long>,
        val apps: Set<String>,
        val hashOnly: Boolean,
        val stealth: Boolean,
        val discreetIcon: Boolean
    )
}
