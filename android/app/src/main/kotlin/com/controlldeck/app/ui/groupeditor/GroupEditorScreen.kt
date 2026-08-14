package com.controlldeck.app.ui.groupeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlldeck.domain.Group
import com.controlldeck.domain.ReconnectPolicy
import com.controlldeck.domain.Widget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(viewModel: GroupEditorViewModel) {
    val state by viewModel.uiState.collectAsState()
    var groupName by remember { mutableStateOf("") }
    var policyMenuExpanded by remember { mutableStateOf(false) }
    var selectedPolicy by remember { mutableStateOf(ReconnectPolicy.SYNC_GROUP_STATE) }

    Scaffold(topBar = { TopAppBar(title = { Text("Groups") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Existing groups", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(state.groups, key = { it.id.value }) { group -> GroupRow(group, onDelete = { viewModel.deleteGroup(group.id) }) }
            }

            Text("Create a group", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text("Select widgets to combine:", style = MaterialTheme.typography.bodySmall)
            LazyColumn {
                items(state.widgets, key = { it.id.value }) { widget ->
                    WidgetSelectRow(widget, checked = widget.id in state.selectedWidgetIds, onToggle = { viewModel.toggleWidgetSelected(widget.id) })
                }
            }

            OutlinedTextField(value = groupName, onValueChange = { groupName = it }, label = { Text("Group name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { policyMenuExpanded = true }) { Text("Reconnect: ${selectedPolicy.name}") }
                DropdownMenu(expanded = policyMenuExpanded, onDismissRequest = { policyMenuExpanded = false }) {
                    ReconnectPolicy.values().forEach { policy ->
                        DropdownMenuItem(text = { Text(policy.name) }, onClick = { selectedPolicy = policy; policyMenuExpanded = false })
                    }
                }
            }

            Button(onClick = { viewModel.createGroup(groupName, selectedPolicy) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Create group")
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun GroupRow(group: Group, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("${group.name} (${group.kind.name}) - ${group.memberWidgetIds.size} members")
            Text("Reconnect policy: ${group.reconnectPolicy.name}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun WidgetSelectRow(widget: Widget, checked: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text("${widget.type.name} -> ${widget.targetDeviceId.value.take(8)}")
    }
}
