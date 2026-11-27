package com.focusforge.app.ui.timer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
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

        // Circular Timer Display
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
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
                    // Background track
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val displaySeconds = if (isTimerActive) timerState.remainingSeconds else uiState.durationMinutes * 60
                    Text(
                        text = TimeUtils.formatSecondsToTime(displaySeconds),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = when (timerState.status) {
                        TimerStatus.RUNNING -> if (timerState.sessionType == SessionType.DEEP) "DEEP FOCUS" else "SHALLOW WORK"
                        TimerStatus.PAUSED -> "PAUSED"
                        TimerStatus.COMPLETED -> "COMPLETED"
                        TimerStatus.IDLE -> "${uiState.durationMinutes} MIN GOAL"
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

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.onSessionTypeChanged(SessionType.DEEP) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDeep) Emerald500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isDeep) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Emerald500)) else null
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Psychology,
                                                contentDescription = null,
                                                tint = if (isDeep) Emerald600 else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Deep Work",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isDeep) Emerald600 else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "High cognitive focus",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.onSessionTypeChanged(SessionType.SHALLOW) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!isDeep) Amber500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (!isDeep) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Amber500)) else null
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = if (!isDeep) Amber500 else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Shallow Work",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (!isDeep) Amber500 else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Administrative tasks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Duration presets + custom input
                        Column {
                            Text(
                                text = "Duration",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 25, 50, 90).forEach { mins ->
                                    val isSelected = uiState.durationMinutes == mins
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
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.customDurationInput,
                                onValueChange = viewModel::onCustomDurationInputChanged,
                                label = { Text("Custom minutes (1–240)") },
                                placeholder = { Text("25") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
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

    // Focus Rating & Save Dialog (1..5 Stars)
    if (uiState.showRatingDialog) {
        FocusRatingDialog(
            currentRating = uiState.selectedRating,
            onRatingSelected = viewModel::onRatingChanged,
            onConfirm = { viewModel.saveCompletedSession(uiState.selectedRating) },
            onDismiss = viewModel::dismissRatingDialog
        )
    }
}

@Composable
fun FocusRatingDialog(
    currentRating: Int,
    onRatingSelected: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val ratingDescriptions = listOf(
        1 to "Very Distracted (struggled to maintain attention)",
        2 to "Somewhat Distracted (frequent interruptions)",
        3 to "Moderate Focus (reasonable progress made)",
        4 to "High Focus (smooth study flow)",
        5 to "Deep Flow State (zero friction, peak concentration)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Emerald600,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Session Completed",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "How would you rate your focus quality?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Interactive 5 stars
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
                                .size(40.dp)
                                .clickable { onRatingSelected(star) }
                                .padding(2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val description = ratingDescriptions.firstOrNull { it.first == currentRating }?.second ?: ""
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Save Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip Rating")
            }
        }
    )
}
