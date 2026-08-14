package com.controlldeck.app.ui.dashboardeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.AppId
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.GridPosition
import com.controlldeck.domain.GridSize
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetId
import com.controlldeck.domain.WidgetType
import java.util.UUID

/** A device the user can target, with a human-readable label for the picker. */
data class TargetableDevice(val deviceId: DeviceId, val label: String)

/**
 * Widget config: pick target device (from paired + capable devices) and
 * action/app. Used both for "add widget" (existing == null) and
 * "reconfigure widget" (existing != null).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigDialog(
    availableDevices: List<TargetableDevice>,
    existing: Widget?,
    nextGridPosition: GridPosition,
    onDismiss: () -> Unit,
    onSave: (Widget) -> Unit,
) {
    var selectedType by remember { mutableStateOf(existing?.type ?: WidgetType.SLIDER_VOLUME) }
    var selectedDeviceIndex by remember { mutableStateOf(availableDevices.indexOfFirst { it.deviceId == existing?.targetDeviceId }.coerceAtLeast(0)) }
    var label by remember { mutableStateOf(existing?.configuration?.get("label") ?: "") }
    var appId by remember { mutableStateOf((existing?.action as? ActionSpec.AppLaunch)?.appId?.value ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add widget" else "Configure widget") },
        text = {
            Column {
                TextButton(onClick = { typeMenuExpanded = true }) { Text("Type: ${selectedType.name}") }
                DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                    WidgetType.values().forEach { type ->
                        DropdownMenuItem(text = { Text(type.name) }, onClick = { selectedType = type; typeMenuExpanded = false })
                    }
                }

                if (availableDevices.isNotEmpty()) {
                    TextButton(onClick = { deviceMenuExpanded = true }) {
                        Text("Target: ${availableDevices.getOrNull(selectedDeviceIndex)?.label ?: "-"}")
                    }
                    DropdownMenu(expanded = deviceMenuExpanded, onDismissRequest = { deviceMenuExpanded = false }) {
                        availableDevices.forEachIndexed { index, device ->
                            DropdownMenuItem(text = { Text(device.label) }, onClick = { selectedDeviceIndex = index; deviceMenuExpanded = false })
                        }
                    }
                } else {
                    Text("No capable paired devices available")
                }

                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Display label") })

                if (selectedType == WidgetType.APP_LAUNCH) {
                    OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("appId") })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val device = availableDevices.getOrNull(selectedDeviceIndex) ?: return@TextButton
                    val action = defaultActionFor(selectedType, appId)
                    val widget = Widget(
                        id = existing?.id ?: WidgetId(UUID.randomUUID().toString()),
                        type = selectedType,
                        position = existing?.position ?: nextGridPosition,
                        size = existing?.size ?: GridSize(1, 1),
                        targetDeviceId = device.deviceId,
                        action = action,
                        configuration = if (label.isBlank()) emptyMap() else mapOf("label" to label),
                    )
                    onSave(widget)
                },
                enabled = availableDevices.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun defaultActionFor(type: WidgetType, appId: String): ActionSpec = when (type) {
    WidgetType.SLIDER_BRIGHTNESS -> ActionSpec.BrightnessSet(50)
    WidgetType.SLIDER_VOLUME -> ActionSpec.VolumeSet(50)
    WidgetType.BUTTON_MUTE -> ActionSpec.SetMuted(false)
    WidgetType.BUTTON_MEDIA_PLAY_PAUSE -> ActionSpec.MediaSetState(com.controlldeck.domain.MediaPlaybackState.PLAYING)
    WidgetType.BUTTON_MEDIA_NEXT -> ActionSpec.MediaNext
    WidgetType.BUTTON_MEDIA_PREVIOUS -> ActionSpec.MediaPrevious
    WidgetType.APP_LAUNCH -> ActionSpec.AppLaunch(AppId(appId.ifBlank { "app" }))
}
