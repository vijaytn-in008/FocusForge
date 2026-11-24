package com.focusforge.app.ui.bot

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforge.app.ui.theme.Emerald500
import com.focusforge.app.ui.theme.Emerald600

@Composable
fun ForgeBotGuideDialog(
    initialUserName: String,
    onSaveUserName: (String) -> Unit,
    onCompleteOnboarding: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToTimer: (() -> Unit)? = null
) {
    var step by remember { mutableIntStateOf(1) }
    var inputName by remember { mutableStateOf(initialUserName) }
    var notificationGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = isGranted
    }

    val displayName = inputName.trim().ifBlank { "Scholar" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Emerald500.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Focus Guide",
                        tint = Emerald600,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FocusForge Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deep Work & Architecture Overview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (step) {
                    // STEP 1: Welcome & Profile Name
                    1 -> {
                        GuideContentBubble(
                            title = "Local-First Architecture",
                            message = "FocusForge is a private digital sanctuary designed to help students plan deliberate practice blocks and track exam readiness.\n\n" +
                                    "All session data is persisted exclusively on this device. There are no accounts, no cloud sync, and no third-party telemetry.\n\n" +
                                    "Please provide a preferred name or title for your dashboard."
                        )

                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            placeholder = { Text("Enter your name or title") },
                            label = { Text("Profile Name") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // STEP 2: The Deep Work Concept
                    2 -> {
                        GuideContentBubble(
                            title = "Deep Work vs. Shallow Work",
                            message = "Based on Cal Newport's research, high-value output requires sustained concentration without distraction.\n\n" +
                                    "• Deep Work: Cognitively demanding tasks (problem solving, analytical reading, coding) performed with zero distraction.\n\n" +
                                    "• Shallow Work: Logistical or operational tasks (administrative work, reviewing schedules, organizing notes).\n\n" +
                                    "FocusForge measures your deep-work ratio to ensure meaningful cognitive effort."
                        )
                    }

                    // STEP 3: Timer & Notification Permission
                    3 -> {
                        GuideContentBubble(
                            title = "Persistent Background Timer",
                            message = "Focus timers run in a persistent foreground service, ensuring the countdown never desynchronizes or stops when the screen locks or when switching applications.\n\n" +
                                    "An ongoing notification provides countdown visibility and controls in your notification shade."
                        )

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Notification Access",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            notificationGranted = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (notificationGranted) Emerald600 else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (notificationGranted) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Enabled", fontSize = 12.sp)
                                    } else {
                                        Text("Enable", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // STEP 4: Ready to Start
                    4 -> {
                        GuideContentBubble(
                            title = "Ready for Session Planning",
                            message = "Your profile is configured, $displayName.\n\n" +
                                    "To initiate a session, navigate to the Timer tab, select a discipline, define your target duration (e.g., 25 or 50 minutes), and start.\n\n" +
                                    "You may revisit this guide at any time from the home dashboard."
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        onSaveUserName(displayName)
                        step = 2
                    } else if (step < 4) {
                        step++
                    } else {
                        onSaveUserName(displayName)
                        onCompleteOnboarding()
                        onDismiss()
                        onNavigateToTimer?.invoke()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (step) {
                        1 -> "Continue"
                        2 -> "Next: Background Timer"
                        3 -> "Next"
                        else -> "Begin Focus Session"
                    }
                )
            }
        },
        dismissButton = {
            if (step > 1) {
                TextButton(onClick = { step-- }) {
                    Text("Back")
                }
            } else {
                TextButton(
                    onClick = {
                        onSaveUserName(displayName)
                        onCompleteOnboarding()
                        onDismiss()
                    }
                ) {
                    Text("Skip Guide")
                }
            }
        }
    )
}

@Composable
private fun GuideContentBubble(title: String, message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
        }
    }
}
