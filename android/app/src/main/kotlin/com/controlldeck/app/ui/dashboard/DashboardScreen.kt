package com.controlldeck.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.controlldeck.app.ui.theme.OfflineIndicatorColor
import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.MediaPlaybackState
import com.controlldeck.domain.WidgetType

/** Main dashboard view: renders sliders/buttons for every widget, dark theme, offline widgets greyed out per docs/ARCHITECTURE.md §7. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(state.dashboardName) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
        ) {
            items(state.widgets, key = { it.widget.id.value }) { widgetState ->
                Card(modifier = Modifier.fillMaxWidth().padding(6.dp).alpha(if (widgetState.online) 1f else 0.5f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(widgetState.widget.configuration["label"] ?: widgetState.widget.type.name.replace('_', ' '), style = MaterialTheme.typography.labelLarge)
                        if (!widgetState.online) {
                            Text("Offline", style = MaterialTheme.typography.bodySmall, color = OfflineIndicatorColor)
                        }
                        WidgetControl(widgetState, viewModel, enabled = widgetState.online)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetControl(state: WidgetUiState, viewModel: DashboardViewModel, enabled: Boolean) {
    val widget = state.widget
    when (widget.type) {
        WidgetType.SLIDER_BRIGHTNESS, WidgetType.SLIDER_VOLUME -> {
            var sliderValue by remember(state.currentValue) { mutableFloatStateOf((state.currentValue ?: 0).toFloat()) }
            var dragStart by remember { mutableStateOf(state.currentValue ?: 0) }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    viewModel.onSliderChanged(widget, dragStart, sliderValue.toInt())
                    dragStart = sliderValue.toInt()
                },
                valueRange = 0f..100f,
                enabled = enabled,
            )
            Text("${sliderValue.toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        WidgetType.BUTTON_MUTE -> {
            Button(onClick = { viewModel.onMutePressed(widget) }, enabled = enabled) {
                Text(if (state.muted == true) "Unmute" else "Mute")
            }
        }
        WidgetType.BUTTON_MEDIA_PLAY_PAUSE -> {
            Button(onClick = { viewModel.onMediaPlayPause(widget) }, enabled = enabled) {
                Text(if (state.mediaState == MediaPlaybackState.PLAYING) "Pause" else "Play")
            }
        }
        WidgetType.BUTTON_MEDIA_NEXT -> {
            Button(onClick = { viewModel.onMediaEdge(widget, ActionSpec.MediaNext) }, enabled = enabled) { Text("Next") }
        }
        WidgetType.BUTTON_MEDIA_PREVIOUS -> {
            Button(onClick = { viewModel.onMediaEdge(widget, ActionSpec.MediaPrevious) }, enabled = enabled) { Text("Previous") }
        }
        WidgetType.APP_LAUNCH -> {
            Button(onClick = { viewModel.onAppLaunch(widget) }, enabled = enabled) {
                Text(widget.configuration["label"] ?: "Launch")
            }
        }
    }
}
