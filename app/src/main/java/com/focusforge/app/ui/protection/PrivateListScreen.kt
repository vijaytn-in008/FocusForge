package com.focusforge.app.ui.protection

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateListScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrivateListViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAppPicker by remember { mutableStateOf(false) }

    // Enforce FLAG_SECURE on every screen showing private entries
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            viewModel.hideRevealedOnly()
        }
    }

    // Hide revealed entries and lock on app switch (ON_PAUSE / ON_STOP)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.hideRevealedOnly()
                if (!viewModel.isWithinAuthGrace()) {
                    viewModel.lockAndHide()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hide revealed entry on shake
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gForce = sqrt(
                    (event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]).toDouble()
                ) / SensorManager.GRAVITY_EARTH
                if (gForce > 2.3) {
                    viewModel.hideRevealedOnly()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sm?.unregisterListener(listener)
        }
    }

    BackHandler {
        viewModel.hideRevealedOnly()
        onNavigateBack()
    }

    val promptAuth = remember(activity) {
        {
            if (activity != null) {
                val biometricManager = BiometricManager.from(activity)
                val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                viewModel.onAuthenticated()
                            }
                        }
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(if (uiState.stealthLabelsEnabled) "Unlock Custom Rules" else "Unlock Private Rules")
                        .setSubtitle("Authenticate to view or edit rules")
                        .setAllowedAuthenticators(authenticators)
                        .build()
                    prompt.authenticate(info)
                } else {
                    // Device has no lock screen configured
                    viewModel.onAuthenticated()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.isWithinAuthGrace()) {
            promptAuth()
        } else {
            viewModel.onAuthenticated()
        }
    }

    val screenTitle = if (uiState.stealthLabelsEnabled) "Custom rules" else "Private Rules"

    if (uiState.pendingPopularWarningDomain != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPopularDomainWarning() },
            title = { Text("Popular Domain Warning") },
            text = {
                Text(
                    "${uiState.pendingPopularWarningDomain} is a widely used domain. " +
                        "Blocking it may affect normal browsing. Add anyway?"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmPopularDomainWarning() }) { Text("Confirm") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissPopularDomainWarning() }) { Text("Cancel") }
            }
        )
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Add Private App Rule") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(uiState.installedApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addPrivateApp(app.packageName, app.label)
                                    showAppPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.hideRevealedOnly()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!uiState.isAuthenticated) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.height(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Authentication required", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { promptAuth() }) { Text("Unlock") }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pending weakening countdown (never displays rule name!)
            uiState.pendingPrivateChange?.let { pending ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1 private rule pending change",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Applying in ${pending.remainingSeconds}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        OutlinedButton(onClick = { viewModel.cancelPendingPrivateChange() }) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // Input field with IME_FLAG_NO_PERSONALIZED_LEARNING & no autofill
            Text("Add Domain Rule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PrivateDomainTextField(
                value = uiState.domainInput,
                onValueChange = { viewModel.onDomainInputChanged(it) },
                hint = "example.com"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.requestAddDomain() }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipText = cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    viewModel.importFromPaste(clipText)
                    // Clear clipboard after paste-import
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        cm?.clearPrimaryClip()
                    } else {
                        cm?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste & Clear")
                }
                if (uiState.isHashOnlyMode) {
                    OutlinedButton(onClick = { viewModel.requestRemoveByRetyping() }) {
                        Text("Remove")
                    }
                }
            }

            uiState.statusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // Domain Rules List or Hash-only count
            if (uiState.isHashOnlyMode) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${uiState.totalDomainCount} private domain rule(s)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hash-only mode is ON. No readable names are stored. Retype a domain above to remove it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "Domain Rules (${uiState.maskedDomains.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                uiState.maskedDomains.forEach { item ->
                    val key = "d_${item.hash}"
                    val isRevealed = uiState.revealedItemKey == key
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRevealed && item.realName != null) item.realName else item.maskedName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.revealItem(key) }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Reveal for 10s")
                            }
                            IconButton(onClick = { viewModel.requestRemoveByHash(item.hash) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            // Private App Rules
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Private App Rules (${uiState.totalAppCount})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { showAppPicker = true }) {
                    Text("Add App")
                }
            }
            if (!uiState.isHashOnlyMode) {
                uiState.maskedApps.forEach { item ->
                    val key = "a_${item.packageName}"
                    val isRevealed = uiState.revealedItemKey == key
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRevealed && item.realLabel != null) item.realLabel else item.maskedLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.revealItem(key) }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Reveal for 10s")
                            }
                            IconButton(onClick = { viewModel.requestRemovePrivateApp(item.packageName) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            // Privacy & Discreet Settings
            Text("Discreet Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ToggleSettingCard(
                title = "Hash-only mode",
                subtitle = "Do not store readable names anywhere (wipes encrypted vault).",
                checked = uiState.isHashOnlyMode,
                onCheckedChange = { viewModel.setHashOnlyMode(it) }
            )
            ToggleSettingCard(
                title = "Stealth labels",
                subtitle = "Use neutral title (\"Custom rules\") instead of \"Private Rules\".",
                checked = uiState.stealthLabelsEnabled,
                onCheckedChange = { viewModel.setStealthLabelsEnabled(it) }
            )
            ToggleSettingCard(
                title = "Discreet launcher icon",
                subtitle = "Show app as \"Focus Notes\" in launcher.",
                checked = uiState.discreetAppIconEnabled,
                onCheckedChange = { viewModel.setDiscreetAppIconEnabled(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivateDomainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    EditText(ctx).apply {
                        this.hint = hint
                        this.background = null
                        this.setPadding(0, 0, 0, 0)
                        this.textSize = 16f
                        this.setTextColor(textColor)
                        this.setHintTextColor(hintColor)
                        isSingleLine = true
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                        }
                        doAfterTextChanged { editable ->
                            val txt = editable?.toString() ?: ""
                            if (txt != value) onValueChange(txt)
                        }
                    }
                },
                update = { editText ->
                    editText.setTextColor(textColor)
                    editText.setHintTextColor(hintColor)
                    if (editText.text.toString() != value) {
                        editText.setText(value)
                        editText.setSelection(value.length)
                    }
                }
            )
        }
    }
}

@Composable
private fun ToggleSettingCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
