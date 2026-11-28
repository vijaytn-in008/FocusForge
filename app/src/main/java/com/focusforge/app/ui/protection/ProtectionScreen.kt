package com.focusforge.app.ui.protection

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusforge.app.data.model.BlockResponseMode
import com.focusforge.app.data.model.PolicyTier
import com.focusforge.app.data.model.UpstreamResolverProvider
import com.focusforge.app.enforcement.DecisionReason
import com.focusforge.app.ui.components.ForgeCard
import com.focusforge.app.ui.components.ForgeGradientCard
import com.focusforge.app.ui.components.ForgeStatusPill
import com.focusforge.app.ui.theme.Emerald700
import com.focusforge.app.ui.theme.Indigo700
import com.focusforge.app.ui.theme.Slate850
import com.focusforge.app.ui.theme.Teal700
import com.focusforge.app.ui.theme.Violet600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivateList: () -> Unit = {},
    onNavigateToWhyBlocked: () -> Unit = {},
    viewModel: ProtectionViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showVpnExplanationDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPrivateDnsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.enableVpn()
        }
    }

    val onToggleVpnClick: (Boolean) -> Unit = { wantEnable ->
        if (!wantEnable) {
            viewModel.disableVpn()
        } else {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                showVpnExplanationDialog = true
            } else {
                viewModel.enableVpn()
            }
        }
    }

    if (showVpnExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showVpnExplanationDialog = false },
            title = { Text("Local DNS Protection") },
            text = {
                Text(
                    "Local filter only; no traffic leaves your phone through us. " +
                        "Only DNS lookups are checked on-device against local blocklists."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showVpnExplanationDialog = false
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) vpnLauncher.launch(vpnIntent) else viewModel.enableVpn()
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showVpnExplanationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("DNS Protection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            // Master Status Card
            ForgeGradientCard(
                brush = if (uiState.isVpnRunning) {
                    Brush.linearGradient(listOf(Emerald700, Teal700, Slate850))
                } else {
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                },
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.isVpnRunning) Icons.Default.Shield else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (uiState.isVpnRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (uiState.isVpnRunning) "Focus rules active" else "DNS filter inactive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                color = if (uiState.isVpnRunning) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ForgeStatusPill(
                                label = if (uiState.isVpnRunning) "ON" else "OFF",
                                active = uiState.isVpnRunning
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.todayBlockedCount} blocked today • ${uiState.pauseEventsCount} pauses",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (uiState.isVpnRunning) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.isVpnRunning,
                        onCheckedChange = { onToggleVpnClick(it) }
                    )
                }
            }

            // Fail-open or 5-min pause banner
            val isPaused = uiState.policySnapshot.isPaused()
            if (uiState.isFailOpenPaused || isPaused) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPaused) "Protection paused (5 min)" else "Protection paused: network issue",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (!isPaused && uiState.failOpenReason != null) {
                                Text(
                                    text = uiState.failOpenReason!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        OutlinedButton(onClick = { viewModel.resumeNow() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restart")
                        }
                    }
                }
            } else if (uiState.isVpnRunning) {
                OutlinedButton(
                    onClick = { viewModel.pauseFor5Minutes() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pause protection for 5 minutes")
                }
            }

            // Private DNS strict mode warning
            if (uiState.isPrivateDnsStrict) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private DNS strict mode active",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Custom Private DNS hostname bypasses local VPN filtering. Switch to Automatic or Off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            } catch (_: Exception) {}
                        }) {
                            Text("Settings")
                        }
                    }
                }
            }

            // Filter Tiers
            Text("Filter Tiers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TierToggleRow(
                title = "Adult Core (Baseline)",
                subtitle = "High-confidence adult domain filter (ON by default).",
                checked = uiState.tierPreferences[PolicyTier.ADULT_CORE] != false,
                onToggle = { viewModel.toggleTier(PolicyTier.ADULT_CORE) }
            )
            TierToggleRow(
                title = "Adult Extended (Aggressive)",
                subtitle = "Broader adult list (OFF by default; higher false-positive risk).",
                checked = uiState.tierPreferences[PolicyTier.ADULT_EXTENDED] == true,
                onToggle = { viewModel.toggleTier(PolicyTier.ADULT_EXTENDED) }
            )
            TierToggleRow(
                title = "Distraction Domains (STRICT profile only)",
                subtitle = "Applies only when Shield profile is set to STRICT.",
                checked = uiState.tierPreferences[PolicyTier.DISTRACTION] == true,
                onToggle = { viewModel.toggleTier(PolicyTier.DISTRACTION) }
            )
            TierToggleRow(
                title = "DoH Bypass Guard",
                subtitle = "Blocks public DoH endpoints so browsers use local DNS (hard-coded IP DoH not covered).",
                checked = uiState.tierPreferences[PolicyTier.DOH_ENDPOINTS] != false,
                onToggle = { viewModel.toggleTier(PolicyTier.DOH_ENDPOINTS) }
            )

            // Upstream Resolver
            Text("Upstream DNS Resolver", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UpstreamResolverProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = uiState.upstreamProvider == provider,
                        onClick = { viewModel.setUpstreamProvider(provider) },
                        label = { Text(provider.displayName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Block Response Mode
            Text("Blocked Response Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockResponseMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.blockResponseMode == mode,
                        onClick = { viewModel.setBlockResponseMode(mode) },
                        label = { Text(if (mode == BlockResponseMode.NXDOMAIN) "NXDOMAIN" else "0.0.0.0 / ::") }
                    )
                }
            }

            // Private / Custom Rules Entry
            OutlinedButton(
                onClick = onNavigateToPrivateList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Custom rules (${uiState.policySnapshot.privateBlockedDomainHashes.size + uiState.policySnapshot.privateBlockedApps.size})")
            }

            // User Allowlist (Hashed, Distraction/Private only)
            Text(
                "User Allowlist (${uiState.policySnapshot.customAllowedDomainHashes.size} rules)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Stored as hashes only. Allows domains blocked by Distraction or Private rules (does not bypass Adult filters).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = uiState.allowDomainInput,
                onValueChange = { viewModel.onAllowDomainInputChanged(it) },
                placeholder = { Text("youtube.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.addAllowDomain() }) { Text("Allow") }
                OutlinedButton(onClick = { viewModel.removeAllowDomain() }) { Text("Remove") }
            }
            uiState.allowActionMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (uiState.allowedDomainItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.allowedDomainItems.forEach { item ->
                        val isRevealed = item.hash in uiState.revealedAllowHashes
                        val displayName = if (isRevealed && !item.rawDomain.isNullOrBlank()) {
                            item.rawDomain
                        } else {
                            item.maskedName
                        }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!item.rawDomain.isNullOrBlank()) {
                                    IconButton(onClick = { viewModel.toggleRevealAllowDomain(item.hash) }) {
                                        Icon(
                                            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isRevealed) "Hide domain" else "Reveal domain"
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.removeAllowDomainByHash(item.hash) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove allow rule",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Test a domain
            Text("Test a Domain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.testDomainInput,
                    onValueChange = { viewModel.onTestDomainInputChanged(it) },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.testDomain() }) { Text("Test") }
            }
            uiState.testDomainResult?.let { res ->
                val reasonLabel = if (res.reason == DecisionReason.PRIVATE_RULE) {
                    "Private rule"
                } else {
                    res.matchedTier?.name ?: res.reason.name
                }
                Text(
                    text = "Decision: ${res.decision.name} ($reasonLabel)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (res.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Debug-only opt-in DNS Decision Buffer & "Why was this blocked?"
            Text("Diagnostics (In-Memory Only)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TierToggleRow(
                title = "DNS decision buffer (Debug)",
                subtitle = "Keeps last 300 queries in memory only. Cleared when VPN stops.",
                checked = uiState.isDecisionBufferEnabled,
                onToggle = { viewModel.setDecisionBufferEnabled(!uiState.isDecisionBufferEnabled) }
            )
            OutlinedButton(
                onClick = onNavigateToWhyBlocked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Why was this blocked? (${uiState.decisionBufferCount})")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TierToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    ForgeCard(
        cornerRadius = 16.dp,
        elevation = 1.dp,
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
