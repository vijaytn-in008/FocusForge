package com.focusforge.app.ui.bot

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforge.app.ui.theme.Amber500
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
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "ForgeBot",
                        tint = Emerald600,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ForgeBot Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your Private Deep Work Guide",
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
                    // STEP 1: Welcome & Name
                    1 -> {
                        BotChatBubble(
                            message = "👋 Hello! I'm ForgeBot. Welcome to FocusForge!\n\n" +
                                    "This app is 100% offline, local-first, and private. No accounts, no cloud sync, and zero trackers.\n\n" +
                                    "What should I call you?"
                        )

                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            placeholder = { Text("e.g. Alex, Vijay, Sarah") },
                            label = { Text("Your Name or Nickname") },
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
                        BotChatBubble(
                            message = "Great to meet you, $displayName! 🎯\n\n" +
                                    "In 'Deep Work', author Cal Newport explains that high focus produces high mastery.\n\n" +
                                    "• 🎯 DEEP WORK: High cognitive demand (math, coding, intense reading) with zero distractions.\n" +
                                    "• 📝 SHALLOW WORK: Administrative tasks, emails, organizing.\n\n" +
                                    "FocusForge tracks your deep-work hours so you can conquer your exams and goals."
                        )
                    }

                    // STEP 3: Timer & Permissions
                    3 -> {
                        BotChatBubble(
                            message = "⏱️ Robust Background Timer\n\n" +
                                    "When you start a session, our timer keeps ticking accurately even if you lock your screen or switch apps.\n\n" +
                                    "To see your countdown live in your notification drawer, FocusForge asks for Notification permission."
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

                    // STEP 4: Ready to Forge
                    4 -> {
                        BotChatBubble(
                            message = "You're all set, $displayName! 🚀\n\n" +
                                    "Ready to forge your focus? Select a topic, set 25 or 50 minutes, and let's get into flow state.\n\n" +
                                    "I'll be right on your Home screen if you ever need tips or guidance!"
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
                        1 -> "Continue ➔"
                        2 -> "How Timer Works ➔"
                        3 -> "Next ➔"
                        else -> "Start My First Session 🎯"
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
                    Text("Skip Tour")
                }
            }
        }
    )
}

@Composable
private fun BotChatBubble(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
        }
    }
}
