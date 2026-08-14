package com.controlldeck.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlldeck.app.persistence.LocalAppRegistryEntry
import com.controlldeck.domain.ReconnectPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var deviceName by remember { mutableStateOf("") }
    LaunchedEffect(state.deviceName) { deviceName = state.deviceName }

    var appId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var policyMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Device name", style = MaterialTheme.typography.titleMedium)
            Row {
                OutlinedTextField(value = deviceName, onValueChange = { deviceName = it }, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.setDeviceName(deviceName) }) { Text("Save") }
            }

            Text("Default reconnect policy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            TextButton(onClick = { policyMenuExpanded = true }) { Text(state.defaultReconnectPolicy.name) }
            DropdownMenu(expanded = policyMenuExpanded, onDismissRequest = { policyMenuExpanded = false }) {
                ReconnectPolicy.values().forEach { policy ->
                    DropdownMenuItem(text = { Text(policy.name) }, onClick = { viewModel.setDefaultReconnectPolicy(policy); policyMenuExpanded = false })
                }
            }

            Text("App registry", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            LazyColumn {
                items(state.appRegistry, key = { it.appId.value }) { entry -> AppRegistryRow(entry, onRemove = { viewModel.removeApp(entry.appId) }) }
            }

            Text("Add app", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("appId (e.g. spotify)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text("Package name") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    viewModel.addOrUpdateApp(appId, displayName, packageName)
                    appId = ""; displayName = ""; packageName = ""
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Add") }
        }
    }
}

@Composable
private fun AppRegistryRow(entry: LocalAppRegistryEntry, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Text(entry.displayName)
            Text(entry.packageName, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
    }
}
