package com.controlldeck.app.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlldeck.app.discovery.DiscoveredDevice
import com.controlldeck.app.ui.theme.OfflineIndicatorColor
import com.controlldeck.domain.DeviceId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    viewModel: DeviceListViewModel,
    onQuickConnect: (DiscoveredDevice) -> Unit,
    onScanQr: () -> Unit,
    onEnterPin: () -> Unit,
    onForget: (DeviceId) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Devices") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onScanQr) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Scan QR")
                }
                Button(onClick = onEnterPin) { Text("Enter PIN") }
            }

            Text("Paired devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            if (state.pairedDevices.isEmpty()) {
                Text("No paired devices yet.", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn {
                items(state.pairedDevices, key = { it.device.deviceId.value }) { item ->
                    PairedDeviceRow(item, onForget = { onForget(item.device.deviceId) })
                }
            }

            Text("Nearby (unpaired)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            Text(
                "Tap a device to connect instantly. This only works if the other device has \"Auto-accept pairing\" turned on in its Settings — otherwise use Scan QR / Enter PIN above.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.discoveredUnpaired.isEmpty()) {
                Text("Searching for devices on your network...", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn {
                items(state.discoveredUnpaired, key = { it.deviceId.value }) { device ->
                    DiscoveredDeviceRow(device, onClick = { onQuickConnect(device) })
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(item: PairedDeviceUi, onForget: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(online = item.online)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(item.device.deviceName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (item.online) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.online) MaterialTheme.colorScheme.secondary else OfflineIndicatorColor,
                )
            }
        }
        Text("Forget", modifier = Modifier.clickable(onClick = onForget), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DiscoveredDeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(device.deviceName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "  (${device.platform})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(
                color = if (online) MaterialTheme.colorScheme.secondary else OfflineIndicatorColor,
                shape = CircleShape,
            ),
    )
}
