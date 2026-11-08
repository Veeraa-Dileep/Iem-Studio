package com.iemstudio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iemstudio.app.IemController
import com.iemstudio.app.data.model.EqPresets
import com.iemstudio.app.ui.components.Caption
import com.iemstudio.app.ui.components.DeviceStatusCard
import com.iemstudio.app.ui.components.ErrorBanner
import com.iemstudio.app.ui.components.SectionCard
import com.iemstudio.app.ui.components.StatusDot
import com.iemstudio.app.ui.components.VolumeRing
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.Danger
import com.iemstudio.app.ui.theme.Success
import com.iemstudio.app.ui.theme.TextPrimary
import com.iemstudio.app.ui.theme.TextSecondary
import com.iemstudio.app.ui.viewmodels.HomeViewModel

@Composable
fun HomeScreen(controller: IemController, onRequestBtPermissions: () -> Unit) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val customPresets by vm.customPresets.collectAsStateWithLifecycle(initialValue = emptyList())
    val volumeSteps by vm.volumeSteps.collectAsStateWithLifecycle(initialValue = 20)

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
                "IEM Studio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Type-C IEM • DAC control",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        state.errorMessage?.let {
            ErrorBanner(it, onDismiss = vm::clearError)
        }

        DeviceStatusCard(
            state = state,
            onConnect = vm::requestUsbPermission,
            onDisconnect = vm::disconnect,
        )

        // ── Volume ──────────────────────────────────────────────────────────
        SectionCard(title = "Volume", icon = Icons.Outlined.VolumeUp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VolumeRing(
                    volume = state.volume,
                    muted = state.muted,
                    onVolumeChange = vm::setVolume,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FilledTonalIconButton(onClick = { vm.stepVolume(-volumeSteps) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Volume down")
                    }
                    IconButton(onClick = vm::toggleMute) {
                        Icon(
                            if (state.muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (state.muted) Danger else TextPrimary,
                        )
                    }
                    FilledTonalIconButton(onClick = { vm.stepVolume(volumeSteps) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Volume up")
                    }
                }
                Caption("Drag the ring, or step by $volumeSteps%")
            }
        }

        // ── Quick presets ───────────────────────────────────────────────────
        SectionCard(title = "Quick presets", icon = Icons.Outlined.GraphicEq) {
            val allPresets = EqPresets.BUILT_INS + customPresets
            val activeId = allPresets.firstOrNull { it.gains == state.eqGains }?.id
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allPresets, key = { it.id }) { preset ->
                    FilterChip(
                        selected = preset.id == activeId,
                        onClick = { vm.applyPreset(preset) },
                        label = { Text(preset.name) },
                    )
                }
            }
            Caption("Tune everything in EQ Studio.")
        }

        // ── Bluetooth companion ─────────────────────────────────────────────
        SectionCard(title = "Bluetooth companion", icon = Icons.Outlined.Bluetooth) {
            when {
                !vm.isBluetoothSupported() ->
                    Caption("Bluetooth is not supported on this device.")

                !vm.isBluetoothEnabled() ->
                    Caption("Bluetooth is off — enable it in Quick Settings to use the companion link.")

                !vm.hasBtPermissions() ->
                    Button(
                        onClick = onRequestBtPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber),
                    ) { Text("Enable Bluetooth permissions") }

                state.btConnectedName != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Success)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            state.btConnectedName ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = vm::disconnectBt) { Text("Disconnect") }
                    }
                }

                else -> {
                    if (state.btScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Amber,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                "Scanning…",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = vm::stopBtScan) { Text("Stop") }
                        }
                    } else {
                        Button(
                            onClick = vm::scanBt,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber),
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Scan for IEM", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    state.btFoundDevices.forEach { device ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.connectBt(device) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    device.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                "${device.rssi} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Icon(
                                Icons.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
