package com.focusforge.app.ui.home

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.ui.bot.ForgeBotGuideDialog
import com.focusforge.app.ui.components.ForgeCard
import com.focusforge.app.ui.components.ForgeGradientCard
import com.focusforge.app.ui.components.ForgeStatusPill
import com.focusforge.app.ui.theme.Amber500
import com.focusforge.app.ui.theme.Emerald100
import com.focusforge.app.ui.theme.Emerald500
import com.focusforge.app.ui.theme.Emerald600
import com.focusforge.app.ui.theme.Emerald700
import com.focusforge.app.ui.theme.Slate850
import com.focusforge.app.util.FocusShareCardGenerator
import com.focusforge.app.util.ShareCardOptions
import com.focusforge.app.util.ShareTimeframe
import com.focusforge.app.util.TimeUtils

@Composable
fun HomeScreen(
    onNavigateToTimer: () -> Unit,
    onNavigateToShield: () -> Unit,
    onNavigateToProtection: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var showSharePreview by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshScreenTime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showEditNameDialog = true }
                        .padding(end = 12.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (uiState.userName.isNotBlank()) "Welcome, ${uiState.userName}" else "FocusForge",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Name",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Local-First Digital Sanctuary",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { showSharePreview = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Focus Report",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = { viewModel.openBotGuide() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Focus Guide",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hero Focus Card with gradient & direct Start CTA
        item {
            val animatedRatio by animateFloatAsState(
                targetValue = uiState.deepWorkRatio,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "heroRatio"
            )

            ForgeGradientCard(
                brush = Brush.linearGradient(
                    colors = listOf(Emerald700, Emerald600, Slate850)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TODAY'S FOCUS",
                            style = MaterialTheme.typography.labelLarge,
                            color = Emerald100,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${uiState.todaySessions.size} session(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = TimeUtils.formatMinutesToDuration(uiState.todayDeepMinutes),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Deep work logged today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = TimeUtils.formatMinutesToDuration(uiState.todayTotalMinutes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Total focus",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { animatedRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Emerald100,
                        trackColor = Color.White.copy(alpha = 0.18f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendDotText("Deep: ${TimeUtils.formatMinutesToDuration(uiState.todayDeepMinutes)}", Emerald100, Color.White.copy(alpha = 0.9f))
                        LegendDotText("Shallow: ${TimeUtils.formatMinutesToDuration(uiState.shallowMinutes)}", Amber500, Color.White.copy(alpha = 0.9f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onNavigateToTimer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Emerald700
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Focus Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Quick Protection Cards: App Shield & Web & DNS (pure Emerald + Slate theme)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ForgeCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToShield,
                    borderColor = if (uiState.isBlockerActive) Emerald500.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.isBlockerActive) Icons.Default.Shield else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (uiState.isBlockerActive) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            ForgeStatusPill(
                                label = if (uiState.isBlockerActive) uiState.activeProfile.name else "OFF",
                                active = uiState.isBlockerActive
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "App Shield",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.blockedAppCount} guarded apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ForgeCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToProtection,
                    borderColor = if (uiState.isDnsRunning) Emerald500.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = if (uiState.isDnsRunning) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            ForgeStatusPill(
                                label = if (uiState.isDnsRunning) "ON" else "OFF",
                                active = uiState.isDnsRunning
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Web & DNS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.todayBlockedSitesCount} blocked today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Interactive 7-Day Focus Chart
        item {
            SevenDayFocusChartCard(
                days = uiState.dailyFocusHistory,
                weekTotalMinutes = uiState.past7DaysTotalMinutes,
                weekDeepMinutes = uiState.past7DaysDeepMinutes
            )
        }

        // Today's Screen Time Card
        item {
            ForgeCard(onClick = onNavigateToShield) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Today's Screen Time",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.topScreenTimeAppName?.let { "Most used: $it" } ?: "Tap to view app breakdown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = TimeUtils.formatMinutesToDuration(uiState.todayScreenTimeMinutes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Today's Sessions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${uiState.todaySessions.size} logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.todaySessions.isEmpty()) {
            item {
                ForgeCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "“Deep work is the ability to focus without distraction on a cognitively demanding task.”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "— Cal Newport, Deep Work",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            items(uiState.todaySessions, key = { it.id }) { session ->
                TodaySessionItem(session = session)
            }
        }
    }

    if (uiState.showBotGuide) {
        ForgeBotGuideDialog(
            initialUserName = uiState.userName,
            onSaveUserName = viewModel::saveUserName,
            onCompleteOnboarding = viewModel::completeOnboarding,
            onDismiss = viewModel::dismissBotGuide,
            onNavigateToTimer = onNavigateToTimer
        )
    }

    if (showSharePreview) {
        var shareName by remember(uiState.userName) { mutableStateOf(uiState.userName) }
        var shareOptions by remember { mutableStateOf(ShareCardOptions()) }
        var backgroundPhoto by remember { mutableStateOf<Bitmap?>(null) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                backgroundPhoto = FocusShareCardGenerator.decodePhotoFromUri(context, uri)
            }
        }

        val timeframeSessions = when (shareOptions.timeframe) {
            ShareTimeframe.TODAY -> uiState.todaySessions
            ShareTimeframe.WEEK_7 -> uiState.past30DaysSessions.filter {
                it.startTime >= System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
            }
            ShareTimeframe.MONTH_30 -> uiState.past30DaysSessions
        }

        var selectedSessionIds by remember(shareOptions.timeframe, timeframeSessions) {
            mutableStateOf(timeframeSessions.map { it.id }.toSet())
        }

        val selectedSessions = timeframeSessions.filter { it.id in selectedSessionIds }
        val heroTotal = when (shareOptions.timeframe) {
            ShareTimeframe.TODAY -> uiState.todayTotalMinutes
            ShareTimeframe.WEEK_7 -> uiState.past7DaysTotalMinutes
            ShareTimeframe.MONTH_30 -> uiState.past30DaysTotalMinutes
        }
        val heroDeep = when (shareOptions.timeframe) {
            ShareTimeframe.TODAY -> uiState.todayDeepMinutes
            ShareTimeframe.WEEK_7 -> uiState.past7DaysDeepMinutes
            ShareTimeframe.MONTH_30 -> uiState.past30DaysDeepMinutes
        }
        val heroShallow = (heroTotal - heroDeep).coerceAtLeast(0)

        val previewBitmap = remember(
            shareName,
            shareOptions,
            heroTotal,
            heroDeep,
            heroShallow,
            uiState.past7DaysTotalMinutes,
            uiState.past7DaysDeepMinutes,
            selectedSessions,
            backgroundPhoto
        ) {
            FocusShareCardGenerator.generateBitmap(
                context = context,
                userName = shareName,
                heroTotalMinutes = heroTotal,
                heroDeepMinutes = heroDeep,
                heroShallowMinutes = heroShallow,
                weekTotalMinutes = uiState.past7DaysTotalMinutes,
                weekDeepMinutes = uiState.past7DaysDeepMinutes,
                selectedSessions = selectedSessions,
                options = shareOptions,
                backgroundPhoto = backgroundPhoto
            )
        }

        AlertDialog(
            onDismissRequest = { showSharePreview = false },
            title = { Text("Share Focus Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Focus Report Preview",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )

                    val chipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Timeframe & Background",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ShareTimeframe.entries.forEach { tf ->
                            FilterChip(
                                selected = shareOptions.timeframe == tf,
                                onClick = { shareOptions = shareOptions.copy(timeframe = tf) },
                                label = { Text(tf.label) },
                                colors = chipColors
                            )
                        }
                        FilterChip(
                            selected = backgroundPhoto != null,
                            onClick = {
                                if (backgroundPhoto != null) {
                                    backgroundPhoto = null
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(if (backgroundPhoto != null) "Clear Photo" else "Add Photo") },
                            colors = chipColors
                        )
                    }

                    Text(
                        text = "Visible on card",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = shareOptions.showName,
                            onClick = { shareOptions = shareOptions.copy(showName = !shareOptions.showName) },
                            label = { Text("Name") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = shareOptions.showSections,
                            onClick = { shareOptions = shareOptions.copy(showSections = !shareOptions.showSections) },
                            label = { Text("Journeys") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = shareOptions.showWeeklyTotal,
                            onClick = { shareOptions = shareOptions.copy(showWeeklyTotal = !shareOptions.showWeeklyTotal) },
                            label = { Text("7-Day") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = shareOptions.showRating,
                            onClick = { shareOptions = shareOptions.copy(showRating = !shareOptions.showRating) },
                            label = { Text("Rating") },
                            colors = chipColors
                        )
                    }

                    if (shareOptions.showSections && timeframeSessions.isNotEmpty()) {
                        Text(
                            text = "Select journeys to share",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            timeframeSessions.take(8).forEach { session ->
                                val isIncluded = session.id in selectedSessionIds
                                FilterChip(
                                    selected = isIncluded,
                                    onClick = {
                                        selectedSessionIds = if (isIncluded) {
                                            selectedSessionIds - session.id
                                        } else {
                                            selectedSessionIds + session.id
                                        }
                                    },
                                    label = {
                                        Text("${session.subject} (${session.durationMin}m)", maxLines = 1)
                                    },
                                    colors = chipColors
                                )
                            }
                        }
                    }

                    if (shareOptions.showName) {
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = { shareName = it },
                            label = { Text("Your Name on Card") },
                            placeholder = { Text("e.g. Vijay") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (shareName.isNotBlank() && shareName != uiState.userName) {
                            viewModel.saveUserName(shareName.trim())
                        }
                        showSharePreview = false
                        FocusShareCardGenerator.shareBitmap(context, previewBitmap)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSharePreview = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showEditNameDialog) {
        var nameDraft by remember(uiState.userName) { mutableStateOf(uiState.userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Your Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveUserName(nameDraft.trim())
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LegendDotText(text: String, dotColor: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

@Composable
private fun SevenDayFocusChartCard(
    days: List<DailyFocusStat>,
    weekTotalMinutes: Int,
    weekDeepMinutes: Int
) {
    if (days.isEmpty()) return
    var selectedIdx by remember(days.size) { mutableIntStateOf(days.lastIndex) }
    val selectedDay = days.getOrElse(selectedIdx) { days.last() }
    val maxMinutes = (days.maxOfOrNull { it.totalMinutes } ?: 0).coerceAtLeast(30)

    ForgeCard(
        borderColor = Emerald500.copy(alpha = 0.28f),
        cornerRadius = 22.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "7-DAY FOCUS TREND",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap any bar to inspect that day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = TimeUtils.formatMinutesToDuration(weekTotalMinutes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${TimeUtils.formatMinutesToDuration(weekDeepMinutes)} deep",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { idx, day ->
                    val isSelected = idx == selectedIdx
                    val deepRatio = day.deepMinutes.toFloat() / maxMinutes.toFloat()
                    val shallowRatio = day.shallowMinutes.toFloat() / maxMinutes.toFloat()
                    val animatedDeepHeight by animateDpAsState(
                        targetValue = if (day.deepMinutes > 0) (76f * deepRatio).dp.coerceAtLeast(6.dp) else 0.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "deepBar"
                    )
                    val animatedShallowHeight by animateDpAsState(
                        targetValue = if (day.shallowMinutes > 0) (76f * shallowRatio).dp.coerceAtLeast(6.dp) else 0.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "shallowBar"
                    )
                    val highlightBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
                        label = "barHighlight"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(highlightBg)
                            .clickable { selectedIdx = idx }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(78.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (day.shallowMinutes > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(animatedShallowHeight)
                                            .background(Amber500)
                                    )
                                }
                                if (day.deepMinutes > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(animatedDeepHeight)
                                            .background(Emerald500)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = day.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${selectedDay.shortLabel} • ${selectedDay.dateLabel}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedDay.sessionCount} session(s) • ${TimeUtils.formatMinutesToDuration(selectedDay.deepMinutes)} deep, ${TimeUtils.formatMinutesToDuration(selectedDay.shallowMinutes)} shallow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald500.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = TimeUtils.formatMinutesToDuration(selectedDay.totalMinutes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Emerald500,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySessionItem(session: Session) {
    ForgeCard(cornerRadius = 16.dp, elevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isDeep = session.type == SessionType.DEEP
                    Surface(
                        color = if (isDeep) Emerald500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeep) Icons.Default.Bolt else Icons.Default.Coffee,
                                contentDescription = null,
                                tint = if (isDeep) Emerald600 else Amber500,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDeep) "DEEP WORK" else "SHALLOW WORK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDeep) Emerald600 else Amber500
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = TimeUtils.formatEpochToTimeOnly(session.startTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = session.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (session.topic.isNotBlank()) {
                    Text(
                        text = session.topic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${session.durationMin}m",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${session.focusRating}/5",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
