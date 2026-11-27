package com.focusforge.app.ui.blocker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.DisposableEffect
import com.focusforge.app.data.model.ScreenTimeOverview
import com.focusforge.app.ui.theme.Amber500
import com.focusforge.app.ui.theme.Emerald500
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.DailyScreenTimeSummary
import com.focusforge.app.data.model.AppCategoryMedium
import com.focusforge.app.data.local.dao.AppAttemptCount
import com.focusforge.app.data.local.entity.BlockedAppAttemptEntity
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import com.focusforge.app.ui.components.ForgeCard
import com.focusforge.app.ui.components.ForgeStatusPill
import com.focusforge.app.ui.theme.Emerald500
import com.focusforge.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsScreen(
    onNavigateToPermissions: () -> Unit,
    onNavigateToProtection: () -> Unit = {},
    viewModel: BlockedAppsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh permission status whenever user returns from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("App Shield") },
                actions = {
                    IconButton(onClick = onNavigateToProtection) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "DNS Protection"
                        )
                    }
                    IconButton(onClick = onNavigateToPermissions) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Shield Permissions"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Master Blocker Switch Header
            BlockerMasterHeader(
                isRunning = uiState.isBlockerRunning,
                activeProfile = uiState.activeProfile,
                isBypassActive = uiState.isBypassActive,
                bypassRemainingSeconds = uiState.bypassRemainingSeconds,
                needsPermissions = uiState.needsPermissions,
                onToggleService = { viewModel.toggleBlockerService() },
                onSelectProfile = { viewModel.setProfile(it) },
                onNavigateToPermissions = onNavigateToPermissions
            )

            // Tabs: Apps List, Screen Time, Intercepted Attempts, Bypass Logs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Apps (${uiState.apps.count { it.isBlocked }})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.loadScreenTime()
                    },
                    text = { Text("Screen Time") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Attempts (${uiState.totalAttemptsCount})") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Bypass") }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Search field
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search installed apps...") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    if (uiState.isLoadingApps) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = uiState.filteredApps,
                                key = { it.packageName }
                            ) { app ->
                                AppItemRow(
                                    app = app,
                                    onToggle = { viewModel.toggleAppBlocked(app) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    ScreenTimeTrendsView(
                        overview = uiState.screenTimeOverview,
                        isLoading = uiState.isLoadingScreenTime,
                        onRefresh = { viewModel.loadScreenTime() }
                    )
                }
                2 -> {
                    AttemptsLogView(
                        attempts = uiState.attempts,
                        groupedAttempts = uiState.attemptsGrouped
                    )
                }
                3 -> {
                    BypassLogsList(logs = uiState.bypassLogs)
                }
            }
        }
    }
}

