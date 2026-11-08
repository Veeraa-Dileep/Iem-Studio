package com.iemstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iemstudio.app.data.model.ConnectionMode
import com.iemstudio.app.data.model.DeviceUiState
import com.iemstudio.app.ui.theme.Amber
import com.iemstudio.app.ui.theme.Danger
import com.iemstudio.app.ui.theme.Success
import com.iemstudio.app.ui.theme.SurfaceHigh
import com.iemstudio.app.ui.theme.TextSecondary

/**
 * Hero card on the Home screen: connection mode, device identity,
 * live format, battery, and connect/disconnect actions.
 */
@Composable
fun DeviceStatusCard(
    state: DeviceUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modeIcon = when (state.mode) {
        ConnectionMode.USB -> Icons.Outlined.Usb
        ConnectionMode.BLUETOOTH -> Icons.Outlined.Bluetooth
        ConnectionMode.DEMO -> Icons.Outlined.Headset
        ConnectionMode.NONE -> Icons.Outlined.Usb
    }
    val statusColor = if (state.connected) Success else TextSecondary
    val statusText = when {
        state.mode == ConnectionMode.DEMO && state.connected -> "Demo"
        state.connected -> "Connected"
        else -> "Not connected"
    }
    val subtitle = when {
        state.deviceName == null -> "Plug in your Type-C IEM"
        state.vendorName != null && state.dacChip != null -> "${state.vendorName} • ${state.dacChip}"
        state.vendorName != null -> state.vendorName
        else -> state.mode.name
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceHigh),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(Amber.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(modeIcon, contentDescription = null, tint = Amber, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.deviceName ?: "No device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(statusColor)
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
                    }
                    Spacer(Modifier.size(3.dp))
                    Text(state.formatLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    if (state.batteryPercent != null) {
                        Spacer(Modifier.size(2.dp))
                        Text("Battery ${state.batteryPercent}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            when {
                state.pendingPermission -> Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = androidx.compose.ui.graphics.Color(0xFF241703)),
                ) { Text("Grant USB access", fontWeight = FontWeight.SemiBold) }

                !state.connected -> Button(
                    onClick = onConnect,
                    enabled = state.usbDevicePlugged,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor = androidx.compose.ui.graphics.Color(0xFF241703),
                        disabledContainerColor = SurfaceHigh,
                        disabledContentColor = TextSecondary,
                    ),
                ) {
                    Text(
                        if (state.usbDevicePlugged) "Connect IEM" else "Plug in your IEM",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                state.mode == ConnectionMode.USB || state.mode == ConnectionMode.BLUETOOTH -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect", color = Danger)
                    }
                }
            }

            if (state.mode == ConnectionMode.DEMO && state.connected) {
                Caption("Simulated device — plug in your IEM over USB, or turn Demo mode off in Settings.")
            }
        }
    }
}
