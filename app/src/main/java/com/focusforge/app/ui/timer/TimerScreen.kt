package com.focusforge.app.ui.timer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.service.TimerStatus
import com.focusforge.app.ui.components.ForgeCard
import com.focusforge.app.ui.theme.Amber500
import com.focusforge.app.ui.theme.Emerald500
import com.focusforge.app.ui.theme.Emerald600
import com.focusforge.app.ui.theme.Rose500
import com.focusforge.app.ui.theme.Rose600
import com.focusforge.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val timerState = uiState.timerServiceState
    val isTimerActive = timerState.status == TimerStatus.RUNNING || timerState.status == TimerStatus.PAUSED

    var showStopConfirmation by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPendingJourney()
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Screen Title
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isTimerActive) timerState.subject else "Focus Timer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isTimerActive && timerState.topic.isNotBlank()) {
                    Text(
                        text = timerState.topic,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!isTimerActive) {
                    Text(
                        text = "Cal Newport Deep Work Timer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Pending Unsaved Journey Banner
        if (!isTimerActive && uiState.pendingJourney != null) {
            item {
                val pending = uiState.pendingJourney!!
                Card(
                    onClick = { viewModel.openPendingJourney() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Saved for Later • ${pending.durationMin}m ${pending.subject}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap to record & save your journey or discard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Record Journey",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Circular Timer Display
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .padding(12.dp)
            ) {
                val progress = if (isTimerActive && timerState.totalSeconds > 0) {
                    (timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat())
                } else {
                    1f
                }

                val animatedProgress by animateFloatAsState(targetValue = progress, label = "timerProgress")
                val activeColor = if (timerState.sessionType == SessionType.DEEP) Emerald500 else Amber500
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthPx = 14.dp.toPx()
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val displaySeconds = if (isTimerActive) timerState.remainingSeconds else uiState.durationSecondsTotal
                    Text(
                        text = TimeUtils.formatSecondsToTime(displaySeconds),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = when (timerState.status) {
                        TimerStatus.RUNNING -> if (timerState.sessionType == SessionType.DEEP) "DEEP FOCUS" else "SHALLOW WORK"
                        TimerStatus.PAUSED -> "PAUSED"
                        TimerStatus.COMPLETED -> "COMPLETED"
                        TimerStatus.IDLE -> "${TimeUtils.formatSecondsToTime(uiState.durationSecondsTotal)} GOAL"
                    }

                    Surface(
                        color = if (isTimerActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isTimerActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Native Scroll-Wheel Timer Picker (HRS : MIN : SEC) + Quick Presets
        if (!isTimerActive) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimerWheelPicker(
                        totalSeconds = uiState.durationSecondsTotal,
                        onTotalSecondsChanged = viewModel::onTotalSecondsChanged
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(15, 25, 50, 90).forEach { mins ->
                            val isSelected = uiState.durationSecondsTotal == mins * 60
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onDurationChanged(mins) },
                                label = {
                                    Text(
                                        text = "${mins}m",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Controls (Play / Pause / Stop)
        item {
            if (isTimerActive) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Stop button
                    IconButton(
                        onClick = { showStopConfirmation = true },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Rose500.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Rose600,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Pause / Resume button
                    Button(
                        onClick = {
                            if (timerState.status == TimerStatus.RUNNING) {
                                viewModel.pauseTimer(context)
                            } else {
                                viewModel.resumeTimer(context)
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (timerState.status == TimerStatus.RUNNING) Amber500 else Emerald600
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (timerState.status == TimerStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (timerState.status == TimerStatus.RUNNING) "Pause" else "Resume",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.startTimer(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Begin Focus Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Configuration Section (When Idle)
        if (!isTimerActive) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "SESSION CONFIGURATION",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Subject & Topic inputs
                        OutlinedTextField(
                            value = uiState.subjectInput,
                            onValueChange = viewModel::onSubjectChanged,
                            label = { Text("Subject / Discipline") },
                            placeholder = { Text("e.g. Physics, Law, Operating Systems") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = uiState.topicInput,
                            onValueChange = viewModel::onTopicChanged,
                            label = { Text("Topic / Specific Goal") },
                            placeholder = { Text("e.g. Practice Chapter 4 problems") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Session Type Toggle
                        Column {
                            Text(
                                text = "Focus Type",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val isDeep = uiState.sessionType == SessionType.DEEP

                                FocusTypeGlassCard(
                                    title = "Deep Work",
                                    subtitle = "Zero-distraction focus",
                                    icon = Icons.Default.CenterFocusStrong,
                                    accentColor = Emerald500,
                                    selectedTextColor = Emerald600,
                                    isSelected = isDeep,
                                    onClick = { viewModel.onSessionTypeChanged(SessionType.DEEP) },
                                    modifier = Modifier.weight(1f)
                                )

                                FocusTypeGlassCard(
                                    title = "Shallow Work",
                                    subtitle = "Routine & admin tasks",
                                    icon = Icons.Default.Checklist,
                                    accentColor = Amber500,
                                    selectedTextColor = Amber500,
                                    isSelected = !isDeep,
                                    onClick = { viewModel.onSessionTypeChanged(SessionType.SHALLOW) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Stop Confirmation Dialog
    if (showStopConfirmation) {
        var earlyRating by remember { androidx.compose.runtime.mutableIntStateOf(4) }
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = {
                Text(
                    text = "End Focus Session Early",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "Would you like to save the elapsed study minutes or discard this session?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Rate Focus Quality (1 to 5):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { star ->
                            val isFilled = star <= earlyRating
                            Icon(
                                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star $star",
                                tint = Amber500,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { earlyRating = star }
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmation = false
                        viewModel.stopAndSaveSession(context, rating = earlyRating)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("Save Minutes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStopConfirmation = false
                        viewModel.stopTimer(context, shouldSavePartial = false)
                    }
                ) {
                    Text("Discard", color = Rose600)
                }
            }
        )
    }

    // Full-Screen Completion Alert (Record & Save Journey / Save Later / Discard)
    if (uiState.showRatingDialog) {
        TimerCompletionFullScreen(
            subject = uiState.completedSubject,
            durationMinutes = uiState.completedDurationMin,
            sessionType = uiState.completedSessionType,
            initialNote = uiState.pendingJourney?.note ?: "",
            currentRating = uiState.selectedRating,
            onRatingSelected = viewModel::onRatingChanged,
            onSaveNow = { note -> viewModel.saveCompletedSession(uiState.selectedRating, note) },
            onSaveLater = { note -> viewModel.saveForLater(note) },
            onDiscard = viewModel::dismissRatingDialog
        )
    }
}

@Composable
private fun FocusTypeGlassCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selectedTextColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 0.95f
        isSelected -> 1.0f
        else -> 0.97f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "glassCardScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassCardGlow"
    )

    val glassBackground = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.08f),
                accentColor.copy(alpha = 0.07f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    }

    val glassBorder = if (isSelected) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.35f),
                accentColor.copy(alpha = 0.45f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(glassBackground)
            .border(
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, glassBorder),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (glowAlpha > 0.05f) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.22f * glowAlpha))
                            .border(1.dp, accentColor.copy(alpha = 0.6f * glowAlpha), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = selectedTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) selectedTextColor else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TimerCompletionFullScreen(
    subject: String,
    durationMinutes: Int,
    sessionType: SessionType,
    initialNote: String,
    currentRating: Int,
    onRatingSelected: (Int) -> Unit,
    onSaveNow: (String) -> Unit,
    onSaveLater: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var reflectionNote by remember(initialNote) { mutableStateOf(initialNote) }
    val ratingDescriptions = listOf(
        1 to "Very Distracted (struggled to maintain attention)",
        2 to "Somewhat Distracted (frequent interruptions)",
        3 to "Moderate Focus (reasonable progress made)",
        4 to "High Focus (smooth study flow)",
        5 to "Deep Flow State (zero friction, peak concentration)"
    )

    Dialog(
        onDismissRequest = { onSaveLater(reflectionNote) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top celebratory summary
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Emerald500.copy(alpha = 0.16f))
                            .border(2.dp, Emerald500, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Emerald500,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Time's Up!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${durationMinutes}m • ${if (sessionType == SessionType.DEEP) "Deep Focus" else "Shallow Work"} • $subject",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sessionType == SessionType.DEEP) Emerald500 else Amber500,
                        textAlign = TextAlign.Center
                    )
                }

                // Middle: Focus Quality + Record Journey Note
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rate your focus quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            val isFilled = star <= currentRating
                            Icon(
                                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star $star",
                                tint = Amber500,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { onRatingSelected(star) }
                                    .padding(4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val description = ratingDescriptions.firstOrNull { it.first == currentRating }?.second ?: ""
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = reflectionNote,
                        onValueChange = { reflectionNote = it },
                        label = { Text("Record your journey (optional)") },
                        placeholder = { Text("What did you accomplish or notice?") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.EditNote, contentDescription = null)
                        },
                        minLines = 3,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Bottom: Record & Save Journey / Save Later / Discard
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { onSaveNow(reflectionNote) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                    ) {
                        Text(
                            text = "Record & Save Journey",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { onSaveLater(reflectionNote) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Save Later",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick = onDiscard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Discard Session",
                            color = Rose600,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
