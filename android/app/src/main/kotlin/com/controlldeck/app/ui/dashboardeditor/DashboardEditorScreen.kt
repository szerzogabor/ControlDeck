package com.controlldeck.app.ui.dashboardeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetId

/**
 * Simple grid-based dashboard editor: a [LazyVerticalGrid] of widget
 * tiles you can tap to configure/remove and a FAB to add a new one.
 * Deliberately not a sophisticated freeform drag-canvas — per the task
 * spec, a grid with add/remove/move/resize is sufficient for the MVP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditorScreen(
    viewModel: DashboardEditorViewModel,
    onAddWidget: () -> Unit,
    onConfigureWidget: (Widget) -> Unit,
    onManageGroups: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val dashboard = state.selectedDashboard

    Scaffold(
        topBar = { TopAppBar(title = { Text(dashboard?.name ?: "Dashboard") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWidget) { Icon(Icons.Filled.Add, contentDescription = "Add widget") }
        },
    ) { padding ->
        if (dashboard == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("Select or create a dashboard", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Groups: ${dashboard.groups.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(dashboard.widgets, key = { it.id.value }) { widget ->
                    WidgetEditorTile(
                        widget = widget,
                        onClick = { onConfigureWidget(widget) },
                        onDelete = { viewModel.removeWidget(widget.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetEditorTile(widget: Widget, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text(widget.type.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
            Text(
                widget.configuration["label"] ?: widget.targetDeviceId.value.take(8),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove widget") }
        }
    }
}
