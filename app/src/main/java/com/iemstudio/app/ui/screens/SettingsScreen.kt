package com.iemstudio.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iemstudio.app.BuildConfig
import com.iemstudio.app.IemController
import com.iemstudio.app.data.prefs.AppSettings
import com.iemstudio.app.ui.components.Caption
import com.iemstudio.app.ui.components.SectionCard
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.Danger
import com.iemstudio.app.ui.theme.TextSecondary
import com.iemstudio.app.ui.viewmodels.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(controller: IemController) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val state by vm.uiState.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Column {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Connection, controls & companion link",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // ── General ─────────────────────────────────────────────────────────
        SectionCard(title = "General", icon = Icons.Outlined.Settings) {
            SwitchRow(
                title = "Demo mode",
                description = "Simulated IEM so the app can be explored without hardware.",
                checked = settings.demoMode,
                onCheckedChange = vm::setDemoMode,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "Auto-connect on plug-in",
                description = "Request USB access automatically when the IEM is attached.",
                checked = settings.autoConnect,
                onCheckedChange = vm::setAutoConnect,
            )
        }

        // ── Controls ────────────────────────────────────────────────────────
        SectionCard(title = "Controls", icon = Icons.Outlined.Tune) {
            Column {
                Text("Volume step size: ${settings.volumeSteps}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.volumeSteps.toFloat(),
                    onValueChange = { value -> vm.setVolumeSteps(((value / 5f).roundToInt() * 5).coerceIn(5, 50)) },
                    valueRange = 5f..50f,
                    steps = 8,
                )
                Caption("Step size used by the − / + buttons on the Home screen.")
            }
        }

        // ── Bluetooth companion ─────────────────────────────────────────────
        SectionCard(title = "Bluetooth companion", icon = Icons.Outlined.Bluetooth) {
            Text(
                "The IEM's BLE link exposes a JSON control channel. Adjust the advertised name " +
                    "prefix and, if needed, the GATT service/characteristic UUIDs the device uses.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp,
            )
            CommitTextField(
                label = "Device name prefix",
                initialValue = settings.btNamePrefix,
                onCommit = vm::setBtPrefix,
            )
            CommitTextField(
                label = "GATT service UUID",
                initialValue = settings.btServiceUuid,
                onCommit = { service -> vm.setBtUuids(service, settings.btCharUuid) },
            )
            CommitTextField(
                label = "GATT characteristic UUID",
                initialValue = settings.btCharUuid,
                onCommit = { char -> vm.setBtUuids(settings.btServiceUuid, char) },
            )
        }

        // ── About ───────────────────────────────────────────────────────────
        SectionCard(title = "About", icon = Icons.Outlined.Info) {
            InfoRow("App version", BuildConfig.VERSION_NAME)
            InfoRow("Device", state.deviceName ?: "—")
            InfoRow("Vendor", state.vendorName ?: "—")
            InfoRow("DAC chip", state.dacChip ?: "—")
            InfoRow("Firmware", state.firmwareVersion ?: "—")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger.copy(alpha = 0.15f),
                    contentColor = Danger,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Power, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Reset all settings", fontWeight = FontWeight.SemiBold)
            }
        }

        Caption(
            "IEM Studio is a hardware companion template. The USB vendor protocol and BLE " +
                "command format must be mapped to your IEM's firmware — see README.md.",
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all settings?") },
            text = { Text("Clears preferences, saved presets, and the remembered volume/EQ curve. The app restarts with defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllSettings()
                    showResetDialog = false
                }) { Text("Reset", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Caption(description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Text field that commits its value on IME Done / focus loss. */
@Composable
private fun CommitTextField(
    label: String,
    initialValue: String,
    onCommit: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onCommit(value.trim())
            keyboard?.hide()
        }),
        modifier = Modifier.fillMaxWidth(),
    )
}