@Composable
fun BlockerMasterHeader(
    isRunning: Boolean,
    activeProfile: BlockingProfile,
    isBypassActive: Boolean,
    bypassRemainingSeconds: Int,
    needsPermissions: Boolean,
    onToggleService: () -> Unit,
    onSelectProfile: (BlockingProfile) -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    ForgeCard(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        containerColor = if (isRunning)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else
            MaterialTheme.colorScheme.surface,
        borderColor = if (isRunning)
            Emerald500.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Shield else Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = if (isRunning) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRunning) "Shield: ${activeProfile.name}" else "Shield is OFF",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ForgeStatusPill(
                            label = if (isRunning) "ACTIVE" else "PAUSED",
                            active = isRunning
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRunning) "Enforcing profile rules" else "Select a profile or toggle ON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isRunning,
                    onCheckedChange = { onToggleService() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = Emerald500,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    thumbContent = if (isRunning) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Profile Mode Selector Row (OFF | RELAXED | STRICT)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BlockingProfile.entries.forEach { profile ->
                    val selected = activeProfile == profile
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectProfile(profile) },
                        label = {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            if (needsPermissions) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permissions Required",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Usage Access & Display Over Apps needed for shield to intercept.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        OutlinedButton(
                            onClick = onNavigateToPermissions,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (isBypassActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency bypass active: ${bypassRemainingSeconds}s remaining",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemRow(
    app: SelectableApp,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isBlocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            if (app.iconDrawable != null) {
                val bitmap = remember(app.iconDrawable) {
                    app.iconDrawable.toBitmap(120, 120).asImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (app.isExempt) "System Essential (Protected)" else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.isExempt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (app.isExempt) {
                Text(
                    text = "EXEMPT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Switch(
                    checked = app.isBlocked,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.error,
                        checkedTrackColor = MaterialTheme.colorScheme.errorContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
fun ScreenTimeTrendsView(
    overview: ScreenTimeOverview,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val days = overview.lastFiveDays
    var showLastFiveDays by remember { mutableStateOf(false) }
    var selectedDayIndex by remember(days.size) {
        mutableIntStateOf((days.size - 1).coerceAtLeast(0))
    }

    val activeDayEntry = days.getOrNull(selectedDayIndex)
    val summary = activeDayEntry?.summary ?: overview.todaySummary

    if (overview.todaySummary.totalScreenTimeMinutes == 0 &&
        overview.todaySummary.appUsageList.isEmpty() &&
        days.all { it.summary.totalScreenTimeMinutes == 0 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.QueryStats,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No screen time recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Initial Day (Today) Screen Time Hero Card
        item {
            val today = overview.todaySummary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedDayIndex == days.lastIndex || activeDayEntry == null) {
                                "TODAY'S PHONE SCREEN TIME"
                            } else {
                                "${activeDayEntry.dayLabel.uppercase()} (${activeDayEntry.dateLabel.uppercase()})"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (days.isNotEmpty()) {
                            androidx.compose.material3.Surface(
                                onClick = { showLastFiveDays = !showLastFiveDays },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (showLastFiveDays) "Hide 5 Days" else "Last 5 Days",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = TimeUtils.formatMinutesToDuration(summary.totalScreenTimeMinutes),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (summary.topApp != null) {
                            "Most used: ${summary.topApp.appName} (${TimeUtils.formatMinutesToDuration(summary.topApp.totalMinutes)})"
                        } else {
                            "Total active time on your phone"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    if (selectedDayIndex != days.lastIndex && today.totalScreenTimeMinutes > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Today so far: ${TimeUtils.formatMinutesToDuration(today.totalScreenTimeMinutes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // 2. Option to inspect Last 5 Days
        if (days.isNotEmpty()) {
            item {
                AnimatedVisibility(visible = showLastFiveDays) {
                    val maxFiveDayMin = days.maxOfOrNull { it.summary.totalScreenTimeMinutes }?.coerceAtLeast(1) ?: 1
                    val avgFiveDayMin = (days.sumOf { it.summary.totalScreenTimeMinutes } / days.size)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LAST 5 DAYS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Avg ${TimeUtils.formatMinutesToDuration(avgFiveDayMin)}/day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(126.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                days.forEachIndexed { index, entry ->
                                    val mins = entry.summary.totalScreenTimeMinutes
                                    val fraction = (mins.toFloat() / maxFiveDayMin.toFloat()).coerceIn(0.06f, 1f)
                                    val isSelected = index == selectedDayIndex
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedDayIndex = index }
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Text(
                                            text = TimeUtils.formatMinutesToDuration(mins),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.55f)
                                                .height((72.dp * fraction).coerceAtLeast(6.dp))
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                .background(
                                                    if (isSelected) Emerald500
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = entry.dayLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Overall Max Usage Card
        if (overview.maxDayEntry != null || overview.overallMaxApp != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "OVERALL MAX USAGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Amber500
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            overview.maxDayEntry?.let { peakDay ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Peak Day",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = TimeUtils.formatMinutesToDuration(peakDay.summary.totalScreenTimeMinutes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${peakDay.dayLabel}, ${peakDay.dateLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            overview.overallMaxApp?.let { maxApp ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Top App Overall",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = TimeUtils.formatMinutesToDuration(maxApp.totalMinutes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = maxApp.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category / Medium Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "USAGE BY MEDIUM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    summary.categoryBreakdown.forEach { categoryStat ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = categoryStat.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${categoryStat.totalMinutes}m (${(categoryStat.percentageOfTotal * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { categoryStat.percentageOfTotal.coerceIn(0.02f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = when (categoryStat.category) {
                                    AppCategoryMedium.SOCIAL -> MaterialTheme.colorScheme.error
                                    AppCategoryMedium.MEDIA_ENTERTAINMENT -> Amber500
                                    AppCategoryMedium.PRODUCTIVITY_EDUCATION -> Emerald500
                                    AppCategoryMedium.GAMING -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                        }
                    }
                }
            }
        }

        // Top App Usage List
        item {
            Text(
                text = "Apps Screen Time Breakdown",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(summary.appUsageList.take(15)) { appStat ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appStat.iconDrawable != null) {
                        val bitmap = remember(appStat.iconDrawable) {
                            appStat.iconDrawable.toBitmap(100, 100).asImageBitmap()
                        }
                        Image(
                            bitmap = bitmap,
                            contentDescription = appStat.appName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appStat.appName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = when (appStat.categoryMedium) {
                                AppCategoryMedium.SOCIAL -> "Social"
                                AppCategoryMedium.MEDIA_ENTERTAINMENT -> "Video / Media"
                                AppCategoryMedium.PRODUCTIVITY_EDUCATION -> "Productivity"
                                AppCategoryMedium.GAMING -> "Gaming"
                                AppCategoryMedium.NEWS -> "News"
                                AppCategoryMedium.OTHER -> "General"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Text(
                        text = "${appStat.totalMinutes}m",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun AttemptsLogView(
    attempts: List<BlockedAppAttemptEntity>,
    groupedAttempts: List<AppAttemptCount>
) {
    if (attempts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No blocked app attempts recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Any time a blocked app is opened, it will be logged here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        val totalAttempts = attempts.size
        val maxAppCount = groupedAttempts.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Overall Attempts Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "OVERALL INTERCEPTED ATTEMPTS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$totalAttempts blocked ${if (totalAttempts == 1) "attempt" else "attempts"}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        groupedAttempts.firstOrNull()?.let { top ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Most attempted: ${top.appName} (${top.count}×)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Overall Attempts by App",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(groupedAttempts) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.appName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${group.count}×",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (group.count.toFloat() / maxAppCount.toFloat()).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BypassLogsList(logs: List<EmergencyBypassLogEntity>) {
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No emergency unlocks recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emergency 60s Unlock",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = TimeUtils.formatEpochToDateTime(log.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
