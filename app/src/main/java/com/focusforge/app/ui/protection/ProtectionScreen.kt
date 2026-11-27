package com.focusforge.app.ui.protection

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusforge.app.data.local.entity.CustomDomainEntity
import com.focusforge.app.data.local.entity.DnsBlockLogEntity
import com.focusforge.app.data.model.DomainCategory
import com.focusforge.app.enforcement.DomainCheckResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProtectionViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showVpnExplanationDialog by remember { mutableStateOf(false) }

    // System VPN permission intent launcher
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleVpnService()
        }
    }

    val onToggleVpnWithPermission = {
        if (uiState.isVpnRunning) {
            viewModel.toggleVpnService()
        } else {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                // Show plain-language explanation before prompting system VPN dialog
                showVpnExplanationDialog = true
            } else {
                viewModel.toggleVpnService()
            }
        }
    }

    if (showVpnExplanationDialog) {
        VpnExplanationDialog(
            onConfirm = {
                showVpnExplanationDialog = false
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnLauncher.launch(vpnIntent)
                } else {
                    viewModel.toggleVpnService()
                }
            },
            onDismiss = { showVpnExplanationDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS & Site Protection") },
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
        ) {
            // Master VPN Header Card
            VpnMasterHeaderCard(
                isRunning = uiState.isVpnRunning,
                blockedToday = uiState.todayBlockedCount,
                onToggleVpn = onToggleVpnWithPermission
            )

            // Warning if Private DNS is set to strict mode
            if (uiState.isPrivateDnsActive) {
                PrivateDnsWarningCard(
                    onOpenWirelessSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        } catch (_: Exception) {}
                    }
                )
            }

            // Tabs: Categories, Custom Lists, Domain Tester, Activity Log
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Filters") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Custom (${uiState.customDomains.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Test") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Activity") }
                )
            }

            when (selectedTab) {
                0 -> CategoryFiltersView(
                    categoryPreferences = uiState.categoryPreferences,
                    onToggleCategory = { viewModel.toggleCategory(it) }
                )
                1 -> CustomDomainsView(
                    customDomains = uiState.customDomains,
                    newDomainInput = uiState.newCustomDomainInput,
                    newDomainType = uiState.newCustomDomainType,
                    onDomainInputChanged = { viewModel.onNewCustomDomainInputChanged(it) },
                    onTypeChanged = { viewModel.setNewCustomDomainType(it) },
                    onAddDomain = { viewModel.addCustomDomain() },
                    onRemoveDomain = { viewModel.removeCustomDomain(it) }
                )
                2 -> DomainTesterView(
                    input = uiState.testDomainInput,
                    result = uiState.testDomainResult,
                    onInputChanged = { viewModel.onTestDomainInputChanged(it) },
                    onTest = { viewModel.testDomain() }
                )
                3 -> DnsActivityLogsView(
                    logs = uiState.recentLogs,
                    onClearAll = { viewModel.clearDnsActivityLogs() }
                )
            }
        }
    }
}

@Composable
fun VpnMasterHeaderCard(
    isRunning: Boolean,
    blockedToday: Int,
    onToggleVpn: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Public else Icons.Default.Block,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRunning) "DNS Filter Active" else "DNS Filter Inactive",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isRunning) "$blockedToday sites blocked today" else "Toggle ON to filter adult & distraction sites",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isRunning,
                onCheckedChange = { onToggleVpn() },
                thumbContent = if (isRunning) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
fun PrivateDnsWarningCard(onOpenWirelessSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Android Private DNS Detected",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Strict Private DNS may bypass local filter. Set to 'Automatic' or 'Off' in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            OutlinedButton(onClick = onOpenWirelessSettings) {
                Text("Settings")
            }
        }
    }
}

@Composable
fun CategoryFiltersView(
    categoryPreferences: Map<DomainCategory, Boolean>,
    onToggleCategory: (DomainCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Active Protection Shields",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Adult websites and explicit search queries are filtered strictly on-device using high-performance binary search and contextual keyword inspection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CategoryToggleCard(
            title = "Adult Content & Queries (ADULT)",
            description = "121,000+ domains + real-time on-device search query inspection with educational disambiguation.",
            isEnabled = categoryPreferences[DomainCategory.ADULT] ?: true,
            onToggle = { onToggleCategory(DomainCategory.ADULT) }
        )

        CategoryToggleCard(
            title = "DNS-over-HTTPS Bypass Prevention (DOH)",
            description = "Blocks public DoH bypass endpoints so browsers cannot silently evade your protection rules.",
            isEnabled = categoryPreferences[DomainCategory.DOH_ENDPOINTS] ?: true,
            onToggle = { onToggleCategory(DomainCategory.DOH_ENDPOINTS) }
        )
    }
}

@Composable
fun CategoryToggleCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun CustomDomainsView(
    customDomains: List<CustomDomainEntity>,
    newDomainInput: String,
    newDomainType: String,
    onDomainInputChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onAddDomain: () -> Unit,
    onRemoveDomain: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Add Custom Domain",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newDomainInput,
                onValueChange = onDomainInputChanged,
                placeholder = { Text("example.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onAddDomain,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = newDomainType == "ALLOW",
                onClick = { onTypeChanged("ALLOW") },
                label = { Text("ALLOW (Whitelist)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            FilterChip(
                selected = newDomainType == "BLOCK",
                onClick = { onTypeChanged("BLOCK") },
                label = { Text("BLOCK") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.error,
                    selectedLabelColor = MaterialTheme.colorScheme.onError
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Custom Rules (${customDomains.size})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (customDomains.isEmpty()) {
            Text(
                text = "No custom rules added yet. Custom Allow rules take precedence over all category blocklists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(customDomains, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.listType == "ALLOW")
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[${item.listType}]",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (item.listType == "ALLOW") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.domain,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveDomain(item.domain) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DomainTesterView(
    input: String,
    result: DomainCheckResult?,
    onInputChanged: (String) -> Unit,
    onTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Interactive Domain Test",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Test any domain or subdomain to see if FocusForge will block or allow it under your current active policy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                placeholder = { Text("m.tiktok.com, pornhub.com, etc.") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = onTest) {
                Text("Test")
            }
        }

        if (result != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isBlocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (result.isBlocked) "BLOCKED" else "ALLOWED",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (result.isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Domain: ${result.domain}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (result.matchedRule != null) {
                        Text(
                            text = "Matching Rule: ${result.matchedRule}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.isBlocked) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DnsActivityLogsView(
    logs: List<DnsBlockLogEntity>,
    onClearAll: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Blocked Activity (${logs.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (logs.isNotEmpty()) {
                OutlinedButton(onClick = onClearAll) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No DNS block events recorded yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.domain,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${log.category} • ${dateFormat.format(Date(log.timestamp))}",
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
}

@Composable
fun VpnExplanationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Local DNS Protection")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "FocusForge uses a local Android VPN interface to filter website requests strictly on your device."
                )
                Text(
                    "• ZERO traffic leaves your phone through us.",
                    fontWeight = FontWeight.SemiBold
                )
                Text("• Only DNS domain names are filtered — your actual internet speed and data are untouched.")
                Text("• Fully local, open-source, and private.")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Enable Guard")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
