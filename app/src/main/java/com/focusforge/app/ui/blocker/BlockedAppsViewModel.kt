package com.focusforge.app.ui.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.DailyScreenTimeSummary
import com.focusforge.app.data.repository.ScreenTimeRepository
import com.focusforge.app.data.repository.BlockedAppRepository
import com.focusforge.app.data.repository.PolicyRepository
import com.focusforge.app.data.repository.UserPreferencesRepository
import com.focusforge.app.enforcement.ProfileScheduler
import com.focusforge.app.service.AppBlockerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SelectableApp(
    val packageName: String,
    val appName: String,
    val iconDrawable: Drawable?,
    val isBlocked: Boolean = false,
    val isExempt: Boolean = false
)

data class BlockerUiState(
    val isBlockerRunning: Boolean = false,
    val activeProfile: BlockingProfile = BlockingProfile.OFF,
    val isBypassActive: Boolean = false,
    val bypassRemainingSeconds: Int = 0,
    val hasUsageAccess: Boolean = true,
    val hasOverlayAccess: Boolean = true,
    val apps: List<SelectableApp> = emptyList(),
    val filteredApps: List<SelectableApp> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = true,
    val bypassLogs: List<EmergencyBypassLogEntity> = emptyList(),
    val attempts: List<com.focusforge.app.data.local.entity.BlockedAppAttemptEntity> = emptyList(),
    val attemptsGrouped: List<com.focusforge.app.data.local.dao.AppAttemptCount> = emptyList(),
    val totalAttemptsCount: Int = 0,
    val screenTimeSummary: DailyScreenTimeSummary = DailyScreenTimeSummary(0L, 0L, emptyList(), emptyList(), null),
    val isLoadingScreenTime: Boolean = false
) {
    val needsPermissions: Boolean
        get() = !hasUsageAccess || !hasOverlayAccess
}

@HiltViewModel
class BlockedAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedAppRepository: BlockedAppRepository,
    private val screenTimeRepository: ScreenTimeRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val policyRepository: PolicyRepository,
    private val profileScheduler: ProfileScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockerUiState())
    val uiState: StateFlow<BlockerUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        loadInstalledApps()
        observeBlockedApps()
        observeBypassLogs()
        observeAttempts()
        loadScreenTime()
        observeBlockerServiceState()
        observePolicy()
    }

    private fun observePolicy() {
        viewModelScope.launch {
            policyRepository.policySnapshot.collect { snapshot ->
                _uiState.value = _uiState.value.copy(
                    activeProfile = snapshot.activeProfile
                )
            }
        }
    }

    fun loadScreenTime() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingScreenTime = true)
            val summary = screenTimeRepository.getTodayScreenTimeSummary()
            _uiState.value = _uiState.value.copy(
                screenTimeSummary = summary,
                isLoadingScreenTime = false
            )
        }
    }

    private fun observeAttempts() {
        viewModelScope.launch {
            blockedAppRepository.getAllAttempts().collect { list ->
                _uiState.value = _uiState.value.copy(
                    attempts = list,
                    totalAttemptsCount = list.size
                )
            }
        }
        viewModelScope.launch {
            blockedAppRepository.getAttemptsGroupedByApp().collect { grouped ->
                _uiState.value = _uiState.value.copy(attemptsGrouped = grouped)
            }
        }
    }

    fun checkPermissions() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps?.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        val hasUsage = mode == android.app.AppOpsManager.MODE_ALLOWED
        val hasOverlay = android.provider.Settings.canDrawOverlays(context)

        _uiState.value = _uiState.value.copy(
            hasUsageAccess = hasUsage,
            hasOverlayAccess = hasOverlay
        )
    }

    private fun observeBlockerServiceState() {
        viewModelScope.launch {
            combine(
                AppBlockerService.isServiceRunning,
                AppBlockerService.isBypassActive,
                AppBlockerService.bypassRemainingSeconds
            ) { isRunning, isBypass, remainingSec ->
                Triple(isRunning, isBypass, remainingSec)
            }.collect { (isRunning, isBypass, remainingSec) ->
                _uiState.value = _uiState.value.copy(
                    isBlockerRunning = isRunning,
                    isBypassActive = isBypass,
                    bypassRemainingSeconds = remainingSec
                )
            }
        }
    }

    private fun observeBlockedApps() {
        viewModelScope.launch {
            blockedAppRepository.getAllApps().collect { blockedEntities ->
                val blockedSet = blockedEntities.filter { it.isBlocked }.map { it.packageName }.toSet()
                val currentApps = _uiState.value.apps.map { app ->
                    app.copy(isBlocked = blockedSet.contains(app.packageName))
                }
                _uiState.value = _uiState.value.copy(
                    apps = currentApps,
                    filteredApps = filterApps(currentApps, _uiState.value.searchQuery)
                )
            }
        }
    }

    private fun observeBypassLogs() {
        viewModelScope.launch {
            blockedAppRepository.getBypassLogs().collect { logs ->
                _uiState.value = _uiState.value.copy(bypassLogs = logs)
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)

            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

            val activeBlockedSet = blockedAppRepository.getActiveBlockedAppsSync()
                .map { it.packageName }.toSet()

            val appList = mutableListOf<SelectableApp>()
            val seenPackages = HashSet<String>()

            for (info in resolveInfos) {
                val pkgName = info.activityInfo.packageName
                if (seenPackages.add(pkgName)) {
                    // Skip ourselves
                    if (pkgName == context.packageName) continue

                    val isExempt = AppBlockerService.HARDCODED_EXEMPT_PACKAGES.contains(pkgName)
                    val label = info.loadLabel(pm).toString()
                    val icon = try {
                        info.loadIcon(pm)
                    } catch (_: Exception) {
                        null
                    }

                    appList.add(
                        SelectableApp(
                            packageName = pkgName,
                            appName = label,
                            iconDrawable = icon,
                            isBlocked = activeBlockedSet.contains(pkgName),
                            isExempt = isExempt
                        )
                    )
                }
            }

            appList.sortBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    apps = appList,
                    filteredApps = filterApps(appList, _uiState.value.searchQuery),
                    isLoadingApps = false
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filterApps(_uiState.value.apps, query)
        )
    }

    private fun filterApps(apps: List<SelectableApp>, query: String): List<SelectableApp> {
        if (query.isBlank()) return apps
        return apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    fun toggleAppBlocked(app: SelectableApp) {
        viewModelScope.launch(Dispatchers.IO) {
            val newBlockedState = !app.isBlocked
            blockedAppRepository.setAppBlocked(
                packageName = app.packageName,
                appName = app.appName,
                isBlocked = newBlockedState
            )
        }
    }

    fun setProfile(profile: BlockingProfile) {
        viewModelScope.launch {
            profileScheduler.switchProfile(profile)
            if (profile == BlockingProfile.OFF) {
                AppBlockerService.stop(context)
                saveBlockerPref(false)
            } else {
                AppBlockerService.start(context)
                saveBlockerPref(true)
            }
        }
    }

    fun toggleBlockerService() {
        val currentlyRunning = _uiState.value.isBlockerRunning
        if (currentlyRunning) {
            setProfile(BlockingProfile.OFF)
        } else {
            setProfile(BlockingProfile.STRICT)
        }
    }

    private fun saveBlockerPref(enabled: Boolean) {
        val prefs = context.getSharedPreferences("focusforge_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("app_blocker_enabled", enabled).apply()
    }
}
