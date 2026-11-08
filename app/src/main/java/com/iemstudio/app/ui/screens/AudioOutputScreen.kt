package com.iemstudio.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.iemstudio.app.data.model.BIT_DEPTHS
import com.iemstudio.app.data.model.SAMPLE_RATES
import com.iemstudio.app.data.model.formatGain
import com.iemstudio.app.data.model.formatRate
import com.iemstudio.app.ui.components.Caption
import com.iemstudio.app.ui.components.SectionCard
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.TextSecondary
import com.iemstudio.app.ui.viewmodels.OutputViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioOutputScreen(controller: IemController) {
    val vm: OutputViewModel = viewModel(factory = OutputViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()

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
                "Audio Output",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "DAC format & gain",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // ── Active format ───────────────────────────────────────────────────
        SectionCard(title = "Active format", icon = Icons.Outlined.Speed) {
            Column {
                Text(
                    state.formatLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Amber,
                )
                Spacer(Modifier.height(4.dp))
                Caption("The format the DAC is currently asked to run.")
            }
        }

        // ── Sample rate ─────────────────────────────────────────────────────
        SectionCard(title = "Sample rate", icon = Icons.Outlined.Tune) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SAMPLE_RATES.forEach { rate ->
                    FilterChip(
                        selected = state.sampleRate == rate,
                        onClick = { vm.setSampleRate(rate) },
                        label = { Text(formatRate(rate)) },
                    )
                }
            }
            Caption("Uses the USB Audio Class sampling-frequency control, with a vendor-command fallback.")
        }

        // ── Bit depth ───────────────────────────────────────────────────────
        SectionCard(title = "Bit depth", icon = Icons.Outlined.Memory) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BIT_DEPTHS.forEach { depth ->
                    FilterChip(
                        selected = state.bitDepth == depth,
                        onClick = { vm.setBitDepth(depth) },
                        label = { Text("$depth-bit") },
                    )
                }
            }
        }

        // ── DAC gain ────────────────────────────────────────────────────────
        SectionCard(title = "DAC gain", icon = Icons.Outlined.VolumeUp) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text("0 dB", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text("+12 dB", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Slider(
                    value = state.gainDb,
                    onValueChange = vm::setGainDb,
                    valueRange = 0f..12f,
                    steps = 23, // 24 half-dB intervals
                )
                Text(
                    formatGain(state.gainDb),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Amber,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Caption("Analog / digital gain applied after the DAC stage.")
            }
        }

        // ── LED ─────────────────────────────────────────────────────────────
        SectionCard(title = "LED", icon = Icons.Outlined.FlashOn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Device LED", style = MaterialTheme.typography.bodyLarge)
                    Caption("Indicator light on the IEM body or cable controller.")
                }
                Switch(checked = state.ledOn, onCheckedChange = vm::setLed)
            }
        }

        // ── Info ────────────────────────────────────────────────────────────
        SectionCard(title = "How it works", icon = Icons.Outlined.Info) {
            Text(
                "Android's USB audio stack owns the PCM stream to the DAC. This app controls the " +
                    "DAC's hardware settings — volume, EQ, gain and format — over the vendor control " +
                    "channel. Sample-rate changes require a DAC and phone that both support the " +
                    "selected rate.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
