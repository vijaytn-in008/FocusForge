package com.focusforge.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.data.repository.BlockedAppRepository
import com.focusforge.app.data.repository.PolicyStore
import com.focusforge.app.data.repository.ScreenTimeRepository
import com.focusforge.app.data.repository.SessionRepository
import com.focusforge.app.data.repository.UserPreferencesRepository
import com.focusforge.app.enforcement.DnsVpnService
import com.focusforge.app.service.AppBlockerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DailyFocusStat(
    val dayIndexFromOldest: Int,
    val shortLabel: String,
    val dateLabel: String,
    val deepMinutes: Int,
    val shallowMinutes: Int,
    val sessionCount: Int
) {
    val totalMinutes: Int get() = deepMinutes + shallowMinutes
}

data class HomeUiState(
    val userName: String = "",
    val isOnboardingCompleted: Boolean = true,
    val showBotGuide: Boolean = false,
    val todayDeepMinutes: Int = 0,
    val todayTotalMinutes: Int = 0,
    val todaySessions: List<Session> = emptyList(),
    val past7DaysTotalMinutes: Int = 0,
    val past7DaysDeepMinutes: Int = 0,
    val past7DaysSessionCount: Int = 0,
    val past30DaysTotalMinutes: Int = 0,
    val past30DaysDeepMinutes: Int = 0,
    val past30DaysSessions: List<Session> = emptyList(),
    val dailyFocusHistory: List<DailyFocusStat> = emptyList(),
    val todayScreenTimeMinutes: Int = 0,
    val topScreenTimeAppName: String? = null,
    val isBlockerActive: Boolean = false,
    val activeProfile: BlockingProfile = BlockingProfile.OFF,
    val isDnsRunning: Boolean = false,
    val todayBlockedSitesCount: Int = 0,
    val blockedAppCount: Int = 0,
    val isLoading: Boolean = false
) {
    val shallowMinutes: Int
        get() = (todayTotalMinutes - todayDeepMinutes).coerceAtLeast(0)

    val deepWorkRatio: Float
        get() = if (todayTotalMinutes > 0) todayDeepMinutes.toFloat() / todayTotalMinutes.toFloat() else 0f

    val greetingName: String
        get() = userName.ifBlank { "Scholar" }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    blockedAppRepository: BlockedAppRepository,
    private val screenTimeRepository: ScreenTimeRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    policyStore: PolicyStore
) : ViewModel() {

    private val _manualShowBotGuide = MutableStateFlow<Boolean?>(null)
    private val _screenTimeState = MutableStateFlow<Pair<Int, String?>>(0 to null)

    private val sevenDaysAgoMillis: Long
        get() = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val thirtyDaysAgoMillis: Long
        get() = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -29)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    init {
        refreshScreenTime()
    }

    fun refreshScreenTime() {
        viewModelScope.launch {
            try {
                val summary = screenTimeRepository.getTodayScreenTimeSummary()
                _screenTimeState.value = summary.totalScreenTimeMinutes to summary.topApp?.appName
            } catch (_: Exception) {
                // Keep existing state if UsageStats not granted
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            sessionRepository.getTodayDeepMinutes(),
            sessionRepository.getTodayTotalMinutes(),
            sessionRepository.getTodaySessions(),
            sessionRepository.getSessionsSince(thirtyDaysAgoMillis)
        ) { deep, total, sessions, monthSessions ->
            SessionBundle(deep, total, sessions, monthSessions)
        },
        combine(
            userPreferencesRepository.getUserName(),
            userPreferencesRepository.isOnboardingCompleted(),
            AppBlockerService.isServiceRunning,
            blockedAppRepository.getActiveBlockedApps()
        ) { name, onboard, running, blockedList ->
            Quad(name, onboard, running, blockedList.size)
        },
        combine(
            policyStore.policySnapshot,
            DnsVpnService.isVpnRunning,
            policyStore.todayBlockedCount,
            _screenTimeState
        ) { snapshot, vpnRunning, blockedSites, screenTime ->
            ProtectionBundle(snapshot.activeProfile, vpnRunning, blockedSites, screenTime.first, screenTime.second)
        },
        _manualShowBotGuide
    ) { sess, (userName, onboardingCompleted, isBlockerRunning, blockedCount), prot, isManual ->
        val showGuide = isManual ?: (!onboardingCompleted)
        val sevenDaysCutoff = sevenDaysAgoMillis
        val weekSessions = sess.weekSessions.filter { it.startTime >= sevenDaysCutoff }
        val weekTotal = weekSessions.sumOf { it.durationMin }
        val weekDeep = weekSessions.filter { it.type == SessionType.DEEP }.sumOf { it.durationMin }
        val monthTotal = sess.weekSessions.sumOf { it.durationMin }
        val monthDeep = sess.weekSessions.filter { it.type == SessionType.DEEP }.sumOf { it.durationMin }
        val dailyHistory = build7DayStats(weekSessions)

        HomeUiState(
            userName = userName,
            isOnboardingCompleted = onboardingCompleted,
            showBotGuide = showGuide,
            todayDeepMinutes = sess.deep,
            todayTotalMinutes = sess.total,
            todaySessions = sess.sessions,
            past7DaysTotalMinutes = weekTotal,
            past7DaysDeepMinutes = weekDeep,
            past7DaysSessionCount = weekSessions.size,
            past30DaysTotalMinutes = monthTotal,
            past30DaysDeepMinutes = monthDeep,
            past30DaysSessions = sess.weekSessions,
            dailyFocusHistory = dailyHistory,
            todayScreenTimeMinutes = prot.screenTimeMin,
            topScreenTimeAppName = prot.topAppName,
            isBlockerActive = isBlockerRunning,
            activeProfile = prot.profile,
            isDnsRunning = prot.vpnRunning,
            todayBlockedSitesCount = prot.blockedSites,
            blockedAppCount = blockedCount,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun saveUserName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserName(name)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
            _manualShowBotGuide.value = false
        }
    }

    fun openBotGuide() {
        _manualShowBotGuide.value = true
    }

    fun dismissBotGuide() {
        _manualShowBotGuide.value = false
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }

    private data class SessionBundle(
        val deep: Int,
        val total: Int,
        val sessions: List<Session>,
        val weekSessions: List<Session>
    )

    private data class ProtectionBundle(
        val profile: BlockingProfile,
        val vpnRunning: Boolean,
        val blockedSites: Int,
        val screenTimeMin: Int,
        val topAppName: String?
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        /**
         * Builds a 7-day breakdown (oldest to Today) from [sessions] without altering any stored data.
         */
        fun build7DayStats(
            sessions: List<Session>,
            nowMillis: Long = System.currentTimeMillis()
        ): List<DailyFocusStat> {
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -6)

            val stats = mutableListOf<DailyFocusStat>()
            for (i in 0..6) {
                val dayStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val dayEnd = cal.timeInMillis

                val daySessions = sessions.filter { it.startTime in dayStart until dayEnd }
                val deep = daySessions.filter { it.type == SessionType.DEEP }.sumOf { it.durationMin }
                val shallow = daySessions.filter { it.type == SessionType.SHALLOW }.sumOf { it.durationMin }
                val shortLabel = if (i == 6) "Today" else dayFmt.format(Date(dayStart))
                val dateLabel = dateFmt.format(Date(dayStart))

                stats.add(
                    DailyFocusStat(
                        dayIndexFromOldest = i,
                        shortLabel = shortLabel,
                        dateLabel = dateLabel,
                        deepMinutes = deep,
                        shallowMinutes = shallow,
                        sessionCount = daySessions.size
                    )
                )
            }
            return stats
        }
    }
}
