package com.focusforge.app.data.repository

import android.content.Context
import com.focusforge.app.enforcement.DomainNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages discreet Private List hash storage (always hashed, never readable in Room/prefs/logs)
 * and optional encrypted vault access for readable names.
 *
 * Excluded from Android Auto-Backup and D2D transfers via `data_extraction_rules.xml`
 * and `full_backup_content.xml`.
 */
@Singleton
class PrivateListStore @Inject constructor(
    @ApplicationContext context: Context,
    private val privateVault: PrivateVault
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _privateDomainHashes = MutableStateFlow(loadDomainHashes())
    val privateDomainHashes: StateFlow<Set<Long>> = _privateDomainHashes.asStateFlow()

    private val _privateBlockedApps = MutableStateFlow(loadPrivateApps())
    val privateBlockedApps: StateFlow<Set<String>> = _privateBlockedApps.asStateFlow()

    private val _isHashOnlyMode = MutableStateFlow(prefs.getBoolean(KEY_HASH_ONLY_MODE, false))
    val isHashOnlyMode: StateFlow<Boolean> = _isHashOnlyMode.asStateFlow()

    private val _stealthLabelsEnabled = MutableStateFlow(prefs.getBoolean(KEY_STEALTH_LABELS, false))
    val stealthLabelsEnabled: StateFlow<Boolean> = _stealthLabelsEnabled.asStateFlow()

    private val _discreetAppIconEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCREET_ICON, false))
    val discreetAppIconEnabled: StateFlow<Boolean> = _discreetAppIconEnabled.asStateFlow()

    private val _authGraceSeconds = MutableStateFlow(prefs.getInt(KEY_AUTH_GRACE_SECONDS, PrivateVault.DEFAULT_GRACE_SECONDS))
    val authGraceSeconds: StateFlow<Int> = _authGraceSeconds.asStateFlow()

    fun addPrivateDomain(rawDomain: String): Long? {
        val normalized = DomainNormalizer.normalize(rawDomain)
        if (normalized.isEmpty()) return null
        val hash = DomainNormalizer.fnv1a64(normalized)

        synchronized(lock) {
            val updated = _privateDomainHashes.value + hash
            saveDomainHashes(updated)
            _privateDomainHashes.value = updated

            if (!_isHashOnlyMode.value) {
                val vaultRes = privateVault.readVault(_authGraceSeconds.value)
                if (vaultRes is PrivateVault.VaultReadResult.Success) {
                    val updatedNames = vaultRes.domainNames.toMutableMap()
                    updatedNames[hash] = normalized
                    privateVault.writeVault(updatedNames, vaultRes.appNames, _authGraceSeconds.value)
                }
            }
        }
        return hash
    }

    fun removePrivateDomainByHash(hash: Long): Boolean {
        synchronized(lock) {
            if (!_privateDomainHashes.value.contains(hash)) return false
            val updated = _privateDomainHashes.value - hash
            saveDomainHashes(updated)
            _privateDomainHashes.value = updated

            if (!_isHashOnlyMode.value) {
                val vaultRes = privateVault.readVault(_authGraceSeconds.value)
                if (vaultRes is PrivateVault.VaultReadResult.Success && vaultRes.domainNames.containsKey(hash)) {
                    val updatedNames = vaultRes.domainNames.toMutableMap()
                    updatedNames.remove(hash)
                    privateVault.writeVault(updatedNames, vaultRes.appNames, _authGraceSeconds.value)
                }
            }
        }
        return true
    }

    fun removePrivateDomainByName(rawDomain: String): Boolean {
        val hash = DomainNormalizer.normalizeAndHash(rawDomain) ?: return false
        return removePrivateDomainByHash(hash)
    }

    fun addPrivateApp(packageName: String, appLabel: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        synchronized(lock) {
            val updated = _privateBlockedApps.value + pkg
            savePrivateApps(updated)
            _privateBlockedApps.value = updated

            if (!_isHashOnlyMode.value) {
                val vaultRes = privateVault.readVault(_authGraceSeconds.value)
                if (vaultRes is PrivateVault.VaultReadResult.Success) {
                    val updatedApps = vaultRes.appNames.toMutableMap()
                    updatedApps[pkg] = appLabel
                    privateVault.writeVault(vaultRes.domainNames, updatedApps, _authGraceSeconds.value)
                }
            }
        }
        return true
    }

    fun removePrivateApp(packageName: String): Boolean {
        val pkg = packageName.trim()
        synchronized(lock) {
            if (!_privateBlockedApps.value.contains(pkg)) return false
            val updated = _privateBlockedApps.value - pkg
            savePrivateApps(updated)
            _privateBlockedApps.value = updated

            if (!_isHashOnlyMode.value) {
                val vaultRes = privateVault.readVault(_authGraceSeconds.value)
                if (vaultRes is PrivateVault.VaultReadResult.Success && vaultRes.appNames.containsKey(pkg)) {
                    val updatedApps = vaultRes.appNames.toMutableMap()
                    updatedApps.remove(pkg)
                    privateVault.writeVault(vaultRes.domainNames, updatedApps, _authGraceSeconds.value)
                }
            }
        }
        return true
    }

    fun setHashOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HASH_ONLY_MODE, enabled).apply()
        _isHashOnlyMode.value = enabled
        if (enabled) {
            privateVault.wipeVault()
        }
    }

    fun setStealthLabelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STEALTH_LABELS, enabled).apply()
        _stealthLabelsEnabled.value = enabled
    }

    fun setDiscreetAppIconEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISCREET_ICON, enabled).apply()
        _discreetAppIconEnabled.value = enabled
    }

    fun setAuthGraceSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 300)
        prefs.edit().putInt(KEY_AUTH_GRACE_SECONDS, clamped).apply()
        _authGraceSeconds.value = clamped
    }

    fun readVaultNames(): PrivateVault.VaultReadResult {
        if (_isHashOnlyMode.value) {
            return PrivateVault.VaultReadResult.Success(emptyMap(), emptyMap())
        }
        return privateVault.readVault(_authGraceSeconds.value)
    }

    private fun loadDomainHashes(): Set<Long> {
        val raw = prefs.getStringSet(KEY_DOMAIN_HASHES, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull(16) }.toSet()
    }

    private fun saveDomainHashes(hashes: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_DOMAIN_HASHES, hashes.map { java.lang.Long.toHexString(it) }.toSet())
            .apply()
    }

    private fun loadPrivateApps(): Set<String> {
        return prefs.getStringSet(KEY_PRIVATE_APPS, emptySet())?.toSet() ?: emptySet()
    }

    private fun savePrivateApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_PRIVATE_APPS, apps).apply()
    }

    companion object {
        const val PREFS_NAME = "focusforge_private_hashes"
        private const val KEY_DOMAIN_HASHES = "private_domain_hashes"
        private const val KEY_PRIVATE_APPS = "private_blocked_apps"
        private const val KEY_HASH_ONLY_MODE = "hash_only_mode"
        private const val KEY_STEALTH_LABELS = "stealth_labels_enabled"
        private const val KEY_DISCREET_ICON = "discreet_app_icon_enabled"
        private const val KEY_AUTH_GRACE_SECONDS = "auth_grace_seconds"
    }
}
