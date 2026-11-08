package com.iemstudio.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iemstudio.app.IemController
import com.iemstudio.app.data.model.EQ_BAND_COUNT
import com.iemstudio.app.data.model.EQ_BAND_LABELS
import com.iemstudio.app.data.model.EqPreset
import com.iemstudio.app.data.model.EqPresets
import com.iemstudio.app.data.model.formatGain
import com.iemstudio.app.ui.components.Caption
import com.iemstudio.app.ui.components.EqBandSlider
import com.iemstudio.app.ui.components.EqCurve
import com.iemstudio.app.ui.components.SectionCard
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.Danger
import com.iemstudio.app.ui.theme.TextSecondary
import com.iemstudio.app.ui.viewmodels.EqViewModel

@Composable
fun EqStudioScreen(controller: IemController) {
    val vm: EqViewModel = viewModel(factory = EqViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle(initialValue = EqPresets.BUILT_INS)

    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }

    val activeId = presets.firstOrNull { it.gains == state.eqGains }?.id ?: "custom"

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
                "EQ Studio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "10-band graphic equalizer",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // ── Preset strip ─────────────────────────────────────────────────────
        SectionCard(
            title = "Presets",
            icon = Icons.Outlined.GraphicEq,
            trailing = {
                Row {
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save current curve", tint = Amber)
                    }
                    IconButton(onClick = { showManageDialog = true }) {
                        Icon(Icons.Outlined.List, contentDescription = "Manage custom presets")
                    }
                }
            },
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.id }) { preset ->
                    FilterChip(
                        selected = preset.id == activeId,
                        onClick = { vm.applyPreset(preset) },
                        label = { Text(preset.name) },
                    )
                }
            }
            Caption("Tap a preset to apply it. Drag the sliders below to sculpt a custom curve.")
        }

        // ── The equalizer ───────────────────────────────────────────────────
        SectionCard(
            title = "Equalizer",
            icon = Icons.Outlined.Equalizer,
            trailing = {
                TextButton(onClick = vm::resetEq) { Text("Reset", color = Amber) }
            },
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Curve behind, one draggable slider column per band on top.
                // Both use the same 0..width / band mapping, so knobs sit on the curve.
                EqCurve(state.eqGains, Modifier.matchParentSize())
                Row(Modifier.matchParentSize()) {
                    repeat(EQ_BAND_COUNT) { band ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            EqBandSlider(
                                bandIndex = band,
                                gainDb = state.eqGains[band],
                                onGainChange = { gain -> vm.setBandGain(band, gain) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            val gain = state.eqGains[band]
                            Text(
                                formatGain(gain),
                                color = if (gain == 0f) TextSecondary else Amber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 2.dp),
                            )
                            Text(
                                EQ_BAND_LABELS[band],
                                color = TextSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp),
                            )
                        }
                    }
                }
            }
            Caption("Range −12 … +12 dB • 0.5 dB steps • vertical drag")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onConfirm = { name ->
                vm.saveCustomPreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }
    if (showManageDialog) {
        ManagePresetsDialog(
            customs = presets.filter { it.isCustom },
            onDelete = vm::deleteCustomPreset,
            onDismiss = { showManageDialog = false },
        )
    }
}

@Composable
private fun SavePresetDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save custom preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save", color = Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ManagePresetsDialog(
    customs: List<EqPreset>,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom presets") },
        text = {
            if (customs.isEmpty()) {
                Text("No custom presets yet. Sculpt a curve, then tap the save icon.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    customs.forEach { preset ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(preset.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete ${preset.name}",
                                    tint = Danger,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Amber) }
        },
    )
}
