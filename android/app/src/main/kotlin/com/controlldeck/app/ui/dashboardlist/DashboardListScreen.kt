package com.controlldeck.app.ui.dashboardlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.controlldeck.app.ui.dashboardeditor.DashboardEditorViewModel
import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DashboardId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardListScreen(
    viewModel: DashboardEditorViewModel,
    onOpenDashboard: (DashboardId) -> Unit,
    onEditDashboard: (DashboardId) -> Unit = onOpenDashboard,
) {
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboards") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "New dashboard") }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(state.dashboards, key = { it.id.value }) { dashboard ->
                DashboardRow(
                    dashboard = dashboard,
                    onClick = { viewModel.switchTo(dashboard.id); onOpenDashboard(dashboard.id) },
                    onEdit = { viewModel.switchTo(dashboard.id); onEditDashboard(dashboard.id) },
                    onDelete = { viewModel.deleteDashboard(dashboard.id) },
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateDashboardDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> viewModel.createDashboard(name); showCreateDialog = false },
        )
    }

    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
}

@Composable
private fun DashboardRow(dashboard: Dashboard, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(dashboard.name, style = MaterialTheme.typography.titleMedium)
                Text("${dashboard.widgets.size} widgets, ${dashboard.groups.size} groups", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CreateDashboardDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New dashboard") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
